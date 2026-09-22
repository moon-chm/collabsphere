package com.collabsphere.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabsphere.app.dto.login.PublicProfileResponse
import com.collabsphere.app.model.UserRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PublicProfileViewModel(
    private val targetUserId: Int,
    private val repo: UserRepo
) : ViewModel() {

    private val _profile = MutableStateFlow<PublicProfileResponse?>(null)
    val profile: StateFlow<PublicProfileResponse?> = _profile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isBlocked = MutableStateFlow(false)
    val isBlocked: StateFlow<Boolean> = _isBlocked.asStateFlow()

    private val _isBlockActionInFlight = MutableStateFlow(false)
    val isBlockActionInFlight: StateFlow<Boolean> = _isBlockActionInFlight.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _notFound = MutableStateFlow(false)
    val notFound: StateFlow<Boolean> = _notFound.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _notFound.value = false

            repo.getPublicProfile(targetUserId)
                .onSuccess { _profile.value = it }
                .onFailure { _notFound.value = true }

            repo.getBlockedUsers()
                .onSuccess { blocked -> _isBlocked.value = blocked.any { it.id == targetUserId } }

            _isLoading.value = false
        }
    }

    fun clearStatus() {
        _statusMessage.value = ""
    }

    fun onToggleBlock() {
        viewModelScope.launch {
            _isBlockActionInFlight.value = true
            val result = if (_isBlocked.value) repo.unblockUser(targetUserId) else repo.blockUser(targetUserId)
            result
                .onSuccess {
                    _isBlocked.value = !_isBlocked.value
                    _statusMessage.value = if (_isBlocked.value) "User blocked" else "User unblocked"
                }
                .onFailure {
                    _statusMessage.value = it.message ?: "Action failed"
                }
            _isBlockActionInFlight.value = false
        }
    }
}
