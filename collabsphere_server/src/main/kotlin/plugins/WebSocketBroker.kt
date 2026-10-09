package plugins

import com.collabsphere.RedisFactory
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.UUID

/**
 * Central WebSocket session registry and message broker.
 *
 * Single-instance (current Render deployment):
 *   All sessions are local. Messages are delivered directly. Redis pub/sub is not used.
 *
 * Multi-instance:
 *   When Redis is configured, each message is delivered locally and published once with an
 *   origin ID. Subscribers skip their own publication and deliver it to remote local sessions.
 *
 * This object is a drop-in replacement for the previous `activeDmSessions` / `channelCapableSessions`
 * global maps. All existing behaviour is preserved exactly.
 */
internal object WebSocketBroker {
    private val logger = LoggerFactory.getLogger(WebSocketBroker::class.java)

    // ── Local session registry ────────────────────────────────────────────────
    // One user can have N concurrent sessions (multi-device). CopyOnWriteArraySet
    // is safe for the typical read-heavy (deliver) / rare write (connect/disconnect) pattern.
    private val localSessions = ConcurrentHashMap<Long, MutableSet<WebSocketServerSession>>()
    private val channelCapableSessions: MutableSet<WebSocketServerSession> = ConcurrentHashMap.newKeySet()

    // ── Redis pub/sub channel prefix ──────────────────────────────────────────
    private const val REDIS_WS_PREFIX = "cs:ws:"  // cs:ws:<userId>
    private val instanceId = UUID.randomUUID().toString()
    private val redisJson = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class RedisWsEnvelope(
        val originInstanceId: String,
        val requireChannelCapable: Boolean,
        val payload: String
    )

    // ── Metrics ───────────────────────────────────────────────────────────────
    val localSessionCount: Int get() = localSessions.values.sumOf { it.size }

    // ─────────────────────────────────────────────────────────────────────────
    // Session lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    fun addSession(userId: Long, session: WebSocketServerSession, supportsChannelEvents: Boolean = false) {
        localSessions.computeIfAbsent(userId) { CopyOnWriteArraySet() }.add(session)
        if (supportsChannelEvents) channelCapableSessions.add(session)
        logger.debug("[WS] Session added for userId=$userId supportsChannels=$supportsChannelEvents total=${localSessionCount}")
    }

    fun removeSession(userId: Long, session: WebSocketServerSession) {
        localSessions[userId]?.let { sessions ->
            sessions.remove(session)
            if (sessions.isEmpty()) localSessions.remove(userId)
        }
        channelCapableSessions.remove(session)
        logger.debug("[WS] Session removed for userId=$userId remaining=${localSessions[userId]?.size ?: 0}")
    }

    fun isUserConnected(userId: Long): Boolean = localSessions.containsKey(userId)

    // ─────────────────────────────────────────────────────────────────────────
    // Message delivery
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Send a message to a specific user.
     * [requireChannelCapable]: when true, only sessions that opted into channel events receive it.
     *
     * Delivery order:
     *   1. Try all local sessions — fast path, no network hop
     *   2. When Redis is available, publish to every other instance as well. The origin ID
     *      prevents the subscriber on this instance from echoing the local delivery.
     */
    suspend fun sendToUser(userId: Long, text: String, requireChannelCapable: Boolean = false) {
        deliverLocally(userId, text, requireChannelCapable)
        if (RedisFactory.isAvailable) {
            publishToRedis(
                userId,
                redisJson.encodeToString(RedisWsEnvelope(instanceId, requireChannelCapable, text))
            )
        }
    }

    /**
     * Fan-out to a list of user IDs — used for workspace-wide broadcasts
     * (e.g. a new channel message visible to all workspace members).
     */
    suspend fun broadcast(userIds: List<Int>, text: String, requireChannelCapable: Boolean = false) {
        userIds.forEach { sendToUser(it.toLong(), text, requireChannelCapable) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns true if at least one session received the message. */
    private suspend fun deliverLocally(userId: Long, text: String, requireChannelCapable: Boolean): Boolean {
        val sessions = localSessions[userId] ?: return false
        var delivered = false
        for (session in sessions) {
            if (requireChannelCapable && session !in channelCapableSessions) continue
            if (!session.isActive) {
                // Stale session — remove it so future deliveries don`t try it again
                logger.warn("[WS] Removing stale session for userId=$userId")
                removeSession(userId, session)
                continue
            }
            try {
                session.send(Frame.Text(text))
                delivered = true
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.warn("[WS] Send failed for userId=$userId — removing dead session: ${e.message}")
                removeSession(userId, session)
            }
        }
        return delivered
    }

    private fun publishToRedis(userId: Long, message: String) {
        try {
            RedisFactory.async?.publish("$REDIS_WS_PREFIX$userId", message)?.whenComplete { _, error ->
                if (error != null) logger.warn("[WS] Redis publish failed for userId=$userId: ${error.message}")
            }
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            logger.warn("[WS] Redis publish failed for userId=$userId: ${e.message}")
        }
    }

    /**
     * Starts a Redis pub/sub subscriber that listens for cross-instance deliveries.
     * No-op when Redis is not available — safe to call unconditionally at startup.
     *
     * Each instance subscribes to the pattern `cs:ws:*` and delivers any matching
     * message to its own local sessions.
     */
    fun startRedisSubscriber(scope: CoroutineScope) {
        if (!RedisFactory.isAvailable) {
            logger.info("[WS] Redis not available — cross-instance WebSocket fan-out disabled (single-instance mode)")
            return
        }
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                val pubSubConn = RedisFactory.newPubSubConnection()
                if (pubSubConn == null) {
                    logger.warn("[WS] Redis subscriber connection unavailable; retrying")
                    delay(5_000)
                    continue
                }
                try {
                    pubSubConn.addListener(object : io.lettuce.core.pubsub.RedisPubSubAdapter<String, String>() {
                        override fun message(pattern: String, channel: String, message: String) {
                            val userId = channel.removePrefix(REDIS_WS_PREFIX).toLongOrNull() ?: return
                            val envelope = runCatching { redisJson.decodeFromString<RedisWsEnvelope>(message) }.getOrNull()
                            if (envelope?.originInstanceId == instanceId) return
                            val payload = envelope?.payload ?: message // compatible with pre-envelope publishers
                            scope.launch {
                                deliverLocally(userId, payload, envelope?.requireChannelCapable ?: false)
                            }
                        }
                    })
                    pubSubConn.async().psubscribe("$REDIS_WS_PREFIX*")
                    logger.info("[WS] Redis pub/sub subscriber started — pattern: ${REDIS_WS_PREFIX}*")
                    while (isActive && pubSubConn.isOpen) delay(1_000)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    logger.warn("[WS] Redis subscriber stopped; retrying: ${e.message}")
                } finally {
                    runCatching { pubSubConn.close() }
                }
                if (isActive) delay(5_000)
            }
        }
    }
}
