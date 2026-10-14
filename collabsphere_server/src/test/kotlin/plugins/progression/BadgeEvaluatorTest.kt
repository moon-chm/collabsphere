package plugins.progression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BadgeEvaluatorTest {
    private val taskTitan = ProgressionCatalogs.current.badges.single { it.key == "TASK_TITAN" } // 10 / 50 / 200

    @Test
    fun `tiers are cumulative`() {
        assertEquals(emptyList(), BadgeEvaluator.tiersReached(taskTitan, 9))
        assertEquals(listOf(BadgeTier.BRONZE), BadgeEvaluator.tiersReached(taskTitan, 10))
        assertEquals(listOf(BadgeTier.BRONZE, BadgeTier.SILVER), BadgeEvaluator.tiersReached(taskTitan, 50))
        assertEquals(BadgeTier.entries, BadgeEvaluator.tiersReached(taskTitan, 500))
    }

    @Test
    fun `next tier and threshold are reported until gold`() {
        assertEquals(BadgeTier.BRONZE to 10L, BadgeEvaluator.nextTier(taskTitan, 0))
        assertEquals(BadgeTier.SILVER to 50L, BadgeEvaluator.nextTier(taskTitan, 10))
        assertEquals(BadgeTier.GOLD to 200L, BadgeEvaluator.nextTier(taskTitan, 199))
        assertNull(BadgeEvaluator.nextTier(taskTitan, 200))
    }

    @Test
    fun `a late day joins the runs on both sides`() {
        assertEquals(2, BadgeEvaluator.runContaining(11, setOf(10L, 11L, 13L)))
        assertEquals(4, BadgeEvaluator.runContaining(12, setOf(10L, 11L, 12L, 13L)))
        assertEquals(0, BadgeEvaluator.runContaining(5, setOf(10L)))
    }

    @Test
    fun `the current streak survives until the end of the day after the last activity`() {
        val days = setOf(7L, 8L, 9L)
        assertEquals(3, BadgeEvaluator.currentStreak(today = 9, activeDays = days))
        assertEquals(3, BadgeEvaluator.currentStreak(today = 10, activeDays = days))
        assertEquals(0, BadgeEvaluator.currentStreak(today = 11, activeDays = days))
    }
}
