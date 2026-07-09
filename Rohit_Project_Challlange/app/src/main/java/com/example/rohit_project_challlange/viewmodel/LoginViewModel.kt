package com.example.rohit_project_challlange.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rohit_project_challlange.model.UserRepo
import android.util.Patterns
import com.example.rohit_project_challlange.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException

class LoginViewModel(
    private val repo: UserRepo,
    private val userPreferences: UserPreferences
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

    init {
        viewModelScope.launch {
            val savedId = userPreferences.userIdFlow.first()
            if (savedId != -1) {
                _loggedInUserId.value = savedId.toLong()
                _isLoggedIn.value = true
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
                    userPreferences.saveUserId(user.id)
                    _loggedInUserId.value = user.id.toLong()
                    _loggedInUserName.value = user.userName
                    _loggedInUserEmail.value = user.email
                    _loginStatus.value = "Login Successful!"
                    _isLoggedIn.value = true
                }
                .onFailure { throwable ->
                    val errorMessage = when (throwable) {
                        is IOException -> "Network issue. Please check your internet connection."
                        else -> throwable.message ?: "Invalid credentials or network issue"
                    }
                    _loginStatus.value = errorMessage
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
                .onSuccess { userResponse ->
                    userPreferences.saveUserId(userResponse.id)
                    _loggedInUserId.value = userResponse.id.toLong()
                    _loggedInUserName.value = userResponse.userName
                    _loggedInUserEmail.value = userResponse.email
                    _loginStatus.value = "Registration Successful!"
                    _isLoggedIn.value = true
                }
                .onFailure { err ->
                    _loginStatus.value = err.message ?: "Registration Failed"
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            userPreferences.clearPreferences()
            _isLoggedIn.value = false
            _loginStatus.value = null
            _loggedInUserId.value = 0L
            _loggedInUserName.value = ""
            _loggedInUserEmail.value = ""
        }
    }

    fun updateLoggedInUserName(newName: String) {
        _loggedInUserName.value = newName
    }

    fun clearLoginStatus() {
        _loginStatus.value = null
    }
}