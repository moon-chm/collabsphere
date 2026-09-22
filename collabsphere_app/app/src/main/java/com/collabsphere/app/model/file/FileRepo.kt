package com.collabsphere.app.model.file
import android.util.Log

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.work.*
import com.collabsphere.app.model.TempId
import com.collabsphere.app.remote.file.FileApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class FileRepo(
    private val fileDoa: FileDao,
    private val fileApiService: FileApiService,
    private val workManager: WorkManager,
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        private const val LAST_FILE_SYNC_KEY_PREFIX = "files_last_sync_time_"
    }

    private fun getSyncKey(workspaceId: Int) =
        longPreferencesKey("${LAST_FILE_SYNC_KEY_PREFIX}$workspaceId")

    // FileRepo is a Koin singleton shared by every FileViewModel instance — without this guard,
    // navigating to the same workspace's files screen more than once (without popping the earlier
    // backstack entry) starts a second independent 3s poller against the same endpoint.
    private val activeSyncLoops = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    suspend fun uploadfilestoscreen(local_files: FileEntity): Long = withContext(Dispatchers.IO) {
        val path = local_files.localpath
        if (path.isNullOrEmpty()) {
            System.err.println("Upload Failed: Absolute local path is empty or invalid.")
            return@withContext -1L
        }

        val physicalFile = File(path)
        if (!physicalFile.exists()) {
            System.err.println("Upload Failed: File does not exist at path: $path")
            return@withContext -1L
        }

        return@withContext try {
            val serverResponse = fileApiService.uploadFile(
                userId = local_files.userId,
                workspaceId = local_files.workspaceId,
                userName = local_files.userName,
                localPath = path,
                fileToUpload = physicalFile
            )

            val updatedEntity = local_files.copy(
                id = serverResponse.id,
                url = serverResponse.url,
                fileLocation = serverResponse.fileLocation
            )

            fileDoa.insertFile(updatedEntity)
            serverResponse.id
        } catch (e: Exception) {
            System.err.println("Network Upload Pipeline Mismatch Failure Exception:")
            Log.e("FileRepo", "Operation failed", e)

            // Draw the placeholder id from the negative range — Room autoGenerate only kicks in for
            // id == 0, so a positive fallback here could collide with a real id synced down later.
            val allocatedLocalId = fileDoa.insertFile(local_files.copy(id = TempId.nextLong()))

            val syncData = workDataOf(
                "ACTION_TYPE" to "UPLOAD",
                "FILE_ID" to allocatedLocalId,
                "USER_ID" to local_files.userId,
                "WORKSPACE_ID" to local_files.workspaceId,
                "USER_NAME" to local_files.userName,
                "LOCAL_PATH" to path,
                "FILE_NAME" to local_files.fileName,
                "MIME_TYPE" to local_files.mimeType,
                "SIZE_BYTES" to local_files.sizebytes
            )
            enqueueSync(syncData)
            allocatedLocalId
        }
    }

    fun getfiles(workspaceId: Int): Flow<List<FileEntity>> {
        return fileDoa.getallfiles(workspaceId)
            .onStart {
                try {
                    val remoteFiles = fileApiService.getFilesByWorkspace(workspaceId)
                    val entities = remoteFiles.map { remote ->
                        FileEntity(
                            id = remote.id,
                            userId = remote.userId,
                            workspaceId = remote.workspaceId,
                            userName = remote.userName,
                            url = remote.url,
                            mimeType = remote.mimeType,
                            localpath = remote.localpath,
                            fileName = remote.fileName,
                            sizebytes = remote.sizebytes,
                            fileLocation = remote.fileLocation
                        )
                    }
                    entities.forEach { fileDoa.insertFile(it) }
                } catch (e: Exception) {
                    System.err.println("Error fetching network files on loop initialization:")
                    Log.e("FileRepo", "Operation failed", e)
                }
            }
            .flowOn(Dispatchers.IO)
    }

    suspend fun downloadFileFromServer(url: String, destination: File) {
        fileApiService.downloadFile(url, destination)
    }

    suspend fun deletefiles(fileId: Long) = withContext(Dispatchers.IO) {
        try {
            val isNetworkDeleted = fileApiService.deleteFile(fileId)
            if (!isNetworkDeleted) {
                // Server explicitly rejected the delete (not a network failure) — retry in the background
                // instead of letting the local cache silently drift from what the server still has.
                System.err.println("Server rejected delete for file $fileId, deferring to background sync.")
                enqueueSync(workDataOf("ACTION_TYPE" to "DELETE", "FILE_ID" to fileId))
            }
            fileDoa.deleteFileById(fileId)
        } catch (e: Exception) {
            System.err.println("Network Delete Exception encountered, deferring execution to background sync:")
            Log.e("FileRepo", "Operation failed", e)

            val syncData = workDataOf(
                "ACTION_TYPE" to "DELETE",
                "FILE_ID" to fileId
            )
            enqueueSync(syncData)

            fileDoa.deleteFileById(fileId)
        }
    }

    suspend fun startDeltaSyncLoop(workspaceId: Int) = withContext(Dispatchers.IO) {
        if (!activeSyncLoops.add(workspaceId)) return@withContext
        try {
        while (isActive) {
            com.collabsphere.app.MyApplication.isAppForegroundFlow.first { it }
            try {
                val syncKey = getSyncKey(workspaceId)
                val lastSyncTime = dataStore.data.map { it[syncKey] ?: 0L }.first()

                val updates = fileApiService.getFileUpdates(workspaceId, lastSyncTime)

                if (updates.isNotEmpty()) {
                    updates.forEach { remote ->
                        if (remote.isDeleted) {
                            fileDoa.deleteFileById(remote.id)
                        } else {
                            val entity = FileEntity(
                                id = remote.id,
                                userId = remote.userId,
                                workspaceId = remote.workspaceId,
                                userName = remote.userName,
                                url = remote.url,
                                mimeType = remote.mimeType,
                                localpath = remote.localpath,
                                fileName = remote.fileName,
                                sizebytes = remote.sizebytes,
                                fileLocation = remote.fileLocation
                            )
                            fileDoa.insertFile(entity)
                        }
                    }

                    val newestTimestamp = updates.maxOf { it.updatedAt }
                    dataStore.edit { preferences ->
                        preferences[syncKey] = newestTimestamp
                    }
                }
            } catch (e: Exception) {
                System.err.println("Exception encountered during workspace file polling updates loop:")
                Log.e("FileRepo", "Operation failed", e)
            }
            delay(1000)
        }
        } finally {
            activeSyncLoops.remove(workspaceId)
        }
    }

    private fun enqueueSync(data: Data) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<FileSyncWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        // Unique per file, not per entity TYPE — see ChannelRepo.enqueueSync for why a shared name
        // across every file would let one permanently-failed sync cancel every other file's queue.
        val fileId = data.getLong("FILE_ID", 0L)
        workManager.enqueueUniqueWork(
            "FILE_SYNC_$fileId",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            syncRequest
        )
    }
}