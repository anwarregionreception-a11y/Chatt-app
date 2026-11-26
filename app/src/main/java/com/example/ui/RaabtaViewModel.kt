package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LoginResult
import com.example.data.RaabtaRepository
import com.example.data.local.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val deliveredTempInfo: String? = null,
    val pendingForceChangeUserId: String? = null,
    val prefilledTempPass: String = ""
)

class RaabtaViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).appDao()
    val repository = RaabtaRepository(dao)

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
        viewModelScope.launch {
            repository.ensureSeeded()
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

    fun clearDeliveredTempInfo() {
        _authState.update { it.copy(deliveredTempInfo = null) }
    }

    fun login(identifier: String, password: String) {
        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, error = null) }
            when (val res = repository.login(identifier, password)) {
                is LoginResult.Success -> {
                    _authState.update { it.copy(isLoading = false, pendingForceChangeUserId = null, prefilledTempPass = "") }
                    _currentUserId.value = res.user.id
                }
                is LoginResult.ForceChangePassword -> {
                    _authState.update {
                        it.copy(
                            isLoading = false,
                            pendingForceChangeUserId = res.user.id,
                            prefilledTempPass = res.prefilledTemp
                        )
                    }
                }
                is LoginResult.Error -> {
                    _authState.update { it.copy(isLoading = false, error = res.message) }
                }
            }
        }
    }

    fun register(name: String, email: String, mobile: String, password: String) {
        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, error = null) }
            repository.registerUser(name, email, mobile, password)
                .onSuccess { user ->
                    _authState.update { it.copy(isLoading = false) }
                    _currentUserId.value = user.id
                    _toastMessage.value = "Welcome ${user.name}! +50 Starter Coins added."
                }
                .onFailure { e ->
                    _authState.update { it.copy(isLoading = false, error = e.message ?: "Registration failed") }
                }
        }
    }

    fun requestForgotPassword(identifier: String) {
        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, error = null, deliveredTempInfo = null) }
            repository.requestUserForgotPassword(identifier)
                .onSuccess { (user, tempPass) ->
                    _authState.update {
                        it.copy(
                            isLoading = false,
                            deliveredTempInfo = "Email (${user.email}) & SMS (${user.mobileNumber}) sent!\nTemporary Password: $tempPass (Valid 15 mins, single-use)"
                        )
                    }
                }
                .onFailure { e ->
                    _authState.update { it.copy(isLoading = false, error = e.message ?: "Reset failed") }
                }
        }
    }

    fun completeForceChangePassword(tempInput: String, newPass: String, confirmPass: String) {
        val uid = _authState.value.pendingForceChangeUserId ?: return
        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, error = null) }
            repository.completeForceChangePassword(uid, tempInput, newPass, confirmPass)
                .onSuccess { user ->
                    _authState.update {
                        it.copy(
                            isLoading = false,
                            pendingForceChangeUserId = null,
                            prefilledTempPass = "",
                            deliveredTempInfo = null
                        )
                    }
                    _currentUserId.value = user.id
                    _toastMessage.value = "Password updated successfully!"
                }
                .onFailure { e ->
                    _authState.update { it.copy(isLoading = false, error = e.message ?: "Could not update password") }
                }
        }
    }

    fun cancelForceChangePassword() {
        _authState.update {
            it.copy(pendingForceChangeUserId = null, prefilledTempPass = "", error = null)
        }
    }

    fun logout() {
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
