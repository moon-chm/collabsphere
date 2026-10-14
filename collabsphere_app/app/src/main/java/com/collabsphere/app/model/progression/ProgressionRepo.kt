package com.collabsphere.app.model.progression

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.collabsphere.app.AuthTokenHolder
import com.collabsphere.app.MyApplication
import com.collabsphere.app.NotificationCenter
import com.collabsphere.app.UserPreferences
import com.collabsphere.app.dto.progression.BadgeEvidenceResponse
import com.collabsphere.app.dto.progression.BadgeProgressResponse
import com.collabsphere.app.dto.progression.LedgerPageResponse
import com.collabsphere.app.dto.progression.ProgressionCatalogDto
import com.collabsphere.app.dto.progression.ProgressionSummaryResponse
import com.collabsphere.app.remote.progression.ProgressionApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.TimeZone

/**
 * Offline-first view of the user's career progression. The server is the only authority: this repo
 * downloads its summary/badges/catalog into Room for offline display and never computes, submits
 * or grants SP, levels or badges itself. Qualifying work reaches the server through the existing
 * task/note sync paths, which is where rewards are applied (idempotently).
 */
class ProgressionRepo(
    private val dao: ProgressionDao,
    private val api: ProgressionApiService,
    private val dataStore: DataStore<Preferences>,
    private val userPreferences: UserPreferences
) {
    companion object {
        private const val TAG = "ProgressionRepo"
        /** Unforced refreshes closer together than this reuse the cached state. */
        private const val MIN_REFRESH_INTERVAL_MS = 15_000L
        private val TIME_ZONE_SENT = stringPreferencesKey("progression_time_zone_sent")
        private fun seenLevelKey(userId: Int) = intPreferencesKey("progression_seen_level_$userId")
        private fun seenBadgesKey(userId: Int) = stringSetPreferencesKey("progression_seen_badges_$userId")
        private val PROGRESSION_NOTIFICATION_TYPES = setOf("LEVEL_UP", "BADGE_UNLOCKED")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val refreshMutex = Mutex()
    @Volatile private var lastRefreshAt = 0L
    @Volatile private var autoRefreshStarted = false

    fun observeSummary(userId: Int): Flow<ProgressionSummaryEntity?> = dao.observeSummary(userId)

    fun observeBadges(userId: Int): Flow<List<BadgeProgressEntity>> = dao.observeBadges(userId)

    fun observeCatalog(): Flow<ProgressionCatalogDto?> = dao.observeCatalog().map { it?.let(::decodeCatalog) }

    /** Null until the first successful refresh has recorded a baseline. */
    fun observeSeen(userId: Int): Flow<SeenProgress?> = dataStore.data.map { prefs ->
        prefs[seenLevelKey(userId)]?.let { level -> SeenProgress(level, prefs[seenBadgesKey(userId)].orEmpty()) }
    }

    /** Records that [feedback] was shown, so it is never shown again for this user. */
    suspend fun acknowledge(userId: Int, feedback: AchievementFeedback) {
        dataStore.edit { prefs ->
            val current = SeenProgress(prefs[seenLevelKey(userId)] ?: 1, prefs[seenBadgesKey(userId)].orEmpty())
            val next = ProgressionFeedback.markSeen(current, feedback)
            prefs[seenLevelKey(userId)] = next.level
            prefs[seenBadgesKey(userId)] = next.badgeKeys
        }
    }

    /**
     * Listens for nudges (task/note syncs, foreground) and for level-up/badge notifications pushed by
     * the server, and refreshes the cached state. Safe to call more than once.
     */
    fun startAutoRefresh() {
        if (autoRefreshStarted) return
        autoRefreshStarted = true
        MyApplication.appScope.launch {
            merge(
                ProgressionEvents.requests.map { false },
                NotificationCenter.incoming.filter { it.type in PROGRESSION_NOTIFICATION_TYPES }.map { true }
            ).collect { force ->
                val userId = userPreferences.userIdFlow.first()
                if (userId > 0 && AuthTokenHolder.token != null) refresh(userId, force)
            }
        }
    }

    suspend fun refresh(userId: Int, force: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        if (userId <= 0) return@withContext Result.failure(IllegalArgumentException("No signed-in user"))
        refreshMutex.withLock {
            val now = System.currentTimeMillis()
            if (!force && now - lastRefreshAt < MIN_REFRESH_INTERVAL_MS && dao.getSummary(userId) != null) {
                return@withLock Result.success(Unit)
            }
            try {
                syncTimeZoneIfChanged()

                val cachedCatalog = dao.getCatalog()
                val fresh = api.getCatalog(cachedCatalog?.etag)
                val catalog = if (fresh != null) {
                    val (dto, etag) = fresh
                    dao.upsertCatalog(ProgressionCatalogEntity(version = dto.version, etag = etag, json = json.encodeToString(ProgressionCatalogDto.serializer(), dto)))
                    dto
                } else {
                    cachedCatalog?.let(::decodeCatalog)
                }

                val summary = api.getSummary()
                val badges = api.getBadges()
                // Never cache another account's data under this user (e.g. a token swap mid-request).
                if (summary.userId != userId) return@withLock Result.failure(IllegalStateException("Summary for a different user"))

                val order = catalog?.badges?.mapIndexed { index, family -> family.key to index }?.toMap().orEmpty()
                val badgeEntities = badges.map { it.toEntity(userId, order[it.familyKey] ?: Int.MAX_VALUE) }
                dao.replaceProgress(summary.toEntity(now), userId, badgeEntities)
                lastRefreshAt = now
                recordBaselineIfFirstSync(userId, summary, badgeEntities)
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Progression refresh failed; keeping cached state", e)
                Result.failure(e)
            }
        }
    }

    suspend fun loadHistory(beforeId: Long?): Result<LedgerPageResponse> = withContext(Dispatchers.IO) {
        runCatchingNonCancellation { api.getHistory(beforeId) }
    }

    suspend fun loadEvidence(familyKey: String): Result<List<BadgeEvidenceResponse>> = withContext(Dispatchers.IO) {
        runCatchingNonCancellation { api.getEvidence(familyKey) }
    }

    /**
     * The first successful sync on a device establishes what counts as "already seen", so progress
     * earned before this device installed the feature is not replayed as a wall of celebrations.
     */
    private suspend fun recordBaselineIfFirstSync(userId: Int, summary: ProgressionSummaryResponse, badges: List<BadgeProgressEntity>) {
        dataStore.edit { prefs ->
            if (prefs[seenLevelKey(userId)] == null) {
                prefs[seenLevelKey(userId)] = summary.level
                prefs[seenBadgesKey(userId)] = ProgressionFeedback.unlockedKeys(badges)
            }
        }
    }

    /** Streaks and daily caps use the user's own calendar day, so the server needs the device's zone. */
    private suspend fun syncTimeZoneIfChanged() {
        val zone = TimeZone.getDefault().id // java.util: java.time needs API 26 (minSdk is 24)
        if (dataStore.data.first()[TIME_ZONE_SENT] == zone) return
        try {
            api.updateTimeZone(zone)
            dataStore.edit { it[TIME_ZONE_SENT] = zone }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not report time zone; will retry on next refresh", e)
        }
    }

    private fun decodeCatalog(entity: ProgressionCatalogEntity): ProgressionCatalogDto? =
        runCatching { json.decodeFromString(ProgressionCatalogDto.serializer(), entity.json) }.getOrNull()

    private inline fun <T> runCatchingNonCancellation(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal fun ProgressionSummaryResponse.toEntity(fetchedAt: Long) = ProgressionSummaryEntity(
    userId = userId,
    totalSp = totalSp,
    level = level,
    maxLevel = maxLevel,
    title = title,
    levelRequiredSp = levelRequiredSp,
    nextLevel = nextLevel,
    nextTitle = nextTitle,
    nextLevelRequiredSp = nextLevelRequiredSp,
    spIntoLevel = spIntoLevel,
    spSpanToNext = spSpanToNext,
    isMaxLevel = isMaxLevel,
    currentStreakDays = currentStreakDays,
    bestStreakDays = bestStreakDays,
    lastLedgerId = lastLedgerId,
    fetchedAt = fetchedAt
)

internal fun BadgeProgressResponse.toEntity(userId: Int, sortOrder: Int): BadgeProgressEntity {
    val unlockedAt = unlocked.associate { it.tier to it.unlockedAt }
    return BadgeProgressEntity(
        userId = userId,
        familyKey = familyKey,
        available = available,
        counterValue = counterValue,
        bronzeUnlockedAt = unlockedAt["BRONZE"],
        silverUnlockedAt = unlockedAt["SILVER"],
        goldUnlockedAt = unlockedAt["GOLD"],
        nextTier = nextTier,
        nextThreshold = nextThreshold,
        sortOrder = sortOrder
    )
}
