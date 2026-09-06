package com.example.rohit_project_challlange.view.TaskUI

import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rohit_project_challlange.model.UserEntity
import com.example.rohit_project_challlange.model.task.TaskEntity
import com.example.rohit_project_challlange.model.task.TaskStatus
import com.example.rohit_project_challlange.ui.theme.*
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Kanban board",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    // Total tasks pill
                    Box(
                        modifier = Modifier
                            .drawBehind {
                                drawRoundRect(
                                    color = ShadowDark.copy(alpha = 0.12f),
                                    topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                                drawRoundRect(
                                    color = SurfaceRaised,
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${tasks.size} tasks",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Muted
                        )
                    }
                }

                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = CoralStart
                    )
                }
            }

            // Kanban Horizontal Scroll Columns
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val toDoTasks = tasks.filter { it.task.status == TaskStatus.TO_DO }
                val inProgressTasks = tasks.filter { it.task.status == TaskStatus.IN_PROGRESS }
                val doneTasks = tasks.filter { it.task.status == TaskStatus.DONE }

                SkeuoKanbanColumn(
                    title = "To do",
                    tasks = toDoTasks,
                    accentColor = Amber,
                    onMoveForward = { viewModel.moveTask(it, TaskStatus.IN_PROGRESS) },
                    onMoveBackward = null,
                    onDelete = { taskToDelete = it },
                    onUpdate = { taskToUpdate = it },
                    onAssignClick = { taskToAssign = it }
                )

                SkeuoKanbanColumn(
                    title = "In progress",
                    tasks = inProgressTasks,
                    accentColor = IndigoStart,
                    onMoveForward = { viewModel.moveTask(it, TaskStatus.DONE) },
                    onMoveBackward = { viewModel.moveTask(it, TaskStatus.TO_DO) },
                    onDelete = { taskToDelete = it },
                    onUpdate = { taskToUpdate = it },
                    onAssignClick = { taskToAssign = it }
                )

                SkeuoKanbanColumn(
                    title = "Done",
                    tasks = doneTasks,
                    accentColor = Mint,
                    onMoveForward = null,
                    onMoveBackward = { viewModel.moveTask(it, TaskStatus.IN_PROGRESS) },
                    onDelete = { taskToDelete = it },
                    onUpdate = { taskToUpdate = it },
                    onAssignClick = { taskToAssign = it }
                )
            }
        }

        // Tactile Skeuomorphic FAB
        val fabInteractionSource = remember { MutableInteractionSource() }
        val isFabPressed by fabInteractionSource.collectIsPressedAsState()
        val fabScale by animateFloatAsState(
            targetValue = if (isFabPressed) 0.92f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "taskFabScale"
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .graphicsLayer { scaleX = fabScale; scaleY = fabScale }
                .drawBehind {
                    val shadowOffset = if (isFabPressed) 2.dp else 5.dp
                    val shadowAlpha = if (isFabPressed) 0.15f else 0.35f

                    drawRoundRect(
                        color = CoralStart.copy(alpha = shadowAlpha),
                        topLeft = Offset(0f, shadowOffset.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(20.dp.toPx())
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.30f),
                        topLeft = Offset(-1.5.dp.toPx(), -1.5.dp.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(20.dp.toPx())
                    )
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(CoralLight, CoralStart),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height)
                        ),
                        cornerRadius = CornerRadius(20.dp.toPx())
                    )
                    // Top hairline highlight hugging rounded contour
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.40f),
                                Color.White.copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = 20.dp.toPx()
                        ),
                        topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                        size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                        cornerRadius = CornerRadius(20.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                .clip(RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = fabInteractionSource,
                    indication = null
                ) {
                    showCreateDialog = true
                }
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "New task",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = Color.White
                )
            }
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
fun SkeuoKanbanColumn(
    title: String,
    tasks: List<TaskUiModel>,
    accentColor: Color,
    onMoveForward: ((TaskEntity) -> Unit)?,
    onMoveBackward: ((TaskEntity) -> Unit)?,
    onDelete: (TaskEntity) -> Unit,
    onUpdate: (TaskEntity) -> Unit,
    onAssignClick: (TaskEntity) -> Unit
) {
    Box(
        modifier = Modifier
            .width(286.dp)
            .fillMaxHeight()
            .padding(bottom = 88.dp)
            .drawBehind {
                drawRoundRect(
                    color = ShadowDark.copy(alpha = 0.16f),
                    topLeft = Offset(3.dp.toPx(), 4.dp.toPx()),
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(20.dp.toPx())
                )
                drawRoundRect(
                    color = ShadowLight.copy(alpha = 0.85f),
                    topLeft = Offset(-2.dp.toPx(), -2.dp.toPx()),
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(20.dp.toPx())
                )
                drawRoundRect(
                    color = Surface,
                    cornerRadius = CornerRadius(20.dp.toPx())
                )
                // Specular hairline highlight hugging rounded contour
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.70f),
                            Color.White.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 20.dp.toPx()
                    ),
                    topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                    size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .clip(RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Column Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Ink
                    )
                }

                // Count badge
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .drawBehind {
                            drawCircle(
                                color = SurfaceRaised,
                                radius = size.minDimension / 2f
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${tasks.size}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Muted
                    )
                }
            }

            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tasks",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(tasks, key = { it.task.id }) { taskUi ->
                        SkeuoKanbanTaskCard(
                            taskUi = taskUi,
                            accentColor = accentColor,
                            onMoveForward = onMoveForward,
                            onMoveBackward = onMoveBackward,
                            onDelete = { onDelete(taskUi.task) },
                            onUpdate = { onUpdate(taskUi.task) },
                            onAssignClick = { onAssignClick(taskUi.task) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SkeuoKanbanTaskCard(
    taskUi: TaskUiModel,
    accentColor: Color,
    onMoveForward: ((TaskEntity) -> Unit)?,
    onMoveBackward: ((TaskEntity) -> Unit)?,
    onDelete: () -> Unit,
    onUpdate: () -> Unit,
    onAssignClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEditable = taskUi.isEditableByMe

    Box(
        modifier = modifier
            .fillMaxWidth()
            .skeuoFloatingCard(cornerRadius = 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column {
            Text(
                text = taskUi.task.taskName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp
                ),
                color = Color(0xFF1F1A17),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (taskUi.task.taskDescription.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = taskUi.task.taskDescription,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    ),
                    color = Color(0xFF6E635C),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Assignee debossed pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .drawBehind {
                        val cr = 8.dp.toPx()
                        drawRoundRect(
                            color = Color(0xFF241A15).copy(alpha = 0.10f),
                            topLeft = Offset(1.dp.toPx(), 1.2.dp.toPx()),
                            size = Size(size.width - 1.dp.toPx(), size.height - 1.2.dp.toPx()),
                            cornerRadius = CornerRadius(cr)
                        )
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                listOf(Color(0xFFEDE8DF), Color(0xFFF7F4ED))
                            ),
                            topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                            size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                            cornerRadius = CornerRadius(cr)
                        )
                    }
                    .clickable(enabled = isEditable, onClick = onAssignClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = IndigoStart,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = taskUi.assigneeName.ifEmpty { "Unassigned" },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = Ink
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action icons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDelete,
                    enabled = isEditable,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete task",
                        tint = if (isEditable) Destructive else Muted.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onUpdate,
                    enabled = isEditable,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit task",
                        tint = if (isEditable) IndigoStart else Muted.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (onMoveBackward != null) {
                    IconButton(
                        onClick = { onMoveBackward(taskUi.task) },
                        enabled = isEditable,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Move back",
                            tint = if (isEditable) Ink else Muted.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (onMoveForward != null) {
                    IconButton(
                        onClick = { onMoveForward(taskUi.task) },
                        enabled = isEditable,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Move forward",
                            tint = if (isEditable) CoralStart else Muted.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
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

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRoundRect(
                        color = ShadowDark.copy(alpha = 0.35f),
                        topLeft = Offset(4.dp.toPx(), 8.dp.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                    drawRoundRect(
                        color = ShadowLight.copy(alpha = 0.90f),
                        topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                    drawRoundRect(
                        color = SurfaceRaised,
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                    // Specular hairline highlight hugging rounded contour
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.75f),
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = 24.dp.toPx()
                        ),
                        topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                        size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                        cornerRadius = CornerRadius(24.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Create new task",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Ink
                )

                // Debossed Name Field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = ShadowDark.copy(alpha = 0.22f),
                                topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                                size = Size(size.width - 1.5.dp.toPx(), size.height - 1.5.dp.toPx()),
                                cornerRadius = CornerRadius(14.dp.toPx())
                            )
                            drawRoundRect(
                                color = ShadowLight.copy(alpha = 0.85f),
                                topLeft = Offset(-1.dp.toPx(), -1.dp.toPx()),
                                size = Size(size.width + 1.dp.toPx(), size.height + 1.dp.toPx()),
                                cornerRadius = CornerRadius(14.dp.toPx())
                            )
                            drawRoundRect(
                                color = Background.copy(alpha = 0.85f),
                                cornerRadius = CornerRadius(14.dp.toPx())
                            )
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { viewModel.onTaskNameChange(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (name.isEmpty()) {
                                    Text(
                                        text = "Task name",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Muted.copy(alpha = 0.6f)
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }

                // Debossed Description Field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = ShadowDark.copy(alpha = 0.22f),
                                topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                                size = Size(size.width - 1.5.dp.toPx(), size.height - 1.5.dp.toPx()),
                                cornerRadius = CornerRadius(14.dp.toPx())
                            )
                            drawRoundRect(
                                color = ShadowLight.copy(alpha = 0.85f),
                                topLeft = Offset(-1.dp.toPx(), -1.dp.toPx()),
                                size = Size(size.width + 1.dp.toPx(), size.height + 1.dp.toPx()),
                                cornerRadius = CornerRadius(14.dp.toPx())
                            )
                            drawRoundRect(
                                color = Background.copy(alpha = 0.85f),
                                cornerRadius = CornerRadius(14.dp.toPx())
                            )
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = description,
                        onValueChange = { viewModel.onTaskDescriptionChange(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (description.isEmpty()) {
                                    Text(
                                        text = "Description (optional)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Muted.copy(alpha = 0.6f)
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }

                // Assignee selection row
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedMemberName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Assign member") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(SurfaceRaised)
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Surface)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Cancel", style = MaterialTheme.typography.labelLarge, color = Muted)
                    }

                    val canCreate = name.trim().isNotEmpty()
                    val btnColor = if (canCreate) CoralStart else Muted.copy(alpha = 0.45f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .drawBehind {
                                drawRoundRect(
                                    color = btnColor.copy(alpha = 0.25f),
                                    topLeft = Offset(0f, 2.dp.toPx()),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                                drawRoundRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(btnColor, if (canCreate) CoralEnd else btnColor),
                                        start = Offset(0f, 0f),
                                        end = Offset(size.width, size.height)
                                    ),
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = canCreate) {
                                if (name.trim().isNotEmpty()) {
                                    viewModel.onCreateTask()
                                    onDismiss()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Create",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateTaskDialog(
    task: TaskEntity,
    viewModel: TaskViewModel,
    onDismiss: () -> Unit
) {
    var updatedName by remember { mutableStateOf(task.taskName) }
    var updatedDescription by remember { mutableStateOf(task.taskDescription) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRoundRect(
                        color = ShadowDark.copy(alpha = 0.35f),
                        topLeft = Offset(4.dp.toPx(), 8.dp.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                    drawRoundRect(
                        color = ShadowLight.copy(alpha = 0.90f),
                        topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                    drawRoundRect(
                        color = SurfaceRaised,
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                    // Specular hairline highlight hugging rounded contour
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.75f),
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = 24.dp.toPx()
                        ),
                        topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                        size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                        cornerRadius = CornerRadius(24.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Update task",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Ink
                )

                OutlinedTextField(
                    value = updatedName,
                    onValueChange = { updatedName = it },
                    label = { Text("Task name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                OutlinedTextField(
                    value = updatedDescription,
                    onValueChange = { updatedDescription = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Surface)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Cancel", style = MaterialTheme.typography.labelLarge, color = Muted)
                    }

                    val canUpdate = updatedName.trim().isNotEmpty()
                    val btnColor = if (canUpdate) IndigoStart else Muted.copy(alpha = 0.45f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .drawBehind {
                                drawRoundRect(
                                    color = btnColor.copy(alpha = 0.25f),
                                    topLeft = Offset(0f, 2.dp.toPx()),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                                drawRoundRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(btnColor, if (canUpdate) IndigoEnd else btnColor),
                                        start = Offset(0f, 0f),
                                        end = Offset(size.width, size.height)
                                    ),
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = canUpdate) {
                                if (updatedName.trim().isNotEmpty()) {
                                    viewModel.onUpdateTask(task, updatedName, updatedDescription)
                                    onDismiss()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Update",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AssignMemberDialog(
    task: TaskEntity,
    members: List<UserEntity>,
    onAssign: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRoundRect(
                        color = ShadowDark.copy(alpha = 0.35f),
                        topLeft = Offset(4.dp.toPx(), 8.dp.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                    drawRoundRect(
                        color = ShadowLight.copy(alpha = 0.90f),
                        topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                    drawRoundRect(
                        color = SurfaceRaised,
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                }
                .padding(22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Assign member",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Ink
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Surface)
                                .clickable { onAssign(null) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Unassigned",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = Muted
                            )
                        }
                    }

                    items(members) { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Surface)
                                .clickable { onAssign(member.id) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(IndigoStart),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = member.userName.take(1).uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                            Text(
                                text = member.userName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Ink
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Cancel", style = MaterialTheme.typography.labelLarge, color = Muted)
                }
            }
        }
    }
}

@Composable
fun DeleteTaskConfirmationDialog(
    taskName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRoundRect(
                        color = ShadowDark.copy(alpha = 0.35f),
                        topLeft = Offset(4.dp.toPx(), 8.dp.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                    drawRoundRect(
                        color = ShadowLight.copy(alpha = 0.90f),
                        topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                    drawRoundRect(
                        color = SurfaceRaised,
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                }
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Destructive.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = Destructive,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "Delete task",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Ink
                )

                Text(
                    text = "Are you sure you want to delete '$taskName'? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Surface)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Cancel", style = MaterialTheme.typography.labelLarge, color = Muted)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .drawBehind {
                                drawRoundRect(
                                    color = Destructive.copy(alpha = 0.3f),
                                    topLeft = Offset(0f, 2.dp.toPx()),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                                drawRoundRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Destructive, Color(0xFFB52E2E)),
                                        start = Offset(0f, 0f),
                                        end = Offset(size.width, size.height)
                                    ),
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onConfirm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}