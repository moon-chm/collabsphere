package com.collabsphere.app.model.progression

import com.collabsphere.app.dto.progression.BadgeFamilyDto
import com.collabsphere.app.dto.progression.CareerLevelDto
import com.collabsphere.app.dto.progression.ProgressionCatalogDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionFeedbackTest {

    private val catalog = ProgressionCatalogDto(
        version = 1,
        levels = listOf(
            CareerLevelDto(1, "Graduate Trainee", 0),
            CareerLevelDto(2, "Junior Developer", 100),
            CareerLevelDto(3, "Associate Software Engineer", 250),
            CareerLevelDto(4, "Software Engineer I", 500)
        ),
        badges = listOf(
            BadgeFamilyDto("TASK_TITAN", "Task Titan", "Task Completion", "Complete tasks", "TASKS_COMPLETED", 10, 50, 200, true),
            BadgeFamilyDto("GUARDIAN", "Guardian", "Quality", "Fix bugs", "QUALITY_IMPROVEMENTS", 5, 20, 50, true)
        )
    )

    private fun summary(level: Int, title: String) = ProgressionSummaryEntity(
        userId = 1, totalSp = 0, level = level, maxLevel = 20, title = title, levelRequiredSp = 0,
        nextLevel = null, nextTitle = null, nextLevelRequiredSp = null, spIntoLevel = 0, spSpanToNext = 0,
        isMaxLevel = false, currentStreakDays = 0, bestStreakDays = 0, lastLedgerId = 0, fetchedAt = 0
    )

    private fun badge(key: String, bronze: Long? = null, silver: Long? = null, gold: Long? = null) = BadgeProgressEntity(
        userId = 1, familyKey = key, available = true, counterValue = 0,
        bronzeUnlockedAt = bronze, silverUnlockedAt = silver, goldUnlockedAt = gold,
        nextTier = null, nextThreshold = null, sortOrder = 0
    )

    @Test
    fun `nothing is owed when the seen state matches the server`() {
        val seen = SeenProgress(2, setOf("TASK_TITAN:BRONZE"))
        val pending = ProgressionFeedback.pending(seen, summary(2, "Junior Developer"), listOf(badge("TASK_TITAN", bronze = 1)), catalog)
        assertTrue(pending.isEmpty())
    }

    @Test
    fun `crossing several levels is one consolidated celebration`() {
        val pending = ProgressionFeedback.pending(SeenProgress(1, emptySet()), summary(4, "Software Engineer I"), emptyList(), catalog)
        assertEquals(1, pending.size)
        assertEquals("Promoted 3 levels to Software Engineer I (Level 4)", pending.single().message)
        assertEquals(4, pending.single().level)
    }

    @Test
    fun `a single promotion names the new designation`() {
        val pending = ProgressionFeedback.pending(SeenProgress(1, emptySet()), summary(2, "Junior Developer"), emptyList(), catalog)
        assertEquals("Promoted to Junior Developer (Level 2)", pending.single().message)
    }

    @Test
    fun `new badge tiers are announced individually or consolidated when many`() {
        val two = ProgressionFeedback.pending(
            SeenProgress(1, emptySet()), summary(1, "Graduate Trainee"),
            listOf(badge("TASK_TITAN", bronze = 1), badge("GUARDIAN", bronze = 1)), catalog
        )
        assertEquals(listOf("Badge unlocked: Bronze Guardian", "Badge unlocked: Bronze Task Titan"), two.map { it.message })

        val many = ProgressionFeedback.pending(
            SeenProgress(1, emptySet()), summary(1, "Graduate Trainee"),
            listOf(badge("TASK_TITAN", bronze = 1, silver = 2, gold = 3)), catalog
        )
        assertEquals(listOf("3 new badges unlocked"), many.map { it.message })
        assertEquals(setOf("TASK_TITAN:BRONZE", "TASK_TITAN:SILVER", "TASK_TITAN:GOLD"), many.single().badgeKeys)
    }

    @Test
    fun `acknowledging a celebration means a replay of the same server state shows nothing`() {
        val badges = listOf(badge("TASK_TITAN", bronze = 1))
        var seen = SeenProgress(1, emptySet())
        ProgressionFeedback.pending(seen, summary(2, "Junior Developer"), badges, catalog).forEach {
            seen = ProgressionFeedback.markSeen(seen, it)
        }
        assertTrue(ProgressionFeedback.pending(seen, summary(2, "Junior Developer"), badges, catalog).isEmpty())
        assertEquals(2, seen.level)
    }

    @Test
    fun `marking an older celebration seen never lowers the recorded level`() {
        val seen = ProgressionFeedback.markSeen(SeenProgress(5, emptySet()), AchievementFeedback("old", level = 3))
        assertEquals(5, seen.level)
    }
}
