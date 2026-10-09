package plugins

import com.collabsphere.DatabaseFactory
import com.collabsphere.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import kotlinx.coroutines.runBlocking
import java.sql.Connection
import java.util.UUID
import kotlin.test.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList

/**
 * Runs only against the disposable PostgreSQL database explicitly configured for integrationTest.
 * The bug being guarded against only exists with real concurrent transactions.
 */
class DeltaSyncIntegrationTest {

    companion object {
        init {
            DatabaseFactory.init()
        }
    }

    private data class Fixture(val userA: Int, val userB: Int, val workspaceId: Int)

    private val createdUserIds = mutableSetOf<Int>()
    private val createdWorkspaceIds = mutableSetOf<Int>()
    private val createdReminderKeys = mutableSetOf<String>()

    private fun fixture(): Fixture {
        val fixture = transaction {
            val tag = UUID.randomUUID().toString().take(8)
            fun user(name: String) = UsersTable.insert {
                it[email] = "$name-$tag@sync.test"
                it[password] = "x"
                it[username] = "$name-$tag"
            }[UsersTable.id]
            val a = user("a")
            val b = user("b")
            val ws = WorkspacesTable.insert {
                it[userId] = a
                it[workspaceName] = "ws-$tag"
                it[workspaceOwner] = "a-$tag"
                it[workspacePassword] = ""
            }[WorkspacesTable.id]
            for (u in listOf(a, b)) {
                WorkspaceMembersTable.insert {
                    it[workspaceId] = ws
                    it[userId] = u
                }
            }
            Fixture(a, b, ws)
        }
        createdUserIds += listOf(fixture.userA, fixture.userB)
        createdWorkspaceIds += fixture.workspaceId
        return fixture
    }

    @AfterTest
    fun cleanUpFixtureRows() {
        if (createdUserIds.isEmpty() && createdWorkspaceIds.isEmpty()) return
        transaction {
            if (createdUserIds.isNotEmpty()) {
                NotificationsTable.deleteWhere { NotificationsTable.recipientId inList createdUserIds }
                WorkspaceMembershipStateTable.deleteWhere { WorkspaceMembershipStateTable.userId inList createdUserIds }
            }
            if (createdWorkspaceIds.isNotEmpty()) {
                NotificationsTable.deleteWhere { NotificationsTable.workspaceId inList createdWorkspaceIds }
                WorkspaceMembershipStateTable.deleteWhere { WorkspaceMembershipStateTable.workspaceId inList createdWorkspaceIds }
                WorkspacesTable.deleteWhere { WorkspacesTable.id inList createdWorkspaceIds }
            }
            if (createdReminderKeys.isNotEmpty()) {
                TaskReminderOutboxTable.deleteWhere { TaskReminderOutboxTable.reminderKey inList createdReminderKeys }
            }
            if (createdUserIds.isNotEmpty()) {
                UsersTable.deleteWhere { UsersTable.id inList createdUserIds }
            }
        }
        createdWorkspaceIds.clear()
        createdUserIds.clear()
    }

    private fun insertTask(workspaceId: Int, userId: Int, name: String, updatedAt: Long = System.currentTimeMillis()) =
        TasksTable.insert {
            it[createdByUserId] = userId
            it[TasksTable.workspaceId] = workspaceId
            it[taskName] = name
            it[taskDescription] = ""
            it[status] = "TODO"
            it[TasksTable.updatedAt] = updatedAt
        }[TasksTable.id]

    /** A second, independent connection — a transaction left open on it is invisible to Exposed's. */
    private fun rawConnection(): Connection =
        TransactionManager.defaultDatabase!!.connector().connection as Connection

    private fun syncTasks(workspaceId: Int, request: SyncRequest) = transaction { taskDeltaSync(workspaceId, request) }

