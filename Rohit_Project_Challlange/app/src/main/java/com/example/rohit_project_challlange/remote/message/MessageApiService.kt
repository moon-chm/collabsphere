package com.example.rohit_project_challlange.remote.message

import com.example.rohit_project_challlange.dto.message.MessageRequest
import com.example.rohit_project_challlange.dto.message.MessageResponse
import com.example.rohit_project_challlange.dto.message.MessageSyncDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.http.*

class MessageApiService(private val client: HttpClient) {

    private val baseUrl = "http://10.238.3.93:8080/api/message"

    suspend fun createMessage(request: MessageRequest): MessageResponse {
        return client.post(baseUrl) {
            contentType(ContentType.Application.Json)
            setBody(request)
            timeout {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
            }
        }.body()
    }

    suspend fun updateMessage(messageId: Int, request: MessageRequest): MessageResponse {
        return client.put("$baseUrl/$messageId") {
            contentType(ContentType.Application.Json)
            setBody(request)
            timeout {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
            }
        }.body()
    }

    suspend fun deleteMessage(messageId: Int, userId: Int, workspaceId: Int, channelId: Int): Boolean {
        return try {
            val response = client.delete("$baseUrl/$messageId/$userId/$workspaceId/$channelId") {
                timeout {
                    requestTimeoutMillis = 15000
                    connectTimeoutMillis = 15000
                }
            }
            if (response.status.isSuccess()) {
                response.body<Boolean>()
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getMessageByuser(workspaceId: Int, channelId: Int): List<MessageResponse> {
        return client.get("$baseUrl/workspace/$workspaceId/channels/$channelId") {
            timeout {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
            }
        }.body()
    }

    suspend fun getMessageUpdates(workspaceId: Int, channelId: Int, since: Long): List<MessageSyncDto> {
        return client.get("$baseUrl/sync/$workspaceId/$channelId") {
            parameter("since", since)
            timeout {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
            }
        }.body()
    }
}