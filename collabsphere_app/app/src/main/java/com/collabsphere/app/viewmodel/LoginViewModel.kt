package com.collabsphere.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabsphere.app.model.UserRepo
import android.util.Patterns
import com.collabsphere.app.SessionManager
import com.collabsphere.app.UserPreferences
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException

class LoginViewModel(
    private val repo: UserRepo,
    private val userPreferences: UserPreferences,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _loginStatus = MutableStateFlow<String?>(null)
    val loginStatus: StateFlow<String?> = _loginStatus.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _loggedInUserId = MutableStateFlow<Long>(0L)
    val loggedInUserId: StateFlow<Long> = _loggedInUserId.asStateFlow()

    private val _loggedInUserName = MutableStateFlow<String>("")
    val loggedInUserName: StateFlow<String> = _loggedInUserName.asStateFlow()

    private val _loggedInUserEmail = MutableStateFlow<String>("")
    val loggedInUserEmail: StateFlow<String> = _loggedInUserEmail.asStateFlow()

    private val _loggedInAvatarUrl = MutableStateFlow<String>("")
    val loggedInAvatarUrl: StateFlow<String> = _loggedInAvatarUrl.asStateFlow()

    private val _registrationSuccessEmail = MutableStateFlow<String?>(null)
    val registrationSuccessEmail: StateFlow<String?> = _registrationSuccessEmail.asStateFlow()

    private val _verificationStatus = MutableStateFlow<String?>(null)
    val verificationStatus: StateFlow<String?> = _verificationStatus.asStateFlow()

    private val _isVerificationSuccess = MutableStateFlow(false)
    val isVerificationSuccess: StateFlow<Boolean> = _isVerificationSuccess.asStateFlow()

    private val _forgotPasswordStatus = MutableStateFlow<String?>(null)
    val forgotPasswordStatus: StateFlow<String?> = _forgotPasswordStatus.asStateFlow()

    private val _forgotPasswordStep = MutableStateFlow(1)
    val forgotPasswordStep: StateFlow<Int> = _forgotPasswordStep.asStateFlow()

    private val _isResetPasswordSuccess = MutableStateFlow(false)
    val isResetPasswordSuccess: StateFlow<Boolean> = _isResetPasswordSuccess.asStateFlow()

    private val _unverifiedEmailForLogin = MutableStateFlow<String?>(null)
    val unverifiedEmailForLogin: StateFlow<String?> = _unverifiedEmailForLogin.asStateFlow()

    init {
        viewModelScope.launch {
            val savedId = userPreferences.userIdFlow.first()
            val savedName = userPreferences.userNameFlow.first()
            val savedEmail = userPreferences.userEmailFlow.first()
            if (savedId != -1) {
                _loggedInUserId.value = savedId.toLong()
                _loggedInUserName.value = savedName
                _loggedInUserEmail.value = savedEmail
                _isLoggedIn.value = true

                val localUser = repo.getUserById(savedId)
                if (localUser != null) {
                    if (savedName.isEmpty()) _loggedInUserName.value = localUser.userName
                    if (savedEmail.isEmpty()) _loggedInUserEmail.value = localUser.email
                    if (localUser.avatarUrl != null) _loggedInAvatarUrl.value = localUser.avatarUrl
                }
            }
        }
    }

    fun onLoginClick(inputEmail: String, inputPassword: String) {
        val trimmedEmail = inputEmail.trim()
        val trimmedPassword = inputPassword.trim()

        if (trimmedEmail.isEmpty() || trimmedPassword.isEmpty()) {
            _loginStatus.value = "Please fill all fields"
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            _loginStatus.value = "Please enter a valid email address"
            return
        }

        viewModelScope.launch {
            repo.loginRemote(trimmedEmail, trimmedPassword)
                .onSuccess { user ->
                    userPreferences.saveUserSession(user.id, user.userName, user.email)
                    _loggedInUserId.value = user.id.toLong()
                    _loggedInUserName.value = user.userName
                    _loggedInUserEmail.value = user.email
                    _loginStatus.value = "Login Successful!"
                    _isLoggedIn.value = true
                }
                .onFailure { throwable ->
                    val rawMsg = throwable.message ?: ""
                    if (rawMsg.contains("EMAIL_NOT_VERIFIED", ignoreCase = true) ||
                        rawMsg.contains("verify your email", ignoreCase = true)
                    ) {
                        _unverifiedEmailForLogin.value = trimmedEmail
                        _loginStatus.value = "EMAIL_NOT_VERIFIED: Please verify your email before logging in."
                    } else {
                        val errorMessage = when (throwable) {
                            is HttpRequestTimeoutException,
                            is SocketTimeoutException -> "Server is waking up, please try again in a moment ☕"
                            is IOException -> "Network issue. Please check your internet connection."
                            else -> rawMsg.ifBlank { "Invalid credentials or network issue" }
                        }
                        _loginStatus.value = errorMessage
                    }
                }
        }
    }

    fun onRegisterClick(inputEmail: String, inputUserName: String, inputPassword: String) {
        val trimmedEmail = inputEmail.trim()
        val trimmedUserName = inputUserName.trim()
        val trimmedPassword = inputPassword.trim()

        if (trimmedEmail.isEmpty() || trimmedUserName.isEmpty() || trimmedPassword.isEmpty()) {
            _loginStatus.value = "Fields cannot be empty"
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            _loginStatus.value = "Please enter a valid email address"
            return
        }

        if (trimmedUserName.length < 3) {
            _loginStatus.value = "Username must be at least 3 characters"
            return
        }

        if (trimmedPassword.length < 6) {
            _loginStatus.value = "Password must be at least 6 characters"
            return
        }

        viewModelScope.launch {
            repo.registerRemote(trimmedEmail, trimmedUserName, trimmedPassword)
                .onSuccess { registerResponse ->
                    _registrationSuccessEmail.value = registerResponse.email
                    _loginStatus.value = "Account created! Please enter the 6-digit code sent to your email."
                }
                .onFailure { err ->
                    val errorMessage = when (err) {
                        is HttpRequestTimeoutException,
                        is SocketTimeoutException -> "Server is waking up, please try again in a moment ☕"
                        is IOException -> "Network issue. Please check your internet connection."
                        else -> err.message ?: "Registration Failed"
                    }
                    _loginStatus.value = errorMessage
                }
        }
    }

    fun onVerifyRegistration(email: String, otp: String) {
        val trimmedEmail = email.trim()
        val trimmedOtp = otp.trim()
        if (trimmedOtp.length != 6) {
            _verificationStatus.value = "Please enter the complete 6-digit code"
            return
        }

        viewModelScope.launch {
            repo.verifyRegistration(trimmedEmail, trimmedOtp)
                .onSuccess { msg ->
                    _verificationStatus.value = msg
                    _isVerificationSuccess.value = true
                    _unverifiedEmailForLogin.value = null
                }
                .onFailure { err ->
                    _verificationStatus.value = err.message ?: "Verification failed"
                }
        }
    }

    fun onResendVerification(email: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) return

        viewModelScope.launch {
            repo.resendVerification(trimmedEmail)
                .onSuccess { msg ->
                    _verificationStatus.value = msg
                }
                .onFailure { err ->
                    _verificationStatus.value = err.message ?: "Failed to resend code"
                }
        }
    }

    fun onForgotPasswordRequest(email: String) {
        val trimmedEmail = email.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            _forgotPasswordStatus.value = "Please enter a valid email address"
            return
        }

        viewModelScope.launch {
            repo.forgotPassword(trimmedEmail)
                .onSuccess { msg ->
                    _forgotPasswordStatus.value = msg
                    _forgotPasswordStep.value = 2
                }
                .onFailure { err ->
                    _forgotPasswordStatus.value = err.message ?: "Failed to request password reset"
                }
        }
    }

    fun onResetPasswordSubmit(email: String, otp: String, newPass: String, confirmPass: String) {
        val trimmedEmail = email.trim()
        val trimmedOtp = otp.trim()
        val trimmedPass = newPass.trim()

        if (trimmedOtp.length != 6) {
            _forgotPasswordStatus.value = "Please enter the 6-digit code"
            return
        }
        if (trimmedPass.length < 6) {
            _forgotPasswordStatus.value = "Password must be at least 6 characters"
            return
        }
        if (trimmedPass != confirmPass.trim()) {
            _forgotPasswordStatus.value = "Passwords do not match"
            return
        }

        viewModelScope.launch {
            repo.resetPassword(trimmedEmail, trimmedOtp, trimmedPass)
                .onSuccess { msg ->
                    _forgotPasswordStatus.value = msg
                    _isResetPasswordSuccess.value = true
                }
                .onFailure { err ->
                    _forgotPasswordStatus.value = err.message ?: "Password reset failed"
                }
        }
    }

    fun clearRegistrationEmail() {
        _registrationSuccessEmail.value = null
    }

    fun clearVerificationStatus() {
        _verificationStatus.value = null
    }

    fun resetVerificationSuccess() {
        _isVerificationSuccess.value = false
    }

    fun clearForgotPasswordState() {
        _forgotPasswordStatus.value = null
        _forgotPasswordStep.value = 1
        _isResetPasswordSuccess.value = false
    }

    fun clearUnverifiedEmailForLogin() {
        _unverifiedEmailForLogin.value = null
    }

    fun setRegistrationEmailForVerification(email: String) {
        _registrationSuccessEmail.value = email
    }

    fun logout() {
        viewModelScope.launch {
            sessionManager.logout()
            _isLoggedIn.value = false
            _loginStatus.value = null
            _loggedInUserId.value = 0L
            _loggedInUserName.value = ""
            _loggedInUserEmail.value = ""
        }
    }

    fun updateLoggedInUserName(newName: String) {
        _loggedInUserName.value = newName
        viewModelScope.launch {
            userPreferences.updateUserName(newName)
        }
    }

    fun updateLoggedInAvatarUrl(newAvatarUrl: String) {
        _loggedInAvatarUrl.value = newAvatarUrl
    }

    fun clearLoginStatus() {
        _loginStatus.value = null
    }
}