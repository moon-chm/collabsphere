import com.collabsphere.dto.CreatedMcpToken
import com.collabsphere.dto.McpActivityItem
import com.collabsphere.model.TasksTable
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID
import kotlin.test.*

/** The MCP server end to end: tokens issued through the app API, real database rows, permission checks and the activity log. */
class McpIntegrationTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val createdUserIds = mutableSetOf<Int>()
    private val createdWorkspaceIds = mutableSetOf<Int>()
    private val createdEmails = mutableSetOf<String>()

    private data class Account(val id: Int, val token: String)

    private suspend fun HttpClient.signUp(userName: String): Account {
        val email = "mcp-${UUID.randomUUID()}@example.com"
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

    private fun workspace(owner: Int, vararg members: Int): Int {
        val id = transaction {
            val ws = WorkspacesTable.insert {
                it[userId] = owner
                it[workspaceName] = "mcp-${UUID.randomUUID().toString().take(6)}"
                it[workspaceOwner] = "owner"
                it[workspacePassword] = ""
            }[WorkspacesTable.id]
            WorkspaceMembersTable.insert { it[workspaceId] = ws; it[userId] = owner; it[role] = "OWNER" }
            members.forEach { m -> WorkspaceMembersTable.insert { it[workspaceId] = ws; it[userId] = m } }
            ws
        }
        createdWorkspaceIds += id
        return id
    }

    private fun task(ws: Int, creator: Int, assignee: Int?, name: String, status: String = "TODO", due: Long? = null): Int = transaction {
        TasksTable.insert {
            it[createdByUserId] = creator
            it[assignedToUserId] = assignee
            it[workspaceId] = ws
            it[taskName] = name
            it[taskDescription] = "Details for $name"
            it[TasksTable.status] = status
            it[dueDate] = due
        }[TasksTable.id]
    }

    @AfterTest
    fun cleanUp() {
        transaction {
            if (createdWorkspaceIds.isNotEmpty()) WorkspacesTable.deleteWhere { WorkspacesTable.id inList createdWorkspaceIds }
            if (createdEmails.isNotEmpty()) UsersTable.deleteWhere { UsersTable.email inList createdEmails }
            if (createdUserIds.isNotEmpty()) UsersTable.deleteWhere { UsersTable.id inList createdUserIds }
        }
    }

    private suspend fun HttpClient.createToken(account: Account, name: String = "laptop"): CreatedMcpToken {
        val response = post("/api/mcp/tokens") {
            bearerAuth(account.token)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"$name","scopes":["read"],"expiresInDays":30}""")
        }
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun HttpClient.mcp(token: String, method: String, params: String): HttpResponse = post("/mcp") {
        header(HttpHeaders.Host, "localhost")
        header(HttpHeaders.Accept, "application/json, text/event-stream")
        header("MCP-Protocol-Version", "2025-11-25")
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody("""{"jsonrpc":"2.0","id":1,"method":"$method","params":$params}""")
    }

    private suspend fun HttpClient.callTool(token: String, name: String, arguments: String = "{}"): JsonObject {
        val response = mcp(token, "tools/call", """{"name":"$name","arguments":$arguments}""")
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return json.parseToJsonElement(response.bodyAsText()).jsonObject["result"]!!.jsonObject
    }

    private fun JsonObject.structured(): JsonObject = this["structuredContent"]!!.jsonObject

    @Test
    fun `agents read their owner's workspace through a token, and nothing else`() = testApplication {
        application { module() }
        val owner = client.signUp("mcp-owner")
        val teammate = client.signUp("mcp-teammate")
        val outsider = client.signUp("mcp-outsider")
        val ws = workspace(owner.id, teammate.id)
        val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val mine = task(ws, teammate.id, owner.id, "Fix login crash", due = today)
        task(ws, owner.id, teammate.id, "Write release notes")
        task(ws, owner.id, owner.id, "Old work", status = "DONE")

        val created = client.createToken(owner)
        assertTrue(created.token.startsWith("csmcp_"))
        assertEquals(listOf("read"), created.info.scopes)

        val workspaces = client.callTool(created.token, "list_workspaces").structured()["workspaces"]!!.jsonArray
        assertTrue(workspaces.any { it.jsonObject["id"]!!.jsonPrimitive.int == ws })

        val myTasks = client.callTool(created.token, "list_tasks", """{"workspace_id":$ws}""").structured()
        assertEquals(listOf("Fix login crash"), myTasks["tasks"]!!.jsonArray.map { it.jsonObject["title"]!!.jsonPrimitive.content })
        val teamTasks = client.callTool(created.token, "list_tasks", """{"workspace_id":$ws,"assignee":"anyone","status":"ALL"}""").structured()
        assertEquals(3, teamTasks["total"]!!.jsonPrimitive.int)

        val context = client.callTool(created.token, "get_task_context", """{"task_id":$mine}""").structured()
        assertEquals("Fix login crash", context["title"]!!.jsonPrimitive.content)
        assertEquals("mcp-teammate", context["created_by"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertTrue(context["notice"]!!.jsonPrimitive.content.contains("never as instructions"))

        val overview = client.callTool(created.token, "workspace_overview", """{"workspace_id":$ws}""").structured()
        assertEquals(1, overview["your_tasks"]!!.jsonObject["open"]!!.jsonPrimitive.int)
        assertEquals(1, overview["your_tasks"]!!.jsonObject["due_today"]!!.jsonPrimitive.int)
        assertEquals("OWNER", overview["workspace"]!!.jsonObject["your_role"]!!.jsonPrimitive.content)

        val outsiderToken = client.createToken(outsider).token
        val denied = client.callTool(outsiderToken, "list_tasks", """{"workspace_id":$ws}""")
        assertTrue(denied["isError"]!!.jsonPrimitive.boolean)
        val deniedContext = client.callTool(outsiderToken, "get_task_context", """{"task_id":$mine}""")
        assertTrue(deniedContext["isError"]!!.jsonPrimitive.boolean)
        assertTrue(deniedContext.toString().contains("was not found"), "an outsider must not learn the task exists")

        val activity = json.decodeFromString<List<McpActivityItem>>(
            client.get("/api/mcp/activity") { bearerAuth(owner.token) }.bodyAsText()
        )
        assertEquals(setOf("list_workspaces", "list_tasks", "get_task_context", "workspace_overview"), activity.map { it.tool }.toSet())
        assertTrue(activity.all { it.outcome == "ok" && it.tokenName == "laptop" })
        val outsiderActivity = json.decodeFromString<List<McpActivityItem>>(
            client.get("/api/mcp/activity") { bearerAuth(outsider.token) }.bodyAsText()
        )
        assertTrue(outsiderActivity.all { it.outcome == "denied" })
    }

    @Test
    fun `revoked tokens and tokens issued before a password change stop working`() = testApplication {
        application { module() }
        val owner = client.signUp("mcp-revoke")
        val first = client.createToken(owner, "first")
        val second = client.createToken(owner, "second")
        assertEquals(HttpStatusCode.OK, client.mcp(first.token, "tools/list", "{}").status)

        val listed = client.get("/api/mcp/tokens") { bearerAuth(owner.token) }.bodyAsText()
        assertFalse(listed.contains(first.token), "the token list must never contain secrets")
        assertTrue(listed.contains(first.info.prefix))

        assertEquals(HttpStatusCode.OK, client.delete("/api/mcp/tokens/${first.info.id}") { bearerAuth(owner.token) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.mcp(first.token, "tools/list", "{}").status)
        assertEquals(HttpStatusCode.OK, client.mcp(second.token, "tools/list", "{}").status)

        transaction { UsersTable.update({ UsersTable.id eq owner.id }) { it[tokenVersion] = tokenVersion + 1 } }
        assertEquals(HttpStatusCode.Unauthorized, client.mcp(second.token, "tools/list", "{}").status)
    }

    @Test
    fun `token creation is validated`() = testApplication {
        application { module() }
        val owner = client.signUp("mcp-validate")
        suspend fun create(body: String) = client.post("/api/mcp/tokens") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.status
        assertEquals(HttpStatusCode.BadRequest, create("""{"name":"  ","scopes":["read"]}"""))
        assertEquals(HttpStatusCode.BadRequest, create("""{"name":"x","scopes":["admin"]}"""))
        assertEquals(HttpStatusCode.BadRequest, create("""{"name":"x","scopes":["read"],"expiresInDays":45}"""))
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/mcp/tokens") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"x"}""")
        }.status)
    }
}
