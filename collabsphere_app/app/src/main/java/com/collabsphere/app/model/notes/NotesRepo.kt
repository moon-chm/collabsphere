package com.collabsphere.app.model.notes

import com.collabsphere.app.remote.ApiStatusException
import com.collabsphere.app.model.SyncDecision
import com.collabsphere.app.model.SyncPolicy
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.work.*
import com.collabsphere.app.dto.notes.NotesRequest
import com.collabsphere.app.model.TempId
import com.collabsphere.app.remote.note.NoteApiService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit
import com.collabsphere.app.model.readSyncPosition
import com.collabsphere.app.model.commitSyncPosition

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

    // NotesRepo is a Koin singleton shared by every NotesViewModel instance — without this guard,
    // navigating to the same workspace's notes screen more than once (without popping the earlier
    // backstack entry) starts a second independent 3s poller against the same endpoint.
    private val activeSyncLoops = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    // Offline-first: emit cached Room data immediately, delta loop keeps it fresh in background.
    fun getallnotestoscreen(workspaceId: Int): Flow<List<NotesEntity>> =
        notesDao.getallnotedbyuser(workspaceId)


    // Runs directly in the caller's coroutine (matching every other repo's sync loop) instead of an
    // app-lifetime singleton scope, so it's actually cancelled when the caller's scope is — e.g. when
    // the owning ViewModel clears — instead of running for the rest of the process's life.
    suspend fun startDeltaSyncLoop(workspaceId: Int) = withContext(Dispatchers.IO) {
        if (!activeSyncLoops.add(workspaceId)) return@withContext
        try {
        val pollingBackoff = com.collabsphere.app.model.SyncPollingBackoff(5_000)
        while (isActive) {
            com.collabsphere.app.MyApplication.isAppForegroundFlow.first { it }
            var syncSucceeded = false
            try {
                val syncKey = getSyncKey(workspaceId)
                val position = dataStore.readSyncPosition(syncKey)
                var newestUpdatedAt: Long? = null
                val page = com.collabsphere.app.remote.applySyncPages(
                    fetch = { token -> apiService.getNoteUpdates(workspaceId, position.since, position.cursor, token) },
                    apply = { updates ->
                        newestUpdatedAt = listOfNotNull(newestUpdatedAt, updates.maxOfOrNull { it.updatedAt }).maxOrNull()
                        if (updates.isNotEmpty()) {
                    val upserts = updates.filter { !it.isDeleted }.map { remote ->
                        NotesEntity(
                            id = remote.id,
                            userId = remote.userId,
                            workspaceId = remote.workspaceId,
                            notesName = remote.notesName,
                            description = remote.description,
                            isPinned = remote.isPinned
                        )
                    }
                    val deletes = updates.filter { it.isDeleted }.map { it.id }
                    notesDao.applyDelta(upserts, deletes)
                }
                    }
                )
                // After the rows are stored: committing first and dying in between would skip them.
                dataStore.commitSyncPosition(syncKey, position, page, newestUpdatedAt)
                syncSucceeded = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("NotesRepo", "Delta sync iteration error", e)
            }
            delay(pollingBackoff.delayAfter(syncSucceeded))
        }
        } finally {
            activeSyncLoops.remove(workspaceId)
        }
    }

    suspend fun addnotestoscreen(notes: NotesEntity): Result<Long> = withContext(Dispatchers.IO) {
        // Generated once and reused on every retry of this same create (frozen into workDataOf
        // below) so a WorkManager retry after a successful-but-lost response is recognized
        // server-side as the same request instead of inserting a duplicate note.
        val idempotencyKey = java.util.UUID.randomUUID().toString()
        return@withContext try {
            val request = NotesRequest(
                userId = notes.userId,
                notesName = notes.notesName,
                description = notes.description,
                workspaceId = notes.workspaceId,
                idempotencyKey = idempotencyKey
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
                "DESCRIPTION" to notes.description,
                "IDEMPOTENCY_KEY" to idempotencyKey
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
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val status = apiService.deleteNote(noteId)
            when (SyncPolicy.forStatus(status.value, isDelete = true)) {
                SyncDecision.DONE -> notesDao.deleteNoteById(noteId)
                SyncDecision.RETRY -> throw ApiStatusException(status.value)
                // Refused: keep the note rather than hiding one that still exists on the server.
                SyncDecision.DROP -> return@withContext false
            }
            true
        } catch (e: Exception) {
            val syncData = workDataOf(
                "ACTION_TYPE" to "DELETE",
                "NOTE_ID" to noteId,
                "USER_ID" to userId,
                "WORKSPACE_ID" to workspaceId,
                "NOTE_NAME" to noteName
            )
            notesDao.deleteNoteById(noteId)
            enqueueSync(syncData)
            true
        }
    }

    /** False when the server refused the edit; true when it was saved or queued for background sync. */
    suspend fun updatetheNote(notes: NotesEntity): Boolean = withContext(Dispatchers.IO) {
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
            true
        } catch (e: Exception) {
            if (SyncPolicy.forFailure(e, isDelete = false) == SyncDecision.DROP) return@withContext false
            val syncData = workDataOf(
                "ACTION_TYPE" to "UPDATE",
                "NOTE_ID" to notes.id,
                "USER_ID" to notes.userId,
                "WORKSPACE_ID" to notes.workspaceId,
                "NOTE_NAME" to notes.notesName,
                "DESCRIPTION" to notes.description
            )
            enqueueSync(syncData)
            true
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

    suspend fun setPinned(noteId: Int, pinned: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.setPinned(noteId, pinned)
            notesDao.updatePinned(noteId, pinned)
        }
    }
}
