package com.collabsphere.app.remote.message
import android.util.Log

import com.collabsphere.app.AppConfig
import com.collabsphere.app.dto.message.ChannelReactionRequest
import com.collabsphere.app.dto.message.ChannelReactionSummary
import com.collabsphere.app.dto.message.MessageRequest
import com.collabsphere.app.dto.message.MessageResponse
import com.collabsphere.app.dto.message.MessageSyncDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class MessageApiService(private val client: HttpClient) {

    private val baseUrl = "${AppConfig.BASE_URL}/api/message"

    suspend fun createMessage(request: MessageRequest): MessageResponse {
        return client.post(baseUrl) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun updateMessage(messageId: Int, request: MessageRequest): MessageResponse {
        return client.put("$baseUrl/$messageId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteMessage(messageId: Int, userId: Int, workspaceId: Int, channelId: Int): Boolean {
        return try {
            val response = client.delete("$baseUrl/$messageId/$userId/$workspaceId/$channelId")
            if (response.status.isSuccess()) {
                response.body<Boolean>()
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("MessageApiService", "Operation failed", e)
            false
        }
    }

    suspend fun getMessageByuser(workspaceId: Int, channelId: Int): List<MessageResponse> {
        return client.get("$baseUrl/workspace/$workspaceId/channels/$channelId").body()
    }

    suspend fun toggleReaction(messageId: Int, emoji: String, add: Boolean): ChannelReactionSummary {
        val response = client.post("$baseUrl/$messageId/reactions") {
            contentType(ContentType.Application.Json)
            setBody(ChannelReactionRequest(emoji, add))
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Reaction failed (${response.status.value})")
        }
        return response.body()
    }

    suspend fun getReactions(workspaceId: Int, channelId: Int, fromId: Int): List<ChannelReactionSummary> {
        return client.get("$baseUrl/reactions/$workspaceId/$channelId") {
            parameter("fromId", fromId)
        }.body()
    }

    suspend fun getMessageHistory(workspaceId: Int, channelId: Int, beforeId: Int?, limit: Int): List<MessageSyncDto> {
        return client.get("$baseUrl/history/$workspaceId/$channelId") {
            beforeId?.let { parameter("before", it) }
            parameter("limit", limit)
        }.body()
    }

    suspend fun getMessageUpdates(workspaceId: Int, channelId: Int, since: Long): List<MessageSyncDto> {
        return client.get("$baseUrl/sync/$workspaceId/$channelId") {
            parameter("since", since)
        }.body()
    }
}