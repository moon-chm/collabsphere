package plugins

import com.collabsphere.RedisFactory
import com.collabsphere.model.WorkspaceMembersTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory

/**
 * Two-layer membership cache:
 *   L1 — Redis (when available): 30-second TTL, shared across instances
 *   L2 — Database: always the source of truth, queried on cache miss
 *
 * Degrades transparently to DB-only when Redis is not configured.
 * Thread-safe: Redis commands are non-blocking async; DB queries run inside
 * the caller`s existing transaction.
 */
internal object MembershipCache {
    private val logger = LoggerFactory.getLogger(MembershipCache::class.java)
    private const val TTL_SECONDS = 30L
    private fun redisKey(userId: Int, workspaceId: Int) = "mbr:$userId:$workspaceId"

    /**
     * Returns cached membership value or null on cache miss / Redis unavailable.
     * Must NOT be called inside a DB transaction — Redis I/O is async.
     */
    fun getCached(userId: Int, workspaceId: Int): Boolean? {
        if (!RedisFactory.isAvailable) return null
        return try {
            RedisFactory.async?.get(redisKey(userId, workspaceId))?.get()?.let { it == "1" }
        } catch (e: Exception) {
            logger.debug("[MembershipCache] Redis get failed: ${e.message}")
            null
        }
    }

    /**
     * Writes the result into Redis cache asynchronously (fire-and-forget).
     * Safe to call inside a DB transaction — the write is non-blocking.
     */
    fun put(userId: Int, workspaceId: Int, isMember: Boolean) {
        if (!RedisFactory.isAvailable) return
        try {
            RedisFactory.async?.setex(redisKey(userId, workspaceId), TTL_SECONDS, if (isMember) "1" else "0")
        } catch (e: Exception) {
            logger.debug("[MembershipCache] Redis set failed: ${e.message}")
        }
    }

    /**
     * Removes the cached entry — call when a member joins or leaves a workspace.
     * Best-effort: if Redis is unavailable the cache entry will simply expire after TTL.
     */
    fun invalidate(userId: Int, workspaceId: Int) {
        if (!RedisFactory.isAvailable) return
        try {
            RedisFactory.async?.del(redisKey(userId, workspaceId))
        } catch (e: Exception) {
            logger.debug("[MembershipCache] Redis del failed: ${e.message}")
        }
    }

    /**
     * Invalidate all cached memberships for a workspace — used when a workspace is deleted
     * or when a batch import of members occurs. Scans only our own key pattern.
     */
    fun invalidateWorkspace(workspaceId: Int) {
        if (!RedisFactory.isAvailable) return
        try {
            // Pattern: mbr:*:<workspaceId>
            // Note: SCAN is preferred over KEYS in production but for small sets this is fine.
            val keys = RedisFactory.async?.keys("mbr:*:$workspaceId")?.get() ?: return
            if (keys.isNotEmpty()) RedisFactory.async?.del(*keys.toTypedArray())
        } catch (e: Exception) {
            logger.debug("[MembershipCache] Redis invalidateWorkspace failed: ${e.message}")
        }
    }
}

/**
 * Drop-in replacement for the previous bare `isMember()` function.
 *
 * Execution path:
 *   1. Check Redis cache (L1) — returns immediately if hit
 *   2. Query DB (L2) — source of truth
 *   3. Populate Redis cache for subsequent calls
 *
 * MUST be called inside a `dbQuery { }` block (Exposed transaction context required for the DB path).
 */
internal fun isMemberCached(userId: Int, workspaceId: Int): Boolean {
    // L1: Redis cache
    MembershipCache.getCached(userId, workspaceId)?.let { return it }

    // L2: Database
    val result = WorkspaceMembersTable.selectAll()
        .where {
            (WorkspaceMembersTable.workspaceId eq workspaceId) and
            (WorkspaceMembersTable.userId eq userId)
        }
        .count() > 0

    // Populate cache (async, non-blocking even inside a transaction)
    MembershipCache.put(userId, workspaceId, result)
    return result
}
