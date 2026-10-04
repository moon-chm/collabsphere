package plugins

import com.collabsphere.RedisFactory
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Central WebSocket session registry and message broker.
 *
 * Single-instance (current Render deployment):
 *   All sessions are local. Messages are delivered directly. Redis pub/sub is not used.
 *
 * Multi-instance (future):
 *   When REDIS_URL is configured, messages that cannot be delivered locally are published
 *   to Redis. Each instance subscribes to a per-user channel pattern and delivers
 *   cross-instance messages to its local sessions.
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
     *   2. If nothing was delivered locally AND Redis is available, publish to Redis so
     *      another instance can deliver it (future multi-instance support, zero cost today)
     */
    suspend fun sendToUser(userId: Long, text: String, requireChannelCapable: Boolean = false) {
        val delivered = deliverLocally(userId, text, requireChannelCapable)
        if (!delivered && RedisFactory.isAvailable) {
            publishToRedis(userId, text)
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
                logger.warn("[WS] Send failed for userId=$userId — removing dead session: ${e.message}")
                removeSession(userId, session)
            }
        }
        return delivered
    }

    private fun publishToRedis(userId: Long, message: String) {
        try {
            RedisFactory.async?.publish("$REDIS_WS_PREFIX$userId", message)
        } catch (e: Exception) {
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
            val pubSubConn = RedisFactory.newPubSubConnection() ?: run {
                logger.warn("[WS] Could not open Redis pub/sub connection — cross-instance fan-out disabled")
                return@launch
            }
            pubSubConn.addListener(object : io.lettuce.core.pubsub.RedisPubSubAdapter<String, String>() {
                override fun message(pattern: String, channel: String, message: String) {
                    val userId = channel.removePrefix(REDIS_WS_PREFIX).toLongOrNull() ?: return
                    // Deliver to local sessions that belong to this user — fire and forget
                    scope.launch { deliverLocally(userId, message, requireChannelCapable = false) }
                }
            })
            pubSubConn.async().psubscribe("$REDIS_WS_PREFIX*")
            logger.info("[WS] Redis pub/sub subscriber started — pattern: ${REDIS_WS_PREFIX}*")
        }
    }
}
