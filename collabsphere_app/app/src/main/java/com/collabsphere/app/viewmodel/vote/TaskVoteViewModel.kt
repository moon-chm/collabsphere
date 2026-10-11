package com.collabsphere.app.viewmodel.vote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabsphere.app.UserPreferences
import com.collabsphere.app.dto.task.ChecklistItem
import com.collabsphere.app.dto.vote.TaskVoteProposalRequest
import com.collabsphere.app.model.vote.TaskVoteProposalEntity
import com.collabsphere.app.model.vote.TaskVoteRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID

class TaskVoteViewModel(
    private val repo: TaskVoteRepo,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _pendingProposals = MutableStateFlow<List<TaskVoteProposalEntity>>(emptyList())
    val pendingProposals: StateFlow<List<TaskVoteProposalEntity>> = _pendingProposals.asStateFlow()

    private val _proposalHistory = MutableStateFlow<List<TaskVoteProposalEntity>>(emptyList())
    val proposalHistory: StateFlow<List<TaskVoteProposalEntity>> = _proposalHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _currentUserId = MutableStateFlow<Int?>(null)
    val currentUserId: StateFlow<Int?> = _currentUserId.asStateFlow()

    private var activeWorkspaceId: Int? = null

    init {
        viewModelScope.launch {
            val uid = userPreferences.userIdFlow.firstOrNull()
            if (uid != null && uid > 0) {
                _currentUserId.value = uid
            }
        }
    }

    fun initWorkspace(workspaceId: Int) {
        if (activeWorkspaceId == workspaceId) return
        activeWorkspaceId = workspaceId

        // Observe Room flows
        viewModelScope.launch {
            repo.getPendingProposals(workspaceId).collect { list ->
                _pendingProposals.value = list
            }
        }

        viewModelScope.launch {
            repo.getProposalHistory(workspaceId).collect { list ->
                _proposalHistory.value = list
            }
        }

        // Fetch initial list
        viewModelScope.launch {
            _isLoading.value = true
            repo.fetchProposals(workspaceId)
            _isLoading.value = false
        }

        // Start polling delta sync
        viewModelScope.launch {
            repo.startDeltaSyncLoop(workspaceId)
        }
    }

    fun submitCreateProposal(
        workspaceId: Int,
        taskName: String,
        taskDescription: String,
        assignedToUserId: Int?,
        priority: String?,
        dueDate: Long?,
        checklist: List<ChecklistItem> = emptyList(),
        labels: List<String> = emptyList(),
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val req = TaskVoteProposalRequest(
                workspaceId = workspaceId,
                type = "CREATE",
                taskName = taskName,
                taskDescription = taskDescription,
                assignedToUserId = assignedToUserId,
                priority = priority,
                dueDate = dueDate,
                checklist = checklist,
                labels = labels,
                idempotencyKey = UUID.randomUUID().toString()
            )
            val result = repo.submitProposal(req)
            _isLoading.value = false
            if (result.isSuccess) {
                onComplete(true)
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to submit proposal"
                onComplete(false)
            }
        }
    }

    fun submitDeleteProposal(
        workspaceId: Int,
        taskId: Int,
        deleteReason: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val req = TaskVoteProposalRequest(
                workspaceId = workspaceId,
                type = "DELETE",
                taskId = taskId,
                deleteReason = deleteReason,
                idempotencyKey = UUID.randomUUID().toString()
            )
            val result = repo.submitProposal(req)
            _isLoading.value = false
            if (result.isSuccess) {
                onComplete(true)
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to submit delete proposal"
                onComplete(false)
            }
        }
    }

    fun castVote(
        proposalId: Int,
        vote: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repo.castVote(proposalId, vote)
            if (result.isSuccess) {
                onComplete(true)
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to cast vote"
                onComplete(false)
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
