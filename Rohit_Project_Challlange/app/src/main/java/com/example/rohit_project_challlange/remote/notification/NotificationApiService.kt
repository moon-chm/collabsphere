package com.example.rohit_project_challlange.remote.notification

import com.example.rohit_project_challlange.AppConfig
import com.example.rohit_project_challlange.dto.notification.MarkReadRequest
import com.example.rohit_project_challlange.dto.notification.NotificationCountResponse
import com.example.rohit_project_challlange.dto.notification.NotificationResponse
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
