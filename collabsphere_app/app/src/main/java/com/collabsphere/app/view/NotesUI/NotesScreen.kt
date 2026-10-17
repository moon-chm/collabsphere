package com.collabsphere.app.view.NotesUI

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.itemsIndexed
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
            .background(PrintPaper)
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
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MisregisteredTitle(text = "Notes", style = PrintType.H2)
                    PrintChip(text = "${notes?.size ?: "…"} notes", fill = PrintSky)
                }
                Spacer(Modifier.width(12.dp))
                SkeuoHeaderActionButton(
                    text = "New note",
                    icon = Icons.Default.Add,
                    onClick = { showCreateDialog = true }
                )
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
                            title = "No notes yet",
                            description = "Tap “New note” to jot down ideas, meeting notes and shared docs.",
                            actionLabel = null,
                            onAction = {},
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        val listState = rememberLazyListState()
                        val entranceRegistry = rememberEntranceRegistry()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .fadingScrollEdges(listState = listState, top = 20.dp, bottom = 48.dp),
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 6.dp,
                                bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(state, key = { _, it -> it.id }) { index, note ->
                                SkeuoNotesItem(
                                    notes = note,
                                    deleteNote = { noteToDelete = note },
                                    updateNote = { noteToEdit = note },
                                    togglePin = { viewModel.togglePin(note) },
                                    modifier = Modifier
                                        .animateItem()
                                        .printListEntrance(note.id, index, entranceRegistry)
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
    val press = rememberPrintPress(interactionSource)
    val tagShape = remember(notes.id) { BlobShape(seed = notes.id, wobble = 0.14f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .printSurface(shape = RoundedCornerShape(PrintTokens.RadiusCard), pressProgress = press)
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
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .printSurface(shape = tagShape, fill = if (notes.isPinned) PrintCoral else PrintSky, shadow = null),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.StickyNote2,
                        contentDescription = null,
                        tint = PrintInk,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = notes.notesName,
                        style = PrintType.H3,
                        color = PrintInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (notes.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        MarkdownText(
                            text = notes.description,
                            style = PrintType.Caption,
                            color = Muted,
                            linkColor = PrintInk,
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
                    tint = PrintInk,
                    fill = if (notes.isPinned) PrintCoral else PrintWhite,
                    size = 36.dp,
                    iconSize = 18.dp
                )

                SkeuoActionIconButton(
                    onClick = updateNote,
                    icon = Icons.Default.Edit,
                    contentDescription = "Edit note",
                    tint = PrintInk,
                    size = 36.dp,
                    iconSize = 18.dp
                )

                SkeuoActionIconButton(
                    onClick = deleteNote,
                    icon = Icons.Default.DeleteOutline,
                    contentDescription = "Delete note",
                    tint = DestructiveStart,
                    size = 36.dp,
                    iconSize = 18.dp
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
        containerColor = PrintWhite,
        scrimColor = PrintInk.copy(alpha = 0.45f),
        dragHandle = { PrintSheetHandle() },
        shape = PrintSheetShape,
        modifier = Modifier.printSheetOutline()
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
                            .size(48.dp)
                            .printSurface(shape = BlobShape(seed = 9, wobble = 0.14f), fill = PrintSky, shadow = null),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = PrintInk,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = if (isEditMode) "Edit note" else "New note",
                            style = PrintType.H2.copy(fontSize = 24.sp, lineHeight = 28.sp),
                            color = PrintInk
                        )
                        Text(
                            text = if (isEditMode) "Update note contents" else "Capture ideas, minutes, or documentation",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted
                        )
                    }
                }

                PrintIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Close",
                    onClick = {
                        onDismiss()
                        viewModel.clearInputs()
                    },
                    fill = PrintPaper,
                    size = 40.dp,
                    iconSize = 18.dp,
                    shadow = false
                )
            }

            // SECTION: NOTE DETAILS
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "NOTE DETAILS",
                    style = PrintType.Tiny,
                    color = PrintInk
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
                            tint = PrintInk,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )

                Spacer(Modifier.height(4.dp))

                com.collabsphere.app.view.components.SkeuoTextField(
                    value = description,
                    onValueChange = { viewModel.onNotesDescChange(it) },
                    label = "Content",
                    placeholder = "Write your note here (Markdown works)",
                    imeAction = ImeAction.Default,
                    singleLine = false,
                    minLines = 5,
                    maxLines = 12
                )
            }

            Spacer(Modifier.height(4.dp))

            val canSubmit = name.trim().isNotEmpty()
            com.collabsphere.app.view.components.WorkspaceDialogButton(
                text = if (isEditMode) "Save note" else "Create note",
                colors = listOf(PrintInk),
                enabled = canSubmit,
                onClick = {
                    if (canSubmit) {
                        if (isEditMode) onConfirmUpdate() else viewModel.createNote()
                        onDismiss()
                    }
                }
            )
            PrintButton(
                text = "Cancel",
                onClick = {
                    onDismiss()
                    viewModel.clearInputs()
                },
                primary = false,
                modifier = Modifier.fillMaxWidth()
            )
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
    com.collabsphere.app.view.components.PrintConfirmDialog(
        title = "Delete “$noteName”?",
        message = "This note will be removed for everyone in the workspace.",
        confirmLabel = "Delete note",
        icon = Icons.Default.DeleteOutline,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
