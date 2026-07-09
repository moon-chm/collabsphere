package plugins

import com.collabsphere.dto.MessageRequest
import com.collabsphere.dto.MessageResponse
import com.collabsphere.dto.NotesRequest
import com.collabsphere.dto.NotesResponse
import com.collabsphere.dto.FileResponse
import com.collabsphere.dto.FileSyncResponse
import com.collabsphere.dto.MessageSyncResponse
import com.collabsphere.dto.NotesSyncResponse
import dto.*
import com.collabsphere.model.TasksTable
import com.collabsphere.model.MessageTable
import com.collabsphere.model.LocalFilesTable
import com.collabsphere.model.NotesTable
import com.collabsphere.model.UsersTable
import com.collabsphere.model.WorkspacesTable
import com.collabsphere.model.DirectMessagesTable
import com.collabsphere.model.WorkspaceMembersTable
import com.collabsphere.model.ChannelsTable
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.Int

suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }

val activeDmSessions = ConcurrentHashMap<Long, WebSocketServerSession>()

fun Application.configureRouting() {
    routing {

        post("/api/login") {
            try {
                val request = call.receive<LoginRequest>()
                val user = dbQuery {
                    UsersTable.selectAll()
                        .where { (UsersTable.email eq request.email) and (UsersTable.password eq request.password) }
                        .map {
                            LoginResponse(
                                id = it[UsersTable.id],
                                userName = it[UsersTable.username],
                                email = it[UsersTable.email],
                                token = "mock-jwt-token-string-for-id-${it[UsersTable.id]}"
                            )
                        }
                        .singleOrNull()
                }

                if (user != null) {
                    call.respond(HttpStatusCode.OK, user)
                } else {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Malformed request body or server error")
            }
        }

        post("/api/register") {
            try {
                val request = call.receive<RegisterRequest>()
                val userExists = dbQuery {
                    UsersTable.selectAll().where { UsersTable.email eq request.email }.count() > 0
                }

                if (userExists) {
                    call.respond(HttpStatusCode.Conflict, "Email already registered")
                    return@post
                }
                val userResponse = dbQuery {
                    val insertStatement = UsersTable.insert {
                        it[email] = request.email
                        it[username] = request.userName
                        it[password] = request.password
                    }

                    val generatedId = insertStatement[UsersTable.id]

                    LoginResponse(
                        id = generatedId,
                        userName = request.userName,
                        email = request.email,
                        token = "mock-jwt-token-string-for-id-$generatedId"
                    )
                }

                call.respond(HttpStatusCode.Created, userResponse)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Server Error: ${e.localizedMessage}")
            }
        }

        route("/api/workspace") {

            post("/create") {
                try {
                    val userIdParam = call.parameters["userId"]?.toIntOrNull()
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            "Missing or invalid userId"
                        )

                    val request = call.receive<WorkspaceRequest>()

                    val response = dbQuery {
                        val insertedId = WorkspacesTable.insert {
                            it[userId] = userIdParam
                            it[workspaceName] = request.workspaceName
                            it[workspaceOwner] = request.workspaceOwner
                            it[workspacePassword] = request.workspacePassword
                            it[isDeleted] = false
                            it[updatedAt] = System.currentTimeMillis()
                        }[WorkspacesTable.id]

                        WorkspaceMembersTable.insert {
                            it[workspaceId] = insertedId
                            it[userId] = userIdParam
                        }

                        WorkspaceResponse(
                            id = insertedId,
                            userId = userIdParam,
                            workspaceName = request.workspaceName,
                            workspaceOwner = request.workspaceOwner
                        )
                    }

                    call.respond(HttpStatusCode.Created, response)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Database error")
                }
            }

            post("/members/{workspaceId}") {
                try {
                    val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            "Missing workspaceId"
                        )

                    val request = call.receive<AddMemberRequest>()

                    val response = dbQuery {
                        val targetUserRow = UsersTable
                            .selectAll()
                            .where { UsersTable.email eq request.email }
                            .singleOrNull()

                        if (targetUserRow == null) {
                            null
                        } else {
                            val targetUserId = targetUserRow[UsersTable.id]
                            val targetUserName = targetUserRow[UsersTable.username]
                            val targetUserEmail = targetUserRow[UsersTable.email]

                            val alreadyMember = WorkspaceMembersTable
                                .selectAll()
                                .where {
                                    (WorkspaceMembersTable.workspaceId eq workspaceIdParam) and
                                            (WorkspaceMembersTable.userId eq targetUserId)
                                }
                                .count() > 0

                            if (!alreadyMember) {
                                WorkspaceMembersTable.insert {
                                    it[workspaceId] = workspaceIdParam
                                    it[userId] = targetUserId
                                }

                                WorkspacesTable.update({ WorkspacesTable.id eq workspaceIdParam }) {
                                    it[updatedAt] = System.currentTimeMillis()
                                }
                            }

                            MemberResponse(
                                workspaceId = workspaceIdParam,
                                userId = targetUserId,
                                userName = targetUserName,
                                email = targetUserEmail
                            )
                        }
                    }

                    if (response == null) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            "No user found with email: ${request.email}"
                        )
                    } else {
                        call.respond(HttpStatusCode.Created, response)
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Error adding member")
                }
            }

            get("/user/{userId}") {
                try {
                    val userIdParam = call.parameters["userId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing userId")

                    val workspaces = dbQuery {
                        (WorkspacesTable innerJoin WorkspaceMembersTable)
                            .selectAll()
                            .where {
                                (WorkspaceMembersTable.userId eq userIdParam) and
                                        (WorkspacesTable.isDeleted eq false)
                            }
                            .map {
                                WorkspaceResponse(
                                    id = it[WorkspacesTable.id],
                                    userId = it[WorkspacesTable.userId],
                                    workspaceName = it[WorkspacesTable.workspaceName],
                                    workspaceOwner = it[WorkspacesTable.workspaceOwner]
                                )
                            }
                    }

                    call.respond(HttpStatusCode.OK, workspaces)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Error fetching user workspaces")
                }
            }

            get("/members/{workspaceId}") {
                try {
                    val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing workspaceId")

                    val members = dbQuery {
                        (WorkspaceMembersTable innerJoin UsersTable)
                            .selectAll()
                            .where { WorkspaceMembersTable.workspaceId eq workspaceIdParam }
                            .map {
                                MemberResponse(
                                    workspaceId = workspaceIdParam,
                                    userId = it[UsersTable.id],
                                    userName = it[UsersTable.username],
                                    email = it[UsersTable.email]
                                )
                            }
                    }

                    call.respond(HttpStatusCode.OK, members)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Error fetching workspace members"
                    )
                }
            }

            get("/sync/{userId}") {
                try {
                    val userIdParam = call.parameters["userId"]?.toIntOrNull()
                    val sinceTimestamp = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L

                    if (userIdParam == null) {
                        call.respond(HttpStatusCode.BadRequest, "Missing userId")
                        return@get
                    }

                    val updates = dbQuery {
                        (WorkspacesTable innerJoin WorkspaceMembersTable)
                            .selectAll()
                            .where {
                                (WorkspaceMembersTable.userId eq userIdParam) and
                                        (WorkspacesTable.updatedAt greater sinceTimestamp)
                            }
                            .map {
                                WorkspaceSyncDto(
                                    id = it[WorkspacesTable.id],
                                    userId = it[WorkspacesTable.userId],
                                    workspaceName = it[WorkspacesTable.workspaceName],
                                    workspaceOwner = it[WorkspacesTable.workspaceOwner],
                                    isDeleted = it[WorkspacesTable.isDeleted],
                                    updatedAt = it[WorkspacesTable.updatedAt]
                                )
                            }
                    }
                    call.respond(HttpStatusCode.OK, updates)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Sync Error")
                }
            }

            delete("/delete") {
                try {
                    val workspaceId = call.request.queryParameters["workspaceId"]?.toIntOrNull()
                        ?: return@delete call.respond(
                            HttpStatusCode.BadRequest,
                            "Missing or invalid workspaceId"
                        )

                    val userId = call.request.queryParameters["userId"]?.toIntOrNull()
                        ?: return@delete call.respond(
                            HttpStatusCode.BadRequest,
                            "Missing or invalid userId"
                        )

                    val password = call.request.queryParameters["workspacePassword"]
                        ?: return@delete call.respond(
                            HttpStatusCode.BadRequest,
                            "Missing password"
                        )

                    val updatedRows = dbQuery {
                        WorkspacesTable.update({
                            (WorkspacesTable.id eq workspaceId) and
                                    (WorkspacesTable.userId eq userId) and
                                    (WorkspacesTable.workspacePassword eq password)
                        }) {
                            it[isDeleted] = true
                            it[updatedAt] = System.currentTimeMillis()
                        }
                    }

                    if (updatedRows > 0) {
                        call.respond(HttpStatusCode.OK, updatedRows)
                    } else {
                        call.respond(HttpStatusCode.NotFound, 0)
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, 0)
                }
            }
        }

        route("/api/channels") {
            post {
                try {
                    val request = call.receive<ChannelRequest>()
                    val response = dbQuery {
                        val insertedId = ChannelsTable.insert {
                            it[userId] = request.userId
                            it[channelName] = request.channelName
                            it[workspaceId] = request.workspaceId
                            it[description] = request.description
                            it[updatedAt] = System.currentTimeMillis()
                            it[isDeleted] = false
                        }[ChannelsTable.id]

                        ChannelResponse(
                            id = insertedId,
                            userId = request.userId,
                            channelName = request.channelName,
                            workspaceId = request.workspaceId,
                            description = request.description
                        )
                    }
                    call.respond(HttpStatusCode.Created, response)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Failed to create channel")
                }
            }

            delete("/{channelName}/{workspaceId}/{userId}") {
                try {
                    val channelNameParam = call.parameters["channelName"]
                    val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                    val userIdParam = call.parameters["userId"]?.toIntOrNull()

                    if (channelNameParam == null || workspaceIdParam == null || userIdParam == null) {
                        call.respond(HttpStatusCode.BadRequest, "Missing parameters")
                        return@delete
                    }

                    val updatedRows = dbQuery {
                        ChannelsTable.update({
                            (ChannelsTable.channelName eq channelNameParam) and
                                    (ChannelsTable.workspaceId eq workspaceIdParam) and
                                    (if (userIdParam == 0) ChannelsTable.userId.isNull() else (ChannelsTable.userId eq userIdParam))
                        }) {
                            it[isDeleted] = true
                            it[updatedAt] = System.currentTimeMillis()
                        }
                    }

                    if (updatedRows > 0) call.respond(HttpStatusCode.OK, true)
                    else call.respond(HttpStatusCode.NotFound, false)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, false)
                }
            }

            get("/sync/{workspaceId}") {
                try {
                    val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                    val sinceTimestamp = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L

                    if (workspaceIdParam == null) {
                        call.respond(HttpStatusCode.BadRequest, "Missing workspaceId")
                        return@get
                    }

                    val updates = dbQuery {
                        ChannelsTable.selectAll()
                            .where {
                                (ChannelsTable.workspaceId eq workspaceIdParam) and
                                        (ChannelsTable.updatedAt greater sinceTimestamp)
                            }
                            .map {
                                ChannelSyncResponse(
                                    id = it[ChannelsTable.id],
                                    userId = it[ChannelsTable.userId],
                                    channelName = it[ChannelsTable.channelName],
                                    workspaceId = it[ChannelsTable.workspaceId],
                                    description = it[ChannelsTable.description],
                                    isDeleted = it[ChannelsTable.isDeleted],
                                    updatedAt = it[ChannelsTable.updatedAt]
                                )
                            }
                    }
                    call.respond(HttpStatusCode.OK, updates)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Sync Error")
                }
            }

            get("/workspace/{workspaceId}") {
                try {
                    val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                    if (workspaceIdParam == null) {
                        call.respond(HttpStatusCode.BadRequest, "Missing workspaceId")
                        return@get
                    }

                    val channels = dbQuery {
                        ChannelsTable.selectAll()
                            .where {
                                (ChannelsTable.workspaceId eq workspaceIdParam) and
                                        (ChannelsTable.isDeleted eq false)
                            }
                            .map {
                                ChannelResponse(
                                    id = it[ChannelsTable.id],
                                    userId = it[ChannelsTable.userId],
                                    channelName = it[ChannelsTable.channelName],
                                    workspaceId = it[ChannelsTable.workspaceId],
                                    description = it[ChannelsTable.description]
                                )
                            }
                    }
                    call.respond(HttpStatusCode.OK, channels)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Error retrieving channels")
                }
            }
        }

        route("/api/tasks") {

            post {
                try {
                    val request = call.receive<TaskRequest>()
                    val creatorId =
                        call.parameters["userId"]?.toIntOrNull() ?: request.assignedToUserId

                    if (creatorId == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            "Provide a passing creator ID via query string or assignment."
                        )
                        return@post
                    }

                    val newTask = dbQuery {
                        val insertedId = TasksTable.insert {
                            it[createdByUserId] = creatorId
                            it[assignedToUserId] = request.assignedToUserId
                            it[workspaceId] = request.workspaceId
                            it[taskName] = request.taskName
                            it[taskDescription] = request.taskDescription
                            it[status] = request.status
                            it[isDeleted] = false
                            it[updatedAt] = System.currentTimeMillis()
                        }[TasksTable.id]

                        TaskResponse(
                            id = insertedId,
                            createdByUserId = creatorId,
                            assignedToUserId = request.assignedToUserId,
                            workspaceId = request.workspaceId,
                            taskName = request.taskName,
                            taskDescription = request.taskDescription,
                            status = request.status
                        )
                    }
                    call.respond(HttpStatusCode.Created, newTask)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        "Foreign key violation: Verify workspaceId and userIds exist."
                    )
                }
            }

            get("/user/{userId}") {
                try {
                    val userIdParam = call.parameters["userId"]?.toIntOrNull()
                    if (userIdParam == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            "Missing or invalid userId path parameter"
                        )
                        return@get
                    }

                    val userTasks = dbQuery {
                        TasksTable.selectAll()
                            .where { (TasksTable.assignedToUserId eq userIdParam) and (TasksTable.isDeleted eq false) }
                            .map {
                                TaskResponse(
                                    id = it[TasksTable.id],
                                    createdByUserId = it[TasksTable.createdByUserId],
                                    assignedToUserId = it[TasksTable.assignedToUserId],
                                    workspaceId = it[TasksTable.workspaceId],
                                    taskName = it[TasksTable.taskName],
                                    taskDescription = it[TasksTable.taskDescription],
                                    status = it[TasksTable.status]
                                )
                            }
                    }
                    call.respond(HttpStatusCode.OK, userTasks)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Failed to retrieve user tasks: ${e.localizedMessage}"
                    )
                }
            }

            delete("/{taskId}") {
                try {
                    val taskIdParam = call.parameters["taskId"]?.toIntOrNull()
                    if (taskIdParam == null) {
                        call.respond(HttpStatusCode.BadRequest, "Missing or invalid taskId")
                        return@delete
                    }

                    val updatedRows = dbQuery {
                        TasksTable.update({ TasksTable.id eq taskIdParam }) {
                            it[isDeleted] = true
                            it[updatedAt] = System.currentTimeMillis()
                        }
                    }

                    if (updatedRows > 0) {
                        call.respond(HttpStatusCode.OK, true)
                    } else {
                        call.respond(HttpStatusCode.NotFound, false)
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, false)
                }
            }

            put("/{taskId}") {
                val taskId = call.parameters["taskId"]?.toIntOrNull()
                if (taskId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing or invalid taskId")
                    return@put
                }

                val request = try {
                    call.receive<TaskRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid request body")
                    return@put
                }

                val updatedCount = dbQuery {
                    TasksTable.update({ TasksTable.id eq taskId }) {
                        it[taskName] = request.taskName
                        it[taskDescription] = request.taskDescription
                        it[assignedToUserId] = request.assignedToUserId
                        it[workspaceId] = request.workspaceId
                        it[status] = request.status
                        it[updatedAt] = System.currentTimeMillis()
                    }
                }

                if (updatedCount == 0) {
                    call.respond(HttpStatusCode.NotFound, "Task not found")
                    return@put
                }

                val updatedTask = dbQuery {
                    TasksTable.selectAll()
                        .where { TasksTable.id eq taskId }
                        .map {
                            TaskResponse(
                                id = it[TasksTable.id],
                                createdByUserId = it[TasksTable.createdByUserId],
                                assignedToUserId = it[TasksTable.assignedToUserId],
                                workspaceId = it[TasksTable.workspaceId],
                                taskName = it[TasksTable.taskName],
                                taskDescription = it[TasksTable.taskDescription],
                                status = it[TasksTable.status]
                            )
                        }.singleOrNull()
                }

                if (updatedTask == null) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Failed to retrieve updated task"
                    )
                } else {
                    call.respond(HttpStatusCode.OK, updatedTask)
                }
            }

            get("/workspace/{workspaceId}") {
                val workspaceId = call.parameters["workspaceId"]?.toIntOrNull()
                if (workspaceId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing workspaceId")
                    return@get
                }

                val workspaceTasks = dbQuery {
                    TasksTable.selectAll()
                        .where { (TasksTable.workspaceId eq workspaceId) and (TasksTable.isDeleted eq false) }
                        .map {
                            TaskResponse(
                                id = it[TasksTable.id],
                                createdByUserId = it[TasksTable.createdByUserId],
                                assignedToUserId = it[TasksTable.assignedToUserId],
                                workspaceId = it[TasksTable.workspaceId],
                                taskName = it[TasksTable.taskName],
                                taskDescription = it[TasksTable.taskDescription],
                                status = it[TasksTable.status]
                            )
                        }
                }
                call.respond(HttpStatusCode.OK, workspaceTasks)
            }

            get("/sync/{workspaceId}") {
                try {
                    val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                    val sinceTimestamp = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L

                    if (workspaceIdParam == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            "Missing structural context arguments."
                        )
                        return@get
                    }

                    val deltaUpdates = dbQuery {
                        TasksTable.selectAll()
                            .where {
                                (TasksTable.workspaceId eq workspaceIdParam) and
                                        (TasksTable.updatedAt greater sinceTimestamp)
                            }
                            .map {
                                TaskSyncResponse(
                                    id = it[TasksTable.id],
                                    createdByUserId = it[TasksTable.createdByUserId],
                                    assignedToUserId = it[TasksTable.assignedToUserId],
                                    workspaceId = it[TasksTable.workspaceId],
                                    taskName = it[TasksTable.taskName],
                                    taskDescription = it[TasksTable.taskDescription],
                                    status = it[TasksTable.status],
                                    isDeleted = it[TasksTable.isDeleted],
                                    updatedAt = it[TasksTable.updatedAt]
                                )
                            }
                    }
                    call.respond(HttpStatusCode.OK, deltaUpdates)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Sync Error processing delta operations query request loop."
                    )
                }
            }
        }

        route("/api/notes") {
            post {
                try {
                    val request = call.receive<NotesRequest>()
                    val newNotes = dbQuery {
                        val insertedId = NotesTable.insert {
                            it[NotesTable.userIdNotes] = request.userId
                            it[NotesTable.workspaceId] = request.workspaceId
                            it[NotesTable.notesName] = request.notesName
                            it[NotesTable.notesDescription] = request.description
                            it[NotesTable.isDeleted] = false
                            it[NotesTable.updatedAt] = System.currentTimeMillis()
                        }[NotesTable.id]

                        NotesResponse(
                            id = insertedId,
                            userId = request.userId,
                            notesName = request.notesName,
                            workspaceId = request.workspaceId,
                            description = request.description
                        )
                    }
                    call.respond(HttpStatusCode.Created, newNotes)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        "Database structure mismatch or missing foreign row."
                    )
                }
            }

            put("/{noteId}") {
                try {
                    val noteIdParam = call.parameters["noteId"]?.toIntOrNull()
                    if (noteIdParam == null) {
                        call.respond(HttpStatusCode.BadRequest, "Missing or invalid noteId")
                        return@put
                    }
                    val request = call.receive<NotesRequest>()

                    val updatedRows = dbQuery {
                        NotesTable.update({ NotesTable.id eq noteIdParam }) {
                            it[NotesTable.notesName] = request.notesName
                            it[NotesTable.workspaceId] = request.workspaceId
                            it[NotesTable.notesDescription] = request.description
                            it[NotesTable.updatedAt] = System.currentTimeMillis()
                        }
                    }

                    if (updatedRows > 0) {
                        val updatedNote = NotesResponse(
                            id = noteIdParam,
                            userId = request.userId,
                            notesName = request.notesName,
                            workspaceId = request.workspaceId,
                            description = request.description
                        )
                        call.respond(HttpStatusCode.OK, updatedNote)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Note not found to update")
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Error updating note")
                }
            }

            delete("/{noteId}") {
                try {
                    val noteIdParam = call.parameters["noteId"]?.toIntOrNull()

                    if (noteIdParam == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            "Missing or invalid noteId parameter"
                        )
                        return@delete
                    }

                    val updatedRows = dbQuery {
                        NotesTable.update({ NotesTable.id eq noteIdParam }) {
                            it[NotesTable.isDeleted] = true
                            it[NotesTable.updatedAt] = System.currentTimeMillis()
                        }
                    }

                    if (updatedRows > 0) {
                        call.respond(HttpStatusCode.OK, true)
                    } else {
                        call.respond(HttpStatusCode.NotFound, false)
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, false)
                }
            }

            get("/workspace/{workspaceId}") {
                val workspaceId = call.parameters["workspaceId"]?.toIntOrNull()
                if (workspaceId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing or invalid workspaceId")
                    return@get
                }

                val workspaceNotes = dbQuery {
                    NotesTable.selectAll()
                        .where { (NotesTable.workspaceId eq workspaceId) and (NotesTable.isDeleted eq false) }
                        .map {
                            NotesResponse(
                                id = it[NotesTable.id],
                                userId = it[NotesTable.userIdNotes],
                                workspaceId = it[NotesTable.workspaceId],
                                notesName = it[NotesTable.notesName],
                                description = it[NotesTable.notesDescription]
                            )
                        }
                }
                call.respond(HttpStatusCode.OK, workspaceNotes)
            }

            get("/sync/{workspaceId}") {
                try {
                    val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                    val sinceTimestamp = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L

                    if (workspaceIdParam == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            "Missing structural context arguments."
                        )
                        return@get
                    }

                    val deltaUpdates = dbQuery {
                        NotesTable.selectAll()
                            .where {
                                (NotesTable.workspaceId eq workspaceIdParam) and
                                        (NotesTable.updatedAt greater sinceTimestamp)
                            }
                            .map {
                                NotesSyncResponse(
                                    id = it[NotesTable.id],
                                    userId = it[NotesTable.userIdNotes],
                                    workspaceId = it[NotesTable.workspaceId],
                                    notesName = it[NotesTable.notesName],
                                    description = it[NotesTable.notesDescription],
                                    isDeleted = it[NotesTable.isDeleted],
                                    updatedAt = it[NotesTable.updatedAt]
                                )
                            }
                    }
                    call.respond(HttpStatusCode.OK, deltaUpdates)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Sync Error processing delta operations query request loop."
                    )
                }
            }
        }


        route("/api/message") {
            post {
                try {
                    val request = call.receive<MessageRequest>()
                    val newMessage = dbQuery {
                        val insertedId = MessageTable.insert {
                            it[MessageTable.userId] = request.userId
                            it[MessageTable.workspaceId] = request.workspaceId
                            it[MessageTable.channelId] = request.channelId
                            it[MessageTable.userName] = request.userName
                            it[MessageTable.content] = request.content
                            it[MessageTable.status] = request.status
                            it[MessageTable.isDeleted] = false
                            it[MessageTable.updatedAt] = System.currentTimeMillis()
                        }[MessageTable.id]

                        MessageResponse(
                            id = insertedId,
                            userId = request.userId,
                            workspaceId = request.workspaceId,
                            channelId = request.channelId,
                            userName = request.userName,
                            content = request.content,
                            status = request.status
                        )
                    }
                    call.respond(HttpStatusCode.Created, newMessage)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        "Failed to insert message. Ensure parent references exist."
                    )
                }
            }

            get("/workspace/{workspaceId}/channels/{channelId}") {
                val workspaceId = call.parameters["workspaceId"]?.toIntOrNull()
                val channelId = call.parameters["channelId"]?.toIntOrNull()

                if (workspaceId == null || channelId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        "Missing or invalid workspaceId or channelId"
                    )
                    return@get
                }

                val channelmessage = dbQuery {
                    MessageTable.selectAll()
                        .where {
                            (MessageTable.workspaceId eq workspaceId) and
                                    (MessageTable.channelId eq channelId) and
                                    (MessageTable.isDeleted eq false)
                        }
                        .map {
                            MessageResponse(
                                id = it[MessageTable.id],
                                userId = it[MessageTable.userId],
                                workspaceId = it[MessageTable.workspaceId],
                                channelId = it[MessageTable.channelId],
                                userName = it[MessageTable.userName],
                                content = it[MessageTable.content],
                                status = it[MessageTable.status]
                            )
                        }
                }
                call.respond(HttpStatusCode.OK, channelmessage)
            }

            put("/{messageId}") {
                try {
                    val messageIdParam = call.parameters["messageId"]?.toIntOrNull()
                    if (messageIdParam == null) {
                        call.respond(HttpStatusCode.BadRequest, "Missing or invalid messageId")
                        return@put
                    }
                    val request = call.receive<MessageRequest>()

                    val updatedRows = dbQuery {
                        MessageTable.update({ MessageTable.id eq messageIdParam }) {
                            it[MessageTable.content] = request.content
                            it[MessageTable.status] = request.status
                            it[MessageTable.updatedAt] = System.currentTimeMillis()
                        }
                    }

                    if (updatedRows > 0) {
                        val updatedMessage = MessageResponse(
                            id = messageIdParam,
                            userId = request.userId,
                            workspaceId = request.workspaceId,
                            channelId = request.channelId,
                            userName = request.userName,
                            content = request.content,
                            status = request.status
                        )
                        call.respond(HttpStatusCode.OK, updatedMessage)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Message not found to update")
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Error updating message")
                }
            }

            delete("/{messageId}/{userId}/{workspaceId}/{channelId}") {
                try {
                    val messageIdParam = call.parameters["messageId"]?.toIntOrNull()
                    val userIdParam = call.parameters["userId"]?.toIntOrNull()
                    val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                    val channelIdParam = call.parameters["channelId"]?.toIntOrNull()

                    if (messageIdParam == null || userIdParam == null || workspaceIdParam == null || channelIdParam == null) {
                        call.respond(HttpStatusCode.BadRequest, false)
                        return@delete
                    }

                    val updatedRows = dbQuery {
                        MessageTable.update({
                            (MessageTable.id eq messageIdParam) and
                                    (MessageTable.userId eq userIdParam) and
                                    (MessageTable.workspaceId eq workspaceIdParam) and
                                    (MessageTable.channelId eq channelIdParam)
                        }) {
                            it[MessageTable.isDeleted] = true
                            it[MessageTable.updatedAt] = System.currentTimeMillis()
                        }
                    }

                    if (updatedRows > 0) {
                        call.respond(HttpStatusCode.OK, true)
                    } else {
                        call.respond(HttpStatusCode.NotFound, false)
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, false)
                }
            }

            get("/sync/{workspaceId}/{channelId}") {
                try {
                    val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                    val channelIdParam = call.parameters["channelId"]?.toIntOrNull()
                    val sinceTimestamp = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L

                    if (workspaceIdParam == null || channelIdParam == null) {
                        call.respond(HttpStatusCode.BadRequest, "Missing structural parameters")
                        return@get
                    }

                    val updates = dbQuery {
                        MessageTable.selectAll()
                            .where {
                                (MessageTable.workspaceId eq workspaceIdParam) and
                                        (MessageTable.channelId eq channelIdParam) and
                                        (MessageTable.updatedAt greater sinceTimestamp)
                            }
                            .map {
                                MessageSyncResponse(
                                    id = it[MessageTable.id],
                                    userId = it[MessageTable.userId],
                                    workspaceId = it[MessageTable.workspaceId],
                                    channelId = it[MessageTable.channelId],
                                    userName = it[MessageTable.userName],
                                    content = it[MessageTable.content],
                                    status = it[MessageTable.status],
                                    isDeleted = it[MessageTable.isDeleted],
                                    updatedAt = it[MessageTable.updatedAt]
                                )
                            }
                    }
                    call.respond(HttpStatusCode.OK, updates)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Sync Error")
                }
            }
        }

        route("/api/file") {
            post {
                try {
                    val multipart = call.receiveMultipart()
                    var userId: Int? = null
                    var workspaceId: Int? = null
                    var userName: String? = null
                    var localpath: String? = null

                    var fileBytes: ByteArray? = null
                    var fileName: String? = null
                    var contentType: String? = null

                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FormItem -> {
                                when (part.name) {
                                    "userId" -> userId = part.value.toIntOrNull()
                                    "workspaceId" -> workspaceId = part.value.toIntOrNull()
                                    "userName" -> userName = part.value
                                    "localpath" -> localpath = part.value
                                }
                                part.dispose()
                            }
                            is PartData.FileItem -> {
                                fileName = part.originalFileName
                                contentType = part.contentType?.toString()
                                fileBytes = part.streamProvider().use { input -> input.readBytes() }
                                part.dispose()
                            }
                            else -> part.dispose()
                        }
                    }

                    if (userId == null || workspaceId == null || userName == null || fileBytes == null || fileName == null) {
                        val missingFields = mutableListOf<String>()
                        if (userId == null) missingFields.add("userId")
                        if (workspaceId == null) missingFields.add("workspaceId")
                        if (userName == null) missingFields.add("userName")
                        if (fileBytes == null) missingFields.add("fileBytes")
                        if (fileName == null) missingFields.add("fileName")

                        call.respond(HttpStatusCode.BadRequest, "Missing multipart assets: ${missingFields.joinToString(", ")}")
                        return@post
                    }

                    val uploadDir = File("E:\\Rohit kumbhar\\collabpshere_server\\local_files_upload")
                    if (!uploadDir.exists()) {
                        uploadDir.mkdirs()
                    }

                    val uniqueFileName = "${UUID.randomUUID()}_$fileName"
                    val physicalFile = File(uploadDir, uniqueFileName)
                    physicalFile.writeBytes(fileBytes)

                    val generatedFileLocation = physicalFile.absolutePath
                    val generatedUrl = "http://10.238.3.93:8080/api/file/download/$uniqueFileName"
                    val fileSize = physicalFile.length()
                    val finalMimeType = contentType ?: "application/octet-stream"
                    val currentTimeMil = System.currentTimeMillis()

                    val insertedId = dbQuery {
                        LocalFilesTable.insert {
                            it[LocalFilesTable.userId] = userId!!
                            it[LocalFilesTable.workspaceId] = workspaceId!!
                            it[LocalFilesTable.userName] = userName!!
                            it[LocalFilesTable.url] = generatedUrl
                            it[LocalFilesTable.mimeType] = finalMimeType
                            it[LocalFilesTable.localPath] = localpath
                            it[LocalFilesTable.fileName] = fileName!!
                            it[LocalFilesTable.sizeBytes] = fileSize
                            it[LocalFilesTable.fileLocation] = generatedFileLocation
                            it[LocalFilesTable.updatedAt] = currentTimeMil
                            it[LocalFilesTable.isDeleted] = false
                        }[LocalFilesTable.id]
                    }

                    val response = FileResponse(
                        id = insertedId,
                        userId = userId!!,
                        workspaceId = workspaceId!!,
                        userName = userName!!,
                        url = generatedUrl,
                        mimeType = finalMimeType,
                        localpath = localpath,
                        fileName = fileName!!,
                        sizebytes = fileSize,
                        fileLocation = generatedFileLocation
                    )

                    call.respond(HttpStatusCode.Created, response)
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, "Backend failure engine trace: ${e.localizedMessage}")
                }
            }

            get("/workspace/{workspaceId}") {
                try {
                    val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                    if (workspaceIdParam == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid workspaceId")
                        return@get
                    }

                    val filesList = dbQuery {
                        LocalFilesTable.select {
                            (LocalFilesTable.workspaceId eq workspaceIdParam) and (LocalFilesTable.isDeleted eq false)
                        }.map {
                            FileResponse(
                                id = it[LocalFilesTable.id],
                                userId = it[LocalFilesTable.userId],
                                workspaceId = it[LocalFilesTable.workspaceId],
                                userName = it[LocalFilesTable.userName],
                                url = it[LocalFilesTable.url],
                                mimeType = it[LocalFilesTable.mimeType],
                                localpath = it[LocalFilesTable.localPath],
                                fileName = it[LocalFilesTable.fileName],
                                sizebytes = it[LocalFilesTable.sizeBytes],
                                fileLocation = it[LocalFilesTable.fileLocation]
                            )
                        }
                    }
                    call.respond(HttpStatusCode.OK, filesList)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Error retrieving files list")
                }
            }

            get("/updates") {
                try {
                    val workspaceIdParam = call.request.queryParameters["workspaceId"]?.toIntOrNull()
                    val lastSyncTimeParam = call.request.queryParameters["lastSyncTime"]?.toLongOrNull()

                    if (workspaceIdParam == null || lastSyncTimeParam == null) {
                        call.respond(HttpStatusCode.BadRequest, "Missing or invalid workspaceId or lastSyncTime tracking values.")
                        return@get
                    }

                    val deltaUpdatesList = dbQuery {
                        LocalFilesTable.select {
                            (LocalFilesTable.workspaceId eq workspaceIdParam) and
                                    (LocalFilesTable.updatedAt greater lastSyncTimeParam)
                        }.map {
                            FileSyncResponse(
                                id = it[LocalFilesTable.id],
                                userId = it[LocalFilesTable.userId],
                                workspaceId = it[LocalFilesTable.workspaceId],
                                userName = it[LocalFilesTable.userName],
                                url = it[LocalFilesTable.url],
                                mimeType = it[LocalFilesTable.mimeType],
                                localpath = it[LocalFilesTable.localPath],
                                fileName = it[LocalFilesTable.fileName],
                                sizebytes = it[LocalFilesTable.sizeBytes],
                                fileLocation = it[LocalFilesTable.fileLocation],
                                isDeleted = it[LocalFilesTable.isDeleted],
                                updatedAt = it[LocalFilesTable.updatedAt]
                            )
                        }
                    }
                    call.respond(HttpStatusCode.OK, deltaUpdatesList)
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, "Error fetching delta updates loop context.")
                }
            }

            get("/download/{fileName}") {
                try {
                    val fileNameParam = call.parameters["fileName"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing filename")
                    val uploadDir = File("E:\\Rohit kumbhar\\collabpshere_server\\local_files_upload")
                    val file = File(uploadDir, fileNameParam)

                    if (file.exists()) {
                        call.respondFile(file)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "File not found on server")
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Error downloading file")
                }
            }

            delete("/{fileId}") {
                try {
                    val fileIdParam = call.parameters["fileId"]?.toLongOrNull()
                    if (fileIdParam == null) {
                        call.respond(HttpStatusCode.BadRequest, "Missing or invalid fileId parameter")
                        return@delete
                    }

                    val updatedRows = dbQuery {
                        val recordExists = LocalFilesTable.select { LocalFilesTable.id eq fileIdParam }.any()

                        if (recordExists) {

                            LocalFilesTable.update({ LocalFilesTable.id eq fileIdParam }) {
                                it[LocalFilesTable.isDeleted] = true
                                it[LocalFilesTable.updatedAt] = System.currentTimeMillis()
                            }
                        } else {
                            0
                        }
                    }

                    if (updatedRows > 0) {
                        call.respond(HttpStatusCode.OK, true)
                    } else {
                        call.respond(HttpStatusCode.NotFound, false)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, false)
                }
            }
        }
        route("/ws") {
            webSocket("/dm") {
                val userIdParam = call.parameters["userId"]?.toLongOrNull()
                if (userIdParam == null) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing userId"))
                    return@webSocket
                }

                activeDmSessions[userIdParam] = this

                try {
                    val initialHistoryPayloads = dbQuery {
                        DirectMessagesTable.select {
                            (DirectMessagesTable.senderId eq userIdParam.toInt()) or
                                    (DirectMessagesTable.receiverId eq userIdParam.toInt())
                        }.map { row ->
                            val rawId = row[DirectMessagesTable.id]
                            val resolvedId = when (rawId) {
                                is org.jetbrains.exposed.dao.id.EntityID<*> -> (rawId.value as Number).toInt()
                                is Number -> rawId.toInt()
                                else -> rawId.toString().toInt()
                            }

                            DmDto(
                                action = "HISTORY",
                                id = resolvedId,
                                workspaceId = row[DirectMessagesTable.workspaceId],
                                senderId = row[DirectMessagesTable.senderId],
                                receiverId = row[DirectMessagesTable.receiverId],
                                content = row[DirectMessagesTable.content],
                                timestamp = row[DirectMessagesTable.timestamp]
                            )
                        }
                    }

                    initialHistoryPayloads.forEach { historyDto ->
                        if (this.isActive) {
                            val historyJson = Json.encodeToString(DmDto.serializer(), historyDto)
                            this.send(Frame.Text(historyJson))
                        }
                    }

                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val receivedText = frame.readText()
                            try {
                                val dmDto = Json.decodeFromString(DmDto.serializer(), receivedText)

                                if (dmDto.action == "SEND_MESSAGE") {
                                    val savedMessageDto = dbQuery {
                                        val insertedStatement = DirectMessagesTable.insert {
                                            it[workspaceId] = dmDto.workspaceId
                                            it[senderId] = dmDto.senderId
                                            it[receiverId] = dmDto.receiverId
                                            it[content] = dmDto.content
                                            it[timestamp] = dmDto.timestamp
                                        }

                                        val rawId = insertedStatement[DirectMessagesTable.id]
                                        val generatedId = when (rawId) {
                                            is org.jetbrains.exposed.dao.id.EntityID<*> -> (rawId.value as Number).toInt()
                                            is Number -> rawId.toInt()
                                            else -> rawId.toString().toInt()
                                        }
                                        dmDto.copy(id = generatedId)
                                    }

                                    val receiverPayload =
                                        savedMessageDto.copy(action = "RECEIVE_MESSAGE")
                                    val receiverJson =
                                        Json.encodeToString(DmDto.serializer(), receiverPayload)
                                    val receiverSession =
                                        activeDmSessions[savedMessageDto.receiverId.toLong()]
                                    if (receiverSession != null && receiverSession.isActive) {
                                        receiverSession.send(Frame.Text(receiverJson))
                                    }

                                    if (this.isActive) {
                                        val senderAcknowledgementPayload =
                                            savedMessageDto.copy(action = "MESSAGE_DELIVERED")
                                        val senderJson = Json.encodeToString(
                                            DmDto.serializer(),
                                            senderAcknowledgementPayload
                                        )
                                        this.send(Frame.Text(senderJson))
                                    }
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    activeDmSessions.remove(userIdParam)
                }
            }
        }
    }
}
