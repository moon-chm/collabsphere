package com.collabsphere.app.remote.file

import com.collabsphere.app.remote.requireSuccess
import com.collabsphere.app.AppConfig
import com.collabsphere.app.dto.file.FileResponse
import com.collabsphere.app.dto.file.FileSyncDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.forms.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.streams.asInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileApiService(private val client: HttpClient) {

    private val baseUrl: String get() = "${AppConfig.BASE_URL}/api/file"

    suspend fun uploadFile(
        userId: Int,
        workspaceId: Int,
        userName: String,
        localPath: String?,
        fileToUpload: File
    ): FileResponse {
        return client.submitFormWithBinaryData(
            url = baseUrl,
            formData = formData {
                append("userId", userId.toString())
                append("workspaceId", workspaceId.toString())
                append("userName", userName)
                if (localPath != null) {
                    append("localpath", localPath)
                }
                append(
                    "file",
                    InputProvider(fileToUpload.length()) { fileToUpload.inputStream().asInput() },
                    Headers.build {
                        append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                        append(HttpHeaders.ContentDisposition, "filename=\"${fileToUpload.name}\"")
                    }
                )
            }
        ).requireSuccess().body()
    }

    suspend fun getFilesByWorkspace(workspaceId: Int): List<FileResponse> {
        return client.get("$baseUrl/workspace/$workspaceId").body()
    }

    /** Streams the response body straight to [destination] instead of buffering the whole file in memory. */
    suspend fun downloadFile(url: String, destination: File) {
        // Without this check an error page ("File not found") would be saved as the downloaded file.
        val response = client.get(url).requireSuccess()
        withContext(Dispatchers.IO) {
            response.bodyAsChannel().toInputStream().use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    suspend fun deleteFile(fileId: Long): HttpStatusCode =
        client.delete("$baseUrl/$fileId").status

    suspend fun getFileUpdates(workspaceId: Int, lastSyncTime: Long): List<FileSyncDto> {
        return client.get("$baseUrl/updates") {
            parameter("workspaceId", workspaceId)
            parameter("lastSyncTime", lastSyncTime)
        }.body()
    }
}