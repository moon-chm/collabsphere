package com.example.rohit_project_challlange.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rohit_project_challlange.dto.login.SearchUserResult
import com.example.rohit_project_challlange.model.UserRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BlockedUsersViewModel(private val repo: UserRepo) : ViewModel() {

    private val _blockedUsers = MutableStateFlow<List<SearchUserResult>>(emptyList())
    val blockedUsers: StateFlow<List<SearchUserResult>> = _blockedUsers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            repo.getBlockedUsers()
                .onSuccess { _blockedUsers.value = it }
                .onFailure { _statusMessage.value = it.message ?: "Failed to load blocked users" }
            _isLoading.value = false
        }
    }

    fun clearStatus() {
        _statusMessage.value = ""
    }

    fun onUnblock(userId: Int) {
        viewModelScope.launch {
            repo.unblockUser(userId)
                .onSuccess {
                    _blockedUsers.value = _blockedUsers.value.filterNot { it.id == userId }
                    _statusMessage.value = "User unblocked"
                }
                .onFailure { _statusMessage.value = it.message ?: "Failed to unblock" }
        }
    }
}
