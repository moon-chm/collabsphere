package dto

import kotlinx.serialization.Serializable

/** The authenticated user's career standing. All SP values are integers; nothing here is client-writable. */
@Serializable
data class ProgressionSummaryResponse(
    val userId: Int,
    val totalSp: Long,
    val level: Int,
    val maxLevel: Int,
    val title: String,
    val levelRequiredSp: Long,
    val nextLevel: Int?,
    val nextTitle: String?,
    val nextLevelRequiredSp: Long?,
    /** SP earned since reaching the current level. */
    val spIntoLevel: Long,
    /** SP between the current and next level; 0 at the maximum level. */
    val spSpanToNext: Long,
    val isMaxLevel: Boolean,
    val currentStreakDays: Int,
    val bestStreakDays: Long,
    val catalogVersion: Int,
    /** Highest ledger id, so a client can tell whether anything new was earned since it last looked. */
    val lastLedgerId: Long
)

@Serializable
data class BadgeUnlockResponse(
    val tier: String,
    val unlockedAt: Long
)

@Serializable
data class BadgeProgressResponse(
    val familyKey: String,
    val available: Boolean,
    val counterValue: Long,
    val unlocked: List<BadgeUnlockResponse>,
    /** Null once Gold is earned. */
    val nextTier: String?,
    val nextThreshold: Long?
)

@Serializable
data class LedgerEntryResponse(
    val id: Long,
    val kind: String,
    val ruleKey: String?,
    val amount: Int,
    val reason: String?,
    val eventType: String?,
    val referenceType: String?,
    val referenceId: String?,
    val workspaceId: Int?,
    val createdAt: Long
)

@Serializable
data class LedgerPageResponse(
    val entries: List<LedgerEntryResponse>,
    /** Pass as `beforeId` for the next (older) page; null when there are no more entries. */
    val nextBeforeId: Long?
)

@Serializable
data class BadgeEvidenceResponse(
    val eventType: String,
    val referenceType: String,
    val referenceId: String,
    val workspaceId: Int?,
    val occurredAt: Long
)

@Serializable
data class TimeZoneRequest(val timeZone: String)

@Serializable
data class HelpfulMarkResponse(val alreadyMarked: Boolean)

/** Whether the user has a verified GitHub identity, used to credit their GitHub contributions. */
@Serializable
data class GitHubIdentityResponse(
    val linked: Boolean,
    val login: String? = null
)

/** Earned badges shown on a public profile: family and highest tier only, never the evidence. */
@Serializable
data class PublicBadge(
    val familyKey: String,
    val tier: String
)
