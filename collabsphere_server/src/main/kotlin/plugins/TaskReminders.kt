package plugins

import com.collabsphere.model.TasksTable
import io.ktor.server.application.Application
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

private const val DAY_MS = 24 * 60 * 60 * 1000L
private const val REMINDER_CHECK_INTERVAL_MS = 15 * 60 * 1000L
private const val REMINDER_STARTUP_DELAY_MS = 60 * 1000L
private const val STATUS_DONE = "DONE"

internal data class DueTask(
    val id: Int,
    val workspaceId: Int,
    val assigneeId: Int,
    val taskName: String,
    val dueDate: Long
)

internal fun isReminderDue(dueDate: Long, now: Long): Boolean =
    now >= dueDate - DAY_MS && now < dueDate + DAY_MS

internal fun reminderBody(taskName: String, dueDate: Long, now: Long): String =
    if (now < dueDate) "\"$taskName\" is due tomorrow" else "\"$taskName\" is due today"

fun Application.startTaskReminderScheduler() {
    launch {
        delay(REMINDER_STARTUP_DELAY_MS)
        while (isActive) {
            try {
                sendDueTaskReminders(System.currentTimeMillis())
            } catch (e: Exception) {
                println("[TaskReminders] Reminder run failed: ${e.message}")
            }
            delay(REMINDER_CHECK_INTERVAL_MS)
        }
    }
}

private suspend fun sendDueTaskReminders(now: Long) {
    val candidates = dbQuery {
        TasksTable.selectAll()
            .where {
                (TasksTable.isDeleted eq false) and
                    (TasksTable.status neq STATUS_DONE) and
                    TasksTable.reminderSentAt.isNull() and
                    TasksTable.assignedToUserId.isNotNull() and
                    TasksTable.dueDate.between(now - DAY_MS + 1, now + DAY_MS)
            }
            .mapNotNull { row ->
                val assigneeId = row[TasksTable.assignedToUserId] ?: return@mapNotNull null
                val dueDate = row[TasksTable.dueDate] ?: return@mapNotNull null
                DueTask(row[TasksTable.id], row[TasksTable.workspaceId], assigneeId, row[TasksTable.taskName], dueDate)
            }
            .filter { isReminderDue(it.dueDate, now) }
    }

    for (task in candidates) {
        val claimed = dbQuery {
            TasksTable.update({ (TasksTable.id eq task.id) and TasksTable.reminderSentAt.isNull() }) {
                it[reminderSentAt] = now
            }
        }
        if (claimed == 0) continue
        createAndPushNotification(
            recipientId = task.assigneeId,
            actorId = null,
            type = "TASK_DUE",
            title = "Task due soon",
            body = reminderBody(task.taskName, task.dueDate, now),
            workspaceId = task.workspaceId,
            referenceId = task.id
        )
    }
}
