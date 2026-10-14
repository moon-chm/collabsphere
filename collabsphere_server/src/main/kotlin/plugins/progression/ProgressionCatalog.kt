package plugins.progression

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The career-progression configuration: level designations and thresholds, reward rule values,
 * anti-abuse policy and badge families. It lives in `progression-catalog.json` so values change
 * through configuration rather than code; [ProgressionCatalog.version] is stamped on every user's
 * progression row so a changed catalog triggers a one-time level recalculation at boot.
 */
@Serializable
data class ProgressionCatalog(
    val version: Int,
    val levels: List<CareerLevel>,
    val rules: List<RewardRule>,
    val policy: ProgressionPolicy,
    val badges: List<BadgeFamily>
) {
    private val rulesByKey by lazy { rules.associateBy { it.key } }

    fun rule(key: String): RewardRule =
        rulesByKey[key] ?: error("Reward rule $key is not configured")

    val maxLevel: CareerLevel get() = levels.last()
}

@Serializable
data class CareerLevel(val level: Int, val title: String, val requiredSp: Long)

@Serializable
data class RewardRule(
    val key: String,
    val sp: Int,
    /** Maximum rewarded occurrences per user per local day; null means uncapped. */
    val dailyCap: Int? = null,
    val description: String
)

@Serializable
data class ProgressionPolicy(
    val complexPriority: String,
    val complexMinChecklistItems: Int,
    val reliabilityLabels: List<String>,
    /** A due date the assignee set themselves only counts for on-time credit if set this long before completion. */
    val onTimeSelfSetMinLeadMs: Long,
    val noteMinLength: Int
)

@Serializable
data class BadgeFamily(
    val key: String,
    val name: String,
    val theme: String,
    val requirement: String,
    /** The counter (see [BadgeCounters]) whose value is compared against the tier thresholds. */
    val counter: String,
    val bronze: Long,
    val silver: Long,
    val gold: Long,
    /** Feature flag: an unavailable family is listed but cannot be earned yet. */
    val available: Boolean
) {
    fun threshold(tier: BadgeTier): Long = when (tier) {
        BadgeTier.BRONZE -> bronze
        BadgeTier.SILVER -> silver
        BadgeTier.GOLD -> gold
    }
}

enum class BadgeTier { BRONZE, SILVER, GOLD }

/** Counter keys referenced by badge families. Derived counters are computed, never incremented directly. */
object BadgeCounters {
    const val ACCEPTED_CODE_CONTRIBUTIONS = "ACCEPTED_CODE_CONTRIBUTIONS"
    const val ON_TIME_COMPLETIONS = "ON_TIME_COMPLETIONS"
    const val COLLABORATIVE_ACTIVITIES = "COLLABORATIVE_ACTIVITIES"
    const val TASKS_COMPLETED = "TASKS_COMPLETED"
    const val STREAK_DAYS = "STREAK_DAYS"
    const val ISSUES_RESOLVED = "ISSUES_RESOLVED"
    const val KNOWLEDGE_CONTRIBUTIONS = "KNOWLEDGE_CONTRIBUTIONS"
    const val MILESTONES_COMPLETED = "MILESTONES_COMPLETED"
    const val QUALITY_IMPROVEMENTS = "QUALITY_IMPROVEMENTS"
    const val PRS_MERGED_AS_MAINTAINER = "PRS_MERGED_AS_MAINTAINER"
    const val HELPFUL_MESSAGES = "HELPFUL_MESSAGES"
    const val HELPFUL_RECOGNIZERS = "HELPFUL_RECOGNIZERS"
    const val RELEASES_PUBLISHED = "RELEASES_PUBLISHED"
    const val DISTINCT_WORKSPACES = "DISTINCT_WORKSPACES"
    const val DISTINCT_BADGE_FAMILIES = "DISTINCT_BADGE_FAMILIES"
}

object ProgressionCatalogs {
    private val json = Json { ignoreUnknownKeys = true }

    /** The deployed catalog, read once from the classpath. */
    val current: ProgressionCatalog by lazy {
        val text = ProgressionCatalogs::class.java.getResource("/progression-catalog.json")?.readText()
            ?: error("progression-catalog.json is missing from the server resources")
        parse(text)
    }

    fun parse(text: String): ProgressionCatalog = json.decodeFromString(ProgressionCatalog.serializer(), text).also(::validate)

    /** Rejects a catalog that would make level or badge evaluation ambiguous. */
    fun validate(catalog: ProgressionCatalog) {
        require(catalog.levels.isNotEmpty()) { "At least one career level is required" }
        catalog.levels.forEachIndexed { index, level ->
            require(level.level == index + 1) { "Career levels must be numbered 1..n in order (found ${level.level} at position ${index + 1})" }
        }
        require(catalog.levels.first().requiredSp == 0L) { "Level 1 must start at 0 SP" }
        catalog.levels.zipWithNext().forEach { (lower, higher) ->
            require(higher.requiredSp > lower.requiredSp) { "Level ${higher.level} must require more SP than level ${lower.level}" }
        }
        require(catalog.rules.map { it.key }.toSet().size == catalog.rules.size) { "Reward rule keys must be unique" }
        catalog.rules.forEach { rule ->
            require(rule.sp >= 0) { "Rule ${rule.key} must not award negative SP" }
            require(rule.dailyCap == null || rule.dailyCap > 0) { "Rule ${rule.key} daily cap must be positive" }
        }
        require(catalog.badges.map { it.key }.toSet().size == catalog.badges.size) { "Badge family keys must be unique" }
        catalog.badges.forEach { family ->
            require(family.bronze in 1 until family.silver && family.silver < family.gold) {
                "Badge ${family.key} tiers must satisfy 0 < bronze < silver < gold"
            }
        }
    }
}
