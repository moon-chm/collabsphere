package com.collabsphere.app.remote.fcm

import android.util.Log
import com.collabsphere.app.MyApplication
import com.collabsphere.app.NotificationHelper
import com.collabsphere.app.UserPreferences
import com.collabsphere.app.dto.dm.DmDto
import com.collabsphere.app.dto.notification.NotificationResponse
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class CollabFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationHelper: NotificationHelper by inject()
    private val userPreferences: UserPreferences by inject()

    companion object {
        private const val TAG = "CollabFCM"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Registration Token generated: $token")
        serviceScope.launch {
            try {
                userPreferences.saveFcmToken(token)
                // If user is already logged in, the token will also be synchronized to the backend
                val currentUserId = userPreferences.userIdFlow.firstOrNull() ?: -1
                if (currentUserId > 0) {
                    // Token registration with server
                    FcmTokenRegistrar.sendTokenToServer(currentUserId, token)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling new FCM token", e)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}, data: ${remoteMessage.data}")

        val data = remoteMessage.data
        if (data.isEmpty()) return

        serviceScope.launch {
            try {
                val currentUserId = userPreferences.userIdFlow.firstOrNull() ?: -1
                val type = data["type"] ?: "GENERIC"

                when (type) {
                    "DM" -> {
                        val senderId = data["sender_id"]?.toIntOrNull() ?: return@launch
                        val receiverId = data["receiver_id"]?.toIntOrNull() ?: currentUserId
                        val workspaceId = data["workspace_id"]?.toIntOrNull() ?: 0
                        val content = data["content"] ?: ""
                        val id = data["id"]?.toIntOrNull() ?: 0
                        val timestamp = data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()

                        // Suppress if the user is in app actively viewing this conversation
                        val isViewingChat = MyApplication.isAppForeground &&
                                (com.collabsphere.app.remote.dm.DmWebSocketService.activeChatPartnerId == senderId)

                        // Suppress if WebSocket is live — it already delivered the message and
                        // triggered the notification via DmWebSocketService. FCM is only the
                        // fallback for when the app is in the background / process is killed.
                        val isWebSocketAlive = com.collabsphere.app.remote.dm.DmWebSocketService.isWebSocketConnected

                        if (senderId != currentUserId && !isViewingChat && !isWebSocketAlive) {
                            val dmDto = DmDto(
                                id = id,
                                workspaceId = workspaceId,
                                senderId = senderId,
                                receiverId = receiverId,
                                content = content,
                                timestamp = timestamp,
                                action = "RECEIVE_MESSAGE"
                            )
                            notificationHelper.showDmNotification(dmDto)
                        } else {
                            Log.d(TAG, "FCM DM suppressed — WebSocket alive=$isWebSocketAlive, viewing=$isViewingChat")
                        }
                    }

                    "TASK_ASSIGNED", "TASK_UPDATED", "MENTION", "CHANNEL_MESSAGE" -> {
                        val notifId = data["notification_id"]?.toIntOrNull() ?: (System.currentTimeMillis() % 100000).toInt()
                        val recipientId = data["recipient_id"]?.toIntOrNull() ?: currentUserId
                        val title = data["title"] ?: "CollabSphere"
                        val body = data["body"] ?: ""
                        val workspaceId = data["workspace_id"]?.toIntOrNull()
                        val actorUsername = data["actor_username"]
                        val actorAvatarUrl = data["actor_avatar_url"]
                        val createdAt = data["created_at"]?.toLongOrNull() ?: System.currentTimeMillis()

                        val notificationResponse = NotificationResponse(
                            id = notifId,
                            recipientId = recipientId,
                            title = title,
                            body = body,
                            type = type,
                            workspaceId = workspaceId,
                            actorUsername = actorUsername,
                            actorAvatarUrl = actorAvatarUrl,
                            isRead = false,
                            createdAt = createdAt
                        )
                        notificationHelper.showGenericNotification(notificationResponse)
                    }

                    else -> {
                        // Fallback generic notification
                        val title = data["title"] ?: remoteMessage.notification?.title ?: "CollabSphere"
                        val body = data["body"] ?: remoteMessage.notification?.body ?: ""
                        if (body.isNotBlank()) {
                            val notifId = (System.currentTimeMillis() % 100000).toInt()
                            val notificationResponse = NotificationResponse(
                                id = notifId,
                                recipientId = currentUserId,
                                title = title,
                                body = body,
                                type = "GENERAL",
                                isRead = false,
                                createdAt = System.currentTimeMillis()
                            )
                            notificationHelper.showGenericNotification(notificationResponse)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process incoming push payload", e)
            }
        }
    }
}
