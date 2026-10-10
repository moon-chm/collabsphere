package com.collabsphere.util

import com.collabsphere.model.UsersTable
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream

object FcmService {
    private val logger = LoggerFactory.getLogger(FcmService::class.java)
    private var isInitialized = false

    /** Shared supervised scope: failures in one push don't cancel others, and the scope
     *  outlives individual calls without leaking a new scope per send. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class PushDispatchResult(val sentCount: Int, val retryableFailure: Boolean)

    fun init() {
        if (ExternalProviderPolicy.areDisabled()) {
            logger.info("FCM initialization disabled by the isolated test profile")
            return
        }
        if (isInitialized || FirebaseApp.getApps().isNotEmpty()) {
            isInitialized = true
            return
        }

        try {
            val credentials = resolveCredentials()

            if (credentials != null) {
                val options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build()
                FirebaseApp.initializeApp(options)
                isInitialized = true
                logger.info("Firebase Admin SDK initialized successfully.")
            } else {
                logger.warn(
                    "Firebase Admin SDK credentials not found. " +
                    "Set FIREBASE_SERVICE_ACCOUNT_JSON (raw JSON) or " +
                    "FIREBASE_CONFIG_PATH (file path) env var to enable FCM pushes."
                )
            }
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            logger.error("Failed to initialize Firebase Admin SDK", e)
        }
    }

    /**
     * Resolves Firebase credentials from (in priority order):
     * 1. FIREBASE_SERVICE_ACCOUNT_JSON env var — raw JSON string (best for Render/cloud)
     * 2. FIREBASE_CONFIG_PATH env var — path to a local JSON file
     * 3. Well-known local file names (for local dev)
     * 4. Application Default Credentials (GCP-hosted environments)
     */
    private fun resolveCredentials(): GoogleCredentials? {
        // 1. Raw JSON string from environment variable (Render secret env var)
        val rawJson = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON")
        if (!rawJson.isNullOrBlank()) {
            logger.info("Loading Firebase credentials from FIREBASE_SERVICE_ACCOUNT_JSON env var.")
            return GoogleCredentials.fromStream(rawJson.byteInputStream())
        }

        // 2. File path from environment variable
        val envPath = System.getenv("FIREBASE_CONFIG_PATH")

        // 3. Well-known local file names (for local development)
        val candidatePaths = listOfNotNull(
            envPath,
            "service-account.json",
            "firebase-service-account.json",
            "collabsphere-firebase-adminsdk.json",
            "collabsphere-66131-firebase-adminsdk-fbsvc-b0298c1ffa.json"
        )

        for (path in candidatePaths) {
            val f = File(path)
            if (f.exists() && f.isFile) {
                logger.info("Loading Firebase credentials from file: ${f.absolutePath}")
                return GoogleCredentials.fromStream(FileInputStream(f))
            }
        }

        // 4. Application Default Credentials (Google Cloud environments)
        return try {
            GoogleCredentials.getApplicationDefault().also {
                logger.info("Using Application Default Credentials for Firebase.")
            }
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    /**
     * Collects ALL active FCM tokens for a given user — both the legacy single-token
     * column ([UsersTable.fcmToken]) and any rows in [UserFcmTokensTable].
     *
     * De-duplicated: if the same token appears in both sources it is sent only once.
     * Returns an empty list if the user has no registered devices.
     */
    private fun getAllFcmTokens(userId: Int): List<String> {
        return try {
            transaction {
                val tokens = mutableSetOf<String>()

                // Legacy column — always check for backward compat
                UsersTable.slice(UsersTable.fcmToken)
                    .select { UsersTable.id eq userId }
                    .firstOrNull()
                    ?.get(UsersTable.fcmToken)
                    ?.let { tokens.add(it) }

                // Multi-device table — new registrations from device-aware clients
                com.collabsphere.model.UserFcmTokensTable
                    .slice(com.collabsphere.model.UserFcmTokensTable.token)
                    .select { com.collabsphere.model.UserFcmTokensTable.userId eq userId }
                    .mapNotNull { it[com.collabsphere.model.UserFcmTokensTable.token].takeIf { t -> t.isNotBlank() } }
                    .forEach { tokens.add(it) }

                tokens.toList()
            }
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            logger.error("Error looking up FCM tokens for user $userId", e)
            emptyList()
        }
    }

    private val fcmSuccessCounter = io.micrometer.core.instrument.Metrics.counter("fcm.push.success")
    private val fcmFailureCounter = io.micrometer.core.instrument.Metrics.counter("fcm.push.failure")

    /**
     * Builds and sends an FCM message with the given [data] to each token in [tokens].
     * Returns the count of successfully delivered messages.
     * UNREGISTERED tokens are skipped silently (stale devices); other errors are logged.
     */
    private fun sendAll(tokens: List<String>, data: Map<String, String>, androidPriority: AndroidConfig.Priority): PushDispatchResult {
        var sent = 0
        var retryableFailure = false
        val androidConfig = AndroidConfig.builder().setPriority(androidPriority).build()
        for (token in tokens) {
            try {
                val msg = Message.builder()
                    .setToken(token)
                    .setAndroidConfig(androidConfig)
                    .apply { data.forEach { (k, v) -> putData(k, v) } }
                    .build()
                FirebaseMessaging.getInstance().send(msg)
                sent++
                fcmSuccessCounter.increment()
            } catch (e: com.google.firebase.messaging.FirebaseMessagingException) {
                fcmFailureCounter.increment()
                if (e.messagingErrorCode?.name == "UNREGISTERED") {
                    logger.info("[FCM] Stale token (UNREGISTERED) skipped, deleting from DB: ${token.take(20)}...")
                    org.jetbrains.exposed.sql.transactions.transaction {
                        com.collabsphere.model.UsersTable.update({ com.collabsphere.model.UsersTable.fcmToken eq token }) {
                            it[com.collabsphere.model.UsersTable.fcmToken] = null
                        }
                        com.collabsphere.model.UserFcmTokensTable.deleteWhere {
                            com.collabsphere.model.UserFcmTokensTable.token eq token
                        }
                    }
                } else {
                    retryableFailure = true
                    logger.error("[FCM] Failed to send to token ${token.take(20)}...: ${e.message}")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                retryableFailure = true
                fcmFailureCounter.increment()
                logger.error("[FCM] Unexpected error for token ${token.take(20)}...: ${e.message}")
            }
        }
        return PushDispatchResult(sent, retryableFailure)
    }

    /**
     * Dispatches a real-time high-priority Direct Message push notification to ALL
     * registered devices of the recipient (multi-device fan-out).
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
        scope.launch {
            val tokens = getAllFcmTokens(recipientUserId)
            if (tokens.isEmpty()) {
                logger.debug("[FCM] No tokens for user $recipientUserId, skipping DM push")
                return@launch
            }
            val data = mapOf(
                "type" to "DM",
                "sender_id" to senderId.toString(),
                "receiver_id" to recipientUserId.toString(),
                "sender_username" to senderUsername,
                "workspace_id" to workspaceId.toString(),
                "id" to messageId.toString(),
                "content" to content,
                "timestamp" to timestamp.toString()
            )
            val result = sendAll(tokens, data, AndroidConfig.Priority.HIGH)
            if (result.sentCount > 0) logger.info("[FCM] DM push to userId=$recipientUserId: ${result.sentCount}/${tokens.size} devices")
        }
    }

    /**
     * Dispatches a high-priority activity push notification to ALL registered devices
     * of the recipient (Mention, Task assigned/updated, Channel message, etc).
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
        scope.launch {
            val tokens = getAllFcmTokens(recipientUserId)
            if (tokens.isEmpty()) {
                logger.debug("[FCM] No tokens for user $recipientUserId, skipping generic push")
                return@launch
            }
            val data = buildMap<String, String> {
                put("type", type)
                put("notification_id", notificationId.toString())
                put("recipient_id", recipientUserId.toString())
                put("title", title)
                put("body", body)
                put("created_at", System.currentTimeMillis().toString())
                workspaceId?.let { put("workspace_id", it.toString()) }
                actorUsername?.let { put("actor_username", it) }
                actorAvatarUrl?.let { put("actor_avatar_url", it) }
            }
            val result = sendAll(tokens, data, AndroidConfig.Priority.HIGH)
            if (result.sentCount > 0) logger.info("[FCM] Generic push '$title' to userId=$recipientUserId: ${result.sentCount}/${tokens.size} devices")
        }
    }

    /** Synchronous result for durable outbox workers; retryable token failures return false. */
    suspend fun sendGenericPushAwait(
        recipientUserId: Int,
        notificationId: Int,
        type: String,
        title: String,
        body: String,
        workspaceId: Int?
    ): Boolean {
        if (!isInitialized) return true
        return withContext(Dispatchers.IO) {
            val tokens = getAllFcmTokens(recipientUserId)
            if (tokens.isEmpty()) return@withContext true
            val data = buildMap {
                put("type", type)
                put("notification_id", notificationId.toString())
                put("recipient_id", recipientUserId.toString())
                put("title", title)
                put("body", body)
                put("created_at", System.currentTimeMillis().toString())
                workspaceId?.let { put("workspace_id", it.toString()) }
            }
            val result = sendAll(tokens, data, AndroidConfig.Priority.HIGH)
            !result.retryableFailure
        }
    }
}
