package plugins.progression

import com.collabsphere.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A verified activity, described by the server-side code that observed it. Never built from client input. */
data class ContributionEvent(
    /** Identifies the activity itself (e.g. `task-complete:42`), so any replay is recognised. */
    val idempotencyKey: String,
    val userId: Int,
    val eventType: String,
    val source: String,
    val referenceType: String,
    val referenceId: String,
    val workspaceId: Int?,
    /** Authoritative time of the activity. */
    val occurredAt: Long,
    val decision: RewardDecision
)

/** A level-up or badge notification already persisted in the reward transaction, to push after commit. */
data class PendingNotification(
    val recipientId: Int,
    val type: String,
    val title: String,
    val body: String,
    val workspaceId: Int?,
    val dedupeKey: String
)

enum class RecordStatus { REWARDED, CAPPED, DUPLICATE }

data class RecordResult(
    val status: RecordStatus,
    val spAwarded: Int = 0,
    val levelsCrossed: List<CareerLevel> = emptyList(),
    val badgesUnlocked: List<Pair<BadgeFamily, BadgeTier>> = emptyList(),
    val notifications: List<PendingNotification> = emptyList()
)

/**
 * Applies verified activities to a user's progression. Every function must run inside the caller's
 * Exposed transaction — the same one that wrote the activity — so a reward commits or rolls back
 * together with the work that earned it.
 */
object ProgressionService {
    private val logger = LoggerFactory.getLogger(ProgressionService::class.java)

    const val STATUS_REWARDED = "REWARDED"
    const val STATUS_CAPPED = "CAPPED"
    const val KIND_AWARD = "AWARD"
    const val KIND_CORRECTION = "CORRECTION"
    const val NOTIFICATION_LEVEL_UP = "LEVEL_UP"
    const val NOTIFICATION_BADGE = "BADGE_UNLOCKED"

    /** Counters whose value is maintained by the service itself rather than incremented per event. */
    private val DERIVED_COUNTERS = setOf(
        BadgeCounters.STREAK_DAYS,
        BadgeCounters.DISTINCT_WORKSPACES,
        BadgeCounters.DISTINCT_BADGE_FAMILIES
    )

