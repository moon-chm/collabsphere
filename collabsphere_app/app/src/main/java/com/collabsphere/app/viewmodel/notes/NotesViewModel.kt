package com.collabsphere.app.viewmodel.notes

import com.collabsphere.app.model.SyncPolicy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabsphere.app.model.notes.NotesEntity
import com.collabsphere.app.model.notes.NotesRepo
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

    val allNotes: StateFlow<List<NotesEntity>?> = repo
        .getallnotestoscreen(loggedWorkspaceId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _notesName = MutableStateFlow("")
    val notesName = _notesName.asStateFlow()

    private val _description = MutableStateFlow("")
    val description = _description.asStateFlow()

    private val _notesStatus = MutableStateFlow<String?>(null)
    val notesStatus = _notesStatus.asStateFlow()

    private val _isCreatingNote = MutableStateFlow(false)
    val isCreatingNote = _isCreatingNote.asStateFlow()
    private val _pendingNoteActionIds = MutableStateFlow<Set<Int>>(emptySet())
    val pendingNoteActionIds = _pendingNoteActionIds.asStateFlow()

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
        if (_isCreatingNote.value) return
        val name = _notesName.value.trim()
        val description = _description.value.trim()

        if (name.isEmpty()) {
            _notesStatus.value = "Notes name cannot be empty"
            return
        }

        _isCreatingNote.value = true
        viewModelScope.launch {
            try {
                val newNote = NotesEntity(
                    id = 0,
                    userId = loggedUserId,
                    workspaceId = loggedWorkspaceId,
                    notesName = name,
                    description = description
                )

                val result = repo.addnotestoscreen(newNote)

                result.onSuccess { localId ->
                    if (localId != 0L) {
                        _notesStatus.value = "Notes $name created successfully"
                        clearInputs()
                    } else {
                        _notesStatus.value = "Failed to save notes locally"
                    }
                }.onFailure { exception ->
                    _notesStatus.value = "Failed to create notes: ${exception.localizedMessage}"
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _notesStatus.value = "Failed to create notes: ${e.localizedMessage}"
            } finally {
                _isCreatingNote.value = false
            }
        }
    }

    fun updateNote(oldNote: NotesEntity) {
        if (!beginNoteAction(oldNote.id)) return
        val updatedName = _notesName.value.trim()
        val updatedDescription = _description.value.trim()

        if (updatedName.isEmpty()) {
            finishNoteAction(oldNote.id)
            _notesStatus.value = "Notes name cannot be empty"
            return
        }

        viewModelScope.launch {
            try {
                val updatedNote = oldNote.copy(
                    notesName = updatedName,
                    description = updatedDescription
                )
                if (repo.updatetheNote(updatedNote)) {
                    _notesStatus.value = "Notes updated successfully"
                    clearInputs()
                } else {
                    _notesStatus.value = SyncPolicy.REFUSED_MESSAGE
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _notesStatus.value = e.localizedMessage ?: SyncPolicy.REFUSED_MESSAGE
            } finally {
                finishNoteAction(oldNote.id)
            }
        }
    }

    fun togglePin(note: NotesEntity) {
        if (note.id <= 0) {
            _notesStatus.value = "This note is still syncing. Try pinning it again in a moment."
            return
        }
        viewModelScope.launch {
            repo.setPinned(note.id, !note.isPinned)
                .onSuccess { _notesStatus.value = if (note.isPinned) "Note unpinned" else "Note pinned" }
                .onFailure { _notesStatus.value = "Couldn't update the pin. Check your connection." }
        }
    }

    fun deleteNote(noteId: Int, name: String) {
        if (!beginNoteAction(noteId)) return
        viewModelScope.launch {
            try {
                _notesStatus.value = if (repo.deletenotestoscreen(noteId, name, loggedUserId, loggedWorkspaceId)) {
                    "Notes $name deleted"
                } else {
                    SyncPolicy.REFUSED_MESSAGE
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _notesStatus.value = e.localizedMessage ?: SyncPolicy.REFUSED_MESSAGE
            } finally {
                finishNoteAction(noteId)
            }
        }
    }

    private fun beginNoteAction(noteId: Int): Boolean {
        if (noteId in _pendingNoteActionIds.value) return false
        _pendingNoteActionIds.value = _pendingNoteActionIds.value + noteId
        return true
    }

    private fun finishNoteAction(noteId: Int) {
        _pendingNoteActionIds.value = _pendingNoteActionIds.value - noteId
    }
}
