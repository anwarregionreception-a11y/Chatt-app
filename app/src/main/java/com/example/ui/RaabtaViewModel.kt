package com.example.ui

import android.app.Application
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.RaabtaRepository
import com.example.data.local.*
import com.example.data.remote.CloudAuthService
import com.example.data.remote.CloudChatService
import com.example.data.remote.CloudPresenceService
import com.example.data.remote.CloudUser
import com.example.data.remote.PresenceUser
import com.example.data.remote.PublicProfile
import com.example.data.remote.readableCloudError
import com.example.domain.FloodDecision
import com.example.domain.FloodGuard
import com.example.domain.LinkFilter
import com.example.security.PasswordHasher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val infoMessage: String? = null
)

data class OwnerPanelState(
    val isLoading: Boolean = false,
    val total: Long = 0L,
    val online: Long = 0L,
    val users: List<PresenceUser> = emptyList(),
    val hasMore: Boolean = false,
    val error: String? = null,
    val deviceAccounts: List<PresenceUser>? = null
)

data class FriendSearchState(
    val isSearching: Boolean = false,
    val results: List<UserEntity> = emptyList(),
    val message: String? = null
)

class RaabtaViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).appDao()
    val repository = RaabtaRepository(dao)
    private val cloud = CloudAuthService()
    private val chat = CloudChatService()
    private var syncJob: Job? = null
    private val refreshedPeers = mutableSetOf<String>()

    private val presence = CloudPresenceService()
    private var lastHeartbeat: Pair<String, Long>? = null
    private val floodGuards = mutableMapOf<String, FloodGuard>()

    private val _ownerPanel = MutableStateFlow(OwnerPanelState())
    val ownerPanel: StateFlow<OwnerPanelState> = _ownerPanel.asStateFlow()
    private var ownerListLimit = OWNER_PAGE_SIZE

    private val _friendSearch = MutableStateFlow(FriendSearchState())
    val friendSearch: StateFlow<FriendSearchState> = _friendSearch.asStateFlow()
    private var searchJob: Job? = null

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _authState = MutableStateFlow(AuthUiState())
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    val allUsers: StateFlow<List<UserEntity>> = repository.observeAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentUser: StateFlow<UserEntity?> = _currentUserId
        .flatMapLatest { uid ->
            if (uid == null) flowOf(null) else repository.observeUser(uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val friendRelations: StateFlow<List<FriendRelationEntity>> = _currentUserId
        .flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList()) else repository.observeFriendRelations(uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userDirectMessages: StateFlow<List<DirectMessageEntity>> = _currentUserId
        .flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList()) else repository.observeUserDirectMessages(uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allGroups: StateFlow<List<GroupEntity>> = repository.observeAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allGroupMembers: StateFlow<List<GroupMemberEntity>> = repository.observeAllGroupMembers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCoinOrders: StateFlow<List<CoinOrderEntity>> = repository.observeAllCoinOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ownerComplaints: StateFlow<List<SystemComplaintEntity>> = _currentUserId
        .flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList()) else repository.observeOwnerComplaints(uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRatings: StateFlow<List<AppRatingEntity>> = repository.observeAllRatings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Stay signed in between app launches.
        cloud.currentUser()?.let { user ->
            viewModelScope.launch { openSession(user) }
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun showMessage(msg: String) {
        _toastMessage.value = msg
    }

    fun clearAuthError() {
        _authState.update { it.copy(error = null) }
    }

    fun clearInfoMessage() {
        _authState.update { it.copy(infoMessage = null) }
    }

    private suspend fun openSession(user: CloudUser) {
        val isNewOnThisPhone = repository.saveSignedInUser(user.uid, user.name, user.email)
        _currentUserId.value = user.uid
        _authState.value = AuthUiState()
        startSync(user, isNewOnThisPhone)
    }

    /** Keeps the profile, friend list and messages in step while the app is running. */
    private fun startSync(user: CloudUser, isNewOnThisPhone: Boolean) {
        val uid = user.uid
        syncJob?.cancel()
        refreshedPeers.clear()
        syncJob = viewModelScope.launch {
            launch { syncOwnProfile(user, isNewOnThisPhone) }
            launch {
                chat.observeFriendships(uid)
                    .catch { _toastMessage.value = readableCloudError(it) }
                    .collect { online ->
                        repository.syncFriendships(uid, online)
                        // One profile read per friend each time the app starts
                        online.items.flatMap { it.members }
                            .filter { it != uid && refreshedPeers.add(it) }
                            .forEach { peerId -> launch { loadProfile(peerId) } }
                    }
            }
            launch {
                chat.observeInbox(uid)
                    .catch { _toastMessage.value = readableCloudError(it) }
                    .collect { messages ->
                        if (messages.isNotEmpty()) {
                            messages.forEach { repository.saveMessage(uid, it) }
                            chat.removeDelivered(uid, messages.map { it.id })
                        }
                    }
            }
        }
    }

    private suspend fun syncOwnProfile(user: CloudUser, isNewOnThisPhone: Boolean) {
        if (isNewOnThisPhone) {
            // Restore the online values first so new defaults do not overwrite them.
            val saved = cloud.fetchProfile(user.uid).getOrElse {
                _toastMessage.value = "Profile load nahi hua: ${it.message}"
                return
            }
            if (saved != null) repository.applyProfile(user.uid, saved, isOwnAccount = true)
        }
        repository.observeUser(user.uid)
            .filterNotNull()
            .map { PublicProfile(it.name, it.showCoinsOnProfile, it.walletCoins, it.authorityCoins) }
            .distinctUntilChanged()
            .collect { profile ->
                cloud.publishProfile(user.uid, user.email, profile).onFailure {
                    _toastMessage.value = "Profile online save nahi hua: ${it.message}"
                }
            }
    }

    private suspend fun loadProfile(peerId: String) {
        cloud.fetchProfile(peerId).getOrNull()?.let {
            repository.applyProfile(peerId, it, isOwnAccount = false)
        }
    }

    /** Fetches the latest public details of another user. */
    fun refreshProfile(peerId: String) {
        if (peerId == _currentUserId.value) return
        viewModelScope.launch { loadProfile(peerId) }
    }

    /** Records that the app is open. Called on screen and then every few minutes. */
    fun heartbeat() {
        val user = cloud.currentUser() ?: return
        val now = System.currentTimeMillis()
        val last = lastHeartbeat
        // Quick app switching should not cost extra database writes.
        if (last != null && last.first == user.uid && now - last.second < MIN_HEARTBEAT_GAP_MS) return
        lastHeartbeat = user.uid to now
        val deviceId = Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()
        viewModelScope.launch {
            presence.heartbeat(user.uid, user.name, deviceId, user.registeredAt)
                .onFailure { lastHeartbeat = null }
        }
    }

    fun loadOwnerPanel(more: Boolean = false) {
        if (_currentUserId.value != AppConstants.OWNER_UID) return
        if (more) ownerListLimit += OWNER_PAGE_SIZE
        viewModelScope.launch {
            _ownerPanel.update { it.copy(isLoading = true, error = null) }
            presence.loadDirectory(ownerListLimit)
                .onSuccess { directory ->
                    _ownerPanel.update {
                        it.copy(
                            isLoading = false,
                            total = directory.total,
                            online = directory.online,
                            users = directory.users,
                            hasMore = directory.users.size < directory.total
                        )
                    }
                }
                .onFailure { e -> _ownerPanel.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun showDeviceAccounts(deviceId: String) {
        viewModelScope.launch {
            presence.accountsOnDevice(deviceId)
                .onSuccess { accounts -> _ownerPanel.update { it.copy(deviceAccounts = accounts) } }
                .onFailure { e -> _ownerPanel.update { it.copy(error = e.message) } }
        }
    }

    fun closeDeviceAccounts() {
        _ownerPanel.update { it.copy(deviceAccounts = null) }
    }

    suspend fun setOwnerBalance(userId: String, coins: Long, authority: Long): Result<Unit> {
        if (userId != AppConstants.OWNER_UID) {
            return Result.failure(IllegalAccessException("Sirf Owner yeh kar sakta hai"))
        }
        repository.setBalance(userId, coins, authority)
        return Result.success(Unit)
    }

    /** Link and flooding rules shared by direct and group chat. */
    private fun chatRuleProblem(senderId: String, text: String): String? {
        if (LinkFilter.containsLink(text)) return "Chat mein website ya app ke links bhejna mana hai"
        val guard = floodGuards.getOrPut(senderId) { FloodGuard() }
        return when (val decision = guard.check(SystemClock.elapsedRealtime())) {
            FloodDecision.Allowed -> null
            FloodDecision.Warning ->
                "Warning: itni tezi se message na bhejein, warna 90 second ke liye ban honge"
            is FloodDecision.Blocked ->
                "Flooding ki wajah se ${decision.secondsLeft} second baad message bhej sakenge"
        }
    }

    suspend fun sendGroupMessage(senderId: String, groupId: String, text: String, tags: String): Result<Unit> {
        chatRuleProblem(senderId, text)?.let {
            return Result.failure(IllegalStateException(it))
        }
        return repository.sendGroupMessage(senderId, groupId, text, tags)
    }

    suspend fun sendFriendRequest(fromUserId: String, toUserId: String): Result<Unit> {
        if (fromUserId == toUserId) {
            return Result.failure(IllegalArgumentException("Aap khud ko add nahi kar sakte"))
        }
        val existing = repository.findFriendRelation(fromUserId, toUserId)
        if (existing != null) {
            val reason = if (existing.status == "ACCEPTED") "Aap pehle se friends hain" else "Request pehle se pending hai"
            return Result.failure(IllegalStateException(reason))
        }
        val target = repository.getUserById(toUserId)
            ?: return Result.failure(IllegalArgumentException("User nahi mila"))
        return chat.sendRequest(fromUserId, toUserId, target.name)
    }

    suspend fun respondFriendRequest(relationId: String, accept: Boolean): Result<Unit> =
        if (accept) chat.acceptRequest(relationId) else chat.removeFriendship(relationId)

    suspend fun sendMessage(senderId: String, receiverId: String, text: String): Result<Unit> {
        val clean = text.trim().take(2000)
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Message khali hai"))
        val relation = repository.findFriendRelation(senderId, receiverId)
        if (relation == null || relation.status != "ACCEPTED") {
            return Result.failure(IllegalStateException("Pehle friend request accept honi chahiye"))
        }
        chatRuleProblem(senderId, clean)?.let {
            return Result.failure(IllegalStateException(it))
        }
        return chat.sendMessage(senderId, receiverId, relation.id, clean).fold(
            onSuccess = {
                repository.saveMessage(receiverId, it)
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) }
        )
    }

    private fun showAuthError(message: String?) {
        _authState.update { it.copy(isLoading = false, error = message) }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            showAuthError("Email aur password likhein")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthUiState(isLoading = true)
            cloud.signIn(email.trim(), password)
                .onSuccess { openSession(it) }
                .onFailure { showAuthError(it.message) }
        }
    }

    fun register(name: String, email: String, password: String) {
        val cleanName = name.trim().take(50)
        val problem = when {
            cleanName.isBlank() || email.isBlank() -> "Naam aur email likhein"
            else -> PasswordHasher.validateNewPassword(password)
        }
        if (problem != null) {
            showAuthError(problem)
            return
        }
        viewModelScope.launch {
            _authState.value = AuthUiState(isLoading = true)
            cloud.signUp(cleanName, email.trim(), password)
                .onSuccess {
                    openSession(it)
                    _toastMessage.value = "Welcome ${it.name}!"
                }
                .onFailure { showAuthError(it.message) }
        }
    }

    fun requestForgotPassword(email: String) {
        if (email.isBlank()) {
            showAuthError("Apni email likhein")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthUiState(isLoading = true)
            cloud.sendPasswordReset(email.trim())
                .onSuccess {
                    _authState.value = AuthUiState(
                        infoMessage = "Agar is email par account hai to reset link bhej diya gaya hai. Spam folder bhi dekhein."
                    )
                }
                .onFailure { showAuthError(it.message) }
        }
    }

    suspend fun changePassword(current: String, newPassword: String, confirm: String): Result<Unit> {
        if (newPassword != confirm) {
            return Result.failure(IllegalArgumentException("Password match nahi ho raha"))
        }
        PasswordHasher.validateNewPassword(newPassword)?.let {
            return Result.failure(IllegalArgumentException(it))
        }
        return cloud.changePassword(current, newPassword)
    }

    fun searchUsers(query: String) {
        val uid = _currentUserId.value ?: return
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _friendSearch.value = FriendSearchState(message = "Kam az kam 2 huroof likhein")
            return
        }
        searchJob = viewModelScope.launch {
            _friendSearch.value = FriendSearchState(isSearching = true)
            cloud.searchUsers(query)
                .onSuccess { found ->
                    val people = found.filter { it.uid != uid }
                        .map { person ->
                            val saved = repository.savePeer(person.uid, person.name, person.email)
                            person.profile?.let {
                                repository.applyProfile(person.uid, it, isOwnAccount = false)
                            }
                            repository.getUserById(person.uid) ?: saved
                        }
                    _friendSearch.value = FriendSearchState(
                        results = people,
                        message = if (people.isEmpty()) "Koi user nahi mila" else null
                    )
                }
                .onFailure { _friendSearch.value = FriendSearchState(message = it.message) }
        }
    }

    fun clearFriendSearch() {
        searchJob?.cancel()
        _friendSearch.value = FriendSearchState()
    }

    fun logout() {
        syncJob?.cancel()
        cloud.signOut()
        clearFriendSearch()
        _ownerPanel.value = OwnerPanelState()
        ownerListLimit = OWNER_PAGE_SIZE
        _currentUserId.value = null
        _authState.value = AuthUiState()
    }

    fun observeConversation(peerId: String): Flow<List<DirectMessageEntity>> {
        val uid = _currentUserId.value ?: return flowOf(emptyList())
        return repository.observeConversation(uid, peerId)
    }

    fun observeGroup(groupId: String): Flow<GroupEntity?> = repository.observeGroup(groupId)
    fun observeGroupMembers(groupId: String): Flow<List<GroupMemberEntity>> = repository.observeGroupMembers(groupId)
    fun observeGroupMessages(groupId: String): Flow<List<GroupMessageEntity>> = repository.observeGroupMessages(groupId)
    fun observeGroupDonations(groupId: String): Flow<List<GroupDonationEntity>> = repository.observeGroupDonations(groupId)

    // Generic action runner
    fun runAction(successMessage: String? = null, block: suspend (String) -> Result<Any>) {
        val uid = _currentUserId.value ?: return
        viewModelScope.launch {
            block(uid)
                .onSuccess { res ->
                    if (successMessage != null) {
                        _toastMessage.value = successMessage
                    } else if (res is String && res.isNotBlank()) {
                        _toastMessage.value = res
                    }
                }
                .onFailure { e ->
                    _toastMessage.value = e.message ?: "Action failed"
                }
        }
    }

    private companion object {
        const val OWNER_PAGE_SIZE = 200L
        const val MIN_HEARTBEAT_GAP_MS = 4L * 60L * 1000L
    }
}
