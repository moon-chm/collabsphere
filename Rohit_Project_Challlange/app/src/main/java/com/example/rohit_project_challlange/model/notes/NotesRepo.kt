package com.example.rohit_project_challlange.model.notes

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.work.*
import com.example.rohit_project_challlange.dto.notes.NotesRequest
import com.example.rohit_project_challlange.model.TempId
import com.example.rohit_project_challlange.remote.note.NoteApiService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit

class NotesRepo(
    private val notesDao: NotesDao,
    private val apiService: NoteApiService,
    private val workManager: WorkManager,
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        private const val LAST_SYNC_KEY_PREFIX = "notes_last_sync_time_"
    }

    private fun getSyncKey(workspaceId: Int) =
        longPreferencesKey("${LAST_SYNC_KEY_PREFIX}$workspaceId")

    fun getallnotestoscreen(workspaceId: Int): Flow<List<NotesEntity>> {
        return flow {
            emitAll(notesDao.getallnotedbyuser(workspaceId))
        }.onStart {
            withContext(Dispatchers.IO) {
                try {
                    val remoteNotes = apiService.getNotes(workspaceId)
                    if (!remoteNotes.isNullOrEmpty()) {
                        val notesEntities = remoteNotes.map { remote ->
                            NotesEntity(
                                id = remote.id,
                                userId = remote.userId,
                                workspaceId = remote.workspaceId,
                                notesName = remote.notesName,
                                description = remote.description
                            )
                        }
                        notesDao.insertAllNotes(notesEntities)
                    }
                } catch (e: Exception) {
                    Log.e("NotesRepo", "Initial notes fetch failed", e)
                }
            }
        }.flowOn(Dispatchers.IO)
    }


    // Runs directly in the caller's coroutine (matching every other repo's sync loop) instead of an
    // app-lifetime singleton scope, so it's actually cancelled when the caller's scope is — e.g. when
    // the owning ViewModel clears — instead of running for the rest of the process's life.
    suspend fun startDeltaSyncLoop(workspaceId: Int) = withContext(Dispatchers.IO) {
        while (isActive) {
            try {
                val syncKey = getSyncKey(workspaceId)
                val lastSyncTime = dataStore.data.map { it[syncKey] ?: 0L }.first()
                val updates = apiService.getNoteUpdates(workspaceId, lastSyncTime)

                if (updates.isNotEmpty()) {
                    updates.forEach { remote ->
                        if (remote.isDeleted) {
                            notesDao.deleteNoteById(remote.id)
                        } else {
                            val entity = NotesEntity(
                                id = remote.id,
                                userId = remote.userId,
                                workspaceId = remote.workspaceId,
                                notesName = remote.notesName,
                                description = remote.description
                            )
                            notesDao.createNotes(entity)
                        }
                    }

                    val newestTimestamp = updates.maxOf { it.updatedAt }
                    dataStore.edit { preferences ->
                        preferences[syncKey] = newestTimestamp
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("NotesRepo", "Delta sync iteration error", e)
            }
            delay(3000)
        }
    }

    suspend fun addnotestoscreen(notes: NotesEntity): Result<Long> = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = NotesRequest(
                userId = notes.userId,
                notesName = notes.notesName,
                description = notes.description,
                workspaceId = notes.workspaceId
            )

            val remoteNote = apiService.createNotes(request)
            val localId = notesDao.createNotes(notes.copy(id = remoteNote.id))
            Result.success(localId)
        } catch (e: Exception) {
            val tempId = TempId.next()
            val fallbackId = notesDao.createNotes(notes.copy(id = tempId))

            val syncData = workDataOf(
                "ACTION_TYPE" to "CREATE",
                "TEMPORARY_NOTE_ID" to tempId,
                "USER_ID" to notes.userId,
                "WORKSPACE_ID" to notes.workspaceId,
                "NOTE_NAME" to notes.notesName,
                "DESCRIPTION" to notes.description
            )

            enqueueSync(syncData)
            Result.success(fallbackId)
        }
    }

    suspend fun deletenotestoscreen(
        noteId: Int,
        noteName: String,
        userId: Int,
        workspaceId: Int
    ) = withContext(Dispatchers.IO) {
        try {
            apiService.deleteNote(noteId)
            notesDao.deleteNoteById(noteId)
        } catch (e: Exception) {
            val syncData = workDataOf(
                "ACTION_TYPE" to "DELETE",
                "NOTE_ID" to noteId,
                "USER_ID" to userId,
                "WORKSPACE_ID" to workspaceId,
                "NOTE_NAME" to noteName
            )
            enqueueSync(syncData)
        }
    }

    suspend fun updatetheNote(notes: NotesEntity) = withContext(Dispatchers.IO) {
        try {
            notesDao.updatenotes(notes)
            apiService.updateNote(
                noteId = notes.id,
                request = NotesRequest(
                    userId = notes.userId,
                    workspaceId = notes.workspaceId,
                    notesName = notes.notesName,
                    description = notes.description
                )
            )
        } catch (e: Exception) {
            val syncData = workDataOf(
                "ACTION_TYPE" to "UPDATE",
                "NOTE_ID" to notes.id,
                "USER_ID" to notes.userId,
                "WORKSPACE_ID" to notes.workspaceId,
                "NOTE_NAME" to notes.notesName,
                "DESCRIPTION" to notes.description
            )
            enqueueSync(syncData)
        }
    }

    private fun enqueueSync(data: Data) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<NotesSyncWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        // Unique per note, not per entity TYPE — see ChannelRepo.enqueueSync for why a shared name
        // across every note would let one permanently-failed sync cancel every other note's queue.
        val noteId = data.getInt("NOTE_ID", data.getInt("TEMPORARY_NOTE_ID", 0))
        workManager.enqueueUniqueWork(
            "NOTES_SYNC_$noteId",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }
}