    @Test
    fun `row committed late by a slow transaction is still delivered`() {
        val f = fixture()
        val start = syncTasks(f.workspaceId, SyncRequest(cursor = 0, since = 0))
        assertTrue(start.rows.isEmpty())

        rawConnection().use { slow ->
            slow.autoCommit = false
            // The slow writer stamps its row first (older updated_at) but commits last.
            slow.prepareStatement(
                "INSERT INTO task (created_by_user_id, workspace_id, task_name, task_description, status, updated_at) " +
                    "VALUES (?, ?, 'slow', '', 'TODO', ?)"
            ).use {
                it.setInt(1, f.userA)
                it.setInt(2, f.workspaceId)
                it.setLong(3, System.currentTimeMillis())
                it.executeUpdate()
            }
            Thread.sleep(5)
            transaction { insertTask(f.workspaceId, f.userA, "fast") }

            val first = syncTasks(f.workspaceId, SyncRequest(cursor = start.nextCursor, since = 0))
            assertEquals(listOf("fast"), first.rows.map { it.taskName })

            // What the old protocol would do next: ask for updated_at > newest seen. Proves the bug is real.
            val legacySince = first.rows.maxOf { it.updatedAt }
            slow.commit()
            val legacy = syncTasks(f.workspaceId, SyncRequest(cursor = null, since = legacySince))
            assertFalse(legacy.rows.any { it.taskName == "slow" }, "legacy timestamp sync is expected to miss the slow row")

            val second = syncTasks(f.workspaceId, SyncRequest(cursor = first.nextCursor, since = 0))
            assertTrue(second.rows.any { it.taskName == "slow" }, "cursor sync must deliver the late-committed row")
        }
    }

    @Test
    fun `updates and soft deletes are re-stamped and delivered`() {
        val f = fixture()
        val taskId = transaction { insertTask(f.workspaceId, f.userA, "t") }
        val cursor = syncTasks(f.workspaceId, SyncRequest(cursor = 0, since = 0)).nextCursor

        assertTrue(syncTasks(f.workspaceId, SyncRequest(cursor = cursor, since = 0)).rows.isEmpty(), "no changes yet")

        transaction { TasksTable.update({ TasksTable.id eq taskId }) { it[isDeleted] = true } }
        val after = syncTasks(f.workspaceId, SyncRequest(cursor = cursor, since = 0))
        assertEquals(listOf(true), after.rows.map { it.isDeleted })
    }

    @Test
    fun `quiet workspace returns nothing on repeated polls`() {
        val f = fixture()
        transaction { insertTask(f.workspaceId, f.userA, "t") }
        var cursor = syncTasks(f.workspaceId, SyncRequest(cursor = 0, since = 0)).nextCursor
        // The first poll may legitimately repeat rows from transactions at/after xmin; after that it settles.
        cursor = syncTasks(f.workspaceId, SyncRequest(cursor = cursor, since = 0)).nextCursor
        assertTrue(syncTasks(f.workspaceId, SyncRequest(cursor = cursor, since = 0)).rows.isEmpty())
    }

    @Test
    fun `bounded task delta pages keep the cursor until the final page`() {
        val f = fixture()
        transaction { repeat(5) { index -> insertTask(f.workspaceId, f.userA, "paged-$index") } }

        val first = syncTasks(f.workspaceId, SyncRequest(cursor = 0, since = 0, pageSize = 2))
        assertEquals(2, first.rows.size)
        assertNotNull(first.nextPageToken)
        val firstSnapshotCursor = first.nextCursor

        val second = syncTasks(f.workspaceId, SyncRequest(cursor = 0, since = 0, pageSize = 2, pageToken = first.nextPageToken))
        assertEquals(2, second.rows.size)
        assertNotNull(second.nextPageToken)
        assertEquals(firstSnapshotCursor, second.nextCursor)

        val third = syncTasks(f.workspaceId, SyncRequest(cursor = 0, since = 0, pageSize = 2, pageToken = second.nextPageToken))
        assertEquals(1, third.rows.size)
        assertNull(third.nextPageToken)
        assertEquals(firstSnapshotCursor, third.nextCursor)
        assertEquals(5, (first.rows + second.rows + third.rows).map { it.id }.distinct().size)
    }

    @Test
    fun `first paged sync uses transaction keys even when legacy timestamp is zero`() {
        val f = fixture()
        transaction { insertTask(f.workspaceId, f.userA, "legacy timestamp", updatedAt = 0L) }

        val legacy = syncTasks(f.workspaceId, SyncRequest(cursor = null, since = 0L))
        assertTrue(legacy.rows.isEmpty(), "legacy updated_at > 0 does not include a zero timestamp")

        val paged = syncTasks(f.workspaceId, SyncRequest(cursor = null, since = 0L, pageSize = 20))
        assertEquals(listOf("legacy timestamp"), paged.rows.map { it.taskName })
        assertFalse(paged.reset)
    }

