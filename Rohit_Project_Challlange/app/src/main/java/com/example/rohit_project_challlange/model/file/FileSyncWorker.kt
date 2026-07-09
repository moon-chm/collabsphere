package com.example.rohit_project_challlange.model.file

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.rohit_project_challlange.remote.file.FileApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class FileSyncWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val fileApiService: FileApiService,
    private val fileDao: FileDao,
    private val dataStore: DataStore<Preferences>
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val actionType = inputData.getString("ACTION_TYPE") ?: "UPLOAD"
        val fileId = inputData.getLong("FILE_ID", -1L)

        if (fileId == -1L) {
            return@withContext Result.failure()
        }

        try {
            if (actionType == "DELETE") {
                val resolvedFileId = dataStore.data.map {
                    it[longPreferencesKey("temp_file_$fileId")] ?: fileId
                }.first()

                val successful = fileApiService.deleteFile(resolvedFileId)
                return@withContext if (successful) Result.success() else Result.retry()
            }

            val localPath = inputData.getString("LOCAL_PATH") ?: ""
            val physicalFile = File(localPath)

            if (localPath.isEmpty() || !physicalFile.exists()) {
                return@withContext Result.failure()
            }

            val userId = inputData.getInt("USER_ID", -1)
            val workspaceIdParam = inputData.getInt("WORKSPACE_ID", -1)
            val userName = inputData.getString("USER_NAME") ?: ""
            val fileName = inputData.getString("FILE_NAME") ?: physicalFile.name
            val mimeType = inputData.getString("MIME_TYPE") ?: "application/octet-stream"
            val sizeBytes = inputData.getLong("SIZE_BYTES", physicalFile.length())

            if (workspaceIdParam == -1) return@withContext Result.failure()

            val workspaceId = if (workspaceIdParam < 0) {
                val mappingKey = intPreferencesKey("temp_ws_$workspaceIdParam")
                dataStore.data.map { it[mappingKey] ?: workspaceIdParam }.first()
            } else {
                workspaceIdParam
            }

            val serverResponse = fileApiService.uploadFile(
                userId = userId,
                workspaceId = workspaceId,
                userName = userName,
                localPath = localPath,
                fileToUpload = physicalFile
            )

            fileDao.deleteFileById(fileId)

            val finalizedSyncedEntity = FileEntity(
                id = serverResponse.id,
                userId = userId,
                workspaceId = workspaceId,
                userName = userName,
                url = serverResponse.url,
                mimeType = mimeType,
                localpath = localPath,
                fileName = fileName,
                sizebytes = sizeBytes,
                fileLocation = serverResponse.fileLocation
            )
            fileDao.insertFile(finalizedSyncedEntity)

            dataStore.edit { preferences ->
                preferences[longPreferencesKey("temp_file_$fileId")] = serverResponse.id
            }

            return@withContext Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.retry()
        }
    }
}