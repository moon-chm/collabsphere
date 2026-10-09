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
import java.util.UUID

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

    private val _isDeletingWorkspace = MutableStateFlow(false)
    val isDeletingWorkspace: StateFlow<Boolean> = _isDeletingWorkspace.asStateFlow()

    private val _isCreatingWorkspace = MutableStateFlow(false)
    val isCreatingWorkspace: StateFlow<Boolean> = _isCreatingWorkspace.asStateFlow()

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
        if (_isCreatingWorkspace.value) return
        val name = _workspaceName.value.trim()
        val owner = _workspaceOwner.value.trim()
        val password = _workspacePassword.value.trim()

        if (name.isEmpty() || owner.isEmpty() || password.isEmpty()) {
            _workspaceStatus.value = "Please fill in all values"
            return
        }

        viewModelScope.launch {
            _isCreatingWorkspace.value = true
            try {
                val result = repo.addWorkspaceToScreen(
                    WorkspaceEntity(
                        id = 0,
                        userId = loggedInUserId,
                        workspaceName = name,
                        workspaceOwner = owner,
                        workspacePassword = password
                    ),
                    clientRequestId = UUID.randomUUID().toString()
                )

                result.onSuccess {
                    _workspaceStatus.value = "Workspace created successfully!"
                    clearInputs()
                }

                result.onFailure {
                    _workspaceStatus.value = it.localizedMessage ?: "Failed to create workspace"
                }
            } finally {
                _isCreatingWorkspace.value = false
            }
        }
    }

    private val _invitationStatus = MutableStateFlow<String?>(null)
    val invitationStatus: StateFlow<String?> = _invitationStatus.asStateFlow()

    private val _joinByCodeStatus = MutableStateFlow<String?>(null)
    val joinByCodeStatus: StateFlow<String?> = _joinByCodeStatus.asStateFlow()

    private val _isSendingInvitation = MutableStateFlow(false)
    val isSendingInvitation: StateFlow<Boolean> = _isSendingInvitation.asStateFlow()

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
            _isSendingInvitation.value = true
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
            _isSendingInvitation.value = false
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

    fun onDeleteWorkspace(workspaceId: Int, expectedWorkspaceName: String) {
        val name = _workspaceName.value.trim()
        val password = _workspacePassword.value.trim()

        if (name.isEmpty() || password.isEmpty() || workspaceId <= 0) {
            _workspaceStatus.value = "Workspace name and password are required"
            return
        }
        if (!name.equals(expectedWorkspaceName.trim(), ignoreCase = true)) {
            _workspaceStatus.value = "Workspace name does not match"
            return
        }
        if (_isDeletingWorkspace.value) return

        viewModelScope.launch {
            _isDeletingWorkspace.value = true
            try {
                val rowsDeleted = repo.deleteWorkspaceFromScreen(
                    workspaceId,
                    password
                )

                if (rowsDeleted > 0) {
                    _workspaceStatus.value = "Workspace deleted successfully!"
                    clearInputs()
                } else {
                    _workspaceStatus.value = "Incorrect workspace name or password."
                }
            } catch (e: Exception) {
                if (e.message?.contains("404") == true || e.message?.contains("NotFound") == true) {
                    _workspaceStatus.value = "Incorrect workspace name or password."
                } else {
                    _workspaceStatus.value = "Network error. Please try again later."
                }
            } finally {
                _isDeletingWorkspace.value = false
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
