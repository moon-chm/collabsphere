package com.example.rohit_project_challlange.viewmodel.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rohit_project_challlange.model.UserEntity
import com.example.rohit_project_challlange.model.task.TaskEntity
import com.example.rohit_project_challlange.model.task.TaskRepo
import com.example.rohit_project_challlange.model.task.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TaskUiModel(
    val task: TaskEntity,
    val assigneeName: String,
    val isEditableByMe: Boolean
)

sealed class TaskUiEvent {
    data class ShowToast(val message: String) : TaskUiEvent()
}

class TaskViewModel(
    private val repo: TaskRepo,
    private val loggedUserId: Int,
    private val loggedWorkspaceId: Int
) : ViewModel() {

    private val rawTasksFlow = repo.getTasks(loggedWorkspaceId)

    private val _uiEvent = Channel<TaskUiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    val workspaceMembers: StateFlow<List<UserEntity>> = repo
        .getWorkspaceMembers(loggedWorkspaceId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val tasks: StateFlow<List<TaskUiModel>> = combine(
        rawTasksFlow,
        workspaceMembers
    ) { taskList, memberList ->
        val memberMap = memberList.associate { it.id to it.userName }
        taskList.map { task ->
            val name = if (task.assignedToUserId == null) {
                "Unassigned"
            } else {
                memberMap[task.assignedToUserId] ?: "User ${task.assignedToUserId}"
            }
            TaskUiModel(
                task = task,
                assigneeName = name,
                isEditableByMe = task.assignedToUserId == loggedUserId
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _taskName = MutableStateFlow("")
    val taskName = _taskName.asStateFlow()

    private val _taskDescription = MutableStateFlow("")
    val taskDescription = _taskDescription.asStateFlow()

    private val _assignedUserId = MutableStateFlow<Int?>(null)
    val assignedUserId = _assignedUserId.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    init {
        viewModelScope.launch {
            repo.startDeltaSyncLoop(loggedWorkspaceId)
        }
    }

    fun syncTasks() {
        viewModelScope.launch {
            _isSyncing.value = true
            withContext(Dispatchers.IO) {
                repo.syncTasks(loggedWorkspaceId)
            }
            _isSyncing.value = false
        }
    }

    fun onTaskNameChange(name: String) {
        _taskName.value = name
    }

    fun onTaskDescriptionChange(desc: String) {
        _taskDescription.value = desc
    }

    fun onAssigneeChange(userId: Int?) {
        _assignedUserId.value = userId
    }

    fun onCreateTask() {
        val name = _taskName.value.trim()
        val desc = _taskDescription.value.trim()

        if (name.isEmpty()) return

        viewModelScope.launch {
            val task = TaskEntity(
                id = 0,
                createdByUserId = loggedUserId,
                assignedToUserId = _assignedUserId.value,
                workspaceId = loggedWorkspaceId,
                taskName = name,
                taskDescription = desc,
                status = TaskStatus.TO_DO
            )
            withContext(Dispatchers.IO) {
                repo.addTask(task)
            }
            _taskName.value = ""
            _taskDescription.value = ""
            _assignedUserId.value = null
        }
    }

    fun moveTask(task: TaskEntity, newStatus: TaskStatus) {
        if (task.assignedToUserId != loggedUserId) {
            sendUiEvent("You can only move tasks assigned to you.")
            return
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.updateTask(task.copy(status = newStatus))
            }
        }
    }

    fun onAssignTask(task: TaskEntity, userId: Int?) {
        if (task.createdByUserId != loggedUserId) {
            sendUiEvent("Only the task creator can change assignments.")
            return
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.updateTask(task.copy(assignedToUserId = userId))
            }
        }
    }

    fun onDeleteTask(taskId: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.deleteTask(taskId)
            }
        }
    }

    fun onUpdateTask(oldTask: TaskEntity, newName: String, newDescription: String) {
        val updatedTaskName = newName.trim()
        val updatedTaskDescription = newDescription.trim()

        if (updatedTaskName.isEmpty()) return

        if (oldTask.assignedToUserId != loggedUserId) {
            sendUiEvent("You can only edit details of tasks assigned to you.")
            return
        }

        viewModelScope.launch {
            val updatedTask = oldTask.copy(
                taskName = updatedTaskName,
                taskDescription = updatedTaskDescription
            )
            withContext(Dispatchers.IO) {
                repo.updateTask(updatedTask)
            }
        }
    }

    private fun sendUiEvent(message: String) {
        viewModelScope.launch {
            _uiEvent.send(TaskUiEvent.ShowToast(message))
        }
    }
}