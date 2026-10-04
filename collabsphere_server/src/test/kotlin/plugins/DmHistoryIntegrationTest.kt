package plugins

import com.collabsphere.DatabaseFactory
import com.collabsphere.model.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID
import kotlin.test.*

/** The initial-history and catch-up queries the DM socket sends on connect (real Postgres). */
class DmHistoryIntegrationTest {

    companion object {
        init {
            DatabaseFactory.init()
        }
    }

    private val tag = UUID.randomUUID().toString().take(8)

    private fun user(name: String) = transaction {
        UsersTable.insert {
            it[email] = "$name-$tag@dm.test"
            it[password] = "x"
            it[username] = "$name-$tag"
        }[UsersTable.id]
    }

    private fun workspace(owner: Int) = transaction {
        WorkspacesTable.insert {
            it[userId] = owner
            it[workspaceName] = "ws-$tag"
            it[workspaceOwner] = "o"
            it[workspacePassword] = ""
        }[WorkspacesTable.id]
    }

    private fun dm(ws: Int, from: Int, to: Int, text: String) = transaction {
        DirectMessagesTable.insert {
            it[workspaceId] = ws
            it[senderId] = from
            it[receiverId] = to
            it[content] = text
            it[timestamp] = System.currentTimeMillis()
        }[DirectMessagesTable.id]
    }

    @Test
    fun `initial history keeps the newest N of each conversation, oldest first, without tombstones`() {
        val me = user("me")
        val alice = user("alice")
        val bob = user("bob")
        val ws = workspace(me)
        val withAlice = (1..4).map { i -> if (i % 2 == 0) dm(ws, me, alice, "a$i") else dm(ws, alice, me, "a$i") }
        val withBob = (1..2).map { i -> dm(ws, bob, me, "b$i") }
        val unrelated = dm(ws, alice, bob, "not mine")
        // Deleting the newest message to Alice means the window shifts back by one.
        transaction { DirectMessagesTable.update({ DirectMessagesTable.id eq withAlice.last() }) { it[isDeleted] = true } }

        val history = transaction { initialDmHistory(me, perConversation = 2) }

        assertEquals(listOf(withAlice[1], withAlice[2], withBob[0], withBob[1]).sorted(), history.map { it.id })
        assertTrue(history.all { it.action == "HISTORY" })
        assertTrue(history.none { it.id == unrelated })
        assertEquals(history.map { it.id }.sortedBy { it }, history.map { it.id }, "must be oldest first")
    }

    @Test
    fun `same partner in two workspaces counts as two conversations`() {
        val me = user("me")
        val alice = user("alice")
        val ws1 = workspace(me)
        val ws2 = workspace(me)
        val inWs1 = dm(ws1, alice, me, "x")
        val inWs2 = dm(ws2, alice, me, "y")
        val history = transaction { initialDmHistory(me, perConversation = 1) }
        assertEquals(listOf(inWs1, inWs2), history.map { it.id })
    }

    @Test
    fun `catch-up pages through everything newer than the device's newest id`() {
        val me = user("me")
        val alice = user("alice")
        val ws = workspace(me)
        val known = dm(ws, alice, me, "known")
        val newer = (1..5).map { dm(ws, alice, me, "n$it") }

        val pages = mutableListOf<List<Int>>()
        var after = known
        while (true) {
            val page = transaction { dmCatchUpPage(me, after, limit = 2) }
            pages += page.map { it.id!! }
            if (page.size < 2) break
            after = page.last().id!!
        }
        assertEquals(newer, pages.flatten())
        assertEquals(listOf(2, 2, 1), pages.map { it.size })
    }

    @Test
    fun `reply id is null when the column is null, not zero`() {
        val me = user("me")
        val alice = user("alice")
        val ws = workspace(me)
        dm(ws, alice, me, "x")
        val row = transaction { initialDmHistory(me, perConversation = 1) }.single()
        assertNull(row.replyToId)
    }
}
