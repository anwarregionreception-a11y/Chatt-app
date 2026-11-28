package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.RaabtaRepository
import com.example.data.local.*
import com.example.data.remote.CloudAuthService
import com.example.data.remote.CloudChatService
import com.example.data.remote.CloudUser
import com.example.data.remote.readableCloudError
import com.example.security.PasswordHasher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val infoMessage: String? = null
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
        repository.saveSignedInUser(user.uid, user.name, user.email)
        _currentUserId.value = user.uid
        _authState.value = AuthUiState()
        startSync(user.uid)
        viewModelScope.launch {
            cloud.ensureProfile(user).onFailure { e ->
                _toastMessage.value = "Profile online save nahi hua: ${e.message}"
            }
        }
    }

    /** Listens for friend requests and new messages while the app is running. */
    private fun startSync(uid: String) {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            launch {
                chat.observeFriendships(uid)
                    .catch { _toastMessage.value = readableCloudError(it) }
                    .collect { repository.syncFriendships(uid, it) }
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
                        .map { repository.savePeer(it.uid, it.name, it.email) }
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
}
