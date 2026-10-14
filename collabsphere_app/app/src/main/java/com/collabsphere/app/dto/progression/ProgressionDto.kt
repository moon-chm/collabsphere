package com.collabsphere.app.dto.progression

import kotlinx.serialization.Serializable

/** Server-authoritative career standing. The app never computes or submits SP, levels or badges. */
@Serializable
data class ProgressionSummaryResponse(
    val userId: Int,
    val totalSp: Long,
    val level: Int,
    val maxLevel: Int,
    val title: String,
    val levelRequiredSp: Long,
    val nextLevel: Int? = null,
    val nextTitle: String? = null,
    val nextLevelRequiredSp: Long? = null,
    val spIntoLevel: Long,
    val spSpanToNext: Long,
    val isMaxLevel: Boolean,
    val currentStreakDays: Int = 0,
    val bestStreakDays: Long = 0,
    val catalogVersion: Int,
    val lastLedgerId: Long = 0
)

@Serializable
data class ProgressionCatalogDto(
    val version: Int,
    val levels: List<CareerLevelDto>,
    val rules: List<RewardRuleDto> = emptyList(),
    val badges: List<BadgeFamilyDto>
)

@Serializable
data class CareerLevelDto(val level: Int, val title: String, val requiredSp: Long)

@Serializable
data class RewardRuleDto(val key: String, val sp: Int, val dailyCap: Int? = null, val description: String)

@Serializable
data class BadgeFamilyDto(
    val key: String,
    val name: String,
    val theme: String,
    val requirement: String,
    val counter: String,
    val bronze: Long,
    val silver: Long,
    val gold: Long,
    val available: Boolean
)

@Serializable
data class BadgeUnlockResponse(val tier: String, val unlockedAt: Long)

@Serializable
data class BadgeProgressResponse(
    val familyKey: String,
    val available: Boolean,
    val counterValue: Long,
    val unlocked: List<BadgeUnlockResponse> = emptyList(),
    val nextTier: String? = null,
    val nextThreshold: Long? = null
)

@Serializable
data class LedgerEntryResponse(
    val id: Long,
    val kind: String,
    val ruleKey: String? = null,
    val amount: Int,
    val reason: String? = null,
    val eventType: String? = null,
    val referenceType: String? = null,
    val referenceId: String? = null,
    val workspaceId: Int? = null,
    val createdAt: Long
)

@Serializable
data class LedgerPageResponse(
    val entries: List<LedgerEntryResponse>,
    val nextBeforeId: Long? = null
)

@Serializable
data class BadgeEvidenceResponse(
    val eventType: String,
    val referenceType: String,
    val referenceId: String,
    val workspaceId: Int? = null,
    val occurredAt: Long
)

@Serializable
data class TimeZoneRequest(val timeZone: String)

@Serializable
data class PublicBadge(val familyKey: String, val tier: String)

/** Whether the user has a verified GitHub identity (used to credit their GitHub contributions). */
@Serializable
data class GitHubIdentityResponse(val linked: Boolean, val login: String? = null)

@Serializable
data class GitHubLinkUrlResponse(val url: String)

@Serializable
data class HelpfulMarkResponse(val alreadyMarked: Boolean)
