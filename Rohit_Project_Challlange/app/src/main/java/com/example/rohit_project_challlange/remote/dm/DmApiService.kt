package com.example.rohit_project_challlange.remote.dm

import android.util.Log
import com.example.rohit_project_challlange.AuthTokenHolder
import com.example.rohit_project_challlange.dto.dm.DmDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class DmApiService(
    private val client: HttpClient,
) {
    private var session: WebSocketSession? = null
    private val sessionMutex = Mutex()

    suspend fun connect(baseUrl: String, userId: Long) = sessionMutex.withLock {
        if (userId <= 0L) return

        try {
            session?.let { activeSession ->
                if (activeSession.isActive) {
                    return
                }
            }
        } catch (e: Exception) {
            session = null
        }

        val cleanBaseUrl = baseUrl.trim().removeSuffix("/")

        val wsUrl = cleanBaseUrl.replace("http://", "ws://").replace("https://", "wss://")

        try {
            val newSession = client.webSocketSession {
                url("$wsUrl/ws/dm")
                AuthTokenHolder.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            }
            session = newSession
        } catch (e: Exception) {
            session = null
            throw e
        }
    }

    suspend fun sendDm(message: DmDto) {
        val currentSession = sessionMutex.withLock { session }
            ?: throw IllegalStateException("WebSocket is not connected yet.")

        if (!currentSession.isActive) {
            throw IllegalStateException("WebSocket session is dead or closed.")
        }

        val jsonText = Json.encodeToString(DmDto.serializer(), message)
        currentSession.send(Frame.Text(jsonText))
    }

    fun observeIncomingDms(): Flow<DmDto> = flow {
        while (true) {
            val currentSession = sessionMutex.withLock { session }
            if (currentSession == null || !currentSession.isActive) {
                kotlinx.coroutines.delay(1000)
                continue
            }

            try {
                for (frame in currentSession.incoming) {
                    if (frame is Frame.Text) {
                        val textPayload = frame.readText()
                        val dto = Json.decodeFromString(DmDto.serializer(), textPayload)
                        emit(dto)
                    }
                }
                sessionMutex.withLock { session = null }
                throw IllegalStateException("WebSocket incoming channel closed by remote host")
            } catch (e: Exception) {
                Log.e("DM_DEBUG", "Exception inside websocket incoming iteration loop", e)
                sessionMutex.withLock { session = null }
                throw e
            }
        }
    }

    suspend fun getOnlineUsers(baseUrl: String, workspaceId: Int): List<Int> {
        return try {
            val cleanBaseUrl = baseUrl.trim().removeSuffix("/")
            client.get("$cleanBaseUrl/api/presence/$workspaceId").body()
        } catch (e: Exception) {
            Log.w("DmApiService", "Failed fetching presence for workspace $workspaceId: ${e.message}")
            emptyList()
        }
    }

    suspend fun sendTypingStatus(workspaceId: Int, senderId: Int, receiverId: Int, isTyping: Boolean) {
        val action = if (isTyping) "TYPING_START" else "TYPING_STOP"
        val payload = DmDto(
            action = action,
            workspaceId = workspaceId,
            senderId = senderId,
            receiverId = receiverId
        )
        try {
            sendDm(payload)
        } catch (_: Exception) {}
    }

    suspend fun disconnect() = sessionMutex.withLock {
        try {
            session?.close()
        } catch (e: Exception) {
            Log.e("DM_DEBUG", "Error dropping session explicitly", e)
        } finally {
            session = null
        }
    }
}