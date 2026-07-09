package com.example.rohit_project_challlange.viewmodel.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rohit_project_challlange.UserPreferences
import com.example.rohit_project_challlange.model.UserRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    val loggedInUserId: Int,
    private val userEmail: String,
    private val repo: UserRepo,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _updatedUserName = MutableStateFlow("")
    val updatedUserName: StateFlow<String> = _updatedUserName.asStateFlow()

    private val _currentPassword = MutableStateFlow("")
    val currentPassword: StateFlow<String> = _currentPassword.asStateFlow()

    private val _newPassword = MutableStateFlow("")
    val newPassword: StateFlow<String> = _newPassword.asStateFlow()

    private val _profileStatus = MutableStateFlow("")
    val profileStatus: StateFlow<String> = _profileStatus.asStateFlow()

    fun onUserNameChanged(name: String) {
        _updatedUserName.value = name
    }

    fun onCurrentPasswordChanged(pass: String) {
        _currentPassword.value = pass
    }

    fun onNewPasswordChanged(pass: String) {
        _newPassword.value = pass
    }

    fun clearProfileStatus() {
        _profileStatus.value = ""
    }

    fun onUpdateProfile() {
        val inputUsername = updatedUserName.value.trim()
        val inputCurrentPassword = currentPassword.value.trim()
        val inputNewPassword = newPassword.value.trim()

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
                    userEmail = userEmail,
                    newName = inputUsername,
                    currentPassword = currentPassArg,
                    newPassword = newPassArg
                )

                if (isSuccess) {
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

    fun onLogout(onLogoutComplete: () -> Unit) {
        viewModelScope.launch {
            userPreferences.clearPreferences()
            onLogoutComplete()
        }
    }
}