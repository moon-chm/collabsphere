package plugins.progression

import com.collabsphere.model.*
import dto.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import java.time.ZoneId
import java.time.ZoneOffset

/** Read models for the progression API. Must run inside a transaction. */
object ProgressionQueries {

    fun summary(userId: Int, catalog: ProgressionCatalog = ProgressionCatalogs.current, now: Long = System.currentTimeMillis()): ProgressionSummaryResponse {
        val totalSp = UserProgressionTable.select(UserProgressionTable.totalSp)
            .where { UserProgressionTable.userId eq userId }
            .singleOrNull()?.get(UserProgressionTable.totalSp) ?: 0L
        val progress = LevelCalculator(catalog.levels).progress(totalSp)

        val zone = UsersTable.select(UsersTable.timeZone).where { UsersTable.id eq userId }
            .singleOrNull()?.get(UsersTable.timeZone)
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC
        val today = ProgressionService.epochDay(now, zone)
        // A live streak can only reach back as far as consecutive days exist; a year bounds the scan.
        val recentDays = UserActivityDaysTable.select(UserActivityDaysTable.day)
            .where { (UserActivityDaysTable.userId eq userId) and (UserActivityDaysTable.day greaterEq (today - 400)) }
            .map { it[UserActivityDaysTable.day] }
            .toSet()

        val lastLedgerId = SpLedgerTable.id.max()
        return ProgressionSummaryResponse(
            userId = userId,
            totalSp = totalSp,
            level = progress.level.level,
            maxLevel = catalog.maxLevel.level,
            title = progress.level.title,
            levelRequiredSp = progress.level.requiredSp,
            nextLevel = progress.next?.level,
            nextTitle = progress.next?.title,
            nextLevelRequiredSp = progress.next?.requiredSp,
            spIntoLevel = progress.spIntoLevel,
            spSpanToNext = progress.spSpanToNext,
            isMaxLevel = progress.isMaxLevel,
            currentStreakDays = BadgeEvaluator.currentStreak(today, recentDays),
            bestStreakDays = counters(userId)[BadgeCounters.STREAK_DAYS] ?: 0L,
            catalogVersion = catalog.version,
            lastLedgerId = SpLedgerTable.select(lastLedgerId)
                .where { SpLedgerTable.userId eq userId }
                .singleOrNull()?.get(lastLedgerId) ?: 0L
        )
    }

    fun badges(userId: Int, catalog: ProgressionCatalog = ProgressionCatalogs.current): List<BadgeProgressResponse> {
        val counterValues = counters(userId)
        val unlocks = UserBadgeUnlocksTable.selectAll()
            .where { UserBadgeUnlocksTable.userId eq userId }
            .groupBy({ it[UserBadgeUnlocksTable.familyKey] }) {
                BadgeUnlockResponse(it[UserBadgeUnlocksTable.tier], it[UserBadgeUnlocksTable.unlockedAt])
            }
        return catalog.badges.map { family ->
            val count = counterValues[family.counter] ?: 0L
            val next = BadgeEvaluator.nextTier(family, count)
            BadgeProgressResponse(
                familyKey = family.key,
                available = family.available,
                counterValue = count,
                unlocked = (unlocks[family.key] ?: emptyList()).sortedBy { BadgeTier.valueOf(it.tier).ordinal },
                nextTier = next?.first?.name,
                nextThreshold = next?.second
            )
        }
    }

    fun history(userId: Int, beforeId: Long?, limit: Int): LedgerPageResponse {
        val rows = (SpLedgerTable leftJoin ContributionEventsTable)
            .selectAll()
            .where {
                (SpLedgerTable.userId eq userId) and
                    (if (beforeId != null) SpLedgerTable.id less beforeId else Op.TRUE)
            }
            .orderBy(SpLedgerTable.id, SortOrder.DESC)
            .limit(limit + 1)
            .toList()
        val page = rows.take(limit).map {
            LedgerEntryResponse(
                id = it[SpLedgerTable.id],
                kind = it[SpLedgerTable.kind],
                ruleKey = it[SpLedgerTable.ruleKey],
                amount = it[SpLedgerTable.amount],
                reason = it[SpLedgerTable.reason],
                eventType = it.getOrNull(ContributionEventsTable.eventType),
                referenceType = it.getOrNull(ContributionEventsTable.referenceType),
                referenceId = it.getOrNull(ContributionEventsTable.referenceId),
                workspaceId = it.getOrNull(ContributionEventsTable.workspaceId),
                createdAt = it[SpLedgerTable.createdAt]
            )
        }
        return LedgerPageResponse(page, if (rows.size > limit) page.last().id else null)
    }

    /** The user's own events that advanced a family's counter, newest first. Derived families have none. */
    fun evidence(userId: Int, family: BadgeFamily, limit: Int = 50): List<BadgeEvidenceResponse> =
        ContributionEventsTable.selectAll()
            .where {
                (ContributionEventsTable.userId eq userId) and
                    (ContributionEventsTable.status eq ProgressionService.STATUS_REWARDED)
            }
            .orderBy(ContributionEventsTable.id, SortOrder.DESC)
            .asSequence()
            .filter { family.counter in it[ContributionEventsTable.counters].split(",") }
            .take(limit)
            .map {
                BadgeEvidenceResponse(
                    eventType = it[ContributionEventsTable.eventType],
                    referenceType = it[ContributionEventsTable.referenceType],
                    referenceId = it[ContributionEventsTable.referenceId],
                    workspaceId = it[ContributionEventsTable.workspaceId],
                    occurredAt = it[ContributionEventsTable.occurredAt]
                )
            }
            .toList()

    /** Level and highest earned tier per family, for another user's public profile. */
    fun publicSummary(userId: Int, catalog: ProgressionCatalog = ProgressionCatalogs.current): Pair<CareerLevel, List<PublicBadge>> {
        val totalSp = UserProgressionTable.select(UserProgressionTable.totalSp)
            .where { UserProgressionTable.userId eq userId }
            .singleOrNull()?.get(UserProgressionTable.totalSp) ?: 0L
        val families = catalog.badges.associateBy { it.key }
        val badges = UserBadgeUnlocksTable.select(UserBadgeUnlocksTable.familyKey, UserBadgeUnlocksTable.tier)
            .where { UserBadgeUnlocksTable.userId eq userId }
            .groupBy({ it[UserBadgeUnlocksTable.familyKey] }) { BadgeTier.valueOf(it[UserBadgeUnlocksTable.tier]) }
            .filterKeys { it in families }
            .map { (key, tiers) -> PublicBadge(key, tiers.max().name) }
            .sortedBy { badge -> catalog.badges.indexOfFirst { it.key == badge.familyKey } }
        return LevelCalculator(catalog.levels).levelFor(totalSp) to badges
    }

    private fun counters(userId: Int): Map<String, Long> =
        UserBadgeCountersTable.selectAll()
            .where { UserBadgeCountersTable.userId eq userId }
            .associate { it[UserBadgeCountersTable.counter] to it[UserBadgeCountersTable.value] }
}
