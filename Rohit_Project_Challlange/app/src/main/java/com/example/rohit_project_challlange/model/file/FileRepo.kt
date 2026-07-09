package com.example.rohit_project_challlange.model.file

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.work.*
import com.example.rohit_project_challlange.remote.file.FileApiService
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
        private val LAST_FILE_SYNC_KEY = longPreferencesKey("files_last_sync_time")
    }

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
            e.printStackTrace()

            val allocatedLocalId = fileDoa.insertFile(local_files)

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
                    e.printStackTrace()
                }
            }
            .flowOn(Dispatchers.IO)
    }

    suspend fun downloadFileFromServer(url: String): ByteArray {
        return fileApiService.downloadFile(url)
    }

    suspend fun deletefiles(fileId: Long) = withContext(Dispatchers.IO) {
        try {
            val isNetworkDeleted = fileApiService.deleteFile(fileId)
            fileDoa.deleteFileById(fileId)
        } catch (e: Exception) {
            System.err.println("Network Delete Exception encountered, deferring execution to background sync:")
            e.printStackTrace()

            val syncData = workDataOf(
                "ACTION_TYPE" to "DELETE",
                "FILE_ID" to fileId
            )
            enqueueSync(syncData)

            fileDoa.deleteFileById(fileId)
        }
    }

    suspend fun startDeltaSyncLoop(workspaceId: Int) = withContext(Dispatchers.IO) {
        while (isActive) {
            try {
                val lastSyncTime = dataStore.data.map { it[LAST_FILE_SYNC_KEY] ?: 0L }.first()

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
                        preferences[LAST_FILE_SYNC_KEY] = newestTimestamp
                    }
                }
            } catch (e: Exception) {
                System.err.println("Exception encountered during workspace file polling updates loop:")
                e.printStackTrace()
            }
            delay(3000)
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

        workManager.enqueueUniqueWork(
            "FILE_SYNC_QUEUE",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            syncRequest
        )
    }
}