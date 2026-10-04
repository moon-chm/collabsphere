package plugins

import com.collabsphere.DatabaseFactory
import com.collabsphere.model.GitHubWebhookEventsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.*

/** The GitHub webhook queue's claim/lease/retry rules, against real Postgres row locking. */
class WebhookQueueIntegrationTest {

    companion object {
        init {
            DatabaseFactory.init()
        }
    }

    private val tag = "test-" + UUID.randomUUID().toString().take(8)

    /** Ids are tagged so a test only ever looks at its own rows; created_at = 0 sorts them first. */
    private fun enqueue(count: Int, status: String = "QUEUED", startedAt: Long? = null, retryCount: Int = 0): List<String> =
        transaction {
            (1..count).map { i ->
                val id = "$tag-$i"
                GitHubWebhookEventsTable.insert {
                    it[deliveryId] = id
                    it[eventType] = "push"
                    it[payload] = "{}"
                    it[GitHubWebhookEventsTable.status] = status
                    it[GitHubWebhookEventsTable.retryCount] = retryCount
                    it[nextRetryAt] = 0L
                    it[createdAt] = 0L
                    it[processingStartedAt] = startedAt
                }
                id
            }
        }

    private fun row(id: String) = transaction {
        GitHubWebhookEventsTable.selectAll().where { GitHubWebhookEventsTable.deliveryId eq id }.single()
    }

    private fun mine(events: List<ClaimedWebhook>) = events.filter { it.deliveryId.startsWith(tag) }

    @AfterTest
    fun cleanUp() {
        transaction { GitHubWebhookEventsTable.deleteWhere { deliveryId like "$tag-%" } }
    }

    @Test
    fun `concurrent claimers never get the same event`() {
        val ids = enqueue(6)
        val holding = CountDownLatch(1)
        val release = CountDownLatch(1)
        var firstClaim = emptyList<ClaimedWebhook>()

        // Worker A claims and keeps its transaction (and row locks) open while B claims.
        val a = thread {
            transaction {
                firstClaim = mine(claimWebhookBatch(System.currentTimeMillis(), limit = 3))
                holding.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
        }
        assertTrue(holding.await(10, TimeUnit.SECONDS))
        val secondClaim = mine(transaction { claimWebhookBatch(System.currentTimeMillis(), limit = 50) })
        release.countDown()
        a.join()

        assertEquals(3, firstClaim.size)
        assertTrue(firstClaim.map { it.deliveryId }.intersect(secondClaim.map { it.deliveryId }.toSet()).isEmpty())
        assertEquals(ids.toSet(), (firstClaim + secondClaim).map { it.deliveryId }.toSet())
    }

    @Test
    fun `abandoned and legacy processing rows are re-queued as a used attempt`() {
        val now = System.currentTimeMillis()
        val (expired) = enqueue(1, status = "PROCESSING", startedAt = now - WEBHOOK_LEASE_MS - 1)
        val live = "$tag-live"
        val legacy = "$tag-legacy"
        transaction {
            for ((id, started) in listOf(live to now - 1_000L, legacy to null)) {
                GitHubWebhookEventsTable.insert {
                    it[deliveryId] = id
                    it[eventType] = "push"
                    it[payload] = "{}"
                    it[status] = "PROCESSING"
                    it[processingStartedAt] = started
                }
            }
        }

        transaction { reclaimAbandonedWebhooks(now) }

        assertEquals("QUEUED", row(expired)[GitHubWebhookEventsTable.status])
        assertEquals(1, row(expired)[GitHubWebhookEventsTable.retryCount])
        assertEquals("QUEUED", row(legacy)[GitHubWebhookEventsTable.status])
        assertEquals("PROCESSING", row(live)[GitHubWebhookEventsTable.status], "a live lease must not be stolen")
    }

    @Test
    fun `an event that keeps getting abandoned ends up failed`() {
        val now = System.currentTimeMillis()
        val (id) = enqueue(1, status = "PROCESSING", startedAt = null, retryCount = WEBHOOK_MAX_RETRIES)
        transaction { reclaimAbandonedWebhooks(now) }
        assertEquals("FAILED", row(id)[GitHubWebhookEventsTable.status])
    }

    @Test
    fun `a stale worker cannot overwrite the outcome of a re-claimed event`() {
        val (id) = enqueue(1)
        val t0 = System.currentTimeMillis() - WEBHOOK_LEASE_MS - 1_000
        val stale = mine(transaction { claimWebhookBatch(t0, limit = 50) }).single { it.deliveryId == id }
        // Lease runs out; the event is reclaimed and claimed again by someone else.
        transaction { reclaimAbandonedWebhooks(System.currentTimeMillis()) }
        val fresh = mine(transaction { claimWebhookBatch(System.currentTimeMillis(), limit = 50) }).single { it.deliveryId == id }

        transaction { completeWebhook(stale) }
        assertEquals("PROCESSING", row(id)[GitHubWebhookEventsTable.status], "stale completion must be ignored")

        transaction { completeWebhook(fresh) }
        assertEquals("DONE", row(id)[GitHubWebhookEventsTable.status])
    }

    @Test
    fun `failures back off and give up after the retry budget`() {
        val (id) = enqueue(1)
        var retries = 0
        while (true) {
            val claimed = mine(transaction { claimWebhookBatch(Long.MAX_VALUE / 2, limit = 50) }).singleOrNull { it.deliveryId == id }
                ?: break
            val before = System.currentTimeMillis()
            transaction { failWebhook(claimed, "boom", before) }
            val r = row(id)
            if (r[GitHubWebhookEventsTable.status] == "FAILED") break
            retries++
            assertEquals(before + 10_000L * (1L shl claimed.retryCount), r[GitHubWebhookEventsTable.nextRetryAt])
        }
        assertEquals(WEBHOOK_MAX_RETRIES, retries)
        assertEquals("FAILED", row(id)[GitHubWebhookEventsTable.status])
    }
}
