package com.collabsphere.app.viewmodel.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabsphere.app.SessionManager
import com.collabsphere.app.UserPreferences
import com.collabsphere.app.model.UserRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class ProfileViewModel(
    val loggedInUserId: Int,
    private val userEmail: String,
    private val repo: UserRepo,
    private val userPreferences: UserPreferences,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _updatedUserName = MutableStateFlow("")
    val updatedUserName: StateFlow<String> = _updatedUserName.asStateFlow()

    private val _currentPassword = MutableStateFlow("")
    val currentPassword: StateFlow<String> = _currentPassword.asStateFlow()

    private val _newPassword = MutableStateFlow("")
    val newPassword: StateFlow<String> = _newPassword.asStateFlow()

    private val _profileStatus = MutableStateFlow("")
    val profileStatus: StateFlow<String> = _profileStatus.asStateFlow()

    // ── Full profile (avatar, bio, status, email, verification) ────────────────
    private val _email = MutableStateFlow(userEmail)
    val email: StateFlow<String> = _email.asStateFlow()

    private val _avatarUrl = MutableStateFlow("")
    val avatarUrl: StateFlow<String> = _avatarUrl.asStateFlow()

    private val _bio = MutableStateFlow("")
    val bio: StateFlow<String> = _bio.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _isEmailVerified = MutableStateFlow(false)
    val isEmailVerified: StateFlow<Boolean> = _isEmailVerified.asStateFlow()

    private val _lastSeen = MutableStateFlow<Long?>(null)
    val lastSeen: StateFlow<Long?> = _lastSeen.asStateFlow()

    private val _isLoadingProfile = MutableStateFlow(false)
    val isLoadingProfile: StateFlow<Boolean> = _isLoadingProfile.asStateFlow()

    private val _isUploadingAvatar = MutableStateFlow(false)
    val isUploadingAvatar: StateFlow<Boolean> = _isUploadingAvatar.asStateFlow()

    // ── Change email dialog ─────────────────────────────────────────────────────
    private val _showEmailDialog = MutableStateFlow(false)
    val showEmailDialog: StateFlow<Boolean> = _showEmailDialog.asStateFlow()

    private val _newEmail = MutableStateFlow("")
    val newEmailInput: StateFlow<String> = _newEmail.asStateFlow()

    private val _emailChangePassword = MutableStateFlow("")
    val emailChangePassword: StateFlow<String> = _emailChangePassword.asStateFlow()

    private val _awaitingVerification = MutableStateFlow(false)
    val awaitingVerification: StateFlow<Boolean> = _awaitingVerification.asStateFlow()

    private val _verificationToken = MutableStateFlow("")
    val verificationToken: StateFlow<String> = _verificationToken.asStateFlow()

    // ── Delete account dialog ────────────────────────────────────────────────────
    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()

    private val _deleteAccountPassword = MutableStateFlow("")
    val deleteAccountPassword: StateFlow<String> = _deleteAccountPassword.asStateFlow()

    private val _isDeletingAccount = MutableStateFlow(false)
    val isDeletingAccount: StateFlow<Boolean> = _isDeletingAccount.asStateFlow()

    // ── Privacy settings ─────────────────────────────────────────────────────────
    private val _showEmail = MutableStateFlow(true)
    val showEmail: StateFlow<Boolean> = _showEmail.asStateFlow()

    private val _showOnlineStatus = MutableStateFlow(true)
    val showOnlineStatus: StateFlow<Boolean> = _showOnlineStatus.asStateFlow()

    private val _showLastSeen = MutableStateFlow(true)
    val showLastSeen: StateFlow<Boolean> = _showLastSeen.asStateFlow()

    private val _profileVisibility = MutableStateFlow("public")
    val profileVisibility: StateFlow<String> = _profileVisibility.asStateFlow()

    private val _isSavingPrivacy = MutableStateFlow(false)
    val isSavingPrivacy: StateFlow<Boolean> = _isSavingPrivacy.asStateFlow()

    fun onUserNameChanged(name: String) {
        _updatedUserName.value = name
    }

    fun onCurrentPasswordChanged(pass: String) {
        _currentPassword.value = pass
    }

    fun onNewPasswordChanged(pass: String) {
        _newPassword.value = pass
    }

    fun onBioChanged(value: String) {
        _bio.value = value
    }

    fun onStatusMessageChanged(value: String) {
        _statusMessage.value = value
    }

    fun clearProfileStatus() {
        _profileStatus.value = ""
    }

    /** Loads the full profile (avatar/bio/status/email verification) — call once when the screen opens. */
    fun loadProfile() {
        viewModelScope.launch {
            _isLoadingProfile.value = true
            repo.fetchProfile(loggedInUserId)
                .onSuccess { profile ->
                    _avatarUrl.value = profile.avatarUrl
                    _bio.value = profile.bio ?: ""
                    _statusMessage.value = profile.statusMessage ?: ""
                    _email.value = profile.email
                    _isEmailVerified.value = profile.isEmailVerified
                    _lastSeen.value = profile.lastSeen
                    _showEmail.value = profile.showEmail
                    _showOnlineStatus.value = profile.showOnlineStatus
                    _showLastSeen.value = profile.showLastSeen
                    _profileVisibility.value = profile.profileVisibility
                    if (_updatedUserName.value.isEmpty()) {
                        _updatedUserName.value = profile.username
                    }
                }
                .onFailure {
                    _profileStatus.value = "Could not load full profile: ${it.message}"
                }
            _isLoadingProfile.value = false
        }
    }

    fun onUpdateProfile() {
        val inputUsername = updatedUserName.value.trim()
        val inputCurrentPassword = currentPassword.value.trim()
        val inputNewPassword = newPassword.value.trim()
        val inputBio = bio.value.trim()
        val inputStatus = statusMessage.value.trim()

        if (inputUsername.isEmpty()) {
            _profileStatus.value = "Username cannot be empty"
            return
        }

        viewModelScope.launch {
            try {
                val changingPassword =
                    inputCurrentPassword.isNotEmpty() || inputNewPassword.isNotEmpty()

                if (changingPassword && (inputCurrentPassword.isEmpty() || inputNewPassword.isEmpty())) {
                    _profileStatus.value =
                        "Both current and new password fields are required to change password"
                    return@launch
                }

                val currentPassArg = if (changingPassword) inputCurrentPassword else null
                val newPassArg = if (changingPassword) inputNewPassword else null

                val isSuccess = repo.updateProfile(
                    userId = loggedInUserId,
                    userEmail = _email.value,
                    newName = inputUsername,
                    bio = inputBio.ifEmpty { null },
                    statusMessage = inputStatus.ifEmpty { null },
                    currentPassword = currentPassArg,
                    newPassword = newPassArg
                )

                if (isSuccess) {
                    userPreferences.updateUserName(inputUsername)
                    _profileStatus.value = "Profile updated successfully!"
                    _currentPassword.value = ""
                    _newPassword.value = ""
                } else {
                    _profileStatus.value = "Incorrect current password"
                }

            } catch (e: Exception) {
                _profileStatus.value = "Error updating profile: ${e.localizedMessage}"
            }
        }
    }

    // ── Avatar ───────────────────────────────────────────────────────────────────

    fun onAvatarPicked(file: File) {
        viewModelScope.launch {
            _isUploadingAvatar.value = true
            repo.uploadAvatar(loggedInUserId, file)
                .onSuccess { url ->
                    _avatarUrl.value = url
                    _profileStatus.value = "Profile picture updated"
                }
                .onFailure {
                    _profileStatus.value = "Failed to upload photo: ${it.message}"
                }
            _isUploadingAvatar.value = false
        }
    }

    fun onRemoveAvatar() {
        viewModelScope.launch {
            _isUploadingAvatar.value = true
            repo.removeAvatar(loggedInUserId)
                .onSuccess { url ->
                    _avatarUrl.value = url
                    _profileStatus.value = "Profile picture removed"
                }
                .onFailure {
                    _profileStatus.value = "Failed to remove photo: ${it.message}"
                }
            _isUploadingAvatar.value = false
        }
    }

    // ── Change email ─────────────────────────────────────────────────────────────

    fun onOpenEmailDialog() {
        _newEmail.value = ""
        _emailChangePassword.value = ""
        _showEmailDialog.value = true
    }

    fun onDismissEmailDialog() {
        _showEmailDialog.value = false
    }

    fun onNewEmailChanged(value: String) {
        _newEmail.value = value
    }

    fun onEmailChangePasswordChanged(value: String) {
        _emailChangePassword.value = value
    }

    fun onSubmitEmailChange() {
        val newEmailValue = _newEmail.value.trim()
        val passwordValue = _emailChangePassword.value.trim()
        if (newEmailValue.isEmpty() || passwordValue.isEmpty()) {
            _profileStatus.value = "Enter your new email and current password"
            return
        }
        viewModelScope.launch {
            repo.changeEmail(loggedInUserId, passwordValue, newEmailValue)
                .onSuccess {
                    _email.value = newEmailValue
                    _isEmailVerified.value = false
                    _showEmailDialog.value = false
                    _profileStatus.value = "Email updated — verify it below"
                    onResendVerification()
                    _awaitingVerification.value = true
                }
                .onFailure {
                    _profileStatus.value = it.message ?: "Failed to update email"
                }
        }
    }

    fun onVerificationTokenChanged(value: String) {
        _verificationToken.value = value.filter { it.isDigit() }.take(6)
    }

    fun onResendVerification() {
        viewModelScope.launch {
            repo.sendVerificationEmail()
                .onSuccess {
                    _awaitingVerification.value = true
                    _profileStatus.value = "Verification code sent to your email"
                }
                .onFailure {
                    _profileStatus.value = it.message ?: "Failed to send verification code"
                }
        }
    }

    fun onConfirmVerification() {
        val token = _verificationToken.value.trim()
        if (token.isEmpty()) {
            _profileStatus.value = "Enter the verification code"
            return
        }
        viewModelScope.launch {
            repo.confirmVerificationEmail(loggedInUserId, token)
                .onSuccess {
                    _isEmailVerified.value = true
                    _awaitingVerification.value = false
                    _verificationToken.value = ""
                    _profileStatus.value = "Email verified!"
                }
                .onFailure {
                    _profileStatus.value = it.message ?: "Verification failed"
                }
        }
    }

    // ── Delete account ───────────────────────────────────────────────────────────

    fun onOpenDeleteDialog() {
        _deleteAccountPassword.value = ""
        _showDeleteDialog.value = true
    }

    fun onDismissDeleteDialog() {
        _showDeleteDialog.value = false
    }

    fun onDeleteAccountPasswordChanged(value: String) {
        _deleteAccountPassword.value = value
    }

    fun onConfirmDeleteAccount(onDeleted: () -> Unit) {
        val password = _deleteAccountPassword.value.trim()
        if (password.isEmpty()) {
            _profileStatus.value = "Enter your password to confirm"
            return
        }
        viewModelScope.launch {
            _isDeletingAccount.value = true
            repo.deleteAccountRemote(password)
                .onSuccess {
                    _showDeleteDialog.value = false
                    sessionManager.logout()
                    onDeleted()
                }
                .onFailure {
                    _profileStatus.value = it.message ?: "Failed to delete account"
                }
            _isDeletingAccount.value = false
        }
    }

    fun onShowEmailChanged(value: Boolean) {
        _showEmail.value = value
    }

    fun onShowOnlineStatusChanged(value: Boolean) {
        _showOnlineStatus.value = value
    }

    fun onShowLastSeenChanged(value: Boolean) {
        _showLastSeen.value = value
    }

    fun onProfileVisibilityChanged(value: String) {
        _profileVisibility.value = value
    }

    fun onSavePrivacySettings() {
        viewModelScope.launch {
            _isSavingPrivacy.value = true
            repo.updatePrivacySettings(
                showEmail = _showEmail.value,
                showOnlineStatus = _showOnlineStatus.value,
                showLastSeen = _showLastSeen.value,
                profileVisibility = _profileVisibility.value
            )
                .onSuccess { _profileStatus.value = "Privacy settings updated" }
                .onFailure { _profileStatus.value = it.message ?: "Failed to update privacy settings" }
            _isSavingPrivacy.value = false
        }
    }

    fun onLogout(onLogoutComplete: () -> Unit) {
        viewModelScope.launch {
            sessionManager.logout()
            onLogoutComplete()
        }
    }
}
