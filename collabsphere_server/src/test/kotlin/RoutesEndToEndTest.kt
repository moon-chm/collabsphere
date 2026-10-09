import com.collabsphere.model.UsersTable
import com.collabsphere.model.WorkspaceMembersTable
import com.collabsphere.model.WorkspacesTable
import com.collabsphere.model.NotificationsTable
import com.collabsphere.model.WorkspaceMembershipStateTable
import com.collabsphere.util.PasswordHasher
import com.collabsphere.module
import dto.LoginResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.util.UUID
import kotlin.test.*

/** The sync, upload and download routes as the app calls them, through auth and real Postgres. */
class RoutesEndToEndTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val createdUserIds = mutableSetOf<Int>()
    private val createdWorkspaceIds = mutableSetOf<Int>()
    private val createdEmails = mutableSetOf<String>()

    private suspend fun HttpClient.signUp(): Pair<Int, String> {
        val email = "e2e-${UUID.randomUUID()}@example.com"
        createdEmails += email
        post("/api/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123","userName":"e2e"}""")
        }
        transaction { UsersTable.update({ UsersTable.email eq email }) { it[isEmailVerified] = true } }
        val login = post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123"}""")
        }
        val response = json.decodeFromString<LoginResponse>(login.bodyAsText())
        createdUserIds += response.id
        return response.id to response.token!!
    }

    private fun workspaceWith(vararg members: Int): Int {
        val workspaceId = transaction {
            val ws = WorkspacesTable.insert {
                it[userId] = members.first()
                it[workspaceName] = "e2e"
                it[workspaceOwner] = "e2e"
                it[workspacePassword] = ""
            }[WorkspacesTable.id]
            members.forEach { m -> WorkspaceMembersTable.insert { it[workspaceId] = ws; it[userId] = m } }
            ws
        }
        createdWorkspaceIds += workspaceId
        return workspaceId
    }

    private fun ownedWorkspace(ownerId: Int, name: String, password: String): Int {
        val workspaceId = transaction {
            val id = WorkspacesTable.insert {
                it[userId] = ownerId
                it[workspaceName] = name
                it[workspaceOwner] = "e2e"
                it[workspacePassword] = PasswordHasher.hash(password)
            }[WorkspacesTable.id]
            WorkspaceMembersTable.insert {
                it[WorkspaceMembersTable.workspaceId] = id
                it[WorkspaceMembersTable.userId] = ownerId
            }
            id
        }
        createdWorkspaceIds += workspaceId
        return workspaceId
    }

    @AfterTest
    fun cleanUpFixtureRows() {
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
            if (createdEmails.isNotEmpty()) UsersTable.deleteWhere { UsersTable.email inList createdEmails }
            if (createdUserIds.isNotEmpty()) UsersTable.deleteWhere { UsersTable.id inList createdUserIds }
        }
    }

    @Test
    fun `workspace delete targets only the requested workspace id`() = testApplication {
        application { module() }
        val (me, token) = client.signUp()
        val sharedName = "duplicate-${UUID.randomUUID()}"
        val password = "workspace-password"
        val requestedId = ownedWorkspace(me, sharedName, password)
        val otherId = ownedWorkspace(me, sharedName, password)

        try {
            val nameOnly = client.delete("/api/workspace/delete?workspaceName=$sharedName") {
                bearerAuth(token)
                header("X-Workspace-Password", password)
            }
            assertEquals(HttpStatusCode.BadRequest, nameOnly.status)

            val response = client.delete("/api/workspace/delete?workspaceId=$requestedId") {
                bearerAuth(token)
                header("X-Workspace-Password", password)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val deletedIds = json.parseToJsonElement(response.bodyAsText()).jsonObject["deletedIds"]!!
                .jsonArray.map { it.jsonPrimitive.content.toInt() }
            assertEquals(listOf(requestedId), deletedIds)

            val deletedStates = transaction {
                listOf(requestedId, otherId).associateWith { id ->
                    WorkspacesTable.selectAll().where { WorkspacesTable.id eq id }
                        .single()[WorkspacesTable.isDeleted]
                }
            }
            assertEquals(true, deletedStates[requestedId])
            assertEquals(false, deletedStates[otherId])
        } finally {
            transaction { UsersTable.deleteWhere { UsersTable.id eq me } }
        }
    }

    @Test
    fun `sync endpoints hand out cursors and accept them back`() = testApplication {
        application { module() }
        val (me, token) = client.signUp()
        val ws = workspaceWith(me)

        val legacy = client.get("/api/tasks/sync/$ws?since=0") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, legacy.status)
        val cursor = assertNotNull(legacy.headers["X-Sync-Cursor"]).toLong()

        val withCursor = client.get("/api/tasks/sync/$ws?since=0&cursor=$cursor") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, withCursor.status)
        assertNotNull(withCursor.headers["X-Sync-Cursor"])
        assertNull(withCursor.headers["X-Sync-Reset"])

        val foreign = client.get("/api/tasks/sync/$ws?cursor=${Long.MAX_VALUE / 2}") { bearerAuth(token) }
        assertEquals("true", foreign.headers["X-Sync-Reset"])

        for (path in listOf("/api/workspace/sync/$me", "/api/channels/sync/$ws", "/api/notes/sync/$ws",
            "/api/file/updates?workspaceId=$ws&lastSyncTime=0", "/api/dm/sync?cursor=0", "/api/message/history/$ws/0")) {
            val r = client.get(path) { bearerAuth(token) }
            assertEquals(HttpStatusCode.OK, r.status, path)
            assertNotNull(r.headers["X-Sync-Cursor"], path)
        }
    }

    @Test
    fun `uploaded file downloads by its storage key and only for members`() = testApplication {
        application { module() }
        val (me, token) = client.signUp()
        val (_, outsiderToken) = client.signUp()
        val ws = workspaceWith(me)
        val content = "hello, streamed upload".toByteArray()

        val upload = client.submitFormWithBinaryData("/api/file", formData {
            append("workspaceId", ws.toString())
            append("userName", "e2e")
            append("file", content, Headers.build {
                append(HttpHeaders.ContentDisposition, "filename=\"notes v2.txt\"")
                append(HttpHeaders.ContentType, "text/plain")
            })
        }) { bearerAuth(token) }
        assertEquals(HttpStatusCode.Created, upload.status, upload.bodyAsText())
        val url = json.parseToJsonElement(upload.bodyAsText()).jsonObject["url"]!!.jsonPrimitive.content
        val key = url.substringAfter("/api/file/download/")
        assertTrue(key.endsWith("_notes v2.txt"))

        try {
            val download = client.get("/api/file/download/${key.encodeURLPathPart()}") { bearerAuth(token) }
            assertEquals(HttpStatusCode.OK, download.status)
            assertContentEquals(content, download.readRawBytes())

            val outsider = client.get("/api/file/download/${key.encodeURLPathPart()}") { bearerAuth(outsiderToken) }
            assertEquals(HttpStatusCode.Forbidden, outsider.status)

            val listed = client.get("/api/file/updates?workspaceId=$ws&cursor=0") { bearerAuth(token) }
            assertEquals(1, json.parseToJsonElement(listed.bodyAsText()).jsonArray.size)
        } finally {
            File(System.getenv("UPLOAD_DIR") ?: "local_files_upload", key).delete()
        }
    }

    @Test
    fun `declared oversize upload is refused before it is read`() = testApplication {
        application { module() }
        val (_, token) = client.signUp()
        val tooBig = ByteArray(26 * 1024 * 1024)
        val response = client.post("/api/media/upload") {
            bearerAuth(token)
            setBody(MultiPartFormDataContent(formData {
                append("file", tooBig, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"big.jpg\"")
                    append(HttpHeaders.ContentType, "image/jpeg")
                })
            }))
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
    }
}
