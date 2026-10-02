package plugins

import com.collabsphere.model.GitHubWebhookEventsTable
import com.collabsphere.util.GitHubWebhookService
import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

fun Application.startGitHubWebhookProcessor() {
    CoroutineScope(Dispatchers.IO).launch {
        while (isActive) {
            try {
                processNextWebhookBatch()
            } catch (e: Exception) {
                println("[GitHub] Webhook processor error: ${e.message}")
            }
            delay(5000L) // Poll every 5 seconds
        }
    }
}

private suspend fun processNextWebhookBatch() {
    val now = System.currentTimeMillis()
    val eventsToProcess = dbQuery {
        GitHubWebhookEventsTable.selectAll()
            .where {
                (GitHubWebhookEventsTable.status eq "QUEUED") and
                    (GitHubWebhookEventsTable.nextRetryAt lessEq now)
            }
            .orderBy(GitHubWebhookEventsTable.createdAt, SortOrder.ASC)
            .limit(10)
            .map {
                Triple(
                    it[GitHubWebhookEventsTable.deliveryId],
                    it[GitHubWebhookEventsTable.eventType],
                    it[GitHubWebhookEventsTable.payload]
                )
            }
    }

    for ((deliveryId, eventType, payload) in eventsToProcess) {
        dbQuery {
            GitHubWebhookEventsTable.update({ GitHubWebhookEventsTable.deliveryId eq deliveryId }) {
                it[status] = "PROCESSING"
            }
        }

        try {
            val activities = GitHubWebhookService.handleWebhookEvent(eventType, payload)
            processGitHubActivities(activities)

            dbQuery {
                GitHubWebhookEventsTable.update({ GitHubWebhookEventsTable.deliveryId eq deliveryId }) {
                    it[status] = "DONE"
                }
            }
        } catch (e: Exception) {
            println("[GitHub] Failed to process webhook delivery $deliveryId: ${e.message}")
            dbQuery {
                val currentRetry = GitHubWebhookEventsTable.selectAll()
                    .where { GitHubWebhookEventsTable.deliveryId eq deliveryId }
                    .singleOrNull()?.get(GitHubWebhookEventsTable.retryCount) ?: 0
                
                if (currentRetry >= 5) {
                    GitHubWebhookEventsTable.update({ GitHubWebhookEventsTable.deliveryId eq deliveryId }) {
                        it[status] = "FAILED"
                        it[error] = e.message
                    }
                } else {
                    val backoff = 10000L * (1 shl currentRetry) // 10s, 20s, 40s, 80s, 160s
                    GitHubWebhookEventsTable.update({ GitHubWebhookEventsTable.deliveryId eq deliveryId }) {
                        it[status] = "QUEUED"
                        it[retryCount] = currentRetry + 1
                        it[nextRetryAt] = System.currentTimeMillis() + backoff
                        it[error] = e.message
                    }
                }
            }
        }
    }
}
