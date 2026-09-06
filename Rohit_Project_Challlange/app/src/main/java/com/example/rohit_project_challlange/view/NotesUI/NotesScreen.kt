package com.example.rohit_project_challlange.view.NotesUI

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.StickyNote2
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rohit_project_challlange.model.notes.NotesEntity
import com.example.rohit_project_challlange.ui.theme.*
import com.example.rohit_project_challlange.viewmodel.notes.NotesViewModel

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
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Notes count pill
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
                        text = "${notes.size} notes",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Muted
                    )
                }
            }

            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Tactile Empty State Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                drawRoundRect(
                                    color = ShadowDark.copy(alpha = 0.20f),
                                    topLeft = Offset(4.dp.toPx(), 6.dp.toPx()),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(24.dp.toPx())
                                )
                                drawRoundRect(
                                    color = ShadowLight.copy(alpha = 0.85f),
                                    topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(24.dp.toPx())
                                )
                                drawRoundRect(
                                    color = SurfaceRaised,
                                    cornerRadius = CornerRadius(24.dp.toPx())
                                )
                            }
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = Amber,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Text(
                                text = "No notes found",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Ink
                            )

                            Text(
                                text = "Tap 'Add note' below to jot down quick thoughts, project summaries, and shared documentation.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Muted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 18.dp,
                        end = 18.dp,
                        top = 6.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        SkeuoNotesItem(
                            notes = note,
                            deleteNote = { noteToDelete = note },
                            updateNote = { noteToEdit = note },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }

        // Tactile Skeuomorphic FAB
        val fabInteractionSource = remember { MutableInteractionSource() }
        val isFabPressed by fabInteractionSource.collectIsPressedAsState()
        val fabScale by animateFloatAsState(
            targetValue = if (isFabPressed) 0.92f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "noteFabScale"
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
                    viewModel.clearInputs()
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
                    text = "Add note",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = Color.White
                )
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
                        Text(
                            text = notes.description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.5.sp,
                                lineHeight = 18.sp
                            ),
                            color = Color(0xFF6E635C),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = updateNote,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit note",
                        tint = Color(0xFF8C7E75),
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = deleteNote,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete note",
                        tint = Destructive.copy(alpha = 0.6f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CreateNotesDialog(
    viewModel: NotesViewModel,
    isEditMode: Boolean = false,
    onDismiss: () -> Unit,
    onConfirmUpdate: () -> Unit = {}
) {
    val name by viewModel.notesName.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()

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
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = Amber,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Text(
                    text = if (isEditMode) "Edit note" else "Create new note",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Ink
                )

                // Debossed Note Name
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
                        onValueChange = { viewModel.onNotesNameChange(it) },
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
                                        text = "Note title",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Muted.copy(alpha = 0.6f)
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }

                // Debossed Note Description
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
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
                    contentAlignment = Alignment.TopStart
                ) {
                    BasicTextField(
                        value = description,
                        onValueChange = { viewModel.onNotesDescChange(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        singleLine = false,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.TopStart) {
                                if (description.isEmpty()) {
                                    Text(
                                        text = "Description / Content...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Muted.copy(alpha = 0.6f)
                                    )
                                }
                                inner()
                            }
                        }
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
                            .clickable {
                                onDismiss()
                                viewModel.clearInputs()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Cancel", style = MaterialTheme.typography.labelLarge, color = Muted)
                    }

                    val canSubmit = name.trim().isNotEmpty()
                    val btnColor = if (canSubmit) CoralStart else Muted.copy(alpha = 0.45f)
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
                                        colors = listOf(btnColor, if (canSubmit) CoralEnd else btnColor),
                                        start = Offset(0f, 0f),
                                        end = Offset(size.width, size.height)
                                    ),
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = canSubmit) {
                                if (name.trim().isNotEmpty()) {
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
                            text = if (isEditMode) "Save" else "Create",
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
fun DeleteNoteConfirmationDialog(
    noteName: String,
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
                    text = "Delete note",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
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