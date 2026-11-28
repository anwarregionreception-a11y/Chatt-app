package com.example.ui

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccountStore
import com.example.data.RaabtaRepository
import com.example.data.SavedAccount
import com.example.data.local.*
import com.example.data.remote.Announcement
import com.example.data.remote.ChatBan
import com.example.data.remote.CloudAccountService
import com.example.data.remote.CloudAuthService
import com.example.data.remote.CloudChatService
import com.example.data.remote.CloudCommunityService
import com.example.data.remote.CloudGroupService
import com.example.data.remote.CloudPresenceService
import com.example.data.remote.CloudUser
import com.example.data.remote.PresenceUser
import com.example.data.remote.PrivateSettings
import com.example.data.remote.PublicProfile
import com.example.data.remote.StaffMember
import com.example.data.remote.StaffRole
import com.example.data.remote.readableCloudError
import com.example.data.remote.searchKeywords
import com.example.domain.FloodDecision
import com.example.domain.FloodGuard
import com.example.domain.LinkFilter
import com.example.domain.UserIds
import com.example.notify.Notifier
import com.example.security.PasswordHasher
import com.example.ui.screens.formatDateTime
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
    val account = CloudAccountService()
    val groups = CloudGroupService()
    val community = CloudCommunityService()
    private val accounts = AccountStore(application)
    private val notices = application.getSharedPreferences("notices", Context.MODE_PRIVATE)
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

    /** null while loading, "" when no User ID has been chosen. */
    private val _myUserId = MutableStateFlow<String?>(null)
    val myUserId: StateFlow<String?> = _myUserId.asStateFlow()

    private val _mySettings = MutableStateFlow<PrivateSettings?>(null)
    val mySettings: StateFlow<PrivateSettings?> = _mySettings.asStateFlow()

    private val _myRole = MutableStateFlow<StaffRole?>(null)
    val myRole: StateFlow<StaffRole?> = _myRole.asStateFlow()

    private val _myBan = MutableStateFlow<ChatBan?>(null)
    val myBan: StateFlow<ChatBan?> = _myBan.asStateFlow()

    private val _savedAccounts = MutableStateFlow(accounts.list())
    val savedAccounts: StateFlow<List<SavedAccount>> = _savedAccounts.asStateFlow()

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

    /** The address the account signs in with; empty for accounts without a real email. */
    fun realLoginEmail(): String =
        cloud.currentUser()?.email?.takeUnless { UserIds.isSynthetic(it) }.orEmpty()

    private suspend fun openSession(user: CloudUser) {
        val email = user.email.takeUnless { UserIds.isSynthetic(it) }.orEmpty()
        val isNewOnThisPhone = repository.saveSignedInUser(user.uid, user.name, email)
        resetAccountState()
        _currentUserId.value = user.uid
        _authState.value = AuthUiState()
        startSync(user, isNewOnThisPhone)
    }

    private fun resetAccountState() {
        clearFriendSearch()
        _myUserId.value = null
        _mySettings.value = null
        _myRole.value = null
        _myBan.value = null
        _ownerPanel.value = OwnerPanelState()
        ownerListLimit = OWNER_PAGE_SIZE
    }

    /** Keeps the profile, friend list and messages in step while the app is running. */
    private fun startSync(user: CloudUser, isNewOnThisPhone: Boolean) {
        val uid = user.uid
        syncJob?.cancel()
        refreshedPeers.clear()
        syncJob = viewModelScope.launch {
            launch { syncOwnProfile(user) }
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
            launch { community.observeOwnRole(uid).collect { _myRole.value = it.data?.value } }
            launch { community.observeBan(uid).collect { _myBan.value = it.data } }
            launch {
                community.observeAnnouncements(1).collect { live ->
                    live.data?.firstOrNull()?.let { notifyOnce(it) }
                }
            }
        }
    }

    private fun notifyOnce(message: Announcement) {
        if (notices.getString("last", null) == message.id) return
        if (System.currentTimeMillis() - message.createdAt > NOTICE_MAX_AGE_MS) return
        notices.edit().putString("last", message.id).apply()
        Notifier.show(getApplication<Application>(), message.id.hashCode(), message.title, message.text)
    }

    /** The online wallet and settings are the source; the public profile is built from them. */
    private suspend fun syncOwnProfile(user: CloudUser) {
        val uid = user.uid
        val loginEmail = user.email.takeUnless { UserIds.isSynthetic(it) }
        account.ensureWallet(uid).onFailure {
            _toastMessage.value = "Wallet load nahi hua: ${it.message}"
        }
        account.ensureSettings(uid, user.name, loginEmail.orEmpty()).onFailure {
            _toastMessage.value = "Profile load nahi hua: ${it.message}"
        }
        combine(
            account.observeSettings(uid),
            account.observeWallet(uid),
            account.observeOwnUserId(uid)
        ) { settings, wallet, userId -> Triple(settings, wallet, userId) }
            .distinctUntilChanged()
            .collect { (settingsState, walletState, userIdState) ->
                (settingsState.error ?: walletState.error ?: userIdState.error)?.let {
                    _toastMessage.value = it
                }
                val settings = settingsState.data ?: return@collect
                val wallet = walletState.data ?: return@collect
                val userId = userIdState.data ?: return@collect
                _mySettings.value = settings
                _myUserId.value = userId
                accounts.setUserId(uid, userId)
                _savedAccounts.value = accounts.list()
                repository.applyOwnAccount(
                    uid, settings.name, settings.showCoins, wallet.coins, wallet.authority
                )
                account.publishCard(
                    uid, loginEmail, settings, wallet, userId, user.registeredAt,
                    searchKeywords(settings.name)
                ).onFailure {
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
        return account.setWallet(userId, coins, authority)
    }

    /** Ban, link and flooding rules shared by direct and group chat. */
    private fun chatRuleProblem(senderId: String, text: String, isGroup: Boolean): String? {
        val ban = _myBan.value
        if (ban != null && ban.isActive && (isGroup || ban.allChat)) {
            return "Aap chat se ban hain: ${formatDateTime(ban.until)} tak"
        }
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

    suspend fun sendGroupMessage(
        senderId: String,
        senderName: String,
        groupId: String,
        text: String
    ): Result<Unit> {
        val clean = text.trim().take(2000)
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Message khali hai"))
        chatRuleProblem(senderId, clean, isGroup = true)?.let {
            return Result.failure(IllegalStateException(it))
        }
        return groups.sendMessage(groupId, senderId, senderName, clean)
    }

    /** Opens a chat with a staff member; [onReady] runs when messages can be sent. */
    fun contactStaff(staff: StaffMember, onReady: () -> Unit) {
        runAction { uid ->
            repository.savePeer(staff.uid, staff.name, "")
            val result = community.openStaffContact(uid, staff.uid, staff.name)
            if (result.isSuccess) {
                loadProfile(staff.uid)
                onReady()
            }
            result
        }
    }

    /** Makes sure a local record exists before a chat with this person is opened. */
    fun rememberPerson(uid: String, name: String) {
        viewModelScope.launch {
            repository.savePeer(uid, name, "")
            loadProfile(uid)
        }
    }

    suspend fun requestFriend(fromUserId: String, toUserId: String, toName: String): Result<Unit> {
        repository.savePeer(toUserId, toName, "")
        return sendFriendRequest(fromUserId, toUserId)
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
        chatRuleProblem(senderId, clean, isGroup = false)?.let {
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

    private fun rememberAccount(user: CloudUser, password: String, userId: String?) {
        accounts.save(user.uid, user.name, user.email, password)
        if (userId != null) accounts.setUserId(user.uid, userId)
        _savedAccounts.value = accounts.list()
    }

    /** [identifier] is an email address or a User ID. */
    fun login(identifier: String, password: String) {
        if (identifier.isBlank() || password.isBlank()) {
            showAuthError("Email ya User ID aur password likhein")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthUiState(isLoading = true)
            val typed = identifier.trim()
            var userId: String? = null
            val email = if (typed.contains("@")) {
                typed
            } else {
                val id = UserIds.normalise(typed)
                if (UserIds.problem(id) != null) {
                    showAuthError("User ID ya email ghalat hai")
                    return@launch
                }
                val record = account.findUserId(id).getOrElse {
                    showAuthError(it.message)
                    return@launch
                }
                if (record == null) {
                    showAuthError("Yeh User ID nahi mili")
                    return@launch
                }
                userId = id
                record.loginEmail ?: accounts.emailForUserId(id) ?: UserIds.syntheticEmail(id)
            }
            cloud.signIn(email, password)
                .onSuccess {
                    rememberAccount(it, password, userId)
                    openSession(it)
                }
                .onFailure {
                    showAuthError(
                        if (userId == null) it.message
                        else "${it.message}. Agar account email se bana tha to email se login karein."
                    )
                }
        }
    }

    fun register(name: String, userId: String, email: String, password: String, idLogin: Boolean) {
        val cleanName = name.trim().take(50)
        val id = UserIds.normalise(userId)
        val mail = email.trim()
        val problem = when {
            cleanName.isBlank() -> "Naam likhein"
            UserIds.problem(id) != null -> UserIds.problem(id)
            else -> PasswordHasher.validateNewPassword(password)
        }
        if (problem != null) {
            showAuthError(problem)
            return
        }
        viewModelScope.launch {
            _authState.value = AuthUiState(isLoading = true)
            val taken = account.findUserId(id).getOrElse {
                showAuthError(it.message)
                return@launch
            }
            if (taken != null) {
                showAuthError("User ID already taken")
                return@launch
            }
            val authEmail = mail.ifBlank { UserIds.syntheticEmail(id) }
            cloud.signUp(cleanName, authEmail, password)
                .onSuccess { user ->
                    val loginEmail = if (mail.isNotBlank() && idLogin) user.email else null
                    val claim = account.claimUserId(user.uid, id, loginEmail)
                    rememberAccount(user, password, if (claim.isSuccess) id else null)
                    openSession(user)
                    _toastMessage.value = claim.exceptionOrNull()?.let {
                        "Account ban gaya, magar User ID save nahi hui: ${it.message}"
                    } ?: "Welcome ${user.name}!"
                }
                .onFailure {
                    showAuthError(
                        if (mail.isBlank() && it.message == EMAIL_IN_USE) "User ID already taken"
                        else it.message
                    )
                }
        }
    }

    fun requestForgotPassword(identifier: String) {
        val typed = identifier.trim()
        if (!typed.contains("@")) {
            showAuthError("Apni email likhein. Baghair email wale account ka password reset nahi hota.")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthUiState(isLoading = true)
            cloud.sendPasswordReset(typed)
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
        val result = cloud.changePassword(current, newPassword)
        if (result.isSuccess) {
            cloud.currentUser()?.let { rememberAccount(it, newPassword, null) }
        }
        return result
    }

    /** Signs in to an account saved on this phone. */
    fun switchAccount(uid: String) {
        val saved = _savedAccounts.value.firstOrNull { it.uid == uid } ?: return
        val password = accounts.password(uid)
        if (password == null) {
            _toastMessage.value = "Is ID ka password save nahi. Dobara login karein."
            return
        }
        viewModelScope.launch {
            _authState.value = AuthUiState(isLoading = true)
            syncJob?.cancel()
            cloud.signIn(saved.email, password)
                .onSuccess {
                    openSession(it)
                    _toastMessage.value = "Ab aap ${it.name} ki ID par hain"
                }
                .onFailure { error ->
                    _authState.value = AuthUiState()
                    _toastMessage.value = error.message
                    cloud.currentUser()?.let { startSync(it, false) }
                }
        }
    }

    fun forgetAccount(uid: String) {
        accounts.remove(uid)
        _savedAccounts.value = accounts.list()
    }

    /** Goes to the login screen and keeps this account saved for switching back. */
    fun addAnotherAccount() {
        cloud.currentUser()?.let { user ->
            if (accounts.password(user.uid) == null) {
                _toastMessage.value = "Yeh ID save nahi thi; wapas aane ke liye password se login karein"
            }
        }
        signOutToLogin()
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
            findPeople(query)
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

    /** Search by User ID, email or name. */
    private suspend fun findPeople(query: String): Result<List<CloudUser>> {
        val byName = cloud.searchUsers(query)
        val id = UserIds.normalise(query)
        if (UserIds.problem(id) != null) return byName
        val record = account.findUserId(id).getOrNull() ?: return byName
        val profile = cloud.fetchProfile(record.uid).getOrNull() ?: return byName
        val match = CloudUser(record.uid, profile.name, "", profile)
        return Result.success(
            listOf(match) + byName.getOrDefault(emptyList()).filter { it.uid != record.uid }
        )
    }

    fun clearFriendSearch() {
        searchJob?.cancel()
        _friendSearch.value = FriendSearchState()
    }

    /** Logs out and removes this account from the phone's saved list. */
    fun logout() {
        _currentUserId.value?.let { forgetAccount(it) }
        signOutToLogin()
    }

    private fun signOutToLogin() {
        syncJob?.cancel()
        _currentUserId.value?.let { account.clearActivity(it) }
        cloud.signOut()
        resetAccountState()
        _currentUserId.value = null
        _authState.value = AuthUiState()
    }

    fun observeConversation(peerId: String): Flow<List<DirectMessageEntity>> {
        val uid = _currentUserId.value ?: return flowOf(emptyList())
        return repository.observeConversation(uid, peerId)
    }

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
        const val NOTICE_MAX_AGE_MS = 3L * 24L * 60L * 60L * 1000L
        const val EMAIL_IN_USE = "Is email se account pehle se bana hua hai"
    }
}