    @Test
    fun `workspace delta pages merge active workspaces in bounded order`() {
        val f = fixture()
        val extraIds = transaction {
            (1..4).map { index ->
                val id = WorkspacesTable.insert {
                    it[userId] = f.userA
                    it[workspaceName] = "extra-$index"
                    it[workspaceOwner] = "a"
                    it[workspacePassword] = ""
                }[WorkspacesTable.id]
                WorkspaceMembersTable.insert {
                    it[workspaceId] = id
                    it[userId] = f.userB
                }
                id
            }
        }
        createdWorkspaceIds += extraIds
        val first = transaction {
            workspaceDeltaSync(f.userB, SyncRequest(cursor = 0, since = 0, pageSize = 2))
        }
        assertEquals(2, first.rows.size)
        assertNotNull(first.nextPageToken)

        val second = transaction {
            workspaceDeltaSync(
                f.userB,
                SyncRequest(cursor = 0, since = 0, pageSize = 2, pageToken = first.nextPageToken)
            )
        }
        assertEquals(2, second.rows.size)
        assertNotNull(second.nextPageToken)

        val third = transaction {
            workspaceDeltaSync(
                f.userB,
                SyncRequest(cursor = 0, since = 0, pageSize = 2, pageToken = second.nextPageToken)
            )
        }
        assertEquals(1, third.rows.size)
        assertNull(third.nextPageToken)
        val allIds = (first.rows + second.rows + third.rows).map { it.id }
        assertEquals((listOf(f.workspaceId) + extraIds).sorted(), allIds)
        assertEquals(5, allIds.distinct().size)
        assertEquals(first.nextCursor, second.nextCursor)
        assertEquals(first.nextCursor, third.nextCursor)
    }

    @Test
    fun `reminder outbox job is claimed by only one worker`() {
        val now = System.currentTimeMillis()
        val key = "test-reminder:${UUID.randomUUID()}"
        createdReminderKeys += key
        transaction {
            TaskReminderOutboxTable.insert {
                it[reminderKey] = key
                it[taskId] = 1
                it[workspaceId] = 1
                it[recipientId] = 1
                it[dueDate] = now
                it[title] = "Task due soon"
                it[body] = "Test reminder"
                it[status] = "PENDING"
                it[attempts] = 0
                it[nextAttemptAt] = now
                it[createdAt] = now
            }
        }

        val firstWorker = transaction { claimReminderBatch(now, limit = 1) }
        val secondWorker = transaction { claimReminderBatch(now, limit = 1) }
        assertEquals(listOf(key), firstWorker.map { it.reminderKey })
        assertTrue(secondWorker.none { it.reminderKey == key })
    }

    @Test
    fun `reminder delivery retries transient push failure without duplicating notification`() = runBlocking {
        val f = fixture()
        val now = System.currentTimeMillis()
        val taskId = transaction {
            val id = insertTask(f.workspaceId, f.userA, "retry reminder")
            TasksTable.update({ TasksTable.id eq id }) {
                it[assignedToUserId] = f.userB
                it[dueDate] = now
            }
            id
        }
        val key = reminderKey(taskId, now)
        createdReminderKeys += key
        transaction {
            TaskReminderOutboxTable.insert {
                it[reminderKey] = key
                it[TaskReminderOutboxTable.taskId] = taskId
                it[workspaceId] = f.workspaceId
                it[recipientId] = f.userB
                it[dueDate] = now
                it[title] = "Task due soon"
                it[body] = "Retry reminder body"
                it[status] = "PENDING"
                it[attempts] = 0
                it[nextAttemptAt] = now
                it[createdAt] = now
            }
        }

        deliverReminderBatch(now) { false }
        val failedDelivery = transaction {
            TaskReminderOutboxTable.selectAll()
                .where { TaskReminderOutboxTable.reminderKey eq key }
                .single()
        }
        assertEquals("PENDING", failedDelivery[TaskReminderOutboxTable.status])
        assertEquals(1, failedDelivery[TaskReminderOutboxTable.attempts])
        assertEquals(now + reminderRetryDelay(1), failedDelivery[TaskReminderOutboxTable.nextAttemptAt])
        assertEquals(1L, transaction {
            NotificationsTable.selectAll().where { NotificationsTable.dedupeKey eq key }.count()
        })

        val retryAt = now + reminderRetryDelay(1)
        deliverReminderBatch(retryAt) { true }
        val delivered = transaction {
            TaskReminderOutboxTable.selectAll()
                .where { TaskReminderOutboxTable.reminderKey eq key }
                .single()
        }
        assertEquals("DONE", delivered[TaskReminderOutboxTable.status])
        assertNotNull(delivered[TaskReminderOutboxTable.deliveredAt])
        assertEquals(2, delivered[TaskReminderOutboxTable.attempts])
        assertEquals(1L, transaction {
            NotificationsTable.selectAll().where { NotificationsTable.dedupeKey eq key }.count()
        })
    }

