package plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FocusRulesTest {

    private val now = 1_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun `clearing focus is always allowed`() {
        assertNull(FocusRules.untilRejection(null, now))
    }

    @Test
    fun `focus must last between a minute and twelve hours`() {
        assertNotNull(FocusRules.untilRejection(now + 30_000L, now))
        assertNotNull(FocusRules.untilRejection(now - minute, now))
        assertNull(FocusRules.untilRejection(now + minute, now))
        assertNull(FocusRules.untilRejection(now + 12 * hour, now))
        assertNotNull(FocusRules.untilRejection(now + 12 * hour + 1, now))
    }

    @Test
    fun `expired focus reads as not focusing`() {
        assertNull(FocusRules.activeUntil(null, now))
        assertNull(FocusRules.activeUntil(now, now))
        assertEquals(now + hour, FocusRules.activeUntil(now + hour, now))
    }

    @Test
    fun `messages are only held when focus has at least thirty seconds left`() {
        assertNull(FocusRules.holdSendAt(null, now))
        assertNull(FocusRules.holdSendAt(now + 29_999L, now))
        assertEquals(now + 30_000L, FocusRules.holdSendAt(now + 30_000L, now))
    }

    @Test
    fun `held messages follow the new focus end or go out now`() {
        assertEquals(now + hour, FocusRules.releaseAt(now + hour, now))
        assertEquals(now, FocusRules.releaseAt(null, now))
        assertEquals(now, FocusRules.releaseAt(now - 1, now))
    }

    @Test
    fun `content must be present and bounded`() {
        assertNotNull(FocusRules.contentRejection("   "))
        assertNotNull(FocusRules.contentRejection("x".repeat(4001)))
        assertNull(FocusRules.contentRejection("ping me after"))
    }
}
