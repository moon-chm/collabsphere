package com.example.rohit_project_challlange.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rohit_project_challlange.SessionManager
import com.example.rohit_project_challlange.UserPreferences
import com.example.rohit_project_challlange.model.workspace.WorkspaceEntity
import com.example.rohit_project_challlange.model.workspace.WorkspaceRepo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val repository: WorkspaceRepo,
    private val initialUserId: Int,
    private val userPreferences: UserPreferences,
    private val sessionManager: SessionManager
) : ViewModel() {

    val userIdState: StateFlow<Int> = userPreferences.userIdFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = if (initialUserId > 0) initialUserId else -1
        )

    init {
        // One delta-sync loop per user, restarted (and the previous one cancelled) only when the
        // user actually changes — collectLatest owns that cancellation instead of a fire-and-forget
        // launch inside flatMapLatest below, which could never cancel a loop it didn't track.
        viewModelScope.launch {
            userIdState.collectLatest { userId ->
                if (userId != -1 && userId > 0) {
                    repository.syncWorkspaces(userId)
                    repository.startDeltaSyncLoop(userId)
                }
            }
        }
    }

    val workspaces: StateFlow<List<WorkspaceEntity>> = userIdState
        .flatMapLatest { userId ->
            if (userId != -1 && userId > 0) {
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
            sessionManager.logout()
        }
    }
}