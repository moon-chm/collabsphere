package com.example.rohit_project_challlange.model.notes

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.rohit_project_challlange.dto.notes.NotesRequest
import com.example.rohit_project_challlange.remote.note.NoteApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class NotesSyncWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val apiService: NoteApiService,
    private val notesDao: NotesDao,
    private val dataStore: DataStore<Preferences>
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val actionType = inputData.getString("ACTION_TYPE") ?: "CREATE"
        val userId = inputData.getInt("USER_ID", -1)
        val workspaceIdParam = inputData.getInt("WORKSPACE_ID", -1)
        val noteName = inputData.getString("NOTE_NAME") ?: ""
        val noteId = inputData.getInt("NOTE_ID", -1)

        if (userId == -1 || workspaceIdParam == -1) return@withContext Result.failure()

        val workspaceId = if (workspaceIdParam < 0) {
            val mappingKey = intPreferencesKey("temp_ws_$workspaceIdParam")
            dataStore.data.map { it[mappingKey] ?: workspaceIdParam }.first()
        } else {
            workspaceIdParam
        }

        try {
            if (actionType == "DELETE") {
                if (noteId == -1) return@withContext Result.failure()
                apiService.deleteNote(noteId)
                return@withContext Result.success()
            }

            val description = inputData.getString("DESCRIPTION") ?: ""
            val request = NotesRequest(
                userId = userId,
                workspaceId = workspaceId,
                notesName = noteName,
                description = description
            )

            if (actionType == "UPDATE") {
                if (noteId == -1) return@withContext Result.failure()
                apiService.updateNote(noteId, request)
            } else {
                val remoteResponse = apiService.createNotes(request)
                val tempNoteId = inputData.getInt("TEMPORARY_NOTE_ID", -1)
                if (tempNoteId != -1 && tempNoteId != remoteResponse.id) {
                    notesDao.updateNotesId(tempNoteId, remoteResponse.id)
                }
            }
            return@withContext Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.retry()
        }
    }
}