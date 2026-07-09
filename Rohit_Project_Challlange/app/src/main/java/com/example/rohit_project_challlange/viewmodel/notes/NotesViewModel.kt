package com.example.rohit_project_challlange.viewmodel.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rohit_project_challlange.model.notes.NotesEntity
import com.example.rohit_project_challlange.model.notes.NotesRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(
    private val repo: NotesRepo,
    private val loggedUserId: Int,
    private val loggedWorkspaceId: Int
) : ViewModel() {

    val allNotes: StateFlow<List<NotesEntity>> = repo
        .getallnotestoscreen(loggedWorkspaceId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    private val _notesName = MutableStateFlow("")
    val notesName = _notesName.asStateFlow()

    private val _description = MutableStateFlow("")
    val description = _description.asStateFlow()

    private val _notesStatus = MutableStateFlow<String?>(null)
    val notesStatus = _notesStatus.asStateFlow()

    init {
        viewModelScope.launch {
            repo.startDeltaSyncLoop(loggedWorkspaceId)
        }
    }

    fun onNotesNameChange(name: String) {
        _notesName.value = name
    }

    fun onNotesDescChange(desc: String) {
        _description.value = desc
    }

    fun clearStatus() {
        _notesStatus.value = null
    }

    fun clearInputs() {
        _notesName.value = ""
        _description.value = ""
    }

    fun createNote() {
        val name = _notesName.value.trim()
        val description = _description.value.trim()

        if (name.isEmpty()) {
            _notesStatus.value = "Notes name cannot be empty"
            return
        }

        viewModelScope.launch {
            val newNote = NotesEntity(
                id = 0,
                userId = loggedUserId,
                workspaceId = loggedWorkspaceId,
                notesName = name,
                description = description
            )

            val result = repo.addnotestoscreen(newNote)

            result.onSuccess { localId ->
                if (localId > 0) {
                    _notesStatus.value = "Notes $name created successfully"
                    clearInputs()
                } else {
                    _notesStatus.value = "Failed to save notes locally"
                }
            }
                .onFailure { exception ->
                    _notesStatus.value = "Failed to create notes: ${exception.localizedMessage}"
                }
        }
    }

    fun updateNote(oldNote: NotesEntity) {
        val updatedName = _notesName.value.trim()
        val updatedDescription = _description.value.trim()

        if (updatedName.isEmpty()) {
            _notesStatus.value = "Notes name cannot be empty"
            return
        }

        viewModelScope.launch {
            val updatedNote = oldNote.copy(
                notesName = updatedName,
                description = updatedDescription
            )
            repo.updatetheNote(updatedNote)
            _notesStatus.value = "Notes updated successfully"
            clearInputs()
        }
    }

    fun deleteNote(noteId: Int, name: String) {
        viewModelScope.launch {
            repo.deletenotestoscreen(noteId, name, loggedUserId, loggedWorkspaceId)
            _notesStatus.value = "Notes $name deleted"
        }
    }
}