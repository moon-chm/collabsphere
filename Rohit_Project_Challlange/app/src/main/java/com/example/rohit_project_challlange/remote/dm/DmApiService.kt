package com.example.rohit_project_challlange.remote.dm

import android.util.Log
import com.example.rohit_project_challlange.dto.dm.DmDto
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
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

        val cleanBaseUrl = if (baseUrl.startsWith("http://10.0.2.2")) {
            "http://10.238.3.93:8080"
        } else {
            baseUrl
        }

        val wsUrl = cleanBaseUrl.replace("http://", "ws://").replace("https://", "wss://")

        try {
            val newSession = client.webSocketSession {
                url("$wsUrl/ws/dm?userId=$userId")
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
            } catch (e: Exception) {
                Log.e("DM_DEBUG", "Exception inside websocket incoming iteration loop", e)
                sessionMutex.withLock { session = null }
                throw e
            }
        }
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