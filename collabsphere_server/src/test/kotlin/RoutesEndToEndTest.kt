import com.collabsphere.dto.*
import com.collabsphere.model.UsersTable
import com.collabsphere.model.ChannelsTable
import com.collabsphere.model.MessageTable
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
    fun `message create retry with same idempotency key returns one durable message`() = testApplication {
        application { module() }
        val (me, token) = client.signUp()
        val workspaceId = workspaceWith(me)
        val channelId = transaction {
            ChannelsTable.insert {
                it[userId] = me
                it[channelName] = "idempotency-${UUID.randomUUID()}"
                it[ChannelsTable.workspaceId] = workspaceId
                it[description] = ""
            }[ChannelsTable.id]
        }
        val idempotencyKey = UUID.randomUUID().toString()
        fun request(content: String) = """{"id":0,"userId":$me,"workspaceId":$workspaceId,"channelId":$channelId,"userName":"e2e","content":"$content","status":"Delivered","idempotencyKey":"$idempotencyKey"}"""

        val first = client.post("/api/message") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request("hello"))
        }
        val retry = client.post("/api/message") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request("hello"))
        }
        val conflictingReplay = client.post("/api/message") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request("different body"))
        }

        assertEquals(HttpStatusCode.Created, first.status)
        assertEquals(HttpStatusCode.Created, retry.status)
        assertEquals(
            json.decodeFromString<com.collabsphere.dto.MessageResponse>(first.bodyAsText()).id,
            json.decodeFromString<com.collabsphere.dto.MessageResponse>(retry.bodyAsText()).id
        )
        assertEquals(HttpStatusCode.BadRequest, conflictingReplay.status)
        assertEquals(
            1,
            transaction { MessageTable.selectAll().where { MessageTable.idempotencyKey eq idempotencyKey }.count() }
        )
    }

    @Test
    fun `channel create retry with same idempotency key returns one durable channel`() = testApplication {
        application { module() }
        val (me, token) = client.signUp()
        val workspaceId = workspaceWith(me)
        val idempotencyKey = UUID.randomUUID().toString()
        fun request(name: String) = """{"id":0,"userId":$me,"channelName":"$name","workspaceId":$workspaceId,"description":"edge test","idempotencyKey":"$idempotencyKey"}"""

        val first = client.post("/api/channels") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request("updates"))
        }
        val retry = client.post("/api/channels") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request("updates"))
        }
        val conflictingReplay = client.post("/api/channels") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request("announcements"))
        }

        assertEquals(HttpStatusCode.Created, first.status)
        assertEquals(HttpStatusCode.Created, retry.status)
        assertEquals(
            json.parseToJsonElement(first.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content,
            json.parseToJsonElement(retry.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content
        )
        assertEquals(HttpStatusCode.BadRequest, conflictingReplay.status)
        assertEquals(
            1,
            transaction { ChannelsTable.selectAll().where { ChannelsTable.idempotencyKey eq idempotencyKey }.count() }
        )
    }

    @Test
    fun `workspace creation retry with same request id returns the original workspace`() = testApplication {
        application { module() }
        val (me, token) = client.signUp()
        val requestId = UUID.randomUUID().toString()
        val workspaceName = "retry-${UUID.randomUUID()}"
        val requestBody = """{"workspaceName":"$workspaceName","workspaceOwner":"e2e","workspacePassword":"workspace-password","clientRequestId":"$requestId"}"""

        suspend fun create() = client.post("/api/workspace/create") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        val first = create()
        val retry = create()
        assertEquals(HttpStatusCode.Created, first.status)
        assertEquals(HttpStatusCode.Created, retry.status)
        val firstId = json.parseToJsonElement(first.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content.toInt()
        val retryId = json.parseToJsonElement(retry.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content.toInt()
        createdWorkspaceIds += firstId
        assertEquals(firstId, retryId)

        val matchingRows = transaction {
            WorkspacesTable.selectAll()
                .where { WorkspacesTable.clientRequestId eq requestId }
                .count()
        }
        assertEquals(1L, matchingRows)
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
        val idempotencyKey = UUID.randomUUID().toString()

        suspend fun upload(bytes: ByteArray) = client.submitFormWithBinaryData("/api/file", formData {
            append("workspaceId", ws.toString())
            append("userName", "e2e")
            append("idempotencyKey", idempotencyKey)
            append("file", bytes, Headers.build {
                append(HttpHeaders.ContentDisposition, "filename=\"notes v2.txt\"")
                append(HttpHeaders.ContentType, "text/plain")
            })
        }) { bearerAuth(token) }

        val upload = upload(content)
        val uploadBody = upload.bodyAsText()
        assertEquals(HttpStatusCode.Created, upload.status, uploadBody)
        val uploadId = json.parseToJsonElement(uploadBody).jsonObject["id"]!!.jsonPrimitive.content
        val replay = upload(content)
        val replayBody = replay.bodyAsText()
        assertEquals(HttpStatusCode.Created, replay.status, replayBody)
        assertEquals(uploadId, json.parseToJsonElement(replayBody).jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals(HttpStatusCode.Conflict, upload("different content".toByteArray()).status)
        val url = json.parseToJsonElement(uploadBody).jsonObject["url"]!!.jsonPrimitive.content
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

    @Test
    fun `workspace member role constraints prevent owner removal and enforce self-leave rules`() = testApplication {
        application { module() }
        val (ownerId, ownerToken) = client.signUp()
        val (memberId, memberToken) = client.signUp()
        val ws = ownedWorkspace(ownerId, "Roles", "pass")
        
        transaction { WorkspaceMembersTable.insert { it[workspaceId] = ws; it[userId] = memberId } }

        val memRes = client.delete("/api/workspace/$ws/members/$ownerId") { bearerAuth(memberToken) }
        assertEquals(HttpStatusCode.Forbidden, memRes.status)

        val ownerSelfRemove = client.delete("/api/workspace/$ws/members/$ownerId") { bearerAuth(ownerToken) }
        assertEquals(HttpStatusCode.Forbidden, ownerSelfRemove.status)

        val ownerDemote = client.put("/api/workspace/$ws/members/$ownerId/role") {
            bearerAuth(ownerToken)
            contentType(ContentType.Application.Json)
            setBody("""{"role":"MEMBER"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, ownerDemote.status)

        val memberLeave = client.delete("/api/workspace/$ws/members/$memberId") { bearerAuth(memberToken) }
        assertEquals(HttpStatusCode.OK, memberLeave.status)
    }

    @Test
    fun `workspace search rejects nonmembers and handles query length boundaries`() = testApplication {
        application { module() }
        val (me, token) = client.signUp()
        val (_, outsiderToken) = client.signUp()
        val ws = workspaceWith(me)

        val longQuery = "a".repeat(300)
        val shortQuery = "a"
        val validQuery = "test"

        val shortRes = client.get("/api/workspace/$ws/search?q=$shortQuery") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, shortRes.status)

        val longRes = client.get("/api/workspace/$ws/search?q=$longQuery") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, longRes.status)

        val outsiderRes = client.get("/api/workspace/$ws/search?q=$validQuery") { bearerAuth(outsiderToken) }
        assertEquals(HttpStatusCode.Forbidden, outsiderRes.status)
    }

    @Test
    fun `channel duplicate names are rejected in same workspace but allowed across workspaces`() = testApplication {
        application { module() }
        val (me, token) = client.signUp()
        val ws1 = workspaceWith(me)
        val ws2 = workspaceWith(me)

        val channelName = "general"
        val reqWs1 = """{"id":0,"userId":$me,"channelName":"$channelName","workspaceId":$ws1,"description":"test"}"""
        val reqWs2 = """{"id":0,"userId":$me,"channelName":"$channelName","workspaceId":$ws2,"description":"test"}"""

        val c1 = client.post("/api/channels") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(reqWs1)
        }
        assertEquals(HttpStatusCode.Created, c1.status)

        val c1Dup = client.post("/api/channels") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(reqWs1.replace(""""id":0""", """"id":0,"idempotencyKey":"${UUID.randomUUID()}""""))
        }
        assertEquals(HttpStatusCode.BadRequest, c1Dup.status)

        val c2 = client.post("/api/channels") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(reqWs2)
        }
        assertEquals(HttpStatusCode.Created, c2.status)
    }

    @Test
    fun `cannot delete the last channel in a workspace`() = testApplication {
        application { module() }
        val (me, token) = client.signUp()
        val ws = workspaceWith(me)
        val channelName = "only-channel"

        val c1 = client.post("/api/channels") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"id":0,"userId":$me,"channelName":"$channelName","workspaceId":$ws,"description":"test"}""")
        }
        assertEquals(HttpStatusCode.Created, c1.status)

        val delRes = client.delete("/api/channels/$channelName/$ws/$me") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.BadRequest, delRes.status)

        val c2Name = "second-channel"
        client.post("/api/channels") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"id":0,"userId":$me,"channelName":"$c2Name","workspaceId":$ws,"description":"test"}""")
        }

        val delRes2 = client.delete("/api/channels/$channelName/$ws/$me") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, delRes2.status)
    }
    @Test
    fun `message creation edge cases are rejected`() = testApplication {
        application { module() }
        val (me, token) = client.signUp()
        val ws = workspaceWith(me)

        val channelName = "general"
        val c1 = client.post("/api/channels") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"id":0,"userId":$me,"channelName":"$channelName","workspaceId":$ws,"description":"test"}""")
        }
        val channelResponse = Json.decodeFromString<dto.ChannelResponse>(c1.bodyAsText())
        val channelId = channelResponse.id

        // blank content
        val req1 = """{"id":0,"userId":$me,"workspaceId":$ws,"channelId":$channelId,"content":"   ","status":"SENT"}"""
        val res1 = client.post("/api/message") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(req1)
        }
        assertEquals(HttpStatusCode.BadRequest, res1.status)

        // invalid reply target
        val req2 = """{"id":0,"userId":$me,"workspaceId":$ws,"channelId":$channelId,"content":"reply","status":"SENT","replyToId":99999}"""
        val res2 = client.post("/api/message") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(req2)
        }
        assertEquals(HttpStatusCode.BadRequest, res2.status)

        // too long
        val longContent = "A".repeat(5000)
        val req3 = """{"id":0,"userId":$me,"workspaceId":$ws,"channelId":$channelId,"content":"$longContent","status":"SENT"}"""
        val res3 = client.post("/api/message") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(req3)
        }
        assertEquals(HttpStatusCode.BadRequest, res3.status)

        // deleted channel
        client.delete("/api/channels/$channelName/$ws/$me") { bearerAuth(token) }
        val req4 = """{"id":0,"userId":$me,"workspaceId":$ws,"channelId":$channelId,"content":"valid","status":"SENT"}"""
        val res4 = client.post("/api/message") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(req4)
        }
        assertEquals(HttpStatusCode.BadRequest, res4.status)
    }
    @Test
    fun `task creation and update boundaries are enforced`() = testApplication {
        application { module() }
        val (me, token) = client.signUp()
        val ws = workspaceWith(me)

        val longName = "A".repeat(300)

        // Blank name
        val t1 = client.post("/api/tasks") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"id":0,"taskName":"  ","taskDescription":"test","workspaceId":$ws,"assignedToUserId":null,"status":"TODO","priority":"HIGH","dueDate":null,"checklist":[],"labels":[]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, t1.status)

        // Long name
        val t2 = client.post("/api/tasks") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"id":0,"taskName":"$longName","taskDescription":"test","workspaceId":$ws,"assignedToUserId":null,"status":"TODO","priority":"HIGH","dueDate":null,"checklist":[],"labels":[]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, t2.status)

        // Invalid status
        val t3 = client.post("/api/tasks") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"id":0,"taskName":"valid","taskDescription":"test","workspaceId":$ws,"assignedToUserId":null,"status":"INVALID","priority":"HIGH","dueDate":null,"checklist":[],"labels":[]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, t3.status)

        // Invalid assignee
        val t4 = client.post("/api/tasks") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"id":0,"taskName":"valid","taskDescription":"test","workspaceId":$ws,"assignedToUserId":99999,"status":"TODO","priority":"HIGH","dueDate":null,"checklist":[],"labels":[]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, t4.status)

        // Valid creation
        val t5 = client.post("/api/tasks") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"id":0,"taskName":"valid","taskDescription":"test","workspaceId":$ws,"assignedToUserId":null,"status":"TODO","priority":"HIGH","dueDate":null,"checklist":[],"labels":[]}""")
        }
        assertEquals(HttpStatusCode.Created, t5.status)
        val taskId = Json.decodeFromString<dto.TaskResponse>(t5.bodyAsText()).id

        // Invalid update
        val t6 = client.put("/api/tasks/$taskId") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"id":$taskId,"taskName":"  ","taskDescription":"test","workspaceId":$ws,"assignedToUserId":null,"status":"TODO","priority":"HIGH","dueDate":null,"checklist":[],"labels":[]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, t6.status)
        @Test
    fun `note creation and update boundaries are enforced`() = testApplication {
        application { module() }
        val (me, token) = client.signUp()
        val ws = workspaceWith(me)

        val longName = "A".repeat(300)
        val longDesc = "A".repeat(15000)

        // Blank name
        val n1 = client.post("/api/notes") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"id":0,"notesName":"  ","description":"test","workspaceId":$ws}""")
        }
        assertEquals(HttpStatusCode.BadRequest, n1.status)

        // Long name
        val n2 = client.post("/api/notes") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"id":0,"notesName":"$longName","description":"test","workspaceId":$ws}""")
        }
        assertEquals(HttpStatusCode.BadRequest, n2.status)

        // Long description
        val n3 = client.post("/api/notes") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"id":0,"notesName":"valid","description":"$longDesc","workspaceId":$ws}""")
        }
        assertEquals(HttpStatusCode.BadRequest, n3.status)

        // Valid creation
        val n4 = client.post("/api/notes") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"id":0,"notesName":"valid","description":"test","workspaceId":$ws}""")
        }
        assertEquals(HttpStatusCode.Created, n4.status)
        val noteId = Json.decodeFromString<com.collabsphere.dto.NotesResponse>(n4.bodyAsText()).id

        // Invalid update
        val n5 = client.put("/api/notes/$noteId") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"id":$noteId,"notesName":"valid","description":"$longDesc","workspaceId":$ws}""")
        }
        assertEquals(HttpStatusCode.BadRequest, n5.status)
    }
    }

    @Test
    fun `file upload boundaries are enforced`() = testApplication {
        application { module() }
        val (me, token) = client.signUp()
        val ws = workspaceWith(me)

        // Zero-byte file
        val req1 = client.post("/api/file") {
            bearerAuth(token)
            setBody(MultiPartFormDataContent(
                formData {
                    append("workspaceId", ws.toString())
                    append("userName", "test")
                    append("fileName", "test.txt")
                    append("fileBytes", ByteArray(0), Headers.build {
                        append(HttpHeaders.ContentType, "text/plain")
                        append(HttpHeaders.ContentDisposition, "filename=\"test.txt\"")
                    })
                }
            ))
        }
        assertEquals(HttpStatusCode.BadRequest, req1.status)

        // Unsupported MIME (executable)
        val req2 = client.post("/api/file") {
            bearerAuth(token)
            setBody(MultiPartFormDataContent(
                formData {
                    append("workspaceId", ws.toString())
                    append("userName", "test")
                    append("fileName", "test.exe")
                    append("fileBytes", byteArrayOf(1, 2, 3), Headers.build {
                        append(HttpHeaders.ContentType, "application/x-msdownload")
                        append(HttpHeaders.ContentDisposition, "filename=\"test.exe\"")
                    })
                }
            ))
        }
        assertEquals(HttpStatusCode.UnsupportedMediaType, req2.status)
    }
}
