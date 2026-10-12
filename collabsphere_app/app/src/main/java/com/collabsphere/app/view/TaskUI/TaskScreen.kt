package com.collabsphere.app.view.TaskUI

import com.collabsphere.app.view.components.CollabSpinner

import com.collabsphere.app.viewmodel.GitHubViewModel
import com.collabsphere.app.view.components.AppToast
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.collabsphere.app.model.UserEntity
import com.collabsphere.app.model.task.TaskEntity
import com.collabsphere.app.model.task.TaskStatus
import com.collabsphere.app.ui.theme.*
import com.collabsphere.app.view.components.TaskSkeletonList
import com.collabsphere.app.viewmodel.task.TaskUiEvent
import com.collabsphere.app.viewmodel.task.TaskUiModel
import com.collabsphere.app.viewmodel.task.TaskViewModel

import com.collabsphere.app.viewmodel.vote.TaskVoteViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun TaskScreen(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier,
    gitHubViewModel: GitHubViewModel? = null,
    taskVoteViewModel: TaskVoteViewModel = koinViewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(viewModel.loggedWorkspaceId) {
        taskVoteViewModel.initWorkspace(viewModel.loggedWorkspaceId)
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is TaskUiEvent.ShowToast -> {
                    AppToast.show(event.message)
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
    val availableLabels by viewModel.availableLabels.collectAsStateWithLifecycle()
    val labelFilter by viewModel.labelFilter.collectAsStateWithLifecycle()
    val pendingProposals by taskVoteViewModel.pendingProposals.collectAsStateWithLifecycle()

    var selectedViewMode by remember { mutableIntStateOf(0) } // 0 = Board, 1 = Team Votes
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
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedViewMode == 0) "Kanban board" else "Team votes",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Tactile Header Action Button
                SkeuoHeaderActionButton(
                    text = "Propose task",
                    icon = Icons.Default.Add,
                    onClick = { showCreateDialog = true }
                )
            }

            // View Toggle Pills Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selectedViewMode == 0) SurfaceRaised else Color.Transparent)
                        .clickable { selectedViewMode = 0 }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Board (${tasks.size})",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selectedViewMode == 0) Ink else Muted
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selectedViewMode == 1) SurfaceRaised else Color.Transparent)
                        .clickable { selectedViewMode = 1 }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Votes",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (selectedViewMode == 1) Ink else Muted
                        )
                        if (pendingProposals.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Amber),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${pendingProposals.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            if (selectedViewMode == 1) {
                TaskVotingSection(
                    viewModel = taskVoteViewModel,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LabelFilterRow(
                    labels = availableLabels,
                    selected = labelFilter,
                    onSelect = viewModel::onLabelFilterChange,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
                )

                // Kanban Horizontal Scroll Columns
                androidx.compose.animation.Crossfade(
                    targetState = Pair(isSyncing, tasks.isEmpty()),
                    label = "tasksCrossfade",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { (syncing, empty) ->
                when {
                    syncing && empty -> {
                        TaskSkeletonList(modifier = Modifier.fillMaxSize())
                    }
                    empty -> {
                        com.collabsphere.app.view.components.SkeuoEmptyState(
                            icon = androidx.compose.material.icons.Icons.Default.AssignmentInd,
                            title = "No tasks yet",
                            description = "Tap the + button or create your first task to start tracking work.",
                            actionLabel = null,
                            onAction = {},
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
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
                        } // Row
                    } // else
                } // when
            } // Crossfade
            } // else
        } // Column

        if (showCreateDialog) {
            CreateTaskDialog(
                viewModel = viewModel,
                members = members,
                onDismiss = { showCreateDialog = false },
                taskVoteViewModel = taskVoteViewModel
            )
        }

        taskToDelete?.let { task ->
            RequestDeleteProposalDialog(
                taskName = task.taskName,
                onConfirm = { reason ->
                    taskVoteViewModel.submitDeleteProposal(
                        workspaceId = viewModel.loggedWorkspaceId,
                        taskId = task.id,
                        deleteReason = reason
                    ) { success ->
                        if (success) {
                            AppToast.show("Deletion proposal submitted for team vote")
                            taskToDelete = null
                        }
                    }
                },
                onDismiss = { taskToDelete = null }
            )
        }

        taskToUpdate?.let { task ->
            UpdateTaskDialog(
                task = task,
                viewModel = viewModel,
                onDismiss = { taskToUpdate = null },
                gitHubViewModel = gitHubViewModel
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
            .drawWithCache {
onDrawBehind {
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
                        .drawWithCache {
onDrawBehind {
                            drawCircle(
                                color = SurfaceRaised,
                                radius = size.minDimension / 2f
                            )
                        }
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
                val columnListState = rememberLazyListState()
                LazyColumn(
                    state = columnListState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .fadingScrollEdges(listState = columnListState, top = 16.dp, bottom = 24.dp)
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "taskCardScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .skeuoFloatingCard(cornerRadius = 14.dp, isPressed = isPressed)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onUpdate
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column {
            taskReferenceLabel(taskUi.task.id)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9E8E89)
                )
            }
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

            TaskPlanningBadges(task = taskUi.task)
            TaskLabelsAndProgress(task = taskUi.task)

            Spacer(modifier = Modifier.height(10.dp))

            // Assignee debossed pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .drawWithCache {
onDrawBehind {
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
                SkeuoActionIconButton(
                    onClick = onDelete,
                    enabled = isEditable,
                    icon = Icons.Default.DeleteOutline,
                    contentDescription = "Delete task",
                    tint = DestructiveStart,
                    size = 30.dp,
                    iconSize = 15.dp
                )

                Spacer(modifier = Modifier.width(6.dp))

                SkeuoActionIconButton(
                    onClick = onUpdate,
                    enabled = isEditable,
                    icon = Icons.Default.Edit,
                    contentDescription = "Edit task",
                    tint = IndigoStart,
                    size = 30.dp,
                    iconSize = 15.dp
                )

                Spacer(modifier = Modifier.weight(1f))

                if (onMoveBackward != null) {
                    SkeuoActionIconButton(
                        onClick = { onMoveBackward(taskUi.task) },
                        enabled = isEditable,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Move back",
                        tint = Ink,
                        size = 30.dp,
                        iconSize = 15.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                if (onMoveForward != null) {
                    SkeuoActionIconButton(
                        onClick = { onMoveForward(taskUi.task) },
                        enabled = isEditable,
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Move forward",
                        tint = CoralStart,
                        size = 30.dp,
                        iconSize = 15.dp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskBottomSheet(
    viewModel: TaskViewModel,
    members: List<UserEntity>,
    onDismiss: () -> Unit,
    taskVoteViewModel: TaskVoteViewModel? = null
) {
    val name by viewModel.taskName.collectAsStateWithLifecycle()
    val description by viewModel.taskDescription.collectAsStateWithLifecycle()
    val assignedUserId by viewModel.assignedUserId.collectAsStateWithLifecycle()
    val dueDate by viewModel.dueDate.collectAsStateWithLifecycle()
    val priority by viewModel.priority.collectAsStateWithLifecycle()
    val labels by viewModel.labels.collectAsStateWithLifecycle()
    val isCreatingTask by viewModel.isCreatingTask.collectAsStateWithLifecycle()

    var dropdownExpanded by remember { mutableStateOf(false) }
    val selectedMemberName = members.find { it.id == assignedUserId }?.userName ?: "Unassigned"

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceRaised,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Muted.copy(alpha = 0.4f))
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header: Icon + Title & Subtitle + Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(CoralStart, CoralEnd)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Propose new task",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            ),
                            color = Ink
                        )
                        Text(
                            text = "Team proposal for voting & tracking",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Surface)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // SECTION: TASK DETAILS
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TASK DETAILS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = Muted
                )

                // Debossed Name Field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .skeuoInset(cornerRadius = 14.dp, depth = 2.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { viewModel.onTaskNameChange(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(CoralStart),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (name.isEmpty()) {
                                    Text(
                                        text = "What needs to be done?",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Ink.copy(alpha = 0.45f)
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
                        .height(72.dp)
                        .skeuoInset(cornerRadius = 14.dp, depth = 2.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.TopStart
                ) {
                    BasicTextField(
                        value = description,
                        onValueChange = { viewModel.onTaskDescriptionChange(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        singleLine = false,
                        maxLines = 3,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                        cursorBrush = SolidColor(CoralStart),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.TopStart) {
                                if (description.isEmpty()) {
                                    Text(
                                        text = "Add optional details or acceptance criteria...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Ink.copy(alpha = 0.45f)
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }
            }

            // SECTION: PRIORITY & SCHEDULING
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PRIORITY & SCHEDULING",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = Muted
                )

                PrioritySelector(selected = priority, onSelect = viewModel::onPriorityChange)

                DueDateSelector(dueDate = dueDate, onDueDateChange = viewModel::onDueDateChange)
            }

            // SECTION: ASSIGN TO
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ASSIGN TO",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = Muted
                )

                Box {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .skeuoInset(cornerRadius = 14.dp, depth = 2.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { dropdownExpanded = true }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (assignedUserId != null) CoralStart else Muted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = selectedMemberName,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (assignedUserId != null) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (assignedUserId != null) Ink else Muted
                                )
                            }
                            Text(
                                text = "▼",
                                fontSize = 10.sp,
                                color = Muted
                            )
                        }
                    }

                    DropdownMenu(
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
            }

            // SECTION: LABELS
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "LABELS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = Muted
                )

                LabelEditor(labels = labels, onLabelsChange = viewModel::onLabelsChange)
            }

            Spacer(Modifier.height(4.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface)
                        .clickable(enabled = !isCreatingTask, onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Muted
                    )
                }

                val canCreate = name.trim().isNotEmpty() && !isCreatingTask
                val createAlpha = if (canCreate) 1f else 0.65f
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .drawWithCache {
                            onDrawBehind {
                                drawRoundRect(
                                    color = CoralStart.copy(alpha = 0.28f * createAlpha),
                                    topLeft = Offset(0f, 3.dp.toPx()),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(14.dp.toPx())
                                )
                                drawRoundRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            CoralStart.copy(alpha = createAlpha),
                                            CoralEnd.copy(alpha = createAlpha)
                                        ),
                                        start = Offset(0f, 0f),
                                        end = Offset(size.width, size.height)
                                    ),
                                    cornerRadius = CornerRadius(14.dp.toPx())
                                )
                            }
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = canCreate) {
                            if (canCreate) {
                                if (taskVoteViewModel != null) {
                                    taskVoteViewModel.submitCreateProposal(
                                        workspaceId = viewModel.loggedWorkspaceId,
                                        taskName = name.trim(),
                                        taskDescription = description.trim(),
                                        assignedToUserId = assignedUserId,
                                        priority = priority.name,
                                        dueDate = dueDate,
                                        labels = labels
                                    ) { success ->
                                        if (success) {
                                            AppToast.show("Task proposal submitted for team vote")
                                            viewModel.onTaskNameChange("")
                                            viewModel.onTaskDescriptionChange("")
                                            viewModel.onAssigneeChange(null)
                                            viewModel.onDueDateChange(null)
                                            viewModel.onPriorityChange(com.collabsphere.app.model.task.TaskPriority.MEDIUM)
                                            viewModel.onLabelsChange(emptyList())
                                            onDismiss()
                                        }
                                    }
                                } else {
                                    viewModel.onCreateTask(onCreated = onDismiss)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isCreatingTask) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Propose Task",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = Color.White.copy(alpha = if (canCreate) 1f else 0.85f)
                        )
                    }
                }
            }
        }
    }
}

// Backward compatibility alias
@Composable
fun CreateTaskDialog(
    viewModel: TaskViewModel,
    members: List<UserEntity>,
    onDismiss: () -> Unit,
    taskVoteViewModel: TaskVoteViewModel? = null
) {
    CreateTaskBottomSheet(
        viewModel = viewModel,
        members = members,
        onDismiss = onDismiss,
        taskVoteViewModel = taskVoteViewModel
    )
}

@Composable
fun UpdateTaskDialog(
    task: TaskEntity,
    viewModel: TaskViewModel,
    onDismiss: () -> Unit,
    gitHubViewModel: GitHubViewModel? = null
) {
    var updatedName by remember { mutableStateOf(task.taskName) }
    var updatedDescription by remember { mutableStateOf(task.taskDescription) }
    var updatedDueDate by remember { mutableStateOf(task.dueDate) }
    var updatedPriority by remember { mutableStateOf(task.priority) }
    var updatedChecklist by remember { mutableStateOf(task.checklist) }
    var updatedLabels by remember { mutableStateOf(task.labels) }
    val canPlan = viewModel.canPlan(task)

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithCache {
onDrawBehind {
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
}
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Update task",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Ink
                )
                taskReferenceLabel(task.id)?.let {
                    Text(text = it, style = MaterialTheme.typography.labelMedium, color = Muted)
                }

                // Debossed Name Field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .skeuoInset(cornerRadius = 14.dp, depth = 2.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = updatedName,
                        onValueChange = { updatedName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                        cursorBrush = SolidColor(CoralStart),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (updatedName.isEmpty()) {
                                    Text(
                                        text = "Task name",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Ink.copy(alpha = 0.65f)
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
                        .skeuoInset(cornerRadius = 14.dp, depth = 2.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = updatedDescription,
                        onValueChange = { updatedDescription = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                        cursorBrush = SolidColor(CoralStart),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (updatedDescription.isEmpty()) {
                                    Text(
                                        text = "Description (optional)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Ink.copy(alpha = 0.65f)
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }

                PrioritySelector(
                    selected = updatedPriority,
                    onSelect = { updatedPriority = it },
                    enabled = canPlan
                )

                DueDateSelector(
                    dueDate = updatedDueDate,
                    onDueDateChange = { updatedDueDate = it },
                    enabled = canPlan
                )

                LabelEditor(
                    labels = updatedLabels,
                    onLabelsChange = { updatedLabels = it },
                    enabled = canPlan
                )

                ChecklistEditor(
                    items = updatedChecklist,
                    onItemsChange = { updatedChecklist = it },
                    enabled = canPlan
                )

                if (gitHubViewModel != null) {
                    TaskGitHubLinksSection(
                        workspaceId = task.workspaceId,
                        taskId = task.id,
                        gitHubViewModel = gitHubViewModel
                    )
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

                    val canUpdate = updatedName.trim().isNotEmpty()
                    val updateAlpha = if (canUpdate) 1f else 0.72f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .drawWithCache {
onDrawBehind {
                                drawRoundRect(
                                    color = IndigoStart.copy(alpha = 0.25f * updateAlpha),
                                    topLeft = Offset(0f, 2.dp.toPx()),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                                drawRoundRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            IndigoStart.copy(alpha = updateAlpha),
                                            IndigoEnd.copy(alpha = updateAlpha)
                                        ),
                                        start = Offset(0f, 0f),
                                        end = Offset(size.width, size.height)
                                    ),
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                            }
}
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = canUpdate) {
                                if (updatedName.trim().isNotEmpty()) {
                                    viewModel.onUpdateTask(task, updatedName, updatedDescription, updatedDueDate, updatedPriority, updatedChecklist, updatedLabels)
                                    onDismiss()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Update",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = if (canUpdate) 1f else 0.85f)
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
                .drawWithCache {
onDrawBehind {
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

                val assignListState = rememberLazyListState()
                LazyColumn(
                    state = assignListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .fadingScrollEdges(listState = assignListState, top = 14.dp, bottom = 14.dp),
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

                    items(members, key = { it.id }) { member ->
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
                .drawWithCache {
onDrawBehind {
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
                            .drawWithCache {
onDrawBehind {
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
