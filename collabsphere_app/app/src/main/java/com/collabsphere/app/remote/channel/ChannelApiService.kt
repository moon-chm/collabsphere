package com.collabsphere.app.remote.channel

import com.collabsphere.app.AppConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import com.collabsphere.app.dto.channel.ChannelRequest
import com.collabsphere.app.dto.channel.ChannelResponse
import com.collabsphere.app.dto.channel.ChannelSyncDto

class ChannelApiService(private val client: HttpClient) {

    private val baseUrl = "${AppConfig.BASE_URL}/api/channels"

    suspend fun createChannel(request: ChannelRequest): ChannelResponse {
        return client.post(baseUrl) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteChannel(channelName: String, workspaceId: Int, userId: Int?): HttpStatusCode {
        val targetUserId = userId ?: 0
        // appendPathSegments percent-encodes each segment, so a channel name containing a space,
        // '/', '#', '?' or '%' can't produce a malformed or misrouted request.
        val url = URLBuilder(baseUrl).apply {
            appendPathSegments(channelName, workspaceId.toString(), targetUserId.toString())
        }.buildString()
        return client.delete(url).status
    }

    suspend fun getchannelbyuser(workspaceId: Int): List<ChannelResponse> {
        return client.get("$baseUrl/workspace/$workspaceId").body()
    }

    suspend fun getChannelUpdates(workspaceId: Int, since: Long): List<ChannelSyncDto> {
        return client.get("$baseUrl/sync/$workspaceId") {
            parameter("since", since)
        }.body()
    }
}