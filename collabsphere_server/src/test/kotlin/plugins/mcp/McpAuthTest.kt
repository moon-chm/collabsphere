package plugins.mcp

import plugins.McpTokenRules
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpAuthTest {

    @Test
    fun `tokens are long, random, prefixed and only their hash is stable`() {
        val a = McpTokenFormat.generate()
        val b = McpTokenFormat.generate()
        assertTrue(a.startsWith("csmcp_"))
        assertEquals(49, a.length)
        assertNotEquals(a, b)
        assertTrue(McpTokenFormat.looksValid(a))
        assertEquals(64, McpTokenFormat.hash(a).length)
        assertEquals(McpTokenFormat.hash(a), McpTokenFormat.hash(a))
        assertNotEquals(McpTokenFormat.hash(a), McpTokenFormat.hash(b))
        assertEquals(a.take(12), McpTokenFormat.displayPrefix(a))
    }

    @Test
    fun `obviously wrong tokens are rejected before any lookup`() {
        assertFalse(McpTokenFormat.looksValid(""))
        assertFalse(McpTokenFormat.looksValid("eyJhbGciOiJIUzI1NiJ9.app-jwt.signature"))
        assertFalse(McpTokenFormat.looksValid("csmcp_short"))
    }

    @Test
    fun `bearer header parsing`() {
        assertEquals("abc", bearerToken("Bearer abc"))
        assertEquals("abc", bearerToken("bearer   abc "))
        assertNull(bearerToken(null))
        assertNull(bearerToken("Basic abc"))
        assertNull(bearerToken("Bearer "))
    }

    @Test
    fun `scopes always include read and reject unknown values`() {
        assertEquals(listOf("read"), McpScopes.normalize(listOf("read")))
        assertEquals(listOf("read", "write"), McpScopes.normalize(listOf("write")))
        assertEquals(listOf("read", "write"), McpScopes.normalize(listOf(" WRITE ", "read")))
        assertNull(McpScopes.normalize(emptyList()))
        assertNull(McpScopes.normalize(listOf("admin")))
        assertEquals(setOf("read", "write"), McpScopes.parse("read,write,admin"))
    }

    @Test
    fun `rate limiter allows a burst then resets with the window`() {
        var now = 0L
        val limiter = McpRateLimiter(maxRequests = 3, windowMs = 1_000, clock = { now })
        repeat(3) { assertTrue(limiter.tryAcquire(1)) }
        assertFalse(limiter.tryAcquire(1))
        assertTrue(limiter.tryAcquire(2))
        now = 1_000
        assertTrue(limiter.tryAcquire(1))
    }

    @Test
    fun `token rules`() {
        assertNotNull(McpTokenRules.nameRejection("  "))
        assertNotNull(McpTokenRules.nameRejection("x".repeat(61)))
        assertNull(McpTokenRules.nameRejection("Claude Code on my laptop"))
        assertNull(McpTokenRules.expiryRejection(null))
        assertNull(McpTokenRules.expiryRejection(90))
        assertNotNull(McpTokenRules.expiryRejection(45))
    }

    @Test
    fun `dates follow the user's own calendar day`() {
        val lateEveningUtc = Instant.parse("2026-10-16T20:00:00Z")
        assertEquals(Instant.parse("2026-10-17T00:00:00Z").toEpochMilli(), McpTools.todayUtcMidnight("Asia/Kolkata", lateEveningUtc))
        assertEquals(Instant.parse("2026-10-16T00:00:00Z").toEpochMilli(), McpTools.todayUtcMidnight(null, lateEveningUtc))
        assertEquals(Instant.parse("2026-10-16T00:00:00Z").toEpochMilli(), McpTools.todayUtcMidnight("Not/AZone", lateEveningUtc))
        assertEquals("2026-10-31", McpTools.dueDateString(Instant.parse("2026-10-31T00:00:00Z").toEpochMilli()))
        assertEquals(Instant.parse("2026-10-31T00:00:00Z").toEpochMilli(), McpTools.parseDate("2026-10-31"))
    }
}
