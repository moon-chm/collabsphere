package com.example.rohit_project_challlange.view.TaskUI

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rohit_project_challlange.model.UserEntity
import com.example.rohit_project_challlange.model.task.TaskEntity
import com.example.rohit_project_challlange.model.task.TaskStatus
import com.example.rohit_project_challlange.viewmodel.task.TaskUiEvent
import com.example.rohit_project_challlange.viewmodel.task.TaskUiModel
import com.example.rohit_project_challlange.viewmodel.task.TaskViewModel

@Composable
fun TaskScreen(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is TaskUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.syncTasks()
    }
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val members by viewModel.workspaceMembers.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<TaskEntity?>(null) }
    var taskToUpdate by remember { mutableStateOf<TaskEntity?>(null) }
    var taskToAssign by remember { mutableStateOf<TaskEntity?>(null) }
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kanban Board",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val toDoTasks = tasks.filter { it.task.status == TaskStatus.TO_DO }
                val inProgressTasks = tasks.filter { it.task.status == TaskStatus.IN_PROGRESS }
                val doneTasks = tasks.filter { it.task.status == TaskStatus.DONE }

                KanbanColumn(
                    title = "To Do",
                    tasks = toDoTasks,
                    onMoveForward = { viewModel.moveTask(it, TaskStatus.IN_PROGRESS) },
                    onMoveBackward = null,
                    onDelete = { taskToDelete = it },
                    onUpdate = { taskToUpdate = it },
                    onAssignClick = { taskToAssign = it }
                )

                KanbanColumn(
                    title = "In Progress",
                    tasks = inProgressTasks,
                    onMoveForward = { viewModel.moveTask(it, TaskStatus.DONE) },
                    onMoveBackward = { viewModel.moveTask(it, TaskStatus.TO_DO) },
                    onDelete = { taskToDelete = it },
                    onUpdate = { taskToUpdate = it },
                    onAssignClick = { taskToAssign = it }
                )

                KanbanColumn(
                    title = "Done",
                    tasks = doneTasks,
                    onMoveForward = null,
                    onMoveBackward = { viewModel.moveTask(it, TaskStatus.IN_PROGRESS) },
                    onDelete = { taskToDelete = it },
                    onUpdate = { taskToUpdate = it },
                    onAssignClick = { taskToAssign = it }
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "New Task")
        }

        if (showCreateDialog) {
            CreateTaskDialog(
                viewModel = viewModel,
                members = members,
                onDismiss = { showCreateDialog = false }
            )
        }

        taskToDelete?.let { task ->
            DeleteTaskConfirmationDialog(
                taskName = task.taskName,
                onConfirm = {
                    viewModel.onDeleteTask(task.id)
                    taskToDelete = null
                },
                onDismiss = { taskToDelete = null }
            )
        }

        taskToUpdate?.let { task ->
            UpdateTaskDialog(
                task = task,
                viewModel = viewModel,
                onDismiss = { taskToUpdate = null }
            )
        }

        taskToAssign?.let { task ->
            AssignMemberDialog(
                task = task,
                members = members,
                onAssign = { userId ->
                    viewModel.onAssignTask(task, userId)
                    taskToAssign = null
                },
                onDismiss = { taskToAssign = null }
            )
        }
    }
}

@Composable
fun KanbanColumn(
    title: String,
    tasks: List<TaskUiModel>,
    onMoveForward: ((TaskEntity) -> Unit)?,
    onMoveBackward: ((TaskEntity) -> Unit)?,
    onDelete: (TaskEntity) -> Unit,
    onUpdate: (TaskEntity) -> Unit,
    onAssignClick: (TaskEntity) -> Unit
) {
    Surface(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .padding(bottom = 80.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "$title (${tasks.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tasks, key = { it.task.id }) { taskUi ->
                    KanbanTaskCard(
                        taskUi = taskUi,
                        onMoveForward = onMoveForward,
                        onMoveBackward = onMoveBackward,
                        onDelete = { onDelete(taskUi.task) },
                        onUpdate = { onUpdate(taskUi.task) },
                        onAssignClick = { onAssignClick(taskUi.task) }
                    )
                }
            }
        }
    }
}

