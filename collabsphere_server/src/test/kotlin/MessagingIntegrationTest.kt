import com.collabsphere.dto.ChannelUnreadCount
import com.collabsphere.dto.MessageResponse
import com.collabsphere.dto.PollView
import com.collabsphere.dto.ScheduledMessageItem
import com.collabsphere.model.DirectMessagesTable
import com.collabsphere.model.MessageTable
import org.jetbrains.exposed.sql.selectAll
import com.collabsphere.dto.SavedMessageItem
import com.collabsphere.model.ChannelsTable
import com.collabsphere.model.UsersTable
import com.collabsphere.model.WorkspaceMembersTable
import com.collabsphere.model.WorkspacesTable
import com.collabsphere.module
import dto.LoginResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID
import kotlin.test.*

/** Channel messaging through auth and real Postgres: moderation, edit stamps, send times and unread counts. */
class MessagingIntegrationTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val createdUserIds = mutableSetOf<Int>()
    private val createdWorkspaceIds = mutableSetOf<Int>()
    private val createdEmails = mutableSetOf<String>()

    private data class Account(val id: Int, val token: String)

    private suspend fun HttpClient.signUp(userName: String): Account {
        val email = "messaging-${UUID.randomUUID()}@example.com"
        createdEmails += email
        post("/api/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123","userName":"$userName"}""")
        }
        transaction { UsersTable.update({ UsersTable.email eq email }) { it[isEmailVerified] = true } }
        val login = post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123"}""")
        }
        val response = json.decodeFromString<LoginResponse>(login.bodyAsText())
        createdUserIds += response.id
        return Account(response.id, response.token!!)
    }

    /** Owner first; every other account joins as a plain member. Returns (workspaceId, channelId). */
    private fun workspaceWithChannel(owner: Int, vararg members: Int): Pair<Int, Int> {
        val ids = transaction {
            val ws = WorkspacesTable.insert {
                it[userId] = owner
                it[workspaceName] = "messaging"
                it[workspaceOwner] = "messaging"
                it[workspacePassword] = ""
            }[WorkspacesTable.id]
            (listOf(owner) + members.toList()).forEach { m ->
                WorkspaceMembersTable.insert { it[workspaceId] = ws; it[userId] = m }
            }
            val channel = ChannelsTable.insert {
                it[userId] = owner
                it[channelName] = "general-${UUID.randomUUID().toString().take(8)}"
                it[workspaceId] = ws
                it[description] = ""
            }[ChannelsTable.id]
            ws to channel
        }
        createdWorkspaceIds += ids.first
        return ids
    }

    @AfterTest
    fun cleanUpFixtureRows() {
        transaction {
            if (createdWorkspaceIds.isNotEmpty()) WorkspacesTable.deleteWhere { WorkspacesTable.id inList createdWorkspaceIds }
            if (createdEmails.isNotEmpty()) UsersTable.deleteWhere { UsersTable.email inList createdEmails }
            if (createdUserIds.isNotEmpty()) UsersTable.deleteWhere { UsersTable.id inList createdUserIds }
        }
    }

    private suspend fun HttpClient.send(author: Account, ws: Int, channel: Int, content: String): MessageResponse {
        val response = post("/api/message") {
            bearerAuth(author.token)
            contentType(ContentType.Application.Json)
            setBody("""{"id":0,"userId":${author.id},"workspaceId":$ws,"channelId":$channel,"userName":"x","content":"$content","status":"Delivered"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun HttpClient.edit(author: Account, message: MessageResponse, content: String, status: String = "Delivered") =
        put("/api/message/${message.id}") {
            bearerAuth(author.token)
            contentType(ContentType.Application.Json)
            setBody("""{"id":${message.id},"userId":${author.id},"workspaceId":${message.workspaceId},"channelId":${message.channelId},"userName":"x","content":"$content","status":"$status"}""")
        }

    private suspend fun HttpClient.remove(actor: Account, message: MessageResponse) =
        delete("/api/message/${message.id}/${actor.id}/${message.workspaceId}/${message.channelId}") { bearerAuth(actor.token) }

    @Test
    fun `owners and admins may delete anyone's channel message, plain members only their own`() = testApplication {
        application { module() }
        val owner = client.signUp("msg-owner")
        val admin = client.signUp("msg-admin")
        val member = client.signUp("msg-member")
        val (ws, channel) = workspaceWithChannel(owner.id, admin.id, member.id)
        transaction {
            WorkspaceMembersTable.update({
                (WorkspaceMembersTable.workspaceId eq ws) and (WorkspaceMembersTable.userId eq admin.id)
            }) { it[role] = "ADMIN" }
        }

        val byOwner = client.send(owner, ws, channel, "owner says hi")
        assertEquals(HttpStatusCode.Forbidden, client.remove(member, byOwner).status, "a member cannot delete someone else's message")

        val byMember1 = client.send(member, ws, channel, "spam one")
        val byMember2 = client.send(member, ws, channel, "spam two")
        assertEquals(HttpStatusCode.OK, client.remove(owner, byMember1).status)
        assertEquals(HttpStatusCode.OK, client.remove(admin, byMember2).status)
        assertEquals(HttpStatusCode.NotFound, client.remove(owner, byMember1).status, "already deleted")

        val own = client.send(member, ws, channel, "mine")
        assertEquals(HttpStatusCode.OK, client.remove(member, own).status)
    }

    @Test
    fun `messages carry their send time and only text changes mark them edited`() = testApplication {
        application { module() }
        val author = client.signUp("msg-author")
        val (ws, channel) = workspaceWithChannel(author.id)
        val before = System.currentTimeMillis()

        val sent = client.send(author, ws, channel, "first")
        assertNotNull(sent.createdAt)
        assertTrue(sent.createdAt!! >= before)
        assertNull(sent.editedAt)

        val statusOnly = client.edit(author, sent, "first", status = "Seen")
        assertEquals(HttpStatusCode.OK, statusOnly.status, statusOnly.bodyAsText())
        assertNull(json.decodeFromString<MessageResponse>(statusOnly.bodyAsText()).editedAt, "a status change is not an edit")

        val edited = json.decodeFromString<MessageResponse>(client.edit(author, sent, "second").bodyAsText())
        assertEquals("second", edited.content)
        assertNotNull(edited.editedAt)
        assertEquals(sent.createdAt, edited.createdAt, "editing keeps the send time")
    }

    @Test
    fun `unread counts are per channel, exclude your own messages and follow your read position`() = testApplication {
        application { module() }
        val owner = client.signUp("unread-owner")
        val reader = client.signUp("unread-reader")
        val (ws, channel) = workspaceWithChannel(owner.id, reader.id)

        val m1 = client.send(owner, ws, channel, "one")
        client.send(owner, ws, channel, "two")
        client.send(owner, ws, channel, "three")
        client.send(reader, ws, channel, "my own")

        suspend fun unread(account: Account): Map<Int, Int> {
            val response = client.get("/api/message/unread/$ws") { bearerAuth(account.token) }
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            return json.decodeFromString<List<ChannelUnreadCount>>(response.bodyAsText()).associate { it.channelId to it.count }
        }

        assertEquals(3, unread(reader)[channel])
        assertEquals(1, unread(owner)[channel], "the reader's message is unread for the owner")

        val mark = client.post("/api/message/read/$ws/$channel") {
            bearerAuth(reader.token)
            contentType(ContentType.Application.Json)
            setBody("""{"lastReadMessageId":${m1.id + 1}}""")
        }
        assertTrue(mark.status.isSuccess(), mark.bodyAsText())
        assertEquals(1, unread(reader)[channel])

        val outsider = client.signUp("unread-outsider")
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/message/unread/$ws") { bearerAuth(outsider.token) }.status)
    }

    @Test
    fun `saved messages are private, need access to the message and drop deleted ones`() = testApplication {
        application { module() }
        val owner = client.signUp("saved-owner")
        val outsider = client.signUp("saved-outsider")
        val (ws, channel) = workspaceWithChannel(owner.id)
        val message = client.send(owner, ws, channel, "keep this")

        suspend fun save(account: Account) = client.post("/api/saved") {
            bearerAuth(account.token)
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"CHANNEL","messageId":${message.id}}""")
        }
        suspend fun list(account: Account): List<SavedMessageItem> =
            json.decodeFromString(client.get("/api/saved") { bearerAuth(account.token) }.bodyAsText())

        assertEquals(HttpStatusCode.NotFound, save(outsider).status, "outsiders cannot save another workspace's message")
        assertEquals(HttpStatusCode.OK, save(owner).status)
        assertEquals(HttpStatusCode.OK, save(owner).status, "saving twice is harmless")

        val saved = list(owner).single()
        assertEquals(message.id, saved.messageId)
        assertEquals(channel, saved.channelId)
        assertEquals("messaging", saved.workspaceName)
        assertEquals("keep this", saved.content)
        assertTrue(list(outsider).isEmpty())

        assertEquals(HttpStatusCode.OK, client.remove(owner, message).status)
        assertTrue(list(owner).isEmpty(), "deleted messages leave the saved list")

        val second = client.send(owner, ws, channel, "second")
        client.post("/api/saved") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"CHANNEL","messageId":${second.id}}""")
        }
        assertEquals(1, list(owner).size)
        assertEquals(HttpStatusCode.OK, client.delete("/api/saved/CHANNEL/${second.id}") { bearerAuth(owner.token) }.status)
        assertTrue(list(owner).isEmpty())
    }

    @Test
    fun `thread replies attach to the root, flatten nested replies and stay out of unread counts`() = testApplication {
        application { module() }
        val owner = client.signUp("thread-owner")
        val reader = client.signUp("thread-reader")
        val (ws, channel) = workspaceWithChannel(owner.id, reader.id)
        val (_, otherChannel) = workspaceWithChannel(owner.id)

        suspend fun reply(author: Account, rootId: Int, inChannel: Int = channel, content: String) = client.post("/api/message") {
            bearerAuth(author.token)
            contentType(ContentType.Application.Json)
            setBody("""{"id":0,"userId":${author.id},"workspaceId":$ws,"channelId":$inChannel,"userName":"x","content":"$content","status":"Delivered","threadRootId":$rootId}""")
        }

        val root = client.send(owner, ws, channel, "root")
        val first = reply(owner, root.id, content = "in thread")
        assertEquals(HttpStatusCode.Created, first.status, first.bodyAsText())
        val firstReply = json.decodeFromString<MessageResponse>(first.bodyAsText())
        assertEquals(root.id, firstReply.threadRootId)

        val nested = json.decodeFromString<MessageResponse>(reply(owner, firstReply.id, content = "nested").bodyAsText())
        assertEquals(root.id, nested.threadRootId, "replying to a reply stays in the same thread")

        assertEquals(HttpStatusCode.BadRequest, reply(owner, root.id, inChannel = otherChannel, content = "wrong").status)

        val unread = json.decodeFromString<List<ChannelUnreadCount>>(
            client.get("/api/message/unread/$ws") { bearerAuth(reader.token) }.bodyAsText()
        ).associate { it.channelId to it.count }
        assertEquals(1, unread[channel], "only the root counts as unread in the channel")
    }

    @Test
    fun `polls count one choice per voter unless multiple answers are allowed and close for the author only`() = testApplication {
        application { module() }
        val owner = client.signUp("poll-owner")
        val voter = client.signUp("poll-voter")
        val outsider = client.signUp("poll-outsider")
        val (ws, channel) = workspaceWithChannel(owner.id, voter.id)
        val message = client.send(owner, ws, channel, "Lunch?")

        val created = client.post("/api/message/${message.id}/poll") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody("""{"question":"Lunch?","options":["Pizza","Sushi","Tacos"]}""")
        }
        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())

        suspend fun vote(account: Account, vararg choices: Int) = client.post("/api/message/${message.id}/poll/vote") {
            bearerAuth(account.token)
            contentType(ContentType.Application.Json)
            setBody("""{"optionIndexes":[${choices.joinToString(",")}]}""")
        }

        assertEquals(HttpStatusCode.OK, vote(voter, 0).status)
        val switched = json.decodeFromString<PollView>(vote(voter, 1).bodyAsText())
        assertEquals(listOf(0, 1, 0), switched.options.map { it.votes }, "changing a vote moves it")
        assertEquals(listOf(1), switched.myVotes)
        assertEquals(HttpStatusCode.BadRequest, vote(voter, 0, 2).status, "single-choice poll")
        assertEquals(HttpStatusCode.BadRequest, vote(voter, 7).status)
        assertEquals(HttpStatusCode.Forbidden, vote(outsider, 0).status)

        val listed = json.decodeFromString<List<PollView>>(
            client.get("/api/message/polls/$ws/$channel") { bearerAuth(owner.token) }.bodyAsText()
        ).single()
        assertEquals(1, listed.totalVoters)
        assertTrue(listed.myVotes.isEmpty())

        assertEquals(HttpStatusCode.Forbidden, client.post("/api/message/${message.id}/poll/close") { bearerAuth(voter.token) }.status)
        assertEquals(HttpStatusCode.OK, client.post("/api/message/${message.id}/poll/close") { bearerAuth(owner.token) }.status)
        assertEquals(HttpStatusCode.Conflict, vote(voter, 2).status, "closed polls take no votes")
    }

    @Test
    fun `scheduled messages wait for their time, can be cancelled and are sent by the dispatcher`() = testApplication {
        application { module() }
        val owner = client.signUp("sched-owner")
        val partner = client.signUp("sched-partner")
        val outsider = client.signUp("sched-outsider")
        val (ws, channel) = workspaceWithChannel(owner.id, partner.id)
        val sendAt = System.currentTimeMillis() + 120_000L

        suspend fun schedule(account: Account, body: String) = client.post("/api/scheduled") {
            bearerAuth(account.token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        val channelBody = """{"kind":"CHANNEL","workspaceId":$ws,"channelId":$channel,"content":"standup in 5","sendAt":$sendAt}"""
        assertEquals(HttpStatusCode.Created, schedule(owner, channelBody).status)
        assertEquals(HttpStatusCode.Forbidden, schedule(outsider, channelBody).status)
        assertEquals(
            HttpStatusCode.BadRequest,
            schedule(owner, """{"kind":"CHANNEL","workspaceId":$ws,"channelId":$channel,"content":"now","sendAt":${System.currentTimeMillis()}}""").status
        )
        val dm = json.decodeFromString<ScheduledMessageItem>(
            schedule(owner, """{"kind":"DM","workspaceId":$ws,"receiverId":${partner.id},"content":"happy birthday","sendAt":$sendAt}""").bodyAsText()
        )
        val cancelled = json.decodeFromString<ScheduledMessageItem>(
            schedule(owner, """{"kind":"DM","workspaceId":$ws,"receiverId":${partner.id},"content":"never mind","sendAt":$sendAt}""").bodyAsText()
        )
        assertEquals(HttpStatusCode.OK, client.delete("/api/scheduled/${cancelled.id}") { bearerAuth(owner.token) }.status)

        val pending = json.decodeFromString<List<ScheduledMessageItem>>(client.get("/api/scheduled?workspaceId=$ws") { bearerAuth(owner.token) }.bodyAsText())
        assertEquals(2, pending.size)

        assertEquals(0, plugins.dispatchDueScheduledMessages(System.currentTimeMillis()), "nothing is due yet")
        assertEquals(2, plugins.dispatchDueScheduledMessages(sendAt + 1))

        transaction {
            assertTrue(MessageTable.selectAll().where { (MessageTable.channelId eq channel) and (MessageTable.content eq "standup in 5") }.count() == 1L)
            assertTrue(DirectMessagesTable.selectAll().where { (DirectMessagesTable.senderId eq owner.id) and (DirectMessagesTable.content eq "happy birthday") }.count() == 1L)
            assertTrue(DirectMessagesTable.selectAll().where { DirectMessagesTable.content eq "never mind" }.count() == 0L)
        }
        assertTrue(json.decodeFromString<List<ScheduledMessageItem>>(client.get("/api/scheduled") { bearerAuth(owner.token) }.bodyAsText()).isEmpty())
        assertEquals(dm.receiverId, partner.id)
    }
}
