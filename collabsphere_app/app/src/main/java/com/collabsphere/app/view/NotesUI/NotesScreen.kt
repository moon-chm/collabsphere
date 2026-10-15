package com.collabsphere.app.view.NotesUI

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import com.collabsphere.app.view.components.MarkdownText
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.collabsphere.app.view.components.NotesSkeletonList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.collabsphere.app.model.notes.NotesEntity
import com.collabsphere.app.ui.theme.*
import com.collabsphere.app.viewmodel.notes.NotesViewModel

@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    modifier: Modifier = Modifier
) {
    val notes by viewModel.allNotes.collectAsStateWithLifecycle()
    val notesstatus by viewModel.notesStatus.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var noteToDelete by remember { mutableStateOf<NotesEntity?>(null) }
    var noteToEdit by remember { mutableStateOf<NotesEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(notesstatus) {
        notesstatus?.let { status ->
            snackbarHostState.showSnackbar(status)
            viewModel.clearStatus()
        }
    }

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
                Text(
                    text = "Notes",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Notes count pill
                    Box(
                        modifier = Modifier
                            .drawWithCache {
                                onDrawBehind {
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
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${notes?.size ?: "…"} notes",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Muted
                        )
                    }

                    // Tactile Header Action Button
                    SkeuoHeaderActionButton(
                        text = "New Note",
                        icon = Icons.Default.Add,
                        onClick = { showCreateDialog = true }
                    )
                }
            }

            androidx.compose.animation.Crossfade(
                targetState = notes,
                label = "notesCrossfade",
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { state ->
                when {
                    state == null -> {
                        NotesSkeletonList(modifier = Modifier.fillMaxSize())
                    }
                    state.isEmpty() -> {
                        com.collabsphere.app.view.components.SkeuoEmptyState(
                            icon = Icons.Default.Description,
                            title = "No notes found",
                            description = "Tap 'Add note' below to jot down quick thoughts, project summaries, and shared documentation.",
                            actionLabel = null,
                            onAction = {},
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        val listState = rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .fadingScrollEdges(listState = listState, top = 20.dp, bottom = 48.dp),
                            contentPadding = PaddingValues(
                                start = 18.dp,
                                end = 18.dp,
                                top = 6.dp,
                                bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(state, key = { it.id }) { note ->
                                SkeuoNotesItem(
                                    notes = note,
                                    deleteNote = { noteToDelete = note },
                                    updateNote = { noteToEdit = note },
                                    togglePin = { viewModel.togglePin(note) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }
            }
        }


        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp)
        )

        if (showCreateDialog) {
            CreateNotesDialog(
                viewModel = viewModel,
                onDismiss = { showCreateDialog = false }
            )
        }

        noteToEdit?.let { note ->
            LaunchedEffect(note) {
                viewModel.onNotesNameChange(note.notesName)
                viewModel.onNotesDescChange(note.description)
            }

            CreateNotesDialog(
                viewModel = viewModel,
                isEditMode = true,
                onDismiss = {
                    noteToEdit = null
                    viewModel.clearInputs()
                },
                onConfirmUpdate = {
                    viewModel.updateNote(note)
                    noteToEdit = null
                }
            )
        }

        noteToDelete?.let { note ->
            DeleteNoteConfirmationDialog(
                noteName = note.notesName,
                onConfirm = {
                    viewModel.deleteNote(noteId = note.id, name = note.notesName)
                    noteToDelete = null
                },
                onDismiss = { noteToDelete = null }
            )
        }
    }
}

@Composable
fun SkeuoNotesItem(
    notes: NotesEntity,
    deleteNote: () -> Unit,
    updateNote: () -> Unit,
    togglePin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "notesScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .skeuoFloatingCard(cornerRadius = 18.dp, isPressed = isPressed)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = updateNote
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Note Icon in debossed squircle well
                SkeuoDebossedIconWell(wellSize = 46.dp, cornerRadius = 14.dp) {
                    Icon(
                        imageVector = Icons.Default.StickyNote2,
                        contentDescription = null,
                        tint = Color(0xFF2C221E),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = notes.notesName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.5.sp
                        ),
                        color = Color(0xFF1F1A17),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (notes.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        MarkdownText(
                            text = notes.description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.5.sp,
                                lineHeight = 18.sp
                            ),
                            color = Color(0xFF6E635C),
                            linkColor = IndigoStart,
                            maxLines = 1
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeuoActionIconButton(
                    onClick = togglePin,
                    icon = Icons.Default.PushPin,
                    contentDescription = if (notes.isPinned) "Unpin note" else "Pin note",
                    tint = if (notes.isPinned) CoralStart else Muted,
                    size = 32.dp,
                    iconSize = 16.dp
                )

                Spacer(modifier = Modifier.width(6.dp))

                SkeuoActionIconButton(
                    onClick = updateNote,
                    icon = Icons.Default.Edit,
                    contentDescription = "Edit note",
                    tint = IndigoStart,
                    size = 32.dp,
                    iconSize = 16.dp
                )

                Spacer(modifier = Modifier.width(6.dp))

                SkeuoActionIconButton(
                    onClick = deleteNote,
                    icon = Icons.Default.DeleteOutline,
                    contentDescription = "Delete note",
                    tint = DestructiveStart,
                    size = 32.dp,
                    iconSize = 16.dp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNotesBottomSheet(
    viewModel: NotesViewModel,
    isEditMode: Boolean = false,
    onDismiss: () -> Unit,
    onConfirmUpdate: () -> Unit = {}
) {
    val name by viewModel.notesName.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            onDismiss()
            viewModel.clearInputs()
        },
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
            // Header
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
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = if (isEditMode) "Edit note" else "New note",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = Ink
                        )
                        Text(
                            text = if (isEditMode) "Update note contents" else "Capture ideas, minutes, or documentation",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted
                        )
                    }
                }

                IconButton(
                    onClick = {
                        onDismiss()
                        viewModel.clearInputs()
                    },
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

            // SECTION: NOTE DETAILS
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "NOTE DETAILS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = Muted
                )

                com.collabsphere.app.view.components.SkeuoTextField(
                    value = name,
                    onValueChange = { viewModel.onNotesNameChange(it) },
                    label = "Title",
                    placeholder = "e.g. Weekly Sync Notes",
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = CoralStart.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                Spacer(Modifier.height(4.dp))

                com.collabsphere.app.view.components.SkeuoTextField(
                    value = description,
                    onValueChange = { viewModel.onNotesDescChange(it) },
                    label = "Content",
                    placeholder = "Write your note here (Markdown supported)...",
                    imeAction = ImeAction.Default,
                    singleLine = false,
                    minLines = 5,
                    maxLines = 12
                )
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
                        .clickable {
                            onDismiss()
                            viewModel.clearInputs()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Muted
                    )
                }

                val canSubmit = name.trim().isNotEmpty()
                val submitAlpha = if (canSubmit) 1f else 0.65f
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .drawWithCache {
                            onDrawBehind {
                                drawRoundRect(
                                    color = CoralStart.copy(alpha = 0.28f * submitAlpha),
                                    topLeft = Offset(0f, 3.dp.toPx()),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(14.dp.toPx())
                                )
                                drawRoundRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            CoralStart.copy(alpha = submitAlpha),
                                            CoralEnd.copy(alpha = submitAlpha)
                                        ),
                                        start = Offset(0f, 0f),
                                        end = Offset(size.width, size.height)
                                    ),
                                    cornerRadius = CornerRadius(14.dp.toPx())
                                )
                            }
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = canSubmit) {
                            if (canSubmit) {
                                if (isEditMode) {
                                    onConfirmUpdate()
                                } else {
                                    viewModel.createNote()
                                }
                                onDismiss()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isEditMode) "Save Note" else "Create Note",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = Color.White.copy(alpha = if (canSubmit) 1f else 0.85f)
                    )
                }
            }
        }
    }
}

// Backward compatibility alias
@Composable
fun CreateNotesDialog(
    viewModel: NotesViewModel,
    isEditMode: Boolean = false,
    onDismiss: () -> Unit,
    onConfirmUpdate: () -> Unit = {}
) {
    CreateNotesBottomSheet(
        viewModel = viewModel,
        isEditMode = isEditMode,
        onDismiss = onDismiss,
        onConfirmUpdate = onConfirmUpdate
    )
}

@Composable
fun DeleteNoteConfirmationDialog(
    noteName: String,
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
                    text = "Delete note",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Ink
                )

                Text(
                    text = "Are you sure you want to delete '$noteName'? This action cannot be undone.",
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