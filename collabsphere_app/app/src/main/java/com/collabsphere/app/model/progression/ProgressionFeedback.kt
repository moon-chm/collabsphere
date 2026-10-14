package com.collabsphere.app.model.progression

import com.collabsphere.app.dto.progression.CareerLevelDto
import com.collabsphere.app.dto.progression.ProgressionCatalogDto

/** A celebration to show once: a promotion (possibly several levels) or newly earned badge tiers. */
data class AchievementFeedback(
    val message: String,
    /** Level reached (for level-ups) — recorded as seen once shown. */
    val level: Int? = null,
    /** "FAMILY:TIER" keys covered by this message — recorded as seen once shown. */
    val badgeKeys: Set<String> = emptySet()
)

/** What the user has already been shown, persisted so replays and restarts never repeat a celebration. */
data class SeenProgress(val level: Int, val badgeKeys: Set<String>)

object ProgressionFeedback {
    private const val MAX_INDIVIDUAL_BADGE_MESSAGES = 2

    fun badgeKey(familyKey: String, tier: String) = "$familyKey:$tier"

    fun unlockedKeys(badges: List<BadgeProgressEntity>): Set<String> = badges.flatMap { badge ->
        listOfNotNull(
            badge.bronzeUnlockedAt?.let { badgeKey(badge.familyKey, "BRONZE") },
            badge.silverUnlockedAt?.let { badgeKey(badge.familyKey, "SILVER") },
            badge.goldUnlockedAt?.let { badgeKey(badge.familyKey, "GOLD") }
        )
    }.toSet()

    /**
     * Celebrations owed for server state the user has not seen yet. Pure: the same inputs always give
     * the same answer, so a replayed server event that changes nothing produces nothing.
     */
    fun pending(
        seen: SeenProgress,
        summary: ProgressionSummaryEntity?,
        badges: List<BadgeProgressEntity>,
        catalog: ProgressionCatalogDto?
    ): List<AchievementFeedback> {
        val feedback = mutableListOf<AchievementFeedback>()
        if (summary != null && summary.level > seen.level) {
            val crossed: List<CareerLevelDto> = catalog?.levels?.filter { it.level in (seen.level + 1)..summary.level }.orEmpty()
            val message = if (crossed.size > 1) {
                "Promoted ${crossed.size} levels to ${summary.title} (Level ${summary.level})"
            } else {
                "Promoted to ${summary.title} (Level ${summary.level})"
            }
            feedback += AchievementFeedback(message, level = summary.level)
        }

        val newKeys = unlockedKeys(badges) - seen.badgeKeys
        if (newKeys.isNotEmpty()) {
            val names = catalog?.badges?.associate { it.key to it.name }.orEmpty()
            fun describe(key: String): String {
                val (family, tier) = key.split(":", limit = 2)
                return "${tier.lowercase().replaceFirstChar { it.uppercase() }} ${names[family] ?: family}"
            }
            if (newKeys.size <= MAX_INDIVIDUAL_BADGE_MESSAGES) {
                newKeys.sorted().forEach { feedback += AchievementFeedback("Badge unlocked: ${describe(it)}", badgeKeys = setOf(it)) }
            } else {
                feedback += AchievementFeedback("${newKeys.size} new badges unlocked", badgeKeys = newKeys)
            }
        }
        return feedback
    }

    fun markSeen(seen: SeenProgress, shown: AchievementFeedback): SeenProgress = SeenProgress(
        level = maxOf(seen.level, shown.level ?: seen.level),
        badgeKeys = seen.badgeKeys + shown.badgeKeys
    )
}
