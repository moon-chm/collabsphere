package plugins

import com.collabsphere.util.ExternalProviderPolicy
import com.collabsphere.model.TaskReminderOutboxTable
import com.collabsphere.model.TasksTable
import com.collabsphere.dto.NotificationResponse
import io.ktor.server.application.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.LongColumnType
import org.jetbrains.exposed.sql.statements.StatementType
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("TaskReminders")

private const val DAY_MS = 24 * 60 * 60 * 1000L
private const val REMINDER_CHECK_INTERVAL_MS = 15 * 60 * 1000L
private const val REMINDER_STARTUP_DELAY_MS = 60 * 1000L
private const val OUTBOX_CHECK_INTERVAL_MS = 5_000L
private const val OUTBOX_BATCH_SIZE = 25
private const val OUTBOX_LEASE_MS = 10 * 60 * 1000L
private const val OUTBOX_MAX_ATTEMPTS = 8
private const val STATUS_DONE = "DONE"

internal data class DueTask(
    val id: Int,
    val workspaceId: Int,
    val assigneeId: Int,
    val taskName: String,
    val dueDate: Long
)

internal data class ClaimedReminder(
    val id: Int,
    val reminderKey: String,
    val taskId: Int,
    val workspaceId: Int,
    val recipientId: Int,
    val dueDate: Long,
    val title: String,
    val body: String,
    val attempts: Int,
    val lockedAt: Long
)

internal fun isReminderDue(dueDate: Long, now: Long): Boolean =
    now >= dueDate - DAY_MS && now < dueDate + DAY_MS

internal fun reminderBody(taskName: String, dueDate: Long, now: Long): String =
    if (now < dueDate) "\"$taskName\" is due tomorrow" else "\"$taskName\" is due today"

internal fun reminderKey(taskId: Int, dueDate: Long): String = "task-reminder:$taskId:$dueDate"

internal fun reminderRetryDelay(attempt: Int): Long =
    (30_000L * (1L shl (attempt - 1).coerceIn(0, 7))).coerceAtMost(60 * 60 * 1000L)

fun Application.startTaskReminderScheduler() {
    if (ExternalProviderPolicy.areDisabled()) return
    launch(Dispatchers.IO) {
        delay(REMINDER_STARTUP_DELAY_MS)
        while (isActive) {
            try {
                enqueueDueTaskReminders(System.currentTimeMillis())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.error("[TaskReminders] Reminder producer failed", e)
            }
            delay(REMINDER_CHECK_INTERVAL_MS)
        }
    }

    launch(Dispatchers.IO) {
        delay(REMINDER_STARTUP_DELAY_MS)
        while (isActive) {
            try {
                deliverReminderBatch(System.currentTimeMillis())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.error("[TaskReminders] Outbox worker failed", e)
            }
            delay(OUTBOX_CHECK_INTERVAL_MS)
        }
    }
}

/** Claims tasks and inserts their outbox row in the same transaction. */
private suspend fun enqueueDueTaskReminders(now: Long) {
    dbQuery {
        val candidates = TasksTable.selectAll()
            .where {
                (TasksTable.isDeleted eq false) and
                    (TasksTable.status neq STATUS_DONE) and
                    TasksTable.reminderSentAt.isNull() and
                    TasksTable.assignedToUserId.isNotNull() and
                    TasksTable.dueDate.between(now - DAY_MS + 1, now + DAY_MS)
            }
            .orderBy(TasksTable.dueDate)
            .limit(100)
            .mapNotNull { row ->
                val assigneeId = row[TasksTable.assignedToUserId] ?: return@mapNotNull null
                val dueDate = row[TasksTable.dueDate] ?: return@mapNotNull null
                DueTask(row[TasksTable.id], row[TasksTable.workspaceId], assigneeId, row[TasksTable.taskName], dueDate)
            }
            .filter { isReminderDue(it.dueDate, now) }

        candidates.forEach { task ->
            val claimed = TasksTable.update({
                (TasksTable.id eq task.id) and
                    (TasksTable.assignedToUserId eq task.assigneeId) and
                    (TasksTable.dueDate eq task.dueDate) and
                    (TasksTable.isDeleted eq false) and
                    (TasksTable.status neq STATUS_DONE) and
                    TasksTable.reminderSentAt.isNull()
            }) {
                it[TasksTable.reminderSentAt] = now
            }
            if (claimed == 0) return@forEach

            val key = reminderKey(task.id, task.dueDate)
            TaskReminderOutboxTable.insertIgnore {
                it[TaskReminderOutboxTable.reminderKey] = key
                it[TaskReminderOutboxTable.taskId] = task.id
                it[TaskReminderOutboxTable.workspaceId] = task.workspaceId
                it[TaskReminderOutboxTable.recipientId] = task.assigneeId
                it[TaskReminderOutboxTable.dueDate] = task.dueDate
                it[TaskReminderOutboxTable.title] = "Task due soon"
                it[TaskReminderOutboxTable.body] = reminderBody(task.taskName, task.dueDate, now)
                it[TaskReminderOutboxTable.status] = "PENDING"
                it[TaskReminderOutboxTable.attempts] = 0
                it[TaskReminderOutboxTable.nextAttemptAt] = now
                it[TaskReminderOutboxTable.createdAt] = now
            }
        }
    }
}

