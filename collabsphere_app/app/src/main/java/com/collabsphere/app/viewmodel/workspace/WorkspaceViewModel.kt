package com.collabsphere.app.viewmodel.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabsphere.app.model.UserEntity
import com.collabsphere.app.model.workspace.WorkspaceEntity
import com.collabsphere.app.model.workspace.WorkspaceRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WorkspaceViewModel(
    private val repo: WorkspaceRepo,
    private val loggedInUserId: Int
) : ViewModel() {

    private val _workspaceName = MutableStateFlow("")
    val workspaceName: StateFlow<String> = _workspaceName.asStateFlow()

    private val _workspaceOwner = MutableStateFlow("")
    val workspaceOwner: StateFlow<String> = _workspaceOwner.asStateFlow()

    private val _workspacePassword = MutableStateFlow("")
    val workspacePassword: StateFlow<String> = _workspacePassword.asStateFlow()

    private val _workspaceStatus = MutableStateFlow<String?>(null)
    val workspaceStatus: StateFlow<String?> = _workspaceStatus.asStateFlow()

    private val _workspaceMembers = MutableStateFlow<List<UserEntity>>(emptyList())
    val workspaceMembers: StateFlow<List<UserEntity>> = _workspaceMembers.asStateFlow()

    fun loadWorkspaceMembers(workspaceId: Int) {
        viewModelScope.launch {
            repo.syncWorkspaceMembers(workspaceId)
            repo.getWorkspaceMembersFlow(workspaceId).collectLatest {
                _workspaceMembers.value = it
            }
        }
    }

    fun onNameChange(name: String) {
        _workspaceName.value = name
    }

    fun onOwnerChange(owner: String) {
        _workspaceOwner.value = owner
    }

    fun onPasswordChange(password: String) {
        _workspacePassword.value = password
    }

    init {
        viewModelScope.launch {
            repo.startDeltaSyncLoop(loggedInUserId)
        }
    }

    fun onCreateWorkspace() {
        val name = _workspaceName.value.trim()
        val owner = _workspaceOwner.value.trim()
        val password = _workspacePassword.value.trim()

        if (name.isEmpty() || owner.isEmpty() || password.isEmpty()) {
            _workspaceStatus.value = "Please fill in all values"
            return
        }

        viewModelScope.launch {
            val result = repo.addWorkspaceToScreen(
                WorkspaceEntity(
                    id = 0,
                    userId = loggedInUserId,
                    workspaceName = name,
                    workspaceOwner = owner,
                    workspacePassword = password
                )
            )

            result.onSuccess {
                _workspaceStatus.value = "Workspace created successfully!"
                clearInputs()
            }

            result.onFailure {
                _workspaceStatus.value = it.localizedMessage ?: "Failed to create workspace"
            }
        }
    }

    private val _invitationStatus = MutableStateFlow<String?>(null)
    val invitationStatus: StateFlow<String?> = _invitationStatus.asStateFlow()

    private val _joinByCodeStatus = MutableStateFlow<String?>(null)
    val joinByCodeStatus: StateFlow<String?> = _joinByCodeStatus.asStateFlow()

    fun onJoinWorkspace(workspaceId: Int, email: String) {
        sendInvitation(workspaceId, email)
    }

    fun sendInvitation(workspaceId: Int, email: String) {
        val trimmedEmail = email.trim().lowercase()

        if (trimmedEmail.isEmpty()) {
            _invitationStatus.value = "Email address cannot be empty"
            return
        }

        viewModelScope.launch {
            val result = repo.sendInvitation(workspaceId, trimmedEmail)
            result.onSuccess {
                _invitationStatus.value = "Invitation sent to $trimmedEmail with invite code: ${it.inviteCode}"
                _workspaceStatus.value = "Invitation sent to $trimmedEmail"
                clearInputs()
                loadWorkspaceMembers(workspaceId)
            }
            result.onFailure {
                _invitationStatus.value = it.localizedMessage ?: "Failed to send invitation"
                _workspaceStatus.value = it.localizedMessage ?: "Failed to send invitation"
            }
        }
    }

    fun joinByCode(inviteCode: String, onJoined: ((Int) -> Unit)? = null) {
        val trimmedCode = inviteCode.trim().uppercase()
        if (trimmedCode.length < 6) {
            _joinByCodeStatus.value = "Please enter a valid 6-character invite code"
            return
        }

        viewModelScope.launch {
            val result = repo.joinWorkspaceByCode(trimmedCode, loggedInUserId)
            result.onSuccess { member ->
                _joinByCodeStatus.value = "Successfully joined workspace!"
                _workspaceStatus.value = "Successfully joined workspace!"
                onJoined?.invoke(member.workspaceId)
            }
            result.onFailure {
                _joinByCodeStatus.value = it.localizedMessage ?: "Invalid or expired invite code"
            }
        }
    }

    fun acceptInvitation(invitationId: Int, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val result = repo.acceptInvitation(invitationId)
            result.onSuccess {
                _workspaceStatus.value = "Joined workspace!"
                onComplete?.invoke()
            }
            result.onFailure {
                _workspaceStatus.value = it.localizedMessage ?: "Failed to accept invitation"
            }
        }
    }

    fun declineInvitation(invitationId: Int, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val result = repo.declineInvitation(invitationId)
            result.onSuccess {
                _workspaceStatus.value = "Invitation declined"
                onComplete?.invoke()
            }
            result.onFailure {
                _workspaceStatus.value = it.localizedMessage ?: "Failed to decline invitation"
            }
        }
    }

    fun clearInvitationStatus() {
        _invitationStatus.value = null
    }

    fun clearJoinByCodeStatus() {
        _joinByCodeStatus.value = null
    }

    fun onDeleteWorkspace() {
        val name = _workspaceName.value.trim()
        val password = _workspacePassword.value.trim()

        if (name.isEmpty() || password.isEmpty()) {
            _workspaceStatus.value = "Fields cannot be empty"
            return
        }

        viewModelScope.launch {
            val rowsDeleted = repo.deleteWorkspaceFromScreen(
                name,
                loggedInUserId,
                password
            )

            if (rowsDeleted > 0) {
                _workspaceStatus.value = "Workspace deleted successfully!"
                clearInputs()
            } else {
                _workspaceStatus.value = "Network error. Please try again later."
            }
        }
    }

    fun clearWorkspaceStatus() {
        _workspaceStatus.value = null
    }

    fun clearInputs() {
        _workspaceName.value = ""
        _workspaceOwner.value = ""
        _workspacePassword.value = ""
    }
}