    fun record(
        event: ContributionEvent,
        catalog: ProgressionCatalog = ProgressionCatalogs.current,
        now: Long = System.currentTimeMillis()
    ): RecordResult {
        val rule = catalog.rule(event.decision.ruleKey)
        // Lock the user's progression row before anything else: it serializes all reward work for
        // this user, so daily-cap counts and running totals cannot race. A concurrent duplicate
        // waits here, then sees the committed event below and is skipped.
        val before = lockProgression(event.userId, catalog)

        val activityDay = epochDay(event.occurredAt, zoneFor(event.userId))
        val capped = rule.dailyCap?.let { cap ->
            ContributionEventsTable.selectAll().where {
                (ContributionEventsTable.userId eq event.userId) and
                    (ContributionEventsTable.ruleKey eq rule.key) and
                    (ContributionEventsTable.activityDay eq activityDay) and
                    (ContributionEventsTable.status eq STATUS_REWARDED)
            }.count() >= cap
        } ?: false

        val countersAdvanced = if (capped) emptySet() else event.decision.counters
        val insert = ContributionEventsTable.insertIgnore {
            it[idempotencyKey] = event.idempotencyKey
            it[userId] = event.userId
            it[eventType] = event.eventType
            it[sourceSystem] = event.source
            it[referenceType] = event.referenceType
            it[referenceId] = event.referenceId
            it[workspaceId] = event.workspaceId
            it[occurredAt] = event.occurredAt
            it[receivedAt] = now
            it[ContributionEventsTable.activityDay] = activityDay
            it[status] = if (capped) STATUS_CAPPED else STATUS_REWARDED
            it[ruleKey] = rule.key
            it[counters] = countersAdvanced.sorted().joinToString(",")
            it[policyVersion] = catalog.version
        }
        if (insert.insertedCount == 0) return RecordResult(RecordStatus.DUPLICATE)
        // A capped activity is consumed (it can never earn later) but awards nothing.
        if (capped) return RecordResult(RecordStatus.CAPPED)
        val eventId = insert[ContributionEventsTable.id]

        val amount = rule.sp
        if (amount > 0) {
            SpLedgerTable.insert {
                it[userId] = event.userId
                it[SpLedgerTable.eventId] = eventId
                it[kind] = KIND_AWARD
                it[ruleKey] = rule.key
                it[SpLedgerTable.amount] = amount
                it[createdAt] = now
            }
        }

        val touched = countersAdvanced.toMutableSet()
        countersAdvanced.filterNot { it in DERIVED_COUNTERS }.forEach { incrementCounter(event.userId, it) }

        // Streak: the best run of consecutive active days. Measured around the new day in both
        // directions so a late event that fills a gap joins the runs on either side.
        val dayInserted = UserActivityDaysTable.insertIgnore {
            it[userId] = event.userId
            it[day] = activityDay
        }.insertedCount > 0
        if (dayInserted) {
            val days = UserActivityDaysTable.select(UserActivityDaysTable.day)
                .where { UserActivityDaysTable.userId eq event.userId }
                .map { it[UserActivityDaysTable.day] }
                .toSet()
            val run = BadgeEvaluator.runContaining(activityDay, days).toLong()
            if (run > counterValue(event.userId, BadgeCounters.STREAK_DAYS)) {
                setCounter(event.userId, BadgeCounters.STREAK_DAYS, run)
                touched += BadgeCounters.STREAK_DAYS
            }
        }

        if (event.workspaceId != null) {
            val firstInWorkspace = UserContributionWorkspacesTable.insertIgnore {
                it[userId] = event.userId
                it[workspaceId] = event.workspaceId
                it[firstEventId] = eventId
            }.insertedCount > 0
            if (firstInWorkspace) {
                incrementCounter(event.userId, BadgeCounters.DISTINCT_WORKSPACES)
                touched += BadgeCounters.DISTINCT_WORKSPACES
            }
        }

        val unlocked = evaluateBadges(event.userId, touched, eventId, catalog, now)

        val afterSp = before.totalSp + amount
        val crossed = LevelCalculator(catalog.levels).levelsCrossed(before.totalSp, afterSp)
        val newLevel = LevelCalculator(catalog.levels).levelFor(afterSp)
        UserProgressionTable.update({ UserProgressionTable.userId eq event.userId }) {
            it[totalSp] = afterSp
            it[level] = newLevel.level
            it[catalogVersion] = catalog.version
            it[updatedAt] = now
        }

        val notifications = buildList {
            if (crossed.isNotEmpty()) add(levelUpNotification(event.userId, crossed))
            unlocked.forEach { (family, tier) -> add(badgeNotification(event.userId, family, tier)) }
        }
        notifications.forEach(::persistNotification)

        return RecordResult(RecordStatus.REWARDED, amount, crossed, unlocked, notifications)
    }

    /**
     * Audited manual adjustment (fraud reversal or administrative grant). Adds a ledger row — earned
     * history is never edited — and recomputes the level, which may go down. Badges are not revoked.
     */
    fun applyCorrection(
        userId: Int,
        amount: Int,
        reason: String,
        actorId: Int?,
        catalog: ProgressionCatalog = ProgressionCatalogs.current,
        now: Long = System.currentTimeMillis()
    ) {
        require(amount != 0) { "A correction must change the total" }
        require(reason.isNotBlank()) { "A correction needs a reason" }
        val before = lockProgression(userId, catalog)
        SpLedgerTable.insert {
            it[SpLedgerTable.userId] = userId
            it[kind] = KIND_CORRECTION
            it[SpLedgerTable.amount] = amount
            it[SpLedgerTable.reason] = reason.take(255)
            it[SpLedgerTable.actorId] = actorId
            it[createdAt] = now
        }
        val afterSp = (before.totalSp + amount).coerceAtLeast(0)
        UserProgressionTable.update({ UserProgressionTable.userId eq userId }) {
            it[totalSp] = afterSp
            it[level] = LevelCalculator(catalog.levels).levelFor(afterSp).level
            it[updatedAt] = now
        }
        logger.info("[Progression] Correction of $amount SP for user $userId by ${actorId ?: "system"}: $reason")
    }

