package com.example.rohit_project_challlange.remote.file

import com.example.rohit_project_challlange.dto.file.FileResponse
import com.example.rohit_project_challlange.dto.file.FileSyncDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.*
import io.ktor.client.request.*
import io.ktor.http.*
import java.io.File

class FileApiService(private val client: HttpClient) {

    private val baseUrl = "http://10.238.3.93:8080/api/file"

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
                append("file", fileToUpload.readBytes(), Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                    append(HttpHeaders.ContentDisposition, "filename=\"${fileToUpload.name}\"")
                })
            }
        ) {
            timeout {
                requestTimeoutMillis = 60000
                connectTimeoutMillis = 15000
            }
        }.body()
    }

    suspend fun getFilesByWorkspace(workspaceId: Int): List<FileResponse> {
        return client.get("$baseUrl/workspace/$workspaceId") {
            timeout {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
            }
        }.body()
    }

    suspend fun downloadFile(url: String): ByteArray {
        return client.get(url) {
            timeout {
                requestTimeoutMillis = 30000
                connectTimeoutMillis = 15000
            }
        }.body()
    }

    suspend fun deleteFile(fileId: Long): Boolean {
        val response = client.delete("$baseUrl/$fileId") {
            timeout {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
            }
        }
        return response.status.isSuccess()
    }


    suspend fun getFileUpdates(workspaceId: Int, lastSyncTime: Long): List<FileSyncDto> {
        return client.get("$baseUrl/updates") {
            parameter("workspaceId", workspaceId)
            parameter("lastSyncTime", lastSyncTime)
            timeout {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
            }
        }.body()
    }
}