@Composable
fun KanbanTaskCard(
    taskUi: TaskUiModel,
    onMoveForward: ((TaskEntity) -> Unit)?,
    onMoveBackward: ((TaskEntity) -> Unit)?,
    onDelete: () -> Unit,
    onUpdate: () -> Unit,
    onAssignClick: () -> Unit
) {
    val isEditable = taskUi.isEditableByMe

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = taskUi.task.taskName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (taskUi.task.taskDescription.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = taskUi.task.taskDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            SuggestionChip(
                onClick = onAssignClick,
                enabled = isEditable,
                label = {
                    Text(
                        text = taskUi.assigneeName,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDelete,
                    enabled = isEditable
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete task",
                        tint = if (isEditable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                IconButton(
                    onClick = onUpdate,
                    enabled = isEditable
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit task",
                        tint = if (isEditable) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (onMoveBackward != null) {
                    IconButton(
                        onClick = { onMoveBackward(taskUi.task) },
                        enabled = isEditable
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Move task back",
                            tint = if (isEditable) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.3f)
                        )
                    }
                }
                if (onMoveForward != null) {
                    IconButton(
                        onClick = { onMoveForward(taskUi.task) },
                        enabled = isEditable
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Move task forward",
                            tint = if (isEditable) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskDialog(
    viewModel: TaskViewModel,
    members: List<UserEntity>,
    onDismiss: () -> Unit
) {
    val name by viewModel.taskName.collectAsStateWithLifecycle()
    val description by viewModel.taskDescription.collectAsStateWithLifecycle()
    val assignedUserId by viewModel.assignedUserId.collectAsStateWithLifecycle()

    var dropdownExpanded by remember { mutableStateOf(false) }
    val selectedMemberName = members.find { it.id == assignedUserId }?.userName ?: "Unassigned"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Create New Task", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { viewModel.onTaskNameChange(it) },
                    label = { Text("Task Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { viewModel.onTaskDescriptionChange(it) },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedMemberName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Assign Member") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Unassigned") },
                            onClick = {
                                viewModel.onAssigneeChange(null)
                                dropdownExpanded = false
                            }
                        )
                        members.forEach { member ->
                            DropdownMenuItem(
                                text = { Text(member.userName) },
                                onClick = {
                                    viewModel.onAssigneeChange(member.id)
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isNotEmpty()) {
                        viewModel.onCreateTask()
                        onDismiss()
                    }
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun UpdateTaskDialog(
    task: TaskEntity,
    viewModel: TaskViewModel,
    onDismiss: () -> Unit
) {
    var updatedName by remember { mutableStateOf(task.taskName) }
    var updatedDescription by remember { mutableStateOf(task.taskDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Update Task", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = updatedName,
                    onValueChange = { updatedName = it },
                    label = { Text("Task Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = updatedDescription,
                    onValueChange = { updatedDescription = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (updatedName.trim().isNotEmpty()) {
                        viewModel.onUpdateTask(task, updatedName, updatedDescription)
                        onDismiss()
                    }
                }
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AssignMemberDialog(
    task: TaskEntity,
    members: List<UserEntity>,
    onAssign: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Assign Member", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    ListItem(
                        headlineContent = { Text("Unassigned") },
                        modifier = Modifier.clickable { onAssign(null) }
                    )
                    HorizontalDivider()
                }
                items(members) { member ->
                    ListItem(
                        headlineContent = { Text(member.userName) },
                        modifier = Modifier.clickable { onAssign(member.id) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteTaskConfirmationDialog(
    taskName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Delete Task", fontWeight = FontWeight.Bold) },
        text = { Text("Are you sure you want to delete '$taskName'? This action cannot be undone.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}