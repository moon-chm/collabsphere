package com.collabsphere.app.model.progression

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressionDao {

    @Query("SELECT * FROM progression_summary WHERE userId = :userId LIMIT 1")
    fun observeSummary(userId: Int): Flow<ProgressionSummaryEntity?>

    @Query("SELECT * FROM progression_summary WHERE userId = :userId LIMIT 1")
    suspend fun getSummary(userId: Int): ProgressionSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSummary(summary: ProgressionSummaryEntity)

    @Query("SELECT * FROM progression_catalog WHERE id = 0 LIMIT 1")
    fun observeCatalog(): Flow<ProgressionCatalogEntity?>

    @Query("SELECT * FROM progression_catalog WHERE id = 0 LIMIT 1")
    suspend fun getCatalog(): ProgressionCatalogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCatalog(catalog: ProgressionCatalogEntity)

    @Query("SELECT * FROM badge_progress WHERE userId = :userId ORDER BY sortOrder")
    fun observeBadges(userId: Int): Flow<List<BadgeProgressEntity>>

    @Query("SELECT * FROM badge_progress WHERE userId = :userId ORDER BY sortOrder")
    suspend fun getBadges(userId: Int): List<BadgeProgressEntity>

    @Query("DELETE FROM badge_progress WHERE userId = :userId")
    suspend fun deleteBadges(userId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBadges(badges: List<BadgeProgressEntity>)

    /** Summary and badges from one server response land together, so the UI never shows a mix. */
    @Transaction
    suspend fun replaceProgress(summary: ProgressionSummaryEntity, userId: Int, badges: List<BadgeProgressEntity>) {
        upsertSummary(summary)
        deleteBadges(userId)
        insertBadges(badges)
    }
}
