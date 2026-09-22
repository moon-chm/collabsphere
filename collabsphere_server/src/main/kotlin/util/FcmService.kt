package com.collabsphere.util

import com.collabsphere.model.UsersTable
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream

object FcmService {
    private val logger = LoggerFactory.getLogger(FcmService::class.java)
    private var isInitialized = false

    fun init() {
        if (isInitialized || FirebaseApp.getApps().isNotEmpty()) {
            isInitialized = true
            return
        }

        try {
            // Check for service account file from environment or well-known local paths
            val envPath = System.getenv("FIREBASE_CONFIG_PATH")
            val candidatePaths = listOfNotNull(
                envPath,
                "service-account.json",
                "firebase-service-account.json",
                "collabsphere-firebase-adminsdk.json"
            )

            var credentialsStream: FileInputStream? = null
            for (path in candidatePaths) {
                val f = File(path)
                if (f.exists() && f.isFile) {
                    credentialsStream = FileInputStream(f)
                    logger.info("Found Firebase service account file at: ${f.absolutePath}")
                    break
                }
            }

            val credentials = if (credentialsStream != null) {
                GoogleCredentials.fromStream(credentialsStream)
            } else {
                try {
                    GoogleCredentials.getApplicationDefault()
                } catch (e: Exception) {
                    null
                }
            }

            if (credentials != null) {
                val options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build()
                FirebaseApp.initializeApp(options)
                isInitialized = true
                logger.info("Firebase Admin SDK initialized successfully.")
            } else {
                logger.warn("Firebase Admin SDK credentials not found. FCM background pushes will be skipped until a service account JSON is supplied.")
            }
        } catch (e: Exception) {
            logger.error("Failed to initialize Firebase Admin SDK", e)
        }
    }

    /**
     * Retrieves the FCM token for a given user from the database.
     */
    private fun getUserFcmToken(userId: Int): String? {
        return try {
            transaction {
                UsersTable.slice(UsersTable.fcmToken)
                    .select { UsersTable.id eq userId }
                    .firstOrNull()
                    ?.get(UsersTable.fcmToken)
            }
        } catch (e: Exception) {
            logger.error("Error looking up FCM token for user $userId", e)
            null
        }
    }

    /**
     * Dispatches a real-time high-priority Direct Message push notification to the recipient device.
     */
    fun sendDmPush(
        recipientUserId: Int,
        senderId: Int,
        senderUsername: String,
        workspaceId: Int,
        messageId: Int,
        content: String,
        timestamp: Long
    ) {
        if (!isInitialized) return
        val token = getUserFcmToken(recipientUserId)
        if (token.isNullOrBlank()) {
            logger.debug("No FCM token for user $recipientUserId, skipping push")
            return
        }

        try {
            val message = Message.builder()
                .setToken(token)
                .putData("type", "DM")
                .putData("sender_id", senderId.toString())
                .putData("receiver_id", recipientUserId.toString())
                .putData("sender_username", senderUsername)
                .putData("workspace_id", workspaceId.toString())
                .putData("id", messageId.toString())
                .putData("content", content)
                .putData("timestamp", timestamp.toString())
                .setAndroidConfig(
                    AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .build()
                )
                .build()

            val response = FirebaseMessaging.getInstance().send(message)
            logger.info("Sent FCM DM push to user $recipientUserId (msgId: $response)")
        } catch (e: Exception) {
            logger.error("Failed to send FCM DM push to user $recipientUserId", e)
        }
    }

    /**
     * Dispatches a high-priority activity push notification (Mention, Task assigned/updated, Channel message).
     */
    fun sendGenericPush(
        recipientUserId: Int,
        notificationId: Int,
        type: String,
        title: String,
        body: String,
        workspaceId: Int?,
        actorUsername: String?,
        actorAvatarUrl: String?
    ) {
        if (!isInitialized) return
        val token = getUserFcmToken(recipientUserId)
        if (token.isNullOrBlank()) {
            logger.debug("No FCM token for user $recipientUserId, skipping push")
            return
        }

        try {
            val builder = Message.builder()
                .setToken(token)
                .putData("type", type)
                .putData("notification_id", notificationId.toString())
                .putData("recipient_id", recipientUserId.toString())
                .putData("title", title)
                .putData("body", body)
                .putData("created_at", System.currentTimeMillis().toString())

            workspaceId?.let { builder.putData("workspace_id", it.toString()) }
            actorUsername?.let { builder.putData("actor_username", it) }
            actorAvatarUrl?.let { builder.putData("actor_avatar_url", it) }

            builder.setAndroidConfig(
                AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .build()
            )

            val response = FirebaseMessaging.getInstance().send(builder.build())
            logger.info("Sent FCM Generic push to user $recipientUserId: $title (msgId: $response)")
        } catch (e: Exception) {
            logger.error("Failed to send FCM generic push to user $recipientUserId", e)
        }
    }
}