    /**
     * Boot-time maintenance: repairs any running total that disagrees with its ledger, then brings
     * users onto the current catalog (recomputing levels and unlocking any badge tiers a lowered
     * threshold now grants). Recalculation never removes earned SP or badges.
     */
    fun reconcileAndMigrate(catalog: ProgressionCatalog = ProgressionCatalogs.current, now: Long = System.currentTimeMillis()) {
        val ledgerSum = SpLedgerTable.amount.sum()
        val sums = SpLedgerTable.select(SpLedgerTable.userId, ledgerSum)
            .groupBy(SpLedgerTable.userId)
            .associate { it[SpLedgerTable.userId] to (it[ledgerSum]?.toLong() ?: 0L).coerceAtLeast(0) }
        val calculator = LevelCalculator(catalog.levels)
        var repaired = 0
        var migrated = 0
        UserProgressionTable.selectAll().forEach { row ->
            val userId = row[UserProgressionTable.userId]
            val expected = sums[userId] ?: 0L
            val totalMismatch = row[UserProgressionTable.totalSp] != expected
            val stale = row[UserProgressionTable.catalogVersion] != catalog.version
            if (!totalMismatch && !stale && row[UserProgressionTable.level] == calculator.levelFor(expected).level) return@forEach
            if (totalMismatch) {
                repaired++
                logger.error("[Progression] user $userId total ${row[UserProgressionTable.totalSp]} != ledger $expected; repairing from ledger")
            }
            if (stale) migrated++
            UserProgressionTable.update({ UserProgressionTable.userId eq userId }) {
                it[totalSp] = expected
                it[level] = calculator.levelFor(expected).level
                it[catalogVersion] = catalog.version
                it[updatedAt] = now
            }
            if (stale) {
                val counters = UserBadgeCountersTable.select(UserBadgeCountersTable.counter)
                    .where { UserBadgeCountersTable.userId eq userId }
                    .map { it[UserBadgeCountersTable.counter] }
                    .toSet()
                evaluateBadges(userId, counters, null, catalog, now).forEach { (family, tier) ->
                    persistNotification(badgeNotification(userId, family, tier))
                }
            }
        }
        if (repaired > 0 || migrated > 0) {
            logger.info("[Progression] Boot maintenance: repaired $repaired totals, migrated $migrated users to catalog v${catalog.version}")
        }
    }

    /**
     * True when [taskId] was created by a task-vote proposal the team approved. A solo workspace's
     * proposal auto-approves with one eligible voter, so it does not count as team approval.
     */
    fun isTeamApprovedTask(taskId: Int): Boolean =
        TaskVoteProposalsTable.selectAll().where {
            (TaskVoteProposalsTable.createdTaskId eq taskId) and
                (TaskVoteProposalsTable.status eq "APPROVED") and
                (TaskVoteProposalsTable.eligibleCount greater 1)
        }.count() > 0

    // ── internals ──────────────────────────────────────────────────────────────

    private data class Snapshot(val totalSp: Long)

    private fun lockProgression(userId: Int, catalog: ProgressionCatalog): Snapshot {
        UserProgressionTable.insertIgnore {
            it[UserProgressionTable.userId] = userId
            it[catalogVersion] = catalog.version
        }
        val row = UserProgressionTable.selectAll()
            .where { UserProgressionTable.userId eq userId }
            .forUpdate()
            .single()
        return Snapshot(row[UserProgressionTable.totalSp])
    }

    private fun zoneFor(userId: Int): ZoneId {
        val raw = UsersTable.select(UsersTable.timeZone).where { UsersTable.id eq userId }
            .singleOrNull()?.get(UsersTable.timeZone)
        return raw?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC
    }

