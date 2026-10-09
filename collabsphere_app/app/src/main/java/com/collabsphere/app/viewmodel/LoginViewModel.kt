package com.collabsphere.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabsphere.app.model.UserRepo
import com.collabsphere.app.SessionManager
import com.collabsphere.app.UserPreferences
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException

class LoginViewModel(
    private val repo: UserRepo,
    private val userPreferences: UserPreferences,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val emailRegex = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    private fun isValidEmail(email: String) = email.length <= 255 && emailRegex.matches(email)

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

    private val _isLoadingEmail = MutableStateFlow(false)
    val isLoadingEmail: StateFlow<Boolean> = _isLoadingEmail.asStateFlow()

    private val _isLoadingLogin = MutableStateFlow(false)
    val isLoadingLogin: StateFlow<Boolean> = _isLoadingLogin.asStateFlow()

    private val _isVerifyingRegistration = MutableStateFlow(false)
    val isVerifyingRegistration: StateFlow<Boolean> = _isVerifyingRegistration.asStateFlow()

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
        val password = inputPassword

        if (trimmedEmail.isEmpty() || password.isBlank()) {
            _loginStatus.value = "Please fill all fields"
            return
        }

        if (!isValidEmail(trimmedEmail)) {
            _loginStatus.value = "Please enter a valid email address"
            return
        }

        if (_isLoadingLogin.value) return
        _isLoadingLogin.value = true
        viewModelScope.launch {
            try {
                repo.loginRemote(trimmedEmail, password)
                .onSuccess { user ->
                    sessionManager.prepareForAuthenticatedUser(user.id)
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _loginStatus.value = error.message ?: "Login failed. Please try again."
            } finally {
                _isLoadingLogin.value = false
            }
        }
    }

    fun onRegisterClick(inputEmail: String, inputUserName: String, inputPassword: String) {
        val trimmedEmail = inputEmail.trim()
        val trimmedUserName = inputUserName.trim()
        val password = inputPassword

        if (trimmedEmail.isEmpty() || trimmedUserName.isEmpty() || password.isBlank()) {
            _loginStatus.value = "Fields cannot be empty"
            return
        }

        if (!isValidEmail(trimmedEmail)) {
            _loginStatus.value = "Please enter a valid email address"
            return
        }

        if (trimmedUserName.length !in 3..50) {
            _loginStatus.value = "Username must be between 3 and 50 characters"
            return
        }

        if (password.length < 6) {
            _loginStatus.value = "Password must be at least 6 characters"
            return
        }
        if (password.toByteArray(Charsets.UTF_8).size > 72) {
            _loginStatus.value = "Password is too long"
            return
        }
        if (_isLoadingEmail.value) return
        _isLoadingEmail.value = true

        viewModelScope.launch {
            try {
            repo.registerRemote(trimmedEmail, trimmedUserName, password)
                .onSuccess { registerResponse ->
                    _registrationSuccessEmail.value = registerResponse.email
                    _loginStatus.value = registerResponse.message
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _loginStatus.value = error.message ?: "Registration failed"
            } finally {
                _isLoadingEmail.value = false
            }
        }
    }

    fun onVerifyRegistration(email: String, otp: String) {
        val trimmedEmail = email.trim()
        val trimmedOtp = otp.trim()
        if (!isValidEmail(trimmedEmail) || !trimmedOtp.matches(Regex("^\\d{6}$"))) {
            _verificationStatus.value = "Please enter the complete 6-digit code"
            return
        }
        if (_isVerifyingRegistration.value) return
        _isVerifyingRegistration.value = true

        viewModelScope.launch {
            try {
            repo.verifyRegistration(trimmedEmail, trimmedOtp)
                .onSuccess { msg ->
                    _verificationStatus.value = msg
                    _isVerificationSuccess.value = true
                    _unverifiedEmailForLogin.value = null
                }
                .onFailure { err ->
                    _verificationStatus.value = err.message ?: "Verification failed"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _verificationStatus.value = error.message ?: "Verification failed"
            } finally {
                _isVerifyingRegistration.value = false
            }
        }
    }

    fun onResendVerification(email: String) {
        val trimmedEmail = email.trim()
        if (!isValidEmail(trimmedEmail) || _isLoadingEmail.value) return
        _isLoadingEmail.value = true

        viewModelScope.launch {
            try {
            repo.resendVerification(trimmedEmail)
                .onSuccess { msg ->
                    _verificationStatus.value = msg
                }
                .onFailure { err ->
                    _verificationStatus.value = err.message ?: "Failed to resend code"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _verificationStatus.value = error.message ?: "Failed to resend code"
            } finally {
                _isLoadingEmail.value = false
            }
        }
    }

    fun onForgotPasswordRequest(email: String) {
        val trimmedEmail = email.trim()
        if (!isValidEmail(trimmedEmail)) {
            _forgotPasswordStatus.value = "Please enter a valid email address"
            return
        }
        if (_isLoadingEmail.value) return
        _isLoadingEmail.value = true

        viewModelScope.launch {
            try {
            repo.forgotPassword(trimmedEmail)
                .onSuccess { msg ->
                    _forgotPasswordStatus.value = msg
                    _forgotPasswordStep.value = 2
                }
                .onFailure { err ->
                    _forgotPasswordStatus.value = err.message ?: "Failed to request password reset"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _forgotPasswordStatus.value = error.message ?: "Failed to request password reset"
            } finally {
                _isLoadingEmail.value = false
            }
        }
    }

    fun onResetPasswordSubmit(email: String, otp: String, newPass: String, confirmPass: String) {
        val trimmedEmail = email.trim()
        val trimmedOtp = otp.trim()
        val password = newPass

        if (!isValidEmail(trimmedEmail) || !trimmedOtp.matches(Regex("^\\d{6}$"))) {
            _forgotPasswordStatus.value = "Please enter the 6-digit code"
            return
        }
        if (password.length < 6) {
            _forgotPasswordStatus.value = "Password must be at least 6 characters"
            return
        }
        if (password.toByteArray(Charsets.UTF_8).size > 72) {
            _forgotPasswordStatus.value = "Password is too long"
            return
        }
        if (password != confirmPass) {
            _forgotPasswordStatus.value = "Passwords do not match"
            return
        }

        if (_isLoadingEmail.value) return
        _isLoadingEmail.value = true
        viewModelScope.launch {
            try {
            repo.resetPassword(trimmedEmail, trimmedOtp, password)
                .onSuccess { msg ->
                    _forgotPasswordStatus.value = msg
                    _isResetPasswordSuccess.value = true
                }
                .onFailure { err ->
                    _forgotPasswordStatus.value = err.message ?: "Password reset failed"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _forgotPasswordStatus.value = error.message ?: "Password reset failed"
            } finally {
                _isLoadingEmail.value = false
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
            _loggedInAvatarUrl.value = ""
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
