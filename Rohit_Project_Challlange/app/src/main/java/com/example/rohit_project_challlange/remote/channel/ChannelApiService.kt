package com.example.rohit_project_challlange.remote.channel

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.http.*
import com.example.rohit_project_challlange.dto.channel.ChannelRequest
import com.example.rohit_project_challlange.dto.channel.ChannelResponse
import com.example.rohit_project_challlange.dto.channel.ChannelSyncDto

class ChannelApiService(private val client: HttpClient) {

    private val baseUrl = "http://10.238.3.93:8080/api/channels"

    suspend fun createChannel(request: ChannelRequest): ChannelResponse {
        return client.post(baseUrl) {
            contentType(ContentType.Application.Json)
            setBody(request)
            timeout {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
            }
        }.body()
    }

    suspend fun deleteChannel(channelName: String, workspaceId: Int, userId: Int?): Boolean {
        val targetUserId = userId ?: 0
        val response = client.delete("$baseUrl/$channelName/$workspaceId/$targetUserId") {
            timeout {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
            }
        }
        return response.status.isSuccess()
    }

    suspend fun getchannelbyuser(workspaceId: Int): List<ChannelResponse> {
        return client.get("$baseUrl/workspace/$workspaceId") {
            timeout {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
            }
        }.body()
    }

    suspend fun getChannelUpdates(workspaceId: Int, since: Long): List<ChannelSyncDto> {
        return client.get("$baseUrl/sync/$workspaceId") {
            parameter("since", since)
            timeout {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
            }
        }.body()
    }
}