package com.collabsphere.app.viewmodel.progression

import com.collabsphere.app.dto.progression.BadgeFamilyDto
import com.collabsphere.app.model.progression.BadgeProgressEntity
import com.collabsphere.app.model.progression.ProgressionSummaryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CareerUiStateTest {

    private fun summary(spInto: Long, span: Long, max: Boolean) = ProgressionSummaryEntity(
        userId = 1, totalSp = 0, level = if (max) 20 else 3, maxLevel = 20, title = "t", levelRequiredSp = 0,
        nextLevel = if (max) null else 4, nextTitle = if (max) null else "n", nextLevelRequiredSp = if (max) null else 500,
        spIntoLevel = spInto, spSpanToNext = span, isMaxLevel = max, currentStreakDays = 0, bestStreakDays = 0,
        lastLedgerId = 0, fetchedAt = 0
    )

    @Test
    fun `progress is the share of the current level span`() {
        assertEquals(0.2f, CareerUiState(summary = summary(50, 250, max = false)).levelProgress, 0.0001f)
    }

    @Test
    fun `the maximum level shows a full bar without dividing by zero`() {
        assertEquals(1f, CareerUiState(summary = summary(5_000, 0, max = true)).levelProgress, 0f)
    }

    @Test
    fun `no summary means no progress and no data`() {
        val state = CareerUiState()
        assertEquals(0f, state.levelProgress, 0f)
        assertTrue(!state.hasData)
    }

    private val titan = BadgeFamilyDto("TASK_TITAN", "Task Titan", "Task Completion", "Complete tasks", "TASKS_COMPLETED", 10, 50, 200, true)
    private val legend = BadgeFamilyDto("SPHERE_LEGEND", "Sphere Legend", "All-Round", "Earn families", "DISTINCT_BADGE_FAMILIES", 3, 7, 12, true)

    @Test
    fun `families without server progress are locked at zero with bronze next`() {
        val badge = ProgressionViewModel.buildBadges(listOf(titan), emptyList()).single()
        assertTrue(badge.isLocked)
        assertEquals(0L, badge.counterValue)
        assertEquals(BadgeTierUi.BRONZE, badge.nextTier)
        assertEquals(10L, badge.nextThreshold)
    }

    @Test
    fun `server progress maps unlocked tiers and the highest tier`() {
        val progress = BadgeProgressEntity(
            userId = 1, familyKey = "TASK_TITAN", available = true, counterValue = 60,
            bronzeUnlockedAt = 100, silverUnlockedAt = 200, goldUnlockedAt = null,
            nextTier = "GOLD", nextThreshold = 200, sortOrder = 0
        )
        val badges = ProgressionViewModel.buildBadges(listOf(titan, legend), listOf(progress))
        val built = badges.first()
        assertEquals(BadgeTierUi.SILVER, built.highestTier)
        assertEquals(setOf(BadgeTierUi.BRONZE, BadgeTierUi.SILVER), built.unlockedAt.keys)
        assertEquals(BadgeTierUi.GOLD, built.nextTier)
        assertEquals("catalog order is kept", listOf("TASK_TITAN", "SPHERE_LEGEND"), badges.map { it.family.key })
        assertNull(badges[1].highestTier)
    }
}
