package com.collabsphere.app.model.progression

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Last server-confirmed career summary, cached so the profile shows progression offline. */
@Entity(tableName = "progression_summary")
data class ProgressionSummaryEntity(
    @PrimaryKey val userId: Int,
    val totalSp: Long,
    val level: Int,
    val maxLevel: Int,
    val title: String,
    val levelRequiredSp: Long,
    val nextLevel: Int?,
    val nextTitle: String?,
    val nextLevelRequiredSp: Long?,
    val spIntoLevel: Long,
    val spSpanToNext: Long,
    val isMaxLevel: Boolean,
    val currentStreakDays: Int,
    val bestStreakDays: Long,
    val lastLedgerId: Long,
    val fetchedAt: Long
)

/** The server's level/badge catalog as JSON, keyed by its ETag so it is only re-downloaded when it changes. */
@Entity(tableName = "progression_catalog")
data class ProgressionCatalogEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val version: Int,
    val etag: String?,
    val json: String
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

/** Per-family badge progress for one user; tier timestamps are null until that tier is unlocked. */
@Entity(tableName = "badge_progress", primaryKeys = ["userId", "familyKey"])
data class BadgeProgressEntity(
    val userId: Int,
    val familyKey: String,
    val available: Boolean,
    val counterValue: Long,
    val bronzeUnlockedAt: Long?,
    val silverUnlockedAt: Long?,
    val goldUnlockedAt: Long?,
    val nextTier: String?,
    val nextThreshold: Long?,
    /** Catalog order, so the grid is stable. */
    val sortOrder: Int
)
