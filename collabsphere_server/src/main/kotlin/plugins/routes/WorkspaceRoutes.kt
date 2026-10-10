package plugins

import com.collabsphere.dto.*
import dto.*
import com.collabsphere.model.*
import com.collabsphere.util.EmailService
import com.collabsphere.util.PasswordHasher
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("WorkspaceRoutes")

internal fun Route.workspaceRoutes() {
    route("/api/workspace") {

        delete("/{workspaceId}/members/{userId}") {
            try {
                val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                val targetUserId = call.parameters["userId"]?.toIntOrNull()
                if (workspaceIdParam == null || targetUserId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid workspaceId or userId")
                    return@delete
                }
                val actingUserId = call.authenticatedUserId()
                val outcome = dbQuery {
                    val actorRole = workspaceRole(actingUserId, workspaceIdParam) ?: return@dbQuery HttpStatusCode.Forbidden
                    val targetRole = workspaceRole(targetUserId, workspaceIdParam) ?: return@dbQuery HttpStatusCode.NotFound
                    if (!WorkspaceRoles.canRemove(actorRole, targetRole, isSelf = actingUserId == targetUserId)) {
                        return@dbQuery HttpStatusCode.Forbidden
                    }
                    val removedMemberships = WorkspaceMembersTable.deleteWhere {
                        (WorkspaceMembersTable.workspaceId eq workspaceIdParam) and (WorkspaceMembersTable.userId eq targetUserId)
                    }
                    if (removedMemberships > 0) {
                        // Stamp the workspace in the same transaction. Remaining members receive
                        // its delta and refresh their authoritative member snapshot; the removed
                        // user's full workspace reconciliation drops the now-inaccessible row.
                        WorkspacesTable.update({ WorkspacesTable.id eq workspaceIdParam }) {
                            it[updatedAt] = System.currentTimeMillis()
                        }
                    }
                    NotificationMutesTable.deleteWhere {
                        (NotificationMutesTable.workspaceId eq workspaceIdParam) and (NotificationMutesTable.userId eq targetUserId)
                    }
                    HttpStatusCode.OK
                }
                if (outcome == HttpStatusCode.OK) {
                    WorkspaceMemberCache.invalidate(workspaceIdParam)
                    MembershipCache.invalidate(targetUserId, workspaceIdParam)
                }
                call.respond(outcome, outcome == HttpStatusCode.OK)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.InternalServerError, false)
            }
        }

        put("/{workspaceId}/members/{userId}/role") {
            try {
                val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                val targetUserId = call.parameters["userId"]?.toIntOrNull()
                if (workspaceIdParam == null || targetUserId == null) {
                    call.respond(HttpStatusCode.BadRequest, false)
                    return@put
                }
                val actingUserId = call.authenticatedUserId()
                val requestedRole = call.receive<RoleRequest>().role.trim().uppercase()
                if (requestedRole != WorkspaceRoles.ADMIN && requestedRole != WorkspaceRoles.MEMBER) {
                    call.respond(HttpStatusCode.BadRequest, false)
                    return@put
                }
                val outcome = dbQuery {
                    if (workspaceRole(actingUserId, workspaceIdParam) != WorkspaceRoles.OWNER) return@dbQuery HttpStatusCode.Forbidden
                    val targetRole = workspaceRole(targetUserId, workspaceIdParam) ?: return@dbQuery HttpStatusCode.NotFound
                    if (targetRole == WorkspaceRoles.OWNER) return@dbQuery HttpStatusCode.BadRequest
                    WorkspaceMembersTable.update({
                        (WorkspaceMembersTable.workspaceId eq workspaceIdParam) and (WorkspaceMembersTable.userId eq targetUserId)
                    }) {
                        it[WorkspaceMembersTable.role] = requestedRole
                    }
                    WorkspacesTable.update({ WorkspacesTable.id eq workspaceIdParam }) {
                        it[updatedAt] = System.currentTimeMillis()
                    }
                    HttpStatusCode.OK
                }
                call.respond(outcome, outcome == HttpStatusCode.OK)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.BadRequest, false)
            }
        }

        get("/{workspaceId}/search") {
            try {
                val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                if (workspaceIdParam == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid workspaceId")
                    return@get
                }
                val query = call.request.queryParameters["q"].orEmpty().trim().take(SearchText.MAX_QUERY_LENGTH)
                if (query.length < SearchText.MIN_QUERY_LENGTH) {
                    call.respond(HttpStatusCode.OK, WorkspaceSearchResponse(query = query))
                    return@get
                }
                val actingUserId = call.authenticatedUserId()
                val pattern = "%${escapeLikeLiteral(query.lowercase())}%"
                val perTypeLimit = 20

                val result = dbReadQuery {
                    if (!isMember(actingUserId, workspaceIdParam) ||
                        WorkspacesTable.select(WorkspacesTable.id)
                            .where { (WorkspacesTable.id eq workspaceIdParam) and (WorkspacesTable.isDeleted eq false) }
                            .singleOrNull() == null
                    ) {
                        return@dbReadQuery null
                    }

                    val messages = (MessageTable innerJoin ChannelsTable)
                        .selectAll()
                        .where {
                            (MessageTable.workspaceId eq workspaceIdParam) and
                                    (MessageTable.isDeleted eq false) and
                                    (ChannelsTable.isDeleted eq false) and
                                    (MessageTable.content.lowerCase() like pattern)
                        }
                        .orderBy(MessageTable.id, SortOrder.DESC)
                        .limit(perTypeLimit)
                        .map {
                            SearchMessageHit(
                                id = it[MessageTable.id],
                                channelId = it[MessageTable.channelId],
                                channelName = it[ChannelsTable.channelName],
                                userName = it[MessageTable.userName],
                                content = SearchText.snippet(it[MessageTable.content], query)
                            )
                        }

                    val dmRows = DirectMessagesTable.selectAll()
                        .where {
                            (DirectMessagesTable.workspaceId eq workspaceIdParam) and
                                    (DirectMessagesTable.isDeleted eq false) and
                                    ((DirectMessagesTable.senderId eq actingUserId) or (DirectMessagesTable.receiverId eq actingUserId)) and
                                    (DirectMessagesTable.content.lowerCase() like pattern)
                        }
                        .orderBy(DirectMessagesTable.id, SortOrder.DESC)
                        .limit(perTypeLimit)
                        .toList()
                    val partnerIds = dmRows.map {
                        if (it[DirectMessagesTable.senderId] == actingUserId) it[DirectMessagesTable.receiverId] else it[DirectMessagesTable.senderId]
                    }.toSet()
                    val partnerNames = if (partnerIds.isEmpty()) emptyMap() else UsersTable.selectAll()
                        .where { UsersTable.id inList partnerIds }
                        .associate { it[UsersTable.id] to it[UsersTable.username] }
                    val directMessages = dmRows.map { row ->
                        val partnerId = if (row[DirectMessagesTable.senderId] == actingUserId) row[DirectMessagesTable.receiverId] else row[DirectMessagesTable.senderId]
                        SearchDmHit(
                            id = row.toDmHistoryDto().id ?: 0,
                            partnerId = partnerId,
                            partnerName = partnerNames[partnerId] ?: "User $partnerId",
                            content = SearchText.snippet(row[DirectMessagesTable.content], query),
                            timestamp = row[DirectMessagesTable.timestamp]
                        )
                    }

                    val tasks = TasksTable.selectAll()
                        .where {
                            (TasksTable.workspaceId eq workspaceIdParam) and
                                    (TasksTable.isDeleted eq false) and
                                    ((TasksTable.taskName.lowerCase() like pattern) or (TasksTable.taskDescription.lowerCase() like pattern))
                        }
                        .orderBy(TasksTable.id, SortOrder.DESC)
                        .limit(perTypeLimit)
                        .map { SearchTaskHit(it[TasksTable.id], it[TasksTable.taskName], it[TasksTable.status]) }

                    val notes = NotesTable.selectAll()
                        .where {
                            (NotesTable.workspaceId eq workspaceIdParam) and
                                    (NotesTable.isDeleted eq false) and
                                    ((NotesTable.notesName.lowerCase() like pattern) or (NotesTable.notesDescription.lowerCase() like pattern))
                        }
                        .orderBy(NotesTable.id, SortOrder.DESC)
                        .limit(perTypeLimit)
                        .map {
                            SearchNoteHit(
                                id = it[NotesTable.id],
                                notesName = it[NotesTable.notesName],
                                snippet = SearchText.snippet(it[NotesTable.notesDescription], query)
                            )
                        }

                    val files = LocalFilesTable.selectAll()
                        .where {
                            (LocalFilesTable.workspaceId eq workspaceIdParam) and
                                    (LocalFilesTable.isDeleted eq false) and
                                    (LocalFilesTable.fileName.lowerCase() like pattern)
                        }
                        .orderBy(LocalFilesTable.id, SortOrder.DESC)
                        .limit(perTypeLimit)
                        .map { SearchFileHit(it[LocalFilesTable.id], it[LocalFilesTable.fileName], it[LocalFilesTable.mimeType]) }

                    WorkspaceSearchResponse(query, messages, directMessages, tasks, notes, files)
                }

                if (result == null) {
                    call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                } else {
                    call.respond(HttpStatusCode.OK, result)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, "Search failed")
            }
        }

        post("/create") {
            try {
                val actingUserId = call.authenticatedUserId()
                val request = call.receive<WorkspaceRequest>()
                if (request.clientRequestId != null &&
                    (request.clientRequestId.isBlank() || request.clientRequestId.length > 64)
                ) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Invalid clientRequestId")
                }
                if (request.workspaceName.isBlank() || request.workspaceName.length > 255 ||
                    request.workspaceOwner.isBlank() || request.workspaceOwner.length > 255 ||
                    request.workspacePassword.isBlank() || request.workspacePassword.length > 1024
                ) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Invalid workspace fields")
                }

                val response = dbQuery {
                    request.clientRequestId?.let { requestId ->
                        TransactionManager.current().exec(
                            "SELECT pg_advisory_xact_lock(?, ?)",
                            listOf(
                                IntegerColumnType() to actingUserId,
                                IntegerColumnType() to requestId.hashCode()
                            ),
                            explicitStatementType = StatementType.SELECT
                        ) { rows -> rows.next() }
                    }
                    val existing = request.clientRequestId?.let { requestId ->
                        WorkspacesTable.selectAll()
                            .where {
                                (WorkspacesTable.userId eq actingUserId) and
                                    (WorkspacesTable.clientRequestId eq requestId)
                            }
                            .singleOrNull()
                    }
                    if (existing != null) {
                        require(
                            existing[WorkspacesTable.workspaceName] == request.workspaceName &&
                                existing[WorkspacesTable.workspaceOwner] == request.workspaceOwner &&
                                PasswordHasher.matches(request.workspacePassword, existing[WorkspacesTable.workspacePassword])
                        ) { "clientRequestId was already used for a different workspace" }
                        return@dbQuery WorkspaceResponse(
                            id = existing[WorkspacesTable.id],
                            userId = actingUserId,
                            workspaceName = existing[WorkspacesTable.workspaceName],
                            workspaceOwner = existing[WorkspacesTable.workspaceOwner]
                        )
                    }

                    val insertedId = WorkspacesTable.insert {
                        it[userId] = actingUserId
                        it[workspaceName] = request.workspaceName
                        it[workspaceOwner] = request.workspaceOwner
                        it[workspacePassword] = PasswordHasher.hash(request.workspacePassword)
                        it[clientRequestId] = request.clientRequestId
                        it[isDeleted] = false
                        it[updatedAt] = System.currentTimeMillis()
                    }[WorkspacesTable.id]

                    WorkspaceMembersTable.insert {
                        it[workspaceId] = insertedId
                        it[userId] = actingUserId
                    }
                    // New workspace — no stale cache exists, but invalidate defensively
                    WorkspaceMemberCache.invalidate(insertedId)

                    WorkspaceResponse(
                        id = insertedId,
                        userId = actingUserId,
                        workspaceName = request.workspaceName,
                        workspaceOwner = request.workspaceOwner
                    )
                }

                call.respond(HttpStatusCode.Created, response)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
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
                val actingUserId = call.authenticatedUserId()

                val request = call.receive<AddMemberRequest>()
                val normalizedEmail = request.email.trim().lowercase()
                if (normalizedEmail.isBlank() || normalizedEmail.length > 255 ||
                    !normalizedEmail.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Invalid email address")
                }

                val response = dbQuery {
                    val actorRole = workspaceRole(actingUserId, workspaceIdParam)
                    if (!WorkspaceRoles.canModerate(actorRole)) {
                        return@dbQuery "FORBIDDEN"
                    }
                    val activeWorkspace = WorkspacesTable.select(WorkspacesTable.id)
                        .where { (WorkspacesTable.id eq workspaceIdParam) and (WorkspacesTable.isDeleted eq false) }
                        .singleOrNull() ?: return@dbQuery "NOT_FOUND"

                    val targetUserRow = UsersTable
                        .selectAll()
                        .where { UsersTable.email.lowerCase() eq normalizedEmail }
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
                            .count() > 0L

                        if (!alreadyMember) {
                            WorkspaceMembersTable.insertIgnore {
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
                            email = targetUserEmail,
                            avatarUrl = targetUserRow[UsersTable.avatarUrl]
                        )
                    }
                }

                if (response is MemberResponse) {
                    WorkspaceMemberCache.invalidate(workspaceIdParam)
                    MembershipCache.invalidate(response.userId, workspaceIdParam)
                }

                when (response) {
                    "FORBIDDEN" -> call.respond(HttpStatusCode.Forbidden, "Insufficient workspace role")
                    "NOT_FOUND" -> call.respond(HttpStatusCode.NotFound, "Workspace not found")
                    null -> call.respond(HttpStatusCode.NotFound, "No user found with email: $normalizedEmail")
                    else -> call.respond(HttpStatusCode.Created, response)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.BadRequest, "Error adding member")
            }
        }

        post("/invitations/{workspaceId}") {
            try {
                val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing workspaceId")
                val actingUserId = call.authenticatedUserId()
                val request = call.receive<SendInvitationRequest>()
                val trimmedEmail = request.email.trim().lowercase()

                if (trimmedEmail.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Email cannot be blank")
                }
                if (trimmedEmail.length > 255 || !trimmedEmail.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Invalid email address")
                }

                val wsInfo = dbReadQuery {
                    if (!WorkspaceRoles.canModerate(workspaceRole(actingUserId, workspaceIdParam))) return@dbReadQuery null
                    val ws = WorkspacesTable.selectAll().where { WorkspacesTable.id eq workspaceIdParam }.singleOrNull()
                    val inviter = UsersTable.selectAll().where { UsersTable.id eq actingUserId }.singleOrNull()
                    if (ws != null && inviter != null) {
                        Pair(ws[WorkspacesTable.workspaceName], inviter[UsersTable.username])
                    } else null
                } ?: return@post call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")

                val (workspaceName, inviterName) = wsInfo

                // Check if already a member
                val alreadyMember = dbReadQuery {
                    val userRow = UsersTable.selectAll().where { UsersTable.email.lowerCase() eq trimmedEmail }.singleOrNull()
                    if (userRow != null) {
                        WorkspaceMembersTable.selectAll().where {
                            (WorkspaceMembersTable.workspaceId eq workspaceIdParam) and (WorkspaceMembersTable.userId eq userRow[UsersTable.id])
                        }.count() > 0L
                    } else false
                }

                if (alreadyMember) {
                    return@post call.respond(HttpStatusCode.Conflict, "User is already a member of this workspace")
                }

                // Generate unique 6-character alphanumeric code
                val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
                val inviteCode = (1..6).map { chars.random() }.joinToString("")
                val expiresAt = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L // 7 days

                val insertResult = dbQuery {
                    // Serialize concurrent invite taps for the same normalized workspace/email pair.
                    TransactionManager.current().exec(
                        "SELECT pg_advisory_xact_lock(?, ?)",
                        listOf(
                            IntegerColumnType() to workspaceIdParam,
                            IntegerColumnType() to trimmedEmail.hashCode()
                        ),
                        explicitStatementType = StatementType.SELECT
                    ) { rows -> rows.next() }

                    val currentRole = workspaceRole(actingUserId, workspaceIdParam)
                    if (!WorkspaceRoles.canModerate(currentRole)) return@dbQuery "FORBIDDEN" to null
                    val activeWorkspace = WorkspacesTable.select(WorkspacesTable.id)
                        .where { (WorkspacesTable.id eq workspaceIdParam) and (WorkspacesTable.isDeleted eq false) }
                        .singleOrNull() ?: return@dbQuery "NOT_FOUND" to null

                    val pending = WorkspaceInvitationsTable.select(WorkspaceInvitationsTable.id)
                        .where {
                            (WorkspaceInvitationsTable.workspaceId eq workspaceIdParam) and
                                (WorkspaceInvitationsTable.inviteeEmail.lowerCase() eq trimmedEmail) and
                                (WorkspaceInvitationsTable.status eq "PENDING") and
                                (WorkspaceInvitationsTable.expiresAt greater System.currentTimeMillis())
                        }.singleOrNull()
                    if (pending != null) return@dbQuery "DUPLICATE" to null

                    val recipientId = UsersTable.select(UsersTable.id)
                        .where { UsersTable.email.lowerCase() eq trimmedEmail }
                        .singleOrNull()?.get(UsersTable.id)
                    if (recipientId != null && WorkspaceMembersTable.select(WorkspaceMembersTable.userId)
                            .where {
                                (WorkspaceMembersTable.workspaceId eq workspaceIdParam) and
                                    (WorkspaceMembersTable.userId eq recipientId)
                            }.singleOrNull() != null
                    ) return@dbQuery "ALREADY_MEMBER" to null

                    val invId = WorkspaceInvitationsTable.insert {
                        it[workspaceId] = workspaceIdParam
                        it[inviterUserId] = actingUserId
                        it[inviteeEmail] = trimmedEmail
                        it[WorkspaceInvitationsTable.inviteCode] = inviteCode
                        it[status] = "PENDING"
                        it[WorkspaceInvitationsTable.expiresAt] = expiresAt
                    }[WorkspaceInvitationsTable.id]

                    // If recipient is already a registered user, send an in-app notification too
                    val recipientRow = UsersTable.selectAll().where { UsersTable.email.lowerCase() eq trimmedEmail }.singleOrNull()
                    if (recipientRow != null) {
                        NotificationsTable.insert {
                            it[NotificationsTable.recipientId] = recipientRow[UsersTable.id]
                            it[actorId] = actingUserId
                            it[type] = "WORKSPACE_INVITE"
                            it[title] = "Workspace Invitation"
                            it[body] = "$inviterName invited you to join $workspaceName (Code: $inviteCode)"
                            it[workspaceId] = workspaceIdParam
                            it[referenceId] = invId
                        }
                    }

                    "CREATED" to InvitationResponse(
                        id = invId,
                        workspaceId = workspaceIdParam,
                        workspaceName = workspaceName,
                        inviterName = inviterName,
                        inviteeEmail = trimmedEmail,
                        inviteCode = inviteCode,
                        status = "PENDING",
                        expiresAt = expiresAt,
                        createdAt = System.currentTimeMillis()
                    )
                }

                when (insertResult.first) {
                    "FORBIDDEN" -> return@post call.respond(HttpStatusCode.Forbidden, "Insufficient workspace role")
                    "NOT_FOUND" -> return@post call.respond(HttpStatusCode.NotFound, "Workspace not found")
                    "DUPLICATE" -> return@post call.respond(HttpStatusCode.Conflict, "A pending invitation already exists")
                    "ALREADY_MEMBER" -> return@post call.respond(HttpStatusCode.Conflict, "User is already a member of this workspace")
                }
                val insertedInvitation = requireNotNull(insertResult.second)

                EmailService.sendWorkspaceInvitation(trimmedEmail, workspaceName, inviterName, inviteCode)
                call.respond(HttpStatusCode.Created, insertedInvitation)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.BadRequest, "Failed to send invitation: ${e.message}")
            }
        }

        get("/invitations/pending") {
            try {
                val actingUserId = call.authenticatedUserId()
                val userEmail = dbReadQuery {
                    UsersTable.selectAll().where { UsersTable.id eq actingUserId }.singleOrNull()?.get(UsersTable.email)
                } ?: return@get call.respond(HttpStatusCode.Unauthorized, "User not found")

                val pendingInvites = dbReadQuery {
                    (WorkspaceInvitationsTable innerJoin WorkspacesTable innerJoin UsersTable)
                        .selectAll()
                        .where {
                            (WorkspaceInvitationsTable.inviteeEmail.lowerCase() eq userEmail.lowercase()) and
                            (WorkspaceInvitationsTable.status eq "PENDING") and
                            (WorkspaceInvitationsTable.expiresAt greater System.currentTimeMillis())
                        }
                        .map {
                            InvitationResponse(
                                id = it[WorkspaceInvitationsTable.id],
                                workspaceId = it[WorkspaceInvitationsTable.workspaceId],
                                workspaceName = it[WorkspacesTable.workspaceName],
                                inviterName = it[UsersTable.username],
                                inviteeEmail = it[WorkspaceInvitationsTable.inviteeEmail],
                                inviteCode = it[WorkspaceInvitationsTable.inviteCode],
                                status = it[WorkspaceInvitationsTable.status],
                                expiresAt = it[WorkspaceInvitationsTable.expiresAt],
                                createdAt = it[WorkspaceInvitationsTable.createdAt]
                            )
                        }
                }
                call.respond(HttpStatusCode.OK, pendingInvites)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.InternalServerError, "Failed to fetch invitations")
            }
        }

        post("/invitations/{id}/accept") {
            try {
                val invId = call.parameters["id"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing invitation id")
                val actingUserId = call.authenticatedUserId()

                val userEmail = dbReadQuery {
                    UsersTable.selectAll().where { UsersTable.id eq actingUserId }.singleOrNull()?.get(UsersTable.email)
                } ?: return@post call.respond(HttpStatusCode.Unauthorized, "User not found")

                val acceptResult = dbQuery {
                    TransactionManager.current().exec(
                        "SELECT pg_advisory_xact_lock(?, ?)",
                        listOf(IntegerColumnType() to 0x494E56, IntegerColumnType() to invId),
                        explicitStatementType = StatementType.SELECT
                    ) { rows -> rows.next() }
                    val invRow = WorkspaceInvitationsTable.selectAll()
                        .where { (WorkspaceInvitationsTable.id eq invId) and (WorkspaceInvitationsTable.inviteeEmail.lowerCase() eq userEmail.lowercase()) }
                        .singleOrNull() ?: return@dbQuery "NOT_FOUND"

                    if (invRow[WorkspaceInvitationsTable.status] != "PENDING") {
                        return@dbQuery "ALREADY_PROCESSED"
                    }

                    if (System.currentTimeMillis() >= invRow[WorkspaceInvitationsTable.expiresAt]) {
                        return@dbQuery "EXPIRED"
                    }

                    val wsId = invRow[WorkspaceInvitationsTable.workspaceId]
                    val workspaceIsActive = WorkspacesTable.select(WorkspacesTable.id)
                        .where { (WorkspacesTable.id eq wsId) and (WorkspacesTable.isDeleted eq false) }
                        .singleOrNull() != null
                    if (!workspaceIsActive) return@dbQuery "NOT_FOUND"

                    val alreadyMember = WorkspaceMembersTable.selectAll().where {
                        (WorkspaceMembersTable.workspaceId eq wsId) and (WorkspaceMembersTable.userId eq actingUserId)
                    }.count() > 0L

                    if (!alreadyMember) {
                        WorkspaceMembersTable.insert {
                            it[workspaceId] = wsId
                            it[userId] = actingUserId
                        }
                        WorkspacesTable.update({ WorkspacesTable.id eq wsId }) {
                            it[updatedAt] = System.currentTimeMillis()
                        }
                    }

                    WorkspaceInvitationsTable.update({ WorkspaceInvitationsTable.id eq invId }) {
                        it[status] = "ACCEPTED"
                    }

                    val userRow = UsersTable.selectAll().where { UsersTable.id eq actingUserId }.single()
                    MemberResponse(
                        workspaceId = wsId,
                        userId = actingUserId,
                        userName = userRow[UsersTable.username],
                        email = userRow[UsersTable.email],
                        avatarUrl = userRow[UsersTable.avatarUrl],
                        role = WorkspaceRoles.MEMBER
                    )
                }

                if (acceptResult is MemberResponse) {
                    // Invalidate after the membership transaction commits so concurrent cache fills
                    // cannot repopulate the pre-join state while that transaction is still open.
                    WorkspaceMemberCache.invalidate(acceptResult.workspaceId)
                    MembershipCache.invalidate(actingUserId, acceptResult.workspaceId)
                }

                when (acceptResult) {
                    is MemberResponse -> call.respond(HttpStatusCode.OK, acceptResult)
                    "EXPIRED" -> call.respond(HttpStatusCode.BadRequest, "Invitation expired")
                    "ALREADY_PROCESSED" -> call.respond(HttpStatusCode.BadRequest, "Invitation already processed")
                    else -> call.respond(HttpStatusCode.NotFound, "Invitation not found")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.BadRequest, "Failed to accept invitation")
            }
        }

        post("/invitations/{id}/decline") {
            try {
                val invId = call.parameters["id"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing invitation id")
                val actingUserId = call.authenticatedUserId()

                val userEmail = dbReadQuery {
                    UsersTable.selectAll().where { UsersTable.id eq actingUserId }.singleOrNull()?.get(UsersTable.email)
                } ?: return@post call.respond(HttpStatusCode.Unauthorized, "User not found")

                val declined = dbQuery {
                    TransactionManager.current().exec(
                        "SELECT pg_advisory_xact_lock(?, ?)",
                        listOf(IntegerColumnType() to 0x494E56, IntegerColumnType() to invId),
                        explicitStatementType = StatementType.SELECT
                    ) { rows -> rows.next() }
                    val invitation = WorkspaceInvitationsTable.selectAll()
                        .where {
                            (WorkspaceInvitationsTable.id eq invId) and
                                (WorkspaceInvitationsTable.inviteeEmail.lowerCase() eq userEmail.lowercase())
                        }
                        .singleOrNull() ?: return@dbQuery false
                    when (invitation[WorkspaceInvitationsTable.status]) {
                        "DECLINED" -> true // Idempotent retry after the response was lost.
                        "PENDING" -> WorkspaceInvitationsTable.update({
                            (WorkspaceInvitationsTable.id eq invId) and (WorkspaceInvitationsTable.status eq "PENDING")
                        }) { it[status] = "DECLINED" } > 0
                        else -> false
                    }
                }

                if (declined) {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "success", "message" to "Invitation declined"))
                } else {
                    call.respond(HttpStatusCode.NotFound, "Invitation not found")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.BadRequest, "Failed to decline invitation")
            }
        }

        post("/join-by-code") {
            try {
                val actingUserId = call.authenticatedUserId()
                val request = call.receive<JoinWorkspaceByCodeRequest>()
                val trimmedCode = request.inviteCode.trim().uppercase()

                if (trimmedCode.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Invite code cannot be blank")
                }

                val joinedWorkspace = dbQuery {
                    val invRow = WorkspaceInvitationsTable.selectAll()
                        .where {
                            (WorkspaceInvitationsTable.inviteCode eq trimmedCode) and
                            (WorkspaceInvitationsTable.status eq "PENDING") and
                            (WorkspaceInvitationsTable.expiresAt greater System.currentTimeMillis())
                        }
                        .singleOrNull()

                    val wsId = if (invRow != null) {
                        invRow[WorkspaceInvitationsTable.workspaceId]
                    } else {
                        null
                    }

                    if (wsId == null) return@dbQuery null

                    TransactionManager.current().exec(
                        "SELECT pg_advisory_xact_lock(?, ?)",
                        listOf(IntegerColumnType() to 0x494E56, IntegerColumnType() to invRow!![WorkspaceInvitationsTable.id]),
                        explicitStatementType = StatementType.SELECT
                    ) { rows -> rows.next() }

                    // Re-read after acquiring the invitation lock. Another request may have
                    // accepted/declined this code while this transaction was waiting.
                    val currentInvitation = WorkspaceInvitationsTable.selectAll()
                        .where { WorkspaceInvitationsTable.id eq invRow!![WorkspaceInvitationsTable.id] }
                        .singleOrNull() ?: return@dbQuery null
                    if (currentInvitation[WorkspaceInvitationsTable.status] != "PENDING" ||
                        System.currentTimeMillis() >= currentInvitation[WorkspaceInvitationsTable.expiresAt]
                    ) return@dbQuery null

                    val ws = WorkspacesTable.selectAll().where {
                        (WorkspacesTable.id eq wsId) and (WorkspacesTable.isDeleted eq false)
                    }.singleOrNull()
                        ?: return@dbQuery null

                    val alreadyMember = WorkspaceMembersTable.selectAll().where {
                        (WorkspaceMembersTable.workspaceId eq wsId) and (WorkspaceMembersTable.userId eq actingUserId)
                    }.count() > 0L

                    if (!alreadyMember) {
                        WorkspaceMembersTable.insert {
                            it[workspaceId] = wsId
                            it[userId] = actingUserId
                        }
                        WorkspacesTable.update({ WorkspacesTable.id eq wsId }) {
                            it[updatedAt] = System.currentTimeMillis()
                        }
                    }

                    if (invRow != null) {
                        WorkspaceInvitationsTable.update({ WorkspaceInvitationsTable.id eq invRow!![WorkspaceInvitationsTable.id] }) {
                            it[status] = "ACCEPTED"
                        }
                    }

                    val userRow = UsersTable.selectAll().where { UsersTable.id eq actingUserId }.single()
                    MemberResponse(
                        workspaceId = wsId,
                        userId = actingUserId,
                        userName = userRow[UsersTable.username],
                        email = userRow[UsersTable.email],
                        avatarUrl = userRow[UsersTable.avatarUrl],
                        role = WorkspaceRoles.MEMBER
                    )
                }

                if (joinedWorkspace != null) {
                    WorkspaceMemberCache.invalidate(joinedWorkspace.workspaceId)
                    MembershipCache.invalidate(actingUserId, joinedWorkspace.workspaceId)
                    call.respond(HttpStatusCode.OK, joinedWorkspace)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Invalid or expired invite code")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.BadRequest, "Failed to join workspace: ${e.message}")
            }
        }

        get("/user/{userId}") {
            try {
                val actingUserId = call.authenticatedUserId()

                val workspaces = dbReadQuery {
                    (WorkspacesTable innerJoin WorkspaceMembersTable)
                        .selectAll()
                        .where {
                            (WorkspaceMembersTable.userId eq actingUserId) and
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
                        .distinctBy { it.id }
                }

                call.respond(HttpStatusCode.OK, workspaces)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.InternalServerError, "Error fetching user workspaces")
            }
        }

        get("/members/{workspaceId}") {
            try {
                val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing workspaceId")
                val actingUserId = call.authenticatedUserId()

                val members = dbReadQuery {
                    if (!isMember(actingUserId, workspaceIdParam) ||
                        WorkspacesTable.select(WorkspacesTable.id)
                            .where { (WorkspacesTable.id eq workspaceIdParam) and (WorkspacesTable.isDeleted eq false) }
                            .singleOrNull() == null
                    ) {
                        return@dbReadQuery null
                    }
                    val ownerId = workspaceOwnerId(workspaceIdParam)
                    (WorkspaceMembersTable innerJoin UsersTable)
                        .selectAll()
                        .where { WorkspaceMembersTable.workspaceId eq workspaceIdParam }
                        .map {
                            MemberResponse(
                                workspaceId = workspaceIdParam,
                                userId = it[UsersTable.id],
                                userName = it[UsersTable.username],
                                email = it[UsersTable.email],
                                avatarUrl = it[UsersTable.avatarUrl],
                                role = if (it[UsersTable.id] == ownerId) WorkspaceRoles.OWNER else it[WorkspaceMembersTable.role]
                            )
                        }
                }

                if (members == null) {
                    call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                } else {
                    call.respond(HttpStatusCode.OK, members)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(
                    HttpStatusCode.InternalServerError,
                    "Error fetching workspace members"
                )
            }
        }

        get("/sync/{userId}") {
            try {
                val actingUserId = call.authenticatedUserId()
                val page = dbReadQuery { workspaceDeltaSync(actingUserId, call.syncRequest()) }
                recordSyncPage("workspaces", page.rows.size)
                call.appendSyncHeaders(page.nextCursor, page.reset, page.nextPageToken)
                call.respond(HttpStatusCode.OK, page.rows)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.error("[Sync] Workspace sync failed", e)
                call.respond(HttpStatusCode.InternalServerError, "Sync Error")
            }
        }

        delete("/delete") {
            try {
                val workspaceId = call.request.queryParameters["workspaceId"]?.toIntOrNull()

                if (workspaceId == null) {
                    return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        "Missing or invalid workspaceId"
                    )
                }

                val actingUserId = call.authenticatedUserId()

                val password = call.request.headers["X-Workspace-Password"]
                    ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        "Missing password"
                    )

                val deletedIds = dbQuery {
                    val condition = (WorkspacesTable.id eq workspaceId) and
                        (WorkspacesTable.userId eq actingUserId) and
                        (WorkspacesTable.isDeleted eq false)

                    val matchingIds = WorkspacesTable.selectAll()
                        .where { condition }
                        .filter { PasswordHasher.matches(password, it[WorkspacesTable.workspacePassword]) }
                        .map { it[WorkspacesTable.id] }

                    if (matchingIds.isNotEmpty()) {
                        WorkspacesTable.update({ WorkspacesTable.id inList matchingIds }) {
                            it[isDeleted] = true
                            it[updatedAt] = System.currentTimeMillis()
                        }
                    }
                    matchingIds
                }

                deletedIds.forEach { deletedWorkspaceId ->
                    WorkspaceMemberCache.invalidate(deletedWorkspaceId)
                    MembershipCache.invalidateWorkspace(deletedWorkspaceId)
                }

                if (deletedIds.isNotEmpty()) {
                    call.respond(HttpStatusCode.OK, DeleteWorkspaceResponse(deletedIds))
                } else {
                    call.respond(HttpStatusCode.NotFound, DeleteWorkspaceResponse(emptyList()))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.InternalServerError, DeleteWorkspaceResponse(emptyList()))
            }
        }
    }
}
