import com.collabsphere.model.ChannelReadStateTable
import com.collabsphere.model.ChannelsTable
import com.collabsphere.model.MessageTable
import com.collabsphere.model.NotesTable
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID
import kotlin.test.*

/**
 * Request/response contracts the Android client depends on, checked through auth and real Postgres:
 * invitations, read state, message and note edits, user search and password changes.
 */
class ApiContractIntegrationTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val createdUserIds = mutableSetOf<Int>()
    private val createdWorkspaceIds = mutableSetOf<Int>()
    private val createdEmails = mutableSetOf<String>()

    private data class Account(val id: Int, val token: String, val email: String, val userName: String)

    private suspend fun HttpClient.signUp(userName: String = "contract", password: String = "password123"): Account {
        val email = "contract-${UUID.randomUUID()}@example.com"
        createdEmails += email
        post("/api/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password","userName":"$userName"}""")
        }
        transaction { UsersTable.update({ UsersTable.email eq email }) { it[isEmailVerified] = true } }
        val login = post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password"}""")
        }
        val response = json.decodeFromString<LoginResponse>(login.bodyAsText())
        createdUserIds += response.id
        return Account(response.id, response.token!!, email, userName)
    }

    private fun workspaceWith(vararg members: Int): Int {
        val workspaceId = transaction {
            val ws = WorkspacesTable.insert {
                it[userId] = members.first()
                it[workspaceName] = "contract"
                it[workspaceOwner] = "contract"
                it[workspacePassword] = ""
            }[WorkspacesTable.id]
            members.forEach { m -> WorkspaceMembersTable.insert { it[workspaceId] = ws; it[userId] = m } }
            ws
        }
        createdWorkspaceIds += workspaceId
        return workspaceId
    }

    private fun channelIn(workspaceId: Int, ownerId: Int): Int = transaction {
        ChannelsTable.insert {
            it[userId] = ownerId
            it[channelName] = "contract-${UUID.randomUUID()}"
            it[ChannelsTable.workspaceId] = workspaceId
            it[description] = ""
        }[ChannelsTable.id]
    }

    private fun messageIn(workspaceId: Int, channelId: Int, authorId: Int, content: String = "hello"): Int = transaction {
        MessageTable.insert {
            it[userId] = authorId
            it[MessageTable.workspaceId] = workspaceId
            it[MessageTable.channelId] = channelId
            it[userName] = "contract"
            it[MessageTable.content] = content
            it[status] = "Delivered"
        }[MessageTable.id]
    }

    @AfterTest
    fun cleanUpFixtureRows() {
        transaction {
            if (createdWorkspaceIds.isNotEmpty()) WorkspacesTable.deleteWhere { WorkspacesTable.id inList createdWorkspaceIds }
            if (createdEmails.isNotEmpty()) UsersTable.deleteWhere { UsersTable.email inList createdEmails }
            if (createdUserIds.isNotEmpty()) UsersTable.deleteWhere { UsersTable.id inList createdUserIds }
        }
    }

    // ── Invitations ──────────────────────────────────────────────────────────

    @Test
    fun `pending invitations list the invite with the inviter's name`() = testApplication {
        application { module() }
        val owner = client.signUp(userName = "inviter-owner")
        val invitee = client.signUp(userName = "invitee")
        val workspaceId = workspaceWith(owner.id)

        val invite = client.post("/api/workspace/invitations/$workspaceId") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody("""{"email":"${invitee.email}"}""")
        }
        assertEquals(HttpStatusCode.Created, invite.status, invite.bodyAsText())

        val pending = client.get("/api/workspace/invitations/pending") { bearerAuth(invitee.token) }
        val body = pending.bodyAsText()
        assertEquals(HttpStatusCode.OK, pending.status, body)
        val invites = json.parseToJsonElement(body).jsonArray
        assertEquals(1, invites.size)
        val only = invites.single().jsonObject
        assertEquals(workspaceId, only["workspaceId"]!!.jsonPrimitive.int)
        assertEquals("inviter-owner", only["inviterName"]!!.jsonPrimitive.content)
    }

    @Test
    fun `pending invitations name the admin who sent the invite rather than the workspace owner`() = testApplication {
        application { module() }
        val owner = client.signUp(userName = "the-owner")
        val admin = client.signUp(userName = "the-admin")
        val invitee = client.signUp(userName = "invitee")
        val workspaceId = workspaceWith(owner.id, admin.id)
        transaction {
            WorkspaceMembersTable.update({
                (WorkspaceMembersTable.workspaceId eq workspaceId) and (WorkspaceMembersTable.userId eq admin.id)
            }) { it[role] = "ADMIN" }
        }

        val invite = client.post("/api/workspace/invitations/$workspaceId") {
            bearerAuth(admin.token)
            contentType(ContentType.Application.Json)
            setBody("""{"email":"${invitee.email}"}""")
        }
        assertEquals(HttpStatusCode.Created, invite.status, invite.bodyAsText())

        val pending = client.get("/api/workspace/invitations/pending") { bearerAuth(invitee.token) }
        assertEquals(HttpStatusCode.OK, pending.status, pending.bodyAsText())
        val only = json.parseToJsonElement(pending.bodyAsText()).jsonArray.single().jsonObject
        assertEquals("the-admin", only["inviterName"]!!.jsonPrimitive.content)
    }

    @Test
    fun `accepted invitation adds the invitee to the member list`() = testApplication {
        application { module() }
        val owner = client.signUp(userName = "owner")
        val invitee = client.signUp(userName = "invitee")
        val workspaceId = workspaceWith(owner.id)

        val invite = client.post("/api/workspace/invitations/$workspaceId") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody("""{"email":"${invitee.email}"}""")
        }
        val invitationId = json.parseToJsonElement(invite.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.int

        val accept = client.post("/api/workspace/invitations/$invitationId/accept") { bearerAuth(invitee.token) }
        assertEquals(HttpStatusCode.OK, accept.status, accept.bodyAsText())
        val again = client.post("/api/workspace/invitations/$invitationId/accept") { bearerAuth(invitee.token) }
        assertEquals(HttpStatusCode.BadRequest, again.status)

        val members = client.get("/api/workspace/members/$workspaceId") { bearerAuth(invitee.token) }
        assertEquals(HttpStatusCode.OK, members.status, members.bodyAsText())
        val memberIds = json.parseToJsonElement(members.bodyAsText()).jsonArray
            .map { it.jsonObject["userId"]!!.jsonPrimitive.int }.toSet()
        assertEquals(setOf(owner.id, invitee.id), memberIds)
    }

    @Test
    fun `invite code joins the workspace once and cannot be reused`() = testApplication {
        application { module() }
        val owner = client.signUp(userName = "owner")
        val invitee = client.signUp(userName = "invitee")
        val workspaceId = workspaceWith(owner.id)

        val invite = client.post("/api/workspace/invitations/$workspaceId") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody("""{"email":"${invitee.email}"}""")
        }
        val code = json.parseToJsonElement(invite.bodyAsText()).jsonObject["inviteCode"]!!.jsonPrimitive.content
        assertTrue(code.matches(Regex("^[A-Z2-9]{6}$")), "unexpected invite code format: $code")

        val join = client.post("/api/workspace/join-by-code") {
            bearerAuth(invitee.token)
            contentType(ContentType.Application.Json)
            setBody("""{"inviteCode":"${code.lowercase()}"}""")
        }
        assertEquals(HttpStatusCode.OK, join.status, join.bodyAsText())
        assertEquals(workspaceId, json.parseToJsonElement(join.bodyAsText()).jsonObject["workspaceId"]!!.jsonPrimitive.int)

        val reuse = client.post("/api/workspace/join-by-code") {
            bearerAuth(invitee.token)
            contentType(ContentType.Application.Json)
            setBody("""{"inviteCode":"$code"}""")
        }
        assertEquals(HttpStatusCode.NotFound, reuse.status)
    }

    @Test
    fun `invitations to a workspace the caller cannot moderate are refused`() = testApplication {
        application { module() }
        val owner = client.signUp()
        val member = client.signUp()
        val workspaceId = workspaceWith(owner.id, member.id)

        val invite = client.post("/api/workspace/invitations/$workspaceId") {
            bearerAuth(member.token)
            contentType(ContentType.Application.Json)
            setBody("""{"email":"someone-${UUID.randomUUID()}@example.com"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, invite.status)

        val malformed = client.post("/api/workspace/invitations/$workspaceId") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody("""{"email":"not-an-email"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
    }

    // ── Channel read state ───────────────────────────────────────────────────

    @Test
    fun `read states of a channel in another workspace are not readable`() = testApplication {
        application { module() }
        val outsider = client.signUp()
        val insider = client.signUp(userName = "insider")
        val outsiderWorkspace = workspaceWith(outsider.id)
        val privateWorkspace = workspaceWith(insider.id)
        val privateChannel = channelIn(privateWorkspace, insider.id)
        val message = messageIn(privateWorkspace, privateChannel, insider.id)
        transaction {
            ChannelReadStateTable.insert {
                it[userId] = insider.id
                it[channelId] = privateChannel
                it[lastReadMessageId] = message
                it[updatedAt] = System.currentTimeMillis()
            }
        }

        // The outsider is a member of their own workspace and names the other workspace's channel.
        val response = client.get("/api/message/read/$outsiderWorkspace/$privateChannel") { bearerAuth(outsider.token) }
        val body = response.bodyAsText()
        assertFalse(body.contains("insider"), "read state of another workspace leaked: $body")
        assertEquals(HttpStatusCode.NotFound, response.status, body)

        // A real member still sees the read state.
        val own = client.get("/api/message/read/$privateWorkspace/$privateChannel") { bearerAuth(insider.token) }
        assertEquals(HttpStatusCode.OK, own.status)
        assertEquals(1, json.parseToJsonElement(own.bodyAsText()).jsonArray.size)
    }

    // ── Channel messages ─────────────────────────────────────────────────────

    @Test
    fun `channel message author name comes from the account, not the request`() = testApplication {
        application { module() }
        val author = client.signUp(userName = "real-author")
        val workspaceId = workspaceWith(author.id)
        val channelId = channelIn(workspaceId, author.id)

        val response = client.post("/api/message") {
            bearerAuth(author.token)
            contentType(ContentType.Application.Json)
            setBody("""{"id":0,"userId":${author.id},"workspaceId":$workspaceId,"channelId":$channelId,"userName":"Workspace Admin","content":"hi","status":"Delivered"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        val created = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("real-author", created["userName"]!!.jsonPrimitive.content)
        val stored = transaction {
            MessageTable.selectAll().where { MessageTable.id eq created["id"]!!.jsonPrimitive.int }.single()[MessageTable.userName]
        }
        assertEquals("real-author", stored)
    }

    @Test
    fun `message edits are validated and answer with the stored message`() = testApplication {
        application { module() }
        val author = client.signUp(userName = "editor")
        val workspaceId = workspaceWith(author.id)
        val channelId = channelIn(workspaceId, author.id)
        val messageId = messageIn(workspaceId, channelId, author.id, content = "original")
        fun body(content: String, ws: Int = workspaceId, ch: Int = channelId) =
            """{"id":$messageId,"userId":${author.id},"workspaceId":$ws,"channelId":$ch,"userName":"spoofed","content":"$content","status":"Delivered"}"""

        val blank = client.put("/api/message/$messageId") {
            bearerAuth(author.token); contentType(ContentType.Application.Json); setBody(body("   "))
        }
        assertEquals(HttpStatusCode.BadRequest, blank.status)
        val tooLong = client.put("/api/message/$messageId") {
            bearerAuth(author.token); contentType(ContentType.Application.Json); setBody(body("x".repeat(4001)))
        }
        assertEquals(HttpStatusCode.BadRequest, tooLong.status)

        // The response describes the stored row, not whatever ids/name the request carried.
        val edit = client.put("/api/message/$messageId") {
            bearerAuth(author.token); contentType(ContentType.Application.Json); setBody(body("edited", ws = 999_999, ch = 999_999))
        }
        assertEquals(HttpStatusCode.OK, edit.status, edit.bodyAsText())
        val edited = json.parseToJsonElement(edit.bodyAsText()).jsonObject
        assertEquals("edited", edited["content"]!!.jsonPrimitive.content)
        assertEquals(workspaceId, edited["workspaceId"]!!.jsonPrimitive.int)
        assertEquals(channelId, edited["channelId"]!!.jsonPrimitive.int)
        assertEquals("contract", edited["userName"]!!.jsonPrimitive.content)

        transaction { MessageTable.update({ MessageTable.id eq messageId }) { it[isDeleted] = true; it[content] = "" } }
        val editDeleted = client.put("/api/message/$messageId") {
            bearerAuth(author.token); contentType(ContentType.Application.Json); setBody(body("resurrected"))
        }
        assertEquals(HttpStatusCode.NotFound, editDeleted.status)
        assertEquals("", transaction { MessageTable.selectAll().where { MessageTable.id eq messageId }.single()[MessageTable.content] })
    }

    // ── Notes ────────────────────────────────────────────────────────────────

    @Test
    fun `note edit keeps the author and pin state in its response and refuses deleted notes`() = testApplication {
        application { module() }
        val author = client.signUp()
        val editor = client.signUp()
        val workspaceId = workspaceWith(author.id, editor.id)
        val noteId = transaction {
            NotesTable.insert {
                it[userIdNotes] = author.id
                it[NotesTable.workspaceId] = workspaceId
                it[notesName] = "spec"
                it[notesDescription] = "body"
                it[isPinned] = true
            }[NotesTable.id]
        }
        fun body(name: String) = """{"userId":${editor.id},"workspaceId":$workspaceId,"notesName":"$name","description":"new body"}"""

        val edit = client.put("/api/notes/$noteId") {
            bearerAuth(editor.token); contentType(ContentType.Application.Json); setBody(body("spec v2"))
        }
        assertEquals(HttpStatusCode.OK, edit.status, edit.bodyAsText())
        val edited = json.parseToJsonElement(edit.bodyAsText()).jsonObject
        assertEquals(author.id, edited["userId"]!!.jsonPrimitive.int)
        assertTrue(edited["isPinned"]!!.jsonPrimitive.boolean)
        assertEquals("spec v2", edited["notesName"]!!.jsonPrimitive.content)

        transaction { NotesTable.update({ NotesTable.id eq noteId }) { it[isDeleted] = true } }
        val editDeleted = client.put("/api/notes/$noteId") {
            bearerAuth(editor.token); contentType(ContentType.Application.Json); setBody(body("spec v3"))
        }
        assertEquals(HttpStatusCode.NotFound, editDeleted.status)
    }

    // ── Client-fixable vs. retryable failures ────────────────────────────────

    @Test
    fun `channel create rejects invalid names with 4xx`() = testApplication {
        application { module() }
        val me = client.signUp()
        val workspaceId = workspaceWith(me.id)
        fun body(name: String, key: String = UUID.randomUUID().toString()) =
            """{"id":0,"userId":${me.id},"channelName":"$name","workspaceId":$workspaceId,"description":"","idempotencyKey":"$key"}"""

        val blank = client.post("/api/channels") { bearerAuth(me.token); contentType(ContentType.Application.Json); setBody(body("  ")) }
        assertEquals(HttpStatusCode.BadRequest, blank.status)
        val tooLong = client.post("/api/channels") { bearerAuth(me.token); contentType(ContentType.Application.Json); setBody(body("c".repeat(256))) }
        assertEquals(HttpStatusCode.BadRequest, tooLong.status)

        val first = client.post("/api/channels") { bearerAuth(me.token); contentType(ContentType.Application.Json); setBody(body("general")) }
        assertEquals(HttpStatusCode.Created, first.status, first.bodyAsText())
        val duplicate = client.post("/api/channels") { bearerAuth(me.token); contentType(ContentType.Application.Json); setBody(body("general")) }
        assertEquals(HttpStatusCode.Conflict, duplicate.status)

        val malformed = client.post("/api/channels") { bearerAuth(me.token); contentType(ContentType.Application.Json); setBody("{not json") }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
    }

    @Test
    fun `workspace create reusing a request id for different fields is a conflict`() = testApplication {
        application { module() }
        val me = client.signUp()
        val requestId = UUID.randomUUID().toString()
        fun body(name: String) = """{"workspaceName":"$name","workspaceOwner":"me","workspacePassword":"pw-123456","clientRequestId":"$requestId"}"""

        val first = client.post("/api/workspace/create") { bearerAuth(me.token); contentType(ContentType.Application.Json); setBody(body("alpha")) }
        assertEquals(HttpStatusCode.Created, first.status, first.bodyAsText())
        createdWorkspaceIds += json.parseToJsonElement(first.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.int

        val retry = client.post("/api/workspace/create") { bearerAuth(me.token); contentType(ContentType.Application.Json); setBody(body("alpha")) }
        assertEquals(HttpStatusCode.Created, retry.status)
        val misuse = client.post("/api/workspace/create") { bearerAuth(me.token); contentType(ContentType.Application.Json); setBody(body("beta")) }
        assertEquals(HttpStatusCode.Conflict, misuse.status)
    }

    // ── Users ────────────────────────────────────────────────────────────────

    @Test
    fun `user search matches usernames case-insensitively`() = testApplication {
        application { module() }
        val searcher = client.signUp()
        val marker = UUID.randomUUID().toString().take(8)
        val target = client.signUp(userName = "CaseTarget$marker")

        val response = client.get("/api/user/search?q=casetarget${marker.uppercase()}") { bearerAuth(searcher.token) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val ids = json.parseToJsonElement(response.bodyAsText()).jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.int }
        assertEquals(listOf(target.id), ids)
    }

    @Test
    fun `changing the password returns a fresh token and revokes the old one`() = testApplication {
        application { module() }
        val me = client.signUp(userName = "rotator", password = "old-password")

        val change = client.post("/api/user/profile") {
            bearerAuth(me.token)
            contentType(ContentType.Application.Json)
            setBody("""{"userId":${me.id},"userName":"rotator","currentPassword":"old-password","newPassword":"new-password"}""")
        }
        assertEquals(HttpStatusCode.OK, change.status, change.bodyAsText())
        val freshToken = (json.parseToJsonElement(change.bodyAsText()) as JsonObject)["token"]!!.jsonPrimitive.content

        val withOld = client.get("/api/user/profile") { bearerAuth(me.token) }
        assertEquals(HttpStatusCode.Unauthorized, withOld.status)
        val withFresh = client.get("/api/user/profile") { bearerAuth(freshToken) }
        assertEquals(HttpStatusCode.OK, withFresh.status)

        // A profile edit that leaves the password alone keeps the session and issues no token.
        val rename = client.post("/api/user/profile") {
            bearerAuth(freshToken)
            contentType(ContentType.Application.Json)
            setBody("""{"userId":${me.id},"userName":"rotator2"}""")
        }
        assertEquals(HttpStatusCode.OK, rename.status)
        val renameToken = (json.parseToJsonElement(rename.bodyAsText()) as JsonObject)["token"]
        assertTrue(renameToken == null || renameToken is JsonNull, "unexpected token: $renameToken")
        assertEquals(HttpStatusCode.OK, client.get("/api/user/profile") { bearerAuth(freshToken) }.status)
    }
}