/** Atomically claims due jobs; SKIP LOCKED allows additional app instances without double claims. */
internal fun claimReminderBatch(now: Long, limit: Int): List<ClaimedReminder> =
    TransactionManager.current().exec(
        """
        UPDATE task_reminder_outbox
        SET status = 'PROCESSING', locked_at = ?, attempts = attempts + 1
        WHERE id IN (
            SELECT id FROM task_reminder_outbox
            WHERE (status = 'PENDING' AND next_attempt_at <= ?)
               OR (status = 'PROCESSING' AND locked_at < ?)
            ORDER BY next_attempt_at, created_at
            LIMIT ?
            FOR UPDATE SKIP LOCKED
        )
        RETURNING id, reminder_key, task_id, workspace_id, recipient_id, due_date, title, body, attempts
        """.trimIndent(),
        listOf(
            LongColumnType() to now,
            LongColumnType() to now,
            LongColumnType() to now - OUTBOX_LEASE_MS,
            IntegerColumnType() to limit
        ),
        explicitStatementType = StatementType.SELECT
    ) { rs ->
        buildList {
            while (rs.next()) {
                add(
                    ClaimedReminder(
                        id = rs.getInt("id"),
                        reminderKey = rs.getString("reminder_key"),
                        taskId = rs.getInt("task_id"),
                        workspaceId = rs.getInt("workspace_id"),
                        recipientId = rs.getInt("recipient_id"),
                        dueDate = rs.getLong("due_date"),
                        title = rs.getString("title"),
                        body = rs.getString("body"),
                        attempts = rs.getInt("attempts"),
                        lockedAt = now
                    )
                )
            }
        }
    } ?: emptyList()

internal suspend fun deliverReminderBatch(
    now: Long,
    sendPush: suspend (NotificationResponse) -> Boolean = { notification ->
        com.collabsphere.util.FcmService.sendGenericPushAwait(
            recipientUserId = notification.recipientId,
            notificationId = notification.id,
            type = notification.type,
            title = notification.title,
            body = notification.body,
            workspaceId = notification.workspaceId
        )
    }
) {
    val jobs = dbQuery { claimReminderBatch(now, OUTBOX_BATCH_SIZE) }
    for (job in jobs) {
        if (!isReminderStillValid(job)) {
            dbQuery { finishReminder(job, status = "CANCELLED", now = now) }
            continue
        }
        try {
            val notification = createAndPushNotification(
                recipientId = job.recipientId,
                actorId = null,
                type = "TASK_DUE",
                title = job.title,
                body = job.body,
                workspaceId = job.workspaceId,
                referenceId = job.taskId,
                dedupeKey = job.reminderKey,
                dispatchPush = false
            )
            val pushSucceeded = sendPush(notification)
            if (!pushSucceeded) throw IllegalStateException("Transient FCM delivery failure")
            dbQuery { finishReminder(job, status = "DONE", now = now) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            logger.error("[TaskReminders] Delivery failed for ${job.reminderKey} attempt=${job.attempts}", e)
            dbQuery { retryReminder(job, e.message, now) }
        }
    }
}

private suspend fun isReminderStillValid(job: ClaimedReminder): Boolean = dbQuery {
    val task = TasksTable.selectAll().where { TasksTable.id eq job.taskId }.singleOrNull() ?: return@dbQuery false
    task[TasksTable.dueDate] == job.dueDate &&
        task[TasksTable.assignedToUserId] == job.recipientId &&
        task[TasksTable.isDeleted].not() && task[TasksTable.status] != STATUS_DONE
}

private fun finishReminder(job: ClaimedReminder, status: String, now: Long) {
    TaskReminderOutboxTable.update({
        (TaskReminderOutboxTable.id eq job.id) and
            (TaskReminderOutboxTable.status eq "PROCESSING") and
            (TaskReminderOutboxTable.lockedAt eq job.lockedAt)
    }) {
        it[TaskReminderOutboxTable.status] = status
        it[TaskReminderOutboxTable.lockedAt] = null
        if (status == "DONE") it[TaskReminderOutboxTable.deliveredAt] = now
    }
}

private fun retryReminder(job: ClaimedReminder, message: String?, now: Long) {
    val failed = job.attempts >= OUTBOX_MAX_ATTEMPTS
    TaskReminderOutboxTable.update({
        (TaskReminderOutboxTable.id eq job.id) and
            (TaskReminderOutboxTable.status eq "PROCESSING") and
            (TaskReminderOutboxTable.lockedAt eq job.lockedAt)
    }) {
        it[TaskReminderOutboxTable.status] = if (failed) "FAILED" else "PENDING"
        it[TaskReminderOutboxTable.nextAttemptAt] = now + reminderRetryDelay(job.attempts)
        it[TaskReminderOutboxTable.lockedAt] = null
        it[TaskReminderOutboxTable.lastError] = message?.take(2_000) ?: "Unknown notification delivery failure"
    }
}
