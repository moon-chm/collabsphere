package com.collabsphere.app.remote.notification

import com.collabsphere.app.AppConfig
import com.collabsphere.app.dto.notification.MarkReadRequest
import com.collabsphere.app.dto.notification.MuteRequest
import com.collabsphere.app.dto.notification.MuteSetting
import com.collabsphere.app.dto.notification.NotificationCountResponse
import com.collabsphere.app.dto.notification.NotificationResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationApiService(private val client: HttpClient) {

    private val baseUrl = "${AppConfig.BASE_URL}/api/notifications"

    suspend fun getNotifications(unreadOnly: Boolean = false, limit: Int = 50, offset: Long = 0): List<NotificationResponse> =
        withContext(Dispatchers.IO) {
            client.get(baseUrl) {
                if (unreadOnly) parameter("unread", "true")
                parameter("limit", limit)
                parameter("offset", offset)
            }.body()
        }

    suspend fun getMutes(): List<MuteSetting> = withContext(Dispatchers.IO) {
        client.get("$baseUrl/mutes").body()
    }

    suspend fun setMute(request: MuteRequest): Boolean = withContext(Dispatchers.IO) {
        client.put("$baseUrl/mutes") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.status.isSuccess()
    }

    suspend fun getCount(): NotificationCountResponse = withContext(Dispatchers.IO) {
        client.get("$baseUrl/count").body()
    }

    suspend fun markRead(ids: List<Int>? = null): Boolean = withContext(Dispatchers.IO) {
        val response = client.put("$baseUrl/read") {
            contentType(ContentType.Application.Json)
            setBody(MarkReadRequest(ids))
        }
        response.status.isSuccess()
    }

    suspend fun deleteOne(id: Int): Boolean = withContext(Dispatchers.IO) {
        client.delete("$baseUrl/$id").status.isSuccess()
    }

    suspend fun clearAll(): Boolean = withContext(Dispatchers.IO) {
        client.delete(baseUrl).status.isSuccess()
    }
}