    @Test
    fun `cursor from another database asks the client to reset`() {
        val f = fixture()
        val page = syncTasks(f.workspaceId, SyncRequest(cursor = Long.MAX_VALUE / 2, since = 0))
        assertTrue(page.reset)
        assertTrue(page.rows.isEmpty())
        assertTrue(page.nextCursor < Long.MAX_VALUE / 2)
    }

    @Test
    fun `being added to an existing workspace shows up in workspace sync`() {
        val f = fixture()
        val tag = UUID.randomUUID().toString().take(8)
        val newcomer = transaction {
            UsersTable.insert {
                it[email] = "c-$tag@sync.test"
                it[password] = "x"
                it[username] = "c-$tag"
            }[UsersTable.id]
        }
        createdUserIds += newcomer
        val cursor = transaction { workspaceDeltaSync(newcomer, SyncRequest(cursor = 0, since = 0)) }.nextCursor
        transaction {
            WorkspaceMembersTable.insert {
                it[workspaceId] = f.workspaceId
                it[userId] = newcomer
            }
        }
        val page = transaction { workspaceDeltaSync(newcomer, SyncRequest(cursor = cursor, since = 0)) }
        assertEquals(listOf(f.workspaceId), page.rows.map { it.id })
    }

    @Test
    fun `member removal emits a private workspace tombstone and refreshes remaining members`() {
        val f = fixture()
        val cursorB = transaction {
            workspaceDeltaSync(f.userB, SyncRequest(cursor = 0, since = 0)).nextCursor
        }
        val cursorA = transaction {
            workspaceDeltaSync(f.userA, SyncRequest(cursor = 0, since = 0)).nextCursor
        }

        transaction {
            WorkspaceMembersTable.deleteWhere {
                (WorkspaceMembersTable.workspaceId eq f.workspaceId) and
                    (WorkspaceMembersTable.userId eq f.userB)
            }
            WorkspacesTable.update({ WorkspacesTable.id eq f.workspaceId }) {
                it[updatedAt] = System.currentTimeMillis()
            }
        }

        val removedUserPage = transaction {
            workspaceDeltaSync(f.userB, SyncRequest(cursor = cursorB, since = 0))
        }
        val remainingUserPage = transaction {
            workspaceDeltaSync(f.userA, SyncRequest(cursor = cursorA, since = 0))
        }
        assertEquals(listOf(f.workspaceId to true), removedUserPage.rows.map { it.id to it.isDeleted })
        assertEquals(listOf(f.workspaceId to false), remainingUserPage.rows.map { it.id to it.isDeleted })
    }

    @Test
    fun `dm sync returns edits and tombstones only to the two participants`() {
        val f = fixture()
        val outsider = fixture().userA
        val dmId = transaction {
            DirectMessagesTable.insert {
                it[workspaceId] = f.workspaceId
                it[senderId] = f.userA
                it[receiverId] = f.userB
                it[content] = "hello"
                it[timestamp] = 1L
            }[DirectMessagesTable.id]
        }
        val cursorB = transaction { dmDeltaSync(f.userB, cursor = 0, knownUpToId = 0) }.nextCursor
        transaction {
            DirectMessagesTable.update({ DirectMessagesTable.id eq dmId }) {
                it[isDeleted] = true
                it[content] = ""
            }
        }
        val forB = transaction { dmDeltaSync(f.userB, cursor = cursorB, knownUpToId = 0) }
        assertEquals(listOf(dmId to true), forB.rows.map { it.id to it.isDeleted })
        val forOutsider = transaction { dmDeltaSync(outsider, cursor = 0, knownUpToId = 0) }
        assertTrue(forOutsider.rows.none { it.id == dmId })
    }

    @Test
    fun `dm bootstrap without a cursor only covers messages the device already holds`() {
        val f = fixture()
        val dmId = transaction {
            DirectMessagesTable.insert {
                it[workspaceId] = f.workspaceId
                it[senderId] = f.userA
                it[receiverId] = f.userB
                it[content] = "hello"
                it[timestamp] = 1L
            }[DirectMessagesTable.id]
        }
        val fresh = transaction { dmDeltaSync(f.userB, cursor = null, knownUpToId = 0) }
        assertTrue(fresh.rows.isEmpty(), "fresh install gets history over the socket, not here")
        val upgraded = transaction { dmDeltaSync(f.userB, cursor = null, knownUpToId = dmId) }
        assertTrue(upgraded.rows.any { it.id == dmId })
    }
}
