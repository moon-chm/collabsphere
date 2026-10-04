package com.collabsphere

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Optional Redis integration.
 *
 * If REDIS_URL environment variable is not set, [isAvailable] is false and every
 * operation is a safe no-op or returns null — the system degrades to in-memory-only
 * behaviour (identical to the pre-Redis state on a single Render instance).
 *
 * When REDIS_URL *is* set (Render Redis add-on or external), the connection is
 * established at startup and all cache/pub-sub features become active.
 */
object RedisFactory {
    private val logger = LoggerFactory.getLogger(RedisFactory::class.java)

    private var client: RedisClient? = null
    private var _connection: StatefulRedisConnection<String, String>? = null

    /** True only when a REDIS_URL is configured AND the connection succeeded. */
    @Volatile
    var isAvailable: Boolean = false
        private set

    val async: RedisAsyncCommands<String, String>?
        get() = _connection?.async()

    /**
     * Called from [DatabaseFactory] (or main.kt) at startup.
     * Completely silent when REDIS_URL is absent — no exception, no crash.
     */
    fun init() {
        val redisUrl = System.getenv("REDIS_URL")
            ?: System.getenv("REDIS_TLS_URL") // Render exposes both
        if (redisUrl.isNullOrBlank()) {
            logger.info("[Redis] REDIS_URL not set — running without Redis (single-instance mode). " +
                "Set REDIS_URL to enable cross-instance WebSocket fan-out and caching.")
            return
        }

        try {
            // Render Redis uses rediss:// (TLS) or redis://
            val uri = RedisURI.create(redisUrl)
            uri.timeout = Duration.ofSeconds(5)

            client = RedisClient.create(uri)
            _connection = client!!.connect()

            // Quick connectivity check — if this throws, Redis is misconfigured
            _connection!!.sync().ping()

            isAvailable = true
            logger.info("[Redis] Connected to Redis at ${uri.host}:${uri.port}")
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            logger.warn("[Redis] Failed to connect to Redis — running without it. " +
                "WebSocket fan-out and caches will use in-memory fallback. Error: ${e.message}")
            // Clean up any partial state
            try { _connection?.close() } catch (_: Exception) {}
            try { client?.shutdown() } catch (_: Exception) {}
            _connection = null
            client = null
            isAvailable = false
        }
    }

    /**
     * Expose the raw client for pub/sub subscriptions that need a dedicated connection.
     * Returns null when Redis is not available.
     */
    fun newPubSubConnection() = if (isAvailable) {
        try { client?.connectPubSub() } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            logger.warn("[Redis] Could not open pub/sub connection: ${e.message}")
            null
        }
    } else null

    fun close() {
        try { _connection?.close() } catch (_: Exception) {}
        try { client?.shutdown() } catch (_: Exception) {}
        isAvailable = false
    }
}
