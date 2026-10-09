package plugins

import com.collabsphere.util.ExternalProviderPolicy
import com.collabsphere.model.GitHubWebhookEventsTable
import com.collabsphere.util.GitHubWebhookService
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.LongColumnType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("GitHubWebhookProcessor")

private const val POLL_INTERVAL_MS = 5_000L
private const val BATCH_SIZE = 10
/** Retries after the first attempt before an event is marked FAILED (backoff 10s, 20s, 40s, 80s, 160s). */
internal const val WEBHOOK_MAX_RETRIES = 5

/**
 * How long a claimed event may stay PROCESSING before it's presumed abandoned (the server died or was
 * redeployed mid-event) and handed out again. Comfortably above the worst case of one event's GitHub
 * calls with their timeouts, so a slow-but-alive worker is never raced by a second one.
 */
internal const val WEBHOOK_LEASE_MS = 15 * 60 * 1000L

internal data class ClaimedWebhook(
    val deliveryId: String,
    val eventType: String,
    val payload: String,
    val retryCount: Int,
    /** The claim's `processing_started_at` — proof the row is still this worker's when it reports back. */
    val claimedAt: Long
)

fun Application.startGitHubWebhookProcessor() {
    if (ExternalProviderPolicy.areDisabled()) return
    launch(Dispatchers.IO) {
        while (isActive) {
            try {
                processNextWebhookBatch()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.error("[GitHub] Webhook processor error", e)
            }
            delay(POLL_INTERVAL_MS)
        }
    }
}

private suspend fun processNextWebhookBatch() {
    val now = System.currentTimeMillis()
    val reclaimed = dbQuery { reclaimAbandonedWebhooks(now) }
    if (reclaimed > 0) logger.warn("[GitHub] Re-queued $reclaimed webhook event(s) abandoned mid-processing")

    for (event in dbQuery { claimWebhookBatch(now, BATCH_SIZE) }) {
        try {
            val activities = GitHubWebhookService.handleWebhookEvent(event.eventType, event.payload)
            processGitHubActivities(activities)
            dbQuery { completeWebhook(event) }
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            logger.error("[GitHub] Failed to process webhook delivery ${event.deliveryId}", e)
            dbQuery { failWebhook(event, e.message, System.currentTimeMillis()) }
        }
    }
}

/**
 * Atomically moves up to [limit] due QUEUED events to PROCESSING and returns them. `SKIP LOCKED` means
 * concurrent claimers (a second instance, or an overlapping run) each get a disjoint set instead of
 * double-processing — or blocking on — the same rows.
 */
internal fun claimWebhookBatch(now: Long, limit: Int): List<ClaimedWebhook> =
    TransactionManager.current().exec(
        """
        UPDATE github_webhook_events SET status = 'PROCESSING', processing_started_at = ?
        WHERE delivery_id IN (
            SELECT delivery_id FROM github_webhook_events
            WHERE status = 'QUEUED' AND next_retry_at <= ?
            ORDER BY created_at
            LIMIT ?
            FOR UPDATE SKIP LOCKED
        )
        RETURNING delivery_id, event_type, payload, retry_count
        """.trimIndent(),
        listOf(LongColumnType() to now, LongColumnType() to now, IntegerColumnType() to limit),
        // UPDATE ... RETURNING yields rows; without this Exposed runs it as a plain update and the driver rejects the result.
        explicitStatementType = StatementType.SELECT
    ) { rs ->
        buildList {
            while (rs.next()) {
                add(ClaimedWebhook(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), claimedAt = now))
            }
        }
    } ?: emptyList()

/**
 * Puts PROCESSING events whose lease ran out back in the queue, counting the lost run as an attempt so
 * an event that crashes the server every time ends up FAILED instead of looping forever. Rows left
 * PROCESSING by the old processor have no claim time and are reclaimed straight away.
 */
internal fun reclaimAbandonedWebhooks(now: Long): Int =
    TransactionManager.current().exec(
        """
        UPDATE github_webhook_events
        SET status = CASE WHEN retry_count >= ? THEN 'FAILED' ELSE 'QUEUED' END,
            retry_count = retry_count + 1,
            next_retry_at = ?,
            processing_started_at = NULL,
            error = COALESCE(error, 'Abandoned mid-processing (server restart?)')
        WHERE status = 'PROCESSING' AND (processing_started_at IS NULL OR processing_started_at < ?)
        RETURNING delivery_id
        """.trimIndent(),
        listOf(
            IntegerColumnType() to WEBHOOK_MAX_RETRIES,
            LongColumnType() to now,
            LongColumnType() to now - WEBHOOK_LEASE_MS
        ),
        explicitStatementType = StatementType.SELECT
    ) { rs ->
        var count = 0
        while (rs.next()) count++
        count
    } ?: 0

// Both outcomes only apply while the row is still this claim's — if the lease ran out and another
// worker re-claimed it, that worker's outcome wins rather than being clobbered by a stale one.
private fun stillClaimedBy(event: ClaimedWebhook) =
    (GitHubWebhookEventsTable.deliveryId eq event.deliveryId) and
        (GitHubWebhookEventsTable.status eq "PROCESSING") and
        (GitHubWebhookEventsTable.processingStartedAt eq event.claimedAt)

internal fun completeWebhook(event: ClaimedWebhook) {
    GitHubWebhookEventsTable.update({ stillClaimedBy(event) }) {
        it[status] = "DONE"
        it[processingStartedAt] = null
    }
}

internal fun failWebhook(event: ClaimedWebhook, message: String?, now: Long) {
    GitHubWebhookEventsTable.update({ stillClaimedBy(event) }) {
        if (event.retryCount >= WEBHOOK_MAX_RETRIES) {
            it[status] = "FAILED"
        } else {
            it[status] = "QUEUED"
            it[retryCount] = event.retryCount + 1
            it[nextRetryAt] = now + 10_000L * (1L shl event.retryCount)
        }
        it[processingStartedAt] = null
        it[error] = message
    }
}
