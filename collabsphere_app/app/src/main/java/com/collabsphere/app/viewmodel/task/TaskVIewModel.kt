package com.collabsphere.app.viewmodel.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabsphere.app.model.UserEntity
import com.collabsphere.app.dto.task.ChecklistItem
import com.collabsphere.app.model.task.TaskEntity
import com.collabsphere.app.model.task.TaskPriority
import com.collabsphere.app.model.task.TaskRepo
import com.collabsphere.app.model.task.TaskStatus
import com.collabsphere.app.model.task.TaskSyncOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TaskUiModel(
    val task: TaskEntity,
    val assigneeName: String,
    val isEditableByMe: Boolean,
    val canPlanByMe: Boolean
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

    private val _labelFilter = MutableStateFlow<String?>(null)
    val labelFilter = _labelFilter.asStateFlow()

    val availableLabels: StateFlow<List<String>> = rawTasksFlow
        .map { taskList ->
            taskList.flatMap { it.labels }
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onLabelFilterChange(label: String?) {
        _labelFilter.value = label
    }

    val tasks: StateFlow<List<TaskUiModel>> = combine(
        rawTasksFlow,
        workspaceMembers,
        _labelFilter
    ) { taskList, memberList, filter ->
        val memberMap = memberList.associate { it.id to it.userName }
        taskList.filter { task ->
            filter == null || task.labels.any { it.equals(filter, ignoreCase = true) }
        }.sortedWith(
            compareByDescending<TaskEntity> { it.priority.ordinal }
                .thenBy { it.dueDate ?: Long.MAX_VALUE }
                .thenBy { it.id }
        ).map { task ->
            val name = if (task.assignedToUserId == null) {
                "Unassigned"
            } else {
                memberMap[task.assignedToUserId] ?: "User ${task.assignedToUserId}"
            }
            TaskUiModel(
                task = task,
                assigneeName = name,
                isEditableByMe = task.assignedToUserId == loggedUserId,
                canPlanByMe = task.assignedToUserId == loggedUserId || task.createdByUserId == loggedUserId
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

    private val _dueDate = MutableStateFlow<Long?>(null)
    val dueDate = _dueDate.asStateFlow()

    private val _priority = MutableStateFlow(TaskPriority.MEDIUM)
    val priority = _priority.asStateFlow()

    private val _labels = MutableStateFlow<List<String>>(emptyList())
    val labels = _labels.asStateFlow()

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

    fun canPlan(task: TaskEntity): Boolean =
        task.assignedToUserId == loggedUserId || task.createdByUserId == loggedUserId

    fun onDueDateChange(dueDate: Long?) {
        _dueDate.value = dueDate
    }

    fun onPriorityChange(priority: TaskPriority) {
        _priority.value = priority
    }

    fun onLabelsChange(labels: List<String>) {
        _labels.value = labels
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
                status = TaskStatus.TO_DO,
                dueDate = _dueDate.value,
                priority = _priority.value,
                labels = _labels.value
            )
            withContext(Dispatchers.IO) {
                repo.addTask(task)
            }
            _taskName.value = ""
            _taskDescription.value = ""
            _assignedUserId.value = null
            _dueDate.value = null
            _priority.value = TaskPriority.MEDIUM
            _labels.value = emptyList()
        }
    }

    fun moveTask(task: TaskEntity, newStatus: TaskStatus) {
        if (task.assignedToUserId != loggedUserId) {
            sendUiEvent("You can only move tasks assigned to you.")
            return
        }

        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                repo.updateTask(task.copy(status = newStatus))
            }
            if (outcome.getOrNull() == TaskSyncOutcome.QUEUED) {
                sendUiEvent("Saved offline — will sync when you're back online.")
            }
        }
    }

    fun onAssignTask(task: TaskEntity, userId: Int?) {
        if (task.createdByUserId != loggedUserId) {
            sendUiEvent("Only the task creator can change assignments.")
            return
        }

        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                repo.updateTask(task.copy(assignedToUserId = userId))
            }
            if (outcome.getOrNull() == TaskSyncOutcome.QUEUED) {
                sendUiEvent("Saved offline — will sync when you're back online.")
            }
        }
    }

    fun onDeleteTask(taskId: Int) {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                repo.deleteTask(taskId)
            }
            if (outcome.getOrNull() == TaskSyncOutcome.QUEUED) {
                sendUiEvent("Deleted offline — will sync when you're back online.")
            }
        }
    }

    fun onUpdateTask(
        oldTask: TaskEntity,
        newName: String,
        newDescription: String,
        newDueDate: Long?,
        newPriority: TaskPriority,
        newChecklist: List<ChecklistItem>,
        newLabels: List<String>
    ) {
        val updatedTaskName = newName.trim()
        val updatedTaskDescription = newDescription.trim()

        if (updatedTaskName.isEmpty()) return

        val isAssignee = oldTask.assignedToUserId == loggedUserId
        val isCreator = oldTask.createdByUserId == loggedUserId
        val contentChanged = updatedTaskName != oldTask.taskName || updatedTaskDescription != oldTask.taskDescription
        val planningChanged = newDueDate != oldTask.dueDate || newPriority != oldTask.priority ||
            newChecklist != oldTask.checklist || newLabels != oldTask.labels

        if (!contentChanged && !planningChanged) return

        if (contentChanged && !isAssignee) {
            sendUiEvent("You can only edit details of tasks assigned to you.")
            return
        }

        if (planningChanged && !isAssignee && !isCreator) {
            sendUiEvent("Only the creator or assignee can change the due date, priority, checklist or labels.")
            return
        }

        viewModelScope.launch {
            val updatedTask = oldTask.copy(
                taskName = updatedTaskName,
                taskDescription = updatedTaskDescription,
                dueDate = newDueDate,
                priority = newPriority,
                checklist = newChecklist,
                labels = newLabels
            )
            val outcome = withContext(Dispatchers.IO) {
                repo.updateTask(updatedTask)
            }
            if (outcome.getOrNull() == TaskSyncOutcome.QUEUED) {
                sendUiEvent("Saved offline — will sync when you're back online.")
            }
        }
    }

    private fun sendUiEvent(message: String) {
        viewModelScope.launch {
            _uiEvent.send(TaskUiEvent.ShowToast(message))
        }
    }
}