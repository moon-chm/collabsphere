package com.example.rohit_project_challlange.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rohit_project_challlange.UserPreferences
import com.example.rohit_project_challlange.model.workspace.WorkspaceEntity
import com.example.rohit_project_challlange.model.workspace.WorkspaceRepo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val repository: WorkspaceRepo,
    private val initialUserId: Int,
    private val userPreferences: UserPreferences
) : ViewModel() {

    init {
        if (initialUserId > 0) {
            viewModelScope.launch {
                repository.startDeltaSyncLoop(initialUserId)
            }
        }
    }

    val userIdState: StateFlow<Int> = userPreferences.userIdFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = if (initialUserId > 0) initialUserId else -1
        )

    val workspaces: StateFlow<List<WorkspaceEntity>> = userIdState
        .flatMapLatest { userId ->
            if (userId != -1 && userId > 0) {
                viewModelScope.launch {
                    repository.syncWorkspaces(userId)
                    repository.startDeltaSyncLoop(userId)
                }
                repository.getAllWorkspacesForUser(userId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateUserId(newUserId: Int) {
        viewModelScope.launch {
            userPreferences.saveUserId(newUserId)
        }
    }

    fun logout() {
        viewModelScope.launch {
            userPreferences.clearPreferences()
        }
    }
}