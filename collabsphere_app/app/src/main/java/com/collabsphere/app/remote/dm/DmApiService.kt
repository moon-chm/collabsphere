package com.collabsphere.app.remote.dm

import android.util.Log
import com.collabsphere.app.AuthTokenHolder
import com.collabsphere.app.ChannelMessageCenter
import com.collabsphere.app.NotificationCenter
import com.collabsphere.app.dto.dm.DmDto
import com.collabsphere.app.dto.message.ChannelMessageEvent
import com.collabsphere.app.dto.message.ChannelReactionSummary
import com.collabsphere.app.dto.message.ChannelTypingEvent
import com.collabsphere.app.dto.notification.NotificationPushFrame
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DmApiService(
    private val client: HttpClient,
) {
    private var session: WebSocketSession? = null
    private val sessionMutex = Mutex()

    suspend fun connect(baseUrl: String, userId: Long, sinceId: Int) = sessionMutex.withLock {
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
                url("$wsUrl/ws/dm?sinceId=$sinceId&caps=channel")
                AuthTokenHolder.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            }
            session = newSession
        } catch (e: Exception) {
            session = null
            throw e
        }
    }

    suspend fun sendChannelTyping(workspaceId: Int, channelId: Int, isTyping: Boolean) {
        sendDm(
            DmDto(
                action = if (isTyping) "CHANNEL_TYPING_START" else "CHANNEL_TYPING_STOP",
                workspaceId = workspaceId,
                channelId = channelId
            )
        )
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

    private val lenientJson = Json { ignoreUnknownKeys = true }

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

                        // Notification push frames (MENTION/CHANNEL_MESSAGE/TASK_ASSIGNED/TASK_UPDATED)
                        // share this socket with DM frames but have a different shape — peek at the
                        // action before committing to a DmDto decode, which would otherwise throw on
                        // the unrecognized "notification" key and kill this connection.
                        val actionField = try {
                            lenientJson.parseToJsonElement(textPayload).jsonObject["action"]?.jsonPrimitive?.content
                        } catch (_: Exception) {
                            null
                        }

                        if (actionField == "NOTIFICATION") {
                            try {
                                val pushFrame = lenientJson.decodeFromString(NotificationPushFrame.serializer(), textPayload)
                                NotificationCenter.push(pushFrame.notification)
                            } catch (e: Exception) {
                                Log.w("DM_DEBUG", "Failed to decode notification push frame", e)
                            }
                            continue
                        }

                        if (actionField == "CHANNEL_REACTION") {
                            try {
                                ChannelMessageCenter.pushReaction(lenientJson.decodeFromString(ChannelReactionSummary.serializer(), textPayload))
                            } catch (e: Exception) {
                                Log.w("DM_DEBUG", "Failed to decode channel reaction event", e)
                            }
                            continue
                        }

                        if (actionField == "CHANNEL_TYPING") {
                            try {
                                ChannelMessageCenter.pushTyping(lenientJson.decodeFromString(ChannelTypingEvent.serializer(), textPayload))
                            } catch (e: Exception) {
                                Log.w("DM_DEBUG", "Failed to decode channel typing event", e)
                            }
                            continue
                        }

                        if (actionField == "CHANNEL_MESSAGE_EVENT") {
                            try {
                                val event = lenientJson.decodeFromString(ChannelMessageEvent.serializer(), textPayload)
                                ChannelMessageCenter.push(event.message)
                            } catch (e: Exception) {
                                Log.w("DM_DEBUG", "Failed to decode channel message event", e)
                            }
                            continue
                        }

                        val dto = lenientJson.decodeFromString(DmDto.serializer(), textPayload)
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

    suspend fun getDmHistoryPage(baseUrl: String, workspaceId: Int, partnerId: Int, beforeId: Int?, limit: Int): List<DmDto> {
        val cleanBaseUrl = baseUrl.trim().removeSuffix("/")
        return client.get("$cleanBaseUrl/api/dm/history/$workspaceId/$partnerId") {
            beforeId?.let { parameter("before", it) }
            parameter("limit", limit)
        }.body()
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

    /**
     * Uploads a file/image to the server's Cloudinary proxy endpoint.
     * Returns the secure Cloudinary URL on success.
     */
    suspend fun uploadDmMedia(baseUrl: String, fileBytes: ByteArray, mimeType: String, fileName: String): String {
        val cleanBaseUrl = baseUrl.trim().removeSuffix("/")
        val boundary = "Boundary${System.currentTimeMillis()}"
        val token = AuthTokenHolder.token ?: throw IllegalStateException("Not authenticated")

        // Build raw multipart body manually (avoids extra ktor multipart plugin)
        val crlf = "\r\n"
        val headerPart = "--$boundary$crlf" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"$crlf" +
            "Content-Type: $mimeType$crlf$crlf"
        val footer = "$crlf--$boundary--$crlf"
        val body = headerPart.toByteArray(Charsets.UTF_8) + fileBytes + footer.toByteArray(Charsets.UTF_8)

        // Use a plain HttpURLConnection so we don't need extra Ktor multipart support
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val url = java.net.URL("$cleanBaseUrl/api/dm/upload-media")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            conn.outputStream.use { it.write(body) }
            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                val element = lenientJson.parseToJsonElement(responseBody)
                element.jsonObject["url"]?.jsonPrimitive?.content
                    ?: error("Server response missing url field: $responseBody")
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                error("Media upload failed ($responseCode): $err")
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