package com.example.rohit_project_challlange.view.NotesUI

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rohit_project_challlange.model.notes.NotesEntity
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
                    text = "Notes",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No Notes found.\nTap 'Add Notes' to start.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NotesItem(
                            notes = note,
                            deleteNote = { noteToDelete = note },
                            updateNote = { noteToEdit = note }
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                viewModel.clearInputs()
                showCreateDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Add Notes")
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
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
fun CreateNotesDialog(
    viewModel: NotesViewModel,
    isEditMode: Boolean = false,
    onDismiss: () -> Unit,
    onConfirmUpdate: () -> Unit = {}
) {
    val name by viewModel.notesName.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditMode) "Edit Note" else "Create New Notes",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { viewModel.onNotesNameChange(it) },
                    label = { Text("Notes Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { viewModel.onNotesDescChange(it) },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isNotEmpty()) {
                        if (isEditMode) {
                            onConfirmUpdate()
                        } else {
                            viewModel.createNote()
                        }
                        onDismiss()
                    }
                }
            ) {
                Text(if (isEditMode) "Save" else "Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    viewModel.clearInputs()
                }
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun NotesItem(
    notes: NotesEntity,
    deleteNote: () -> Unit,
    updateNote: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = notes.notesName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.inverseSurface
                )

                if (notes.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = notes.description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = deleteNote) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Note",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                IconButton(onClick = updateNote) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Note",
                        tint = MaterialTheme.colorScheme.primary,
                    )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Delete Note", fontWeight = FontWeight.Bold) },
        text = { Text("Are you sure you want to delete '$noteName'? This action cannot be undone.") },
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