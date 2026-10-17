package com.collabsphere.app.view.TaskUI

import com.collabsphere.app.view.components.CollabSpinner

import com.collabsphere.app.viewmodel.GitHubViewModel
import com.collabsphere.app.view.components.AppToast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.outlined.TaskAlt
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
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.window.DialogProperties
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

    var selectedViewMode by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<TaskEntity?>(null) }
    var taskToUpdate by remember { mutableStateOf<TaskEntity?>(null) }
    var taskToAssign by remember { mutableStateOf<TaskEntity?>(null) }
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PrintPaper)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MisregisteredTitle(
                    text = if (selectedViewMode == 0) "Board" else "Team votes",
                    style = PrintType.H2,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                SkeuoHeaderActionButton(
                    text = "Propose task",
                    icon = Icons.Default.Add,
                    onClick = { showCreateDialog = true }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrintChip(
                    text = "Board · ${tasks.size}",
                    fill = if (selectedViewMode == 0) PrintLeaf else PrintWhite,
                    onClick = { selectedViewMode = 0 }
                )
                PrintChip(
                    text = if (pendingProposals.isEmpty()) "Votes" else "Votes · ${pendingProposals.size}",
                    fill = if (selectedViewMode == 1) PrintCoral else PrintWhite,
                    onClick = { selectedViewMode = 1 }
                )
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
                            description = "Tap “Propose task” to put your first piece of work up for a team vote.",
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
                                .padding(start = 20.dp, end = 24.dp, top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                    val toDoTasks = tasks.filter { it.task.status == TaskStatus.TO_DO }
                val inProgressTasks = tasks.filter { it.task.status == TaskStatus.IN_PROGRESS }
                val doneTasks = tasks.filter { it.task.status == TaskStatus.DONE }

                SkeuoKanbanColumn(
                    title = "To do",
                    tasks = toDoTasks,
                    accentColor = PrintCoral,
                    onMoveForward = { viewModel.moveTask(it, TaskStatus.IN_PROGRESS) },
                    onMoveBackward = null,
                    onDelete = { taskToDelete = it },
                    onUpdate = { taskToUpdate = it },
                    onAssignClick = { taskToAssign = it }
                )

                SkeuoKanbanColumn(
                    title = "In progress",
                    tasks = inProgressTasks,
                    accentColor = PrintSky,
                    onMoveForward = { viewModel.moveTask(it, TaskStatus.DONE) },
                    onMoveBackward = { viewModel.moveTask(it, TaskStatus.TO_DO) },
                    onDelete = { taskToDelete = it },
                    onUpdate = { taskToUpdate = it },
                    onAssignClick = { taskToAssign = it }
                )

                SkeuoKanbanColumn(
                    title = "Done",
                    tasks = doneTasks,
                    accentColor = PrintLeaf,
                    onMoveForward = null,
                    onMoveBackward = { viewModel.moveTask(it, TaskStatus.IN_PROGRESS) },
                    onDelete = { taskToDelete = it },
                    onUpdate = { taskToUpdate = it },
                    onAssignClick = { taskToAssign = it }
                )
                        }
                    }
                }
            }
            }
        }

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
            .printSurface(shape = RoundedCornerShape(PrintTokens.RadiusCard), fill = PrintPaper)
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .printSurface(shape = BlobShape(seed = title.length, wobble = 0.16f), fill = accentColor, shadow = null, outline = 1.5.dp)
                    )
                    Text(
                        text = title,
                        style = PrintType.H3,
                        color = PrintInk
                    )
                }
                PrintChip(text = "${tasks.size}", fill = accentColor)
            }

            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nothing here yet",
                        style = PrintType.Caption,
                        color = Muted
                    )
                }
            } else {
                val columnListState = rememberLazyListState()
                val entranceRegistry = rememberEntranceRegistry()
                LazyColumn(
                    state = columnListState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 4.dp, bottom = 8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .fadingScrollEdges(listState = columnListState, top = 16.dp, bottom = 24.dp)
                ) {
                    itemsIndexed(tasks, key = { _, it -> it.task.id }) { index, taskUi ->
                        SkeuoKanbanTaskCard(
                            taskUi = taskUi,
                            accentColor = accentColor,
                            onMoveForward = onMoveForward,
                            onMoveBackward = onMoveBackward,
                            onDelete = { onDelete(taskUi.task) },
                            onUpdate = { onUpdate(taskUi.task) },
                            onAssignClick = { onAssignClick(taskUi.task) },
                            modifier = Modifier
                                .animateItem()
                                .printListEntrance(taskUi.task.id, index, entranceRegistry)
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
    val press = rememberPrintPress(interactionSource)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .printSurface(shape = RoundedCornerShape(18.dp), shadowOffset = 3.dp, pressProgress = press)
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
                    style = PrintType.Tiny,
                    color = Muted
                )
            }
            Text(
                text = taskUi.task.taskName,
                style = PrintType.H3.copy(fontSize = 16.sp, lineHeight = 21.sp),
                color = PrintInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (taskUi.task.taskDescription.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = taskUi.task.taskDescription,
                    style = PrintType.Caption,
                    color = Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            TaskPlanningBadges(task = taskUi.task)
            TaskLabelsAndProgress(task = taskUi.task)

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(PrintPaper)
                    .border(1.5.dp, PrintInk, RoundedCornerShape(percent = 50))
                    .clickable(enabled = isEditable, onClick = onAssignClick)
                    .heightIn(min = 32.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = PrintInk,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = taskUi.assigneeName.ifEmpty { "Unassigned" },
                        style = PrintType.Caption.copy(fontWeight = FontWeight.Bold),
                        color = PrintInk
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                    size = 36.dp,
                    iconSize = 18.dp
                )

                Spacer(modifier = Modifier.width(6.dp))

                SkeuoActionIconButton(
                    onClick = onUpdate,
                    enabled = isEditable,
                    icon = Icons.Default.Edit,
                    contentDescription = "Edit task",
                    tint = PrintInk,
                    size = 36.dp,
                    iconSize = 18.dp
                )

                Spacer(modifier = Modifier.weight(1f))

                if (onMoveBackward != null) {
                    SkeuoActionIconButton(
                        onClick = { onMoveBackward(taskUi.task) },
                        enabled = isEditable,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Move back",
                        tint = Ink,
                        size = 36.dp,
                        iconSize = 18.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                if (onMoveForward != null) {
                    SkeuoActionIconButton(
                        onClick = { onMoveForward(taskUi.task) },
                        enabled = isEditable,
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Move forward",
                        tint = PrintInk,
                        fill = accentColor,
                        size = 36.dp,
                        iconSize = 18.dp
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
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
        containerColor = PrintWhite,
        scrimColor = PrintInk.copy(alpha = 0.45f),
        dragHandle = { PrintSheetHandle() },
        shape = PrintSheetShape,
        modifier = Modifier.printSheetOutline()
    ) {
        val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
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
                            .size(48.dp)
                            .printSurface(shape = BlobShape(seed = 11, wobble = 0.14f), fill = PrintLeaf, shadow = null),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = PrintInk,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Propose a task",
                            style = PrintType.H2.copy(fontSize = 24.sp, lineHeight = 28.sp),
                            color = PrintInk
                        )
                        Text(
                            text = "Your team votes before it lands on the board",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted
                        )
                    }
                }

                PrintIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Close",
                    onClick = onDismiss,
                    fill = PrintPaper,
                    size = 40.dp,
                    iconSize = 18.dp,
                    shadow = false
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TASK DETAILS",
                    style = PrintType.Tiny,
                    color = PrintInk
                )

                com.collabsphere.app.view.components.SkeuoTextField(
                    value = name,
                    onValueChange = { viewModel.onTaskNameChange(it) },
                    label = "Task name",
                    placeholder = "What needs to be done?",
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.TaskAlt,
                            contentDescription = null,
                            tint = PrintInk,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )

                Spacer(Modifier.height(4.dp))

                com.collabsphere.app.view.components.SkeuoTextField(
                    value = description,
                    onValueChange = { viewModel.onTaskDescriptionChange(it) },
                    label = "Description",
                    placeholder = "Optional details or acceptance criteria",
                    imeAction = ImeAction.Done,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PRIORITY & SCHEDULING",
                    style = PrintType.Tiny,
                    color = PrintInk
                )

                PrioritySelector(selected = priority, onSelect = viewModel::onPriorityChange)

                DueDateSelector(dueDate = dueDate, onDueDateChange = viewModel::onDueDateChange)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ASSIGN TO",
                    style = PrintType.Tiny,
                    color = PrintInk
                )

                Box {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .skeuoInset(cornerRadius = 27.dp, depth = 3.dp, symmetric = true, shadowStrength = com.collabsphere.app.view.components.FieldInsetShadow)
                            .clip(RoundedCornerShape(27.dp))
                            .clickable { dropdownExpanded = true }
                            .padding(start = 18.dp, end = 20.dp),
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
                                    tint = PrintInk,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = selectedMemberName,
                                    style = PrintType.Body.copy(
                                        fontWeight = if (assignedUserId != null) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (assignedUserId != null) PrintInk else Muted
                                )
                            }
                            Text(
                                text = "▼",
                                fontSize = 12.sp,
                                color = PrintInk
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(PrintWhite)
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

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "LABELS",
                    style = PrintType.Tiny,
                    color = PrintInk
                )

                LabelEditor(labels = labels, onLabelsChange = viewModel::onLabelsChange)
            }

            Spacer(Modifier.height(4.dp))

            val canCreate = name.trim().isNotEmpty() && !isCreatingTask
            com.collabsphere.app.view.components.WorkspaceDialogButton(
                text = "Propose task",
                loadingText = "Proposing…",
                colors = listOf(PrintInk),
                enabled = canCreate,
                loading = isCreatingTask,
                onClick = {
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
                }
            )
            PrintButton(
                text = "Cancel",
                onClick = onDismiss,
                primary = false,
                enabled = !isCreatingTask,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        com.collabsphere.app.view.components.WorkspaceDialogCard(
            icon = Icons.Default.Edit,
            accent = PrintSky,
            title = "Edit task",
            subtitle = taskReferenceLabel(task.id) ?: "Update the details and plan",
            onClose = onDismiss
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                com.collabsphere.app.view.components.SkeuoTextField(
                    value = updatedName,
                    onValueChange = { updatedName = it },
                    label = "Task name",
                    placeholder = "Task name",
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.TaskAlt,
                            contentDescription = null,
                            tint = PrintInk,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )

                com.collabsphere.app.view.components.SkeuoTextField(
                    value = updatedDescription,
                    onValueChange = { updatedDescription = it },
                    label = "Description",
                    placeholder = "Description (optional)",
                    imeAction = ImeAction.Done,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4
                )

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

                val canUpdate = updatedName.trim().isNotEmpty()
                com.collabsphere.app.view.components.WorkspaceDialogButton(
                    text = "Save changes",
                    colors = listOf(PrintInk),
                    enabled = canUpdate,
                    onClick = {
                        if (canUpdate) {
                            viewModel.onUpdateTask(task, updatedName, updatedDescription, updatedDueDate, updatedPriority, updatedChecklist, updatedLabels)
                            onDismiss()
                        }
                    }
                )
                PrintButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    primary = false,
                    modifier = Modifier.fillMaxWidth()
                )
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        com.collabsphere.app.view.components.WorkspaceDialogCard(
            icon = Icons.Default.AssignmentInd,
            accent = PrintLeaf,
            title = "Assign member",
            subtitle = task.taskName,
            onClose = onDismiss
        ) {
            val assignListState = rememberLazyListState()
            LazyColumn(
                state = assignListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .fadingScrollEdges(listState = assignListState, top = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    AssignMemberRow(
                        name = "Unassigned",
                        initial = "–",
                        fill = PrintPaper,
                        selected = task.assignedToUserId == null,
                        onClick = { onAssign(null) }
                    )
                }
                itemsIndexed(members, key = { _, it -> it.id }) { index, member ->
                    AssignMemberRow(
                        name = member.userName,
                        initial = member.userName.take(1).uppercase(),
                        fill = listOf(PrintSky, PrintLeaf, PrintCoral)[index % 3],
                        selected = task.assignedToUserId == member.id,
                        onClick = { onAssign(member.id) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            PrintButton(
                text = "Cancel",
                onClick = onDismiss,
                primary = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AssignMemberRow(
    name: String,
    initial: String,
    fill: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(PrintTokens.RadiusSmall)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(if (selected) PrintLeaf.copy(alpha = 0.35f) else PrintWhite)
            .border(1.5.dp, PrintInk, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .printSurface(shape = CircleShape, fill = fill, shadow = null, outline = 1.5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = PrintType.Label.copy(fontSize = 14.sp),
                color = PrintInk
            )
        }
        Text(
            text = name,
            style = PrintType.Body.copy(fontWeight = FontWeight.Bold),
            color = PrintInk,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Current assignee",
                tint = PrintInk,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun DeleteTaskConfirmationDialog(
    taskName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    com.collabsphere.app.view.components.PrintConfirmDialog(
        title = "Delete “$taskName”?",
        message = "This task will be removed from the board for everyone.",
        confirmLabel = "Delete task",
        icon = Icons.Default.WarningAmber,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
