package plugins

import com.collabsphere.RedisFactory
import com.collabsphere.model.WorkspaceMembersTable
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Two-tier workspace member ID cache.
 *
 * Every channel message broadcast calls `workspaceMemberIds()` once to fan out
 * `NEW_MESSAGE` WebSocket events to all workspace members. On an active workspace
 * this is the single hottest DB query. Caching it cuts ~N selects per message to 0.
 *
 * Tier 1 — JVM in-process (always on):
 *   ConcurrentHashMap with a 60-second TTL entry wrapper.
 *   Eviction is lazy (on next read) — no background thread needed.
 *
 * Tier 2 — Redis (when REDIS_URL is set):
 *   Stored as comma-separated integer string, TTL 120 s.
 *   Shared across instances so a second instance warms immediately.
 *
 * Invalidation:
 *   Call [invalidate] whenever a member joins or leaves a workspace so the
 *   next broadcast sees the correct member list.
 */
internal object WorkspaceMemberCache {
    private val logger = LoggerFactory.getLogger(WorkspaceMemberCache::class.java)

    private const val JVM_TTL_MS = 60_000L          // 60 s in-process TTL
    private const val REDIS_TTL_S = 120L             // 120 s Redis TTL
    private const val REDIS_KEY_PREFIX = "cs:wm:"   // cs:wm:<workspaceId>

    // ── In-process cache ──────────────────────────────────────────────────────
    private data class CacheEntry(val memberIds: List<Int>, val expiresAt: Long)
    private val jvmCache = ConcurrentHashMap<Int, CacheEntry>()

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns member user IDs for [workspaceId].
     * MUST be called from inside an Exposed transaction / `dbQuery` block.
     */
    fun getMembers(workspaceId: Int): List<Int> {
        // L1 — JVM cache
        jvmCache[workspaceId]?.let { entry ->
            if (System.currentTimeMillis() < entry.expiresAt) return entry.memberIds
            jvmCache.remove(workspaceId)   // expired — evict
        }

        // L2 — Redis (optional)
        if (RedisFactory.isAvailable) {
            val cached = try {
                RedisFactory.async?.get("$REDIS_KEY_PREFIX$workspaceId")?.get()
            } catch (e: Exception) {
                logger.warn("[WMCache] Redis read failed for workspaceId=$workspaceId: ${e.message}")
                null
            }
            if (!cached.isNullOrBlank()) {
                val ids = cached.split(",").mapNotNull { it.trim().toIntOrNull() }
                jvmCache[workspaceId] = CacheEntry(ids, System.currentTimeMillis() + JVM_TTL_MS)
                return ids
            }
        }

        // L3 — DB (source of truth)
        val ids = queryFromDb(workspaceId)
        populateCaches(workspaceId, ids)
        return ids
    }

    /**
     * Evict cache entries for [workspaceId] — call on member join/leave.
     * Safe to call inside or outside a transaction.
     */
    fun invalidate(workspaceId: Int) {
        jvmCache.remove(workspaceId)
        if (RedisFactory.isAvailable) {
            try {
                RedisFactory.async?.del("$REDIS_KEY_PREFIX$workspaceId")
            } catch (e: Exception) {
                logger.warn("[WMCache] Redis del failed for workspaceId=$workspaceId: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun queryFromDb(workspaceId: Int): List<Int> =
        WorkspaceMembersTable
            .select(WorkspaceMembersTable.userId)
            .where { WorkspaceMembersTable.workspaceId eq workspaceId }
            .map { it[WorkspaceMembersTable.userId] }

    private fun populateCaches(workspaceId: Int, ids: List<Int>) {
        jvmCache[workspaceId] = CacheEntry(ids, System.currentTimeMillis() + JVM_TTL_MS)
        if (RedisFactory.isAvailable && ids.isNotEmpty()) {
            try {
                RedisFactory.async?.setex(
                    "$REDIS_KEY_PREFIX$workspaceId",
                    REDIS_TTL_S,
                    ids.joinToString(",")
                )
            } catch (e: Exception) {
                logger.warn("[WMCache] Redis set failed for workspaceId=$workspaceId: ${e.message}")
            }
        }
    }
}