    internal fun epochDay(epochMs: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().toEpochDay()

    private fun counterValue(userId: Int, counter: String): Long =
        UserBadgeCountersTable.select(UserBadgeCountersTable.value)
            .where { (UserBadgeCountersTable.userId eq userId) and (UserBadgeCountersTable.counter eq counter) }
            .singleOrNull()?.get(UserBadgeCountersTable.value) ?: 0L

    private fun incrementCounter(userId: Int, counter: String) {
        ensureCounter(userId, counter)
        UserBadgeCountersTable.update({
            (UserBadgeCountersTable.userId eq userId) and (UserBadgeCountersTable.counter eq counter)
        }) {
            it[value] = SqlExpressionBuilder.run { UserBadgeCountersTable.value + 1L }
        }
    }

    private fun setCounter(userId: Int, counter: String, newValue: Long) {
        ensureCounter(userId, counter)
        UserBadgeCountersTable.update({
            (UserBadgeCountersTable.userId eq userId) and (UserBadgeCountersTable.counter eq counter)
        }) {
            it[value] = newValue
        }
    }

    private fun ensureCounter(userId: Int, counter: String) {
        UserBadgeCountersTable.insertIgnore {
            it[UserBadgeCountersTable.userId] = userId
            it[UserBadgeCountersTable.counter] = counter
        }
    }

    /** Unlocks every newly reached tier for families fed by [counters], then re-checks Sphere Legend. */
    private fun evaluateBadges(
        userId: Int,
        counters: Set<String>,
        evidenceEventId: Long?,
        catalog: ProgressionCatalog,
        now: Long
    ): List<Pair<BadgeFamily, BadgeTier>> {
        val unlocked = mutableListOf<Pair<BadgeFamily, BadgeTier>>()
        catalog.badges
            .filter { it.available && it.counter in counters && it.counter != BadgeCounters.DISTINCT_BADGE_FAMILIES }
            .forEach { family -> unlocked += unlockReached(userId, family, counterValue(userId, family.counter), evidenceEventId, now) }

        // Sphere Legend counts distinct families earned (any tier), never individual tiers.
        val legendFamilies = catalog.badges.filter { it.available && it.counter == BadgeCounters.DISTINCT_BADGE_FAMILIES }
        if (legendFamilies.isNotEmpty()) {
            val legendKeys = legendFamilies.map { it.key }.toSet()
            val familyKey = UserBadgeUnlocksTable.familyKey
            val distinct = UserBadgeUnlocksTable.select(familyKey)
                .where { UserBadgeUnlocksTable.userId eq userId }
                .withDistinct()
                .map { it[familyKey] }
                .count { it !in legendKeys }
                .toLong()
            if (distinct > counterValue(userId, BadgeCounters.DISTINCT_BADGE_FAMILIES)) {
                setCounter(userId, BadgeCounters.DISTINCT_BADGE_FAMILIES, distinct)
            }
            legendFamilies.forEach { family -> unlocked += unlockReached(userId, family, distinct, evidenceEventId, now) }
        }
        return unlocked
    }

    private fun unlockReached(
        userId: Int,
        family: BadgeFamily,
        count: Long,
        evidenceEventId: Long?,
        now: Long
    ): List<Pair<BadgeFamily, BadgeTier>> =
        BadgeEvaluator.tiersReached(family, count).mapNotNull { tier ->
            val inserted = UserBadgeUnlocksTable.insertIgnore {
                it[UserBadgeUnlocksTable.userId] = userId
                it[familyKey] = family.key
                it[UserBadgeUnlocksTable.tier] = tier.name
                it[unlockedAt] = now
                it[UserBadgeUnlocksTable.evidenceEventId] = evidenceEventId
            }.insertedCount > 0
            if (inserted) family to tier else null
        }

    private fun levelUpNotification(userId: Int, crossed: List<CareerLevel>): PendingNotification {
        val top = crossed.last()
        val body = if (crossed.size == 1) {
            "You reached Level ${top.level} of 20 in your CollabSphere career."
        } else {
            "You advanced ${crossed.size} levels: " + crossed.joinToString(", ") { "${it.level} ${it.title}" } + "."
        }
        return PendingNotification(
            recipientId = userId,
            type = NOTIFICATION_LEVEL_UP,
            title = "Promoted to ${top.title}",
            body = body,
            workspaceId = null,
            dedupeKey = "level-up:$userId:${top.level}"
        )
    }

    private fun badgeNotification(userId: Int, family: BadgeFamily, tier: BadgeTier): PendingNotification {
        val tierName = tier.name.lowercase().replaceFirstChar { it.uppercase() }
        return PendingNotification(
            recipientId = userId,
            type = NOTIFICATION_BADGE,
            title = "$tierName badge: ${family.name}",
            body = "${family.theme} — ${family.requirement} (${family.threshold(tier)}).",
            workspaceId = null,
            dedupeKey = "badge:$userId:${family.key}:${tier.name}"
        )
    }

    /** Durable in the reward transaction; the dedupe key makes a replay a no-op. Pushed after commit. */
    private fun persistNotification(notification: PendingNotification) {
        NotificationsTable.insertIgnore {
            it[recipientId] = notification.recipientId
            it[type] = notification.type
            it[title] = notification.title
            it[body] = notification.body
            it[workspaceId] = notification.workspaceId
            it[dedupeKey] = notification.dedupeKey
        }
    }
}
