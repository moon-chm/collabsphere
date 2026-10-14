package plugins.progression

import com.collabsphere.model.MessageHelpfulMarksTable
import com.collabsphere.model.MessageTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import plugins.isMember

enum class HelpfulMarkStatus { MARKED, ALREADY_MARKED, OWN_MESSAGE, NOT_MEMBER, NOT_FOUND, DAILY_LIMIT }

data class HelpfulMarkOutcome(val status: HelpfulMarkStatus, val notifications: List<PendingNotification> = emptyList())

/**
 * Peer recognition on channel messages. Marks are badge progress only (no SP), at most one per person
 * per message, never on your own message, and limited per marker per day, so they cannot be farmed.
 * Must run inside a transaction.
 */
object HelpfulMarks {
    const val DAILY_LIMIT_PER_MARKER = 10
    private const val DAY_MS = 86_400_000L

    fun mark(
        messageId: Int,
        markerId: Int,
        catalog: ProgressionCatalog = ProgressionCatalogs.current,
        now: Long = System.currentTimeMillis()
    ): HelpfulMarkOutcome {
        val message = MessageTable.selectAll()
            .where { (MessageTable.id eq messageId) and (MessageTable.isDeleted eq false) }
            .singleOrNull() ?: return HelpfulMarkOutcome(HelpfulMarkStatus.NOT_FOUND)
        val workspaceId = message[MessageTable.workspaceId]
        val authorId = message[MessageTable.userId]
        if (!isMember(markerId, workspaceId)) return HelpfulMarkOutcome(HelpfulMarkStatus.NOT_MEMBER)
        if (authorId == markerId) return HelpfulMarkOutcome(HelpfulMarkStatus.OWN_MESSAGE)

        val alreadyMarked = MessageHelpfulMarksTable.selectAll().where {
            (MessageHelpfulMarksTable.messageId eq messageId) and (MessageHelpfulMarksTable.markerId eq markerId)
        }.count() > 0
        if (alreadyMarked) return HelpfulMarkOutcome(HelpfulMarkStatus.ALREADY_MARKED)

        val markedToday = MessageHelpfulMarksTable.selectAll().where {
            (MessageHelpfulMarksTable.markerId eq markerId) and (MessageHelpfulMarksTable.createdAt greater (now - DAY_MS))
        }.count()
        if (markedToday >= DAILY_LIMIT_PER_MARKER) return HelpfulMarkOutcome(HelpfulMarkStatus.DAILY_LIMIT)

        val inserted = MessageHelpfulMarksTable.insertIgnore {
            it[MessageHelpfulMarksTable.messageId] = messageId
            it[MessageHelpfulMarksTable.markerId] = markerId
            it[createdAt] = now
        }.insertedCount > 0
        if (!inserted) return HelpfulMarkOutcome(HelpfulMarkStatus.ALREADY_MARKED) // a concurrent duplicate won

        // Bridge Builder: each of the author's messages counts once, however many people mark it.
        val helpfulMessage = ProgressionService.record(
            ContributionEvent(
                idempotencyKey = "helpful-msg:$messageId",
                userId = authorId,
                eventType = "MESSAGE_MARKED_HELPFUL",
                source = "MESSAGES",
                referenceType = "MESSAGE",
                referenceId = messageId.toString(),
                workspaceId = workspaceId,
                occurredAt = now,
                decision = RewardDecision(RuleKeys.MESSAGE_MARKED_HELPFUL, setOf(BadgeCounters.HELPFUL_MESSAGES))
            ),
            catalog, now
        )
        // Mentor Spark: each distinct teammate who recognises the author counts once.
        val recognition = ProgressionService.record(
            ContributionEvent(
                idempotencyKey = "helpful-peer:$authorId:$markerId",
                userId = authorId,
                eventType = "PEER_RECOGNITION",
                source = "MESSAGES",
                referenceType = "MESSAGE",
                referenceId = messageId.toString(),
                workspaceId = workspaceId,
                occurredAt = now,
                decision = RewardDecision(RuleKeys.PEER_RECOGNITION, setOf(BadgeCounters.HELPFUL_RECOGNIZERS))
            ),
            catalog, now
        )
        return HelpfulMarkOutcome(HelpfulMarkStatus.MARKED, helpfulMessage.notifications + recognition.notifications)
    }
}
