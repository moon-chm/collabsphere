package plugins

import com.collabsphere.dto.MessageRequest
import com.collabsphere.dto.MessageResponse
import com.collabsphere.dto.NotesRequest
import com.collabsphere.dto.NotesResponse
import com.collabsphere.dto.FileResponse
import com.collabsphere.dto.FileSyncResponse
import com.collabsphere.dto.MessageSyncResponse
import com.collabsphere.dto.NotesSyncResponse
import com.collabsphere.dto.*
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
import com.collabsphere.model.UserBlocksTable
import com.collabsphere.model.UserVerificationTable
import com.collabsphere.model.NotificationsTable
import com.collabsphere.dto.NotificationResponse
import com.collabsphere.dto.NotificationCountResponse
import com.collabsphere.dto.MarkReadRequest
import com.collabsphere.dto.NotificationPushFrame
import kotlinx.serialization.encodeToString
import com.collabsphere.util.JwtConfig
import com.collabsphere.util.PasswordHasher
import com.collabsphere.util.AvatarGenerator
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
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

// A user can have more than one live connection (multiple devices, or a second app instance) — keyed
// by a set of sessions per user instead of a single session, so one doesn't silently evict another.
val activeDmSessions = ConcurrentHashMap<Long, MutableSet<WebSocketServerSession>>()

private fun addDmSession(userId: Long, session: WebSocketServerSession) {
    activeDmSessions.computeIfAbsent(userId) { java.util.concurrent.CopyOnWriteArraySet() }.add(session)
}

private fun removeDmSession(userId: Long, session: WebSocketServerSession) {
    activeDmSessions[userId]?.let { sessions ->
        sessions.remove(session)
        if (sessions.isEmpty()) activeDmSessions.remove(userId)
    }
}

private suspend fun sendToUser(userId: Long, text: String) {
    activeDmSessions[userId]?.forEach { session ->
        if (session.isActive) {
            try {
                session.send(Frame.Text(text))
            } catch (_: Exception) {
            }
        }
    }
}

private const val MAX_UPLOAD_BYTES = 25L * 1024 * 1024
private class UploadTooLargeException :
    Exception("File exceeds the ${MAX_UPLOAD_BYTES / (1024 * 1024)}MB upload limit")

/** The caller's identity, established by a verified JWT. Only valid inside an `authenticate("auth-jwt")` block. */
fun ApplicationCall.authenticatedUserId(): Int =
    principal<JWTPrincipal>()!!.payload.getClaim("userId").asInt()

/** Must be called from inside an existing `dbQuery`/transaction block. */
private fun isMember(userId: Int, workspaceId: Int): Boolean =
    WorkspaceMembersTable.selectAll()
        .where { (WorkspaceMembersTable.workspaceId eq workspaceId) and (WorkspaceMembersTable.userId eq userId) }
        .count() > 0

/** Regex to detect @username mentions in message content. */
private val MENTION_REGEX = Regex("@(\\w{2,})") 

/**
 * Inserts a notification row and, if the recipient has an active WebSocket session,
 * pushes the notification frame in real-time. Safe to call from any coroutine context.
 */
private suspend fun createAndPushNotification(
    recipientId: Int,
    actorId: Int?,
    type: String,
    title: String,
    body: String,
    workspaceId: Int? = null,
    referenceId: Int? = null
) {
    val notification = dbQuery {
        val actorRow = actorId?.let {
            UsersTable.selectAll().where { UsersTable.id eq it }.singleOrNull()
        }
        val insertedId = NotificationsTable.insert {
            it[NotificationsTable.recipientId] = recipientId
            it[NotificationsTable.actorId] = actorId
            it[NotificationsTable.type] = type
            it[NotificationsTable.title] = title
            it[NotificationsTable.body] = body
            it[NotificationsTable.workspaceId] = workspaceId
            it[NotificationsTable.referenceId] = referenceId
        }[NotificationsTable.id]
        NotificationResponse(
            id = insertedId,
            recipientId = recipientId,
            actorId = actorId,
            actorUsername = actorRow?.get(UsersTable.username),
            actorAvatarUrl = actorRow?.let { r -> AvatarGenerator.avatarUrlFor(r[UsersTable.id], r[UsersTable.avatarUrl]) },
            type = type,
            title = title,
            body = body,
            workspaceId = workspaceId,
            referenceId = referenceId,
            isRead = false,
            createdAt = System.currentTimeMillis()
        )
    }
    val frame = NotificationPushFrame(notification = notification)
    val json = Json.encodeToString(frame)
    sendToUser(recipientId.toLong(), json)
}

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("CollabSphere Server is running!", ContentType.Text.Plain, HttpStatusCode.OK)
        }

        head("/") {
            call.respond(HttpStatusCode.OK)
        }

        get("/health") {
            call.respondText("OK", ContentType.Text.Plain, HttpStatusCode.OK)
        }

        head("/health") {
            call.respond(HttpStatusCode.OK)
        }

        // ── Public avatar endpoint (no auth required) ─────────────────────────
        get("/avatars/default/{userId}") {
            val userId = call.parameters["userId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid userId")
            val username = dbQuery {
                UsersTable.selectAll().where { UsersTable.id eq userId }
                    .singleOrNull()?.get(UsersTable.username) ?: "?"
            }
            val svg = AvatarGenerator.generate(userId, username)
            call.respondText(svg, ContentType.parse("image/svg+xml"), HttpStatusCode.OK)
        }

        get("/avatars/{filename}") {
            val filename = call.parameters["filename"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val file = File("local_files_upload/avatars/$filename")
            if (file.exists()) {
                call.respondFile(file)
            } else {
                call.respond(HttpStatusCode.NotFound, "Avatar not found")
            }
        }

        post("/api/login") {
            try {
                val request = call.receive<LoginRequest>()
                val user = dbQuery {
                    UsersTable.selectAll()
                        .where { UsersTable.email eq request.email }
                        .singleOrNull()
                        ?.takeIf { PasswordHasher.matches(request.password, it[UsersTable.password]) }
                        ?.let { row ->
                            if (!PasswordHasher.isHashed(row[UsersTable.password])) {
                                UsersTable.update({ UsersTable.id eq row[UsersTable.id] }) { stmt ->
                                    stmt[password] = PasswordHasher.hash(request.password)
                                }
                            }
                            val uid = row[UsersTable.id]
                            LoginResponse(
                                id = uid,
                                userName = row[UsersTable.username],
                                email = row[UsersTable.email],
                                token = JwtConfig.generateToken(uid),
                                avatarUrl = AvatarGenerator.avatarUrlFor(uid, row[UsersTable.avatarUrl]),
                                isEmailVerified = row[UsersTable.isEmailVerified]
                            )
                        }
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
                        it[password] = PasswordHasher.hash(request.password)
                    }

                    val generatedId = insertStatement[UsersTable.id]

                    LoginResponse(
                        id = generatedId,
                        userName = request.userName,
                        email = request.email,
                        token = JwtConfig.generateToken(generatedId),
                        avatarUrl = AvatarGenerator.avatarUrlFor(generatedId, null),
                        isEmailVerified = false
                    )
                }

                call.respond(HttpStatusCode.Created, userResponse)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Server Error")
            }
        }

        authenticate("auth-jwt") {

            // ── GET own full profile ───────────────────────────────────────────
            get("/api/user/profile") {
                try {
                    val actingUserId = call.authenticatedUserId()
                    val profile = dbQuery {
                        UsersTable.selectAll().where { UsersTable.id eq actingUserId }.singleOrNull()
                            ?.let { row ->
                                UserProfileResponse(
                                    id = row[UsersTable.id],
                                    username = row[UsersTable.username],
                                    email = row[UsersTable.email],
                                    avatarUrl = AvatarGenerator.avatarUrlFor(row[UsersTable.id], row[UsersTable.avatarUrl]),
                                    bio = row[UsersTable.bio],
                                    statusMessage = row[UsersTable.statusMessage],
                                    isEmailVerified = row[UsersTable.isEmailVerified],
                                    lastSeen = row[UsersTable.lastSeen],
                                    showEmail = row[UsersTable.showEmail],
                                    showOnlineStatus = row[UsersTable.showOnlineStatus],
                                    showLastSeen = row[UsersTable.showLastSeen],
                                    profileVisibility = row[UsersTable.profileVisibility]
                                )
                            }
                    }
                    if (profile != null) call.respond(HttpStatusCode.OK, profile)
                    else call.respond(HttpStatusCode.NotFound, "User not found")
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Error fetching profile")
                }
            }

            // ── UPDATE own profile (username, bio, status, password) ───────────
            post("/api/user/profile") {
                try {
                    val request = call.receive<UpdateProfileRequest>()
                    val actingUserId = call.authenticatedUserId()
                    val isSuccess = dbQuery {
                        val userRow = UsersTable.selectAll().where { UsersTable.id eq actingUserId }.singleOrNull()
                        if (userRow == null) {
                            false
                        } else {
                            if (!request.newPassword.isNullOrBlank() && !request.currentPassword.isNullOrBlank()) {
                                if (!PasswordHasher.matches(request.currentPassword, userRow[UsersTable.password])) {
                                    return@dbQuery false
                                }
                                UsersTable.update({ UsersTable.id eq actingUserId }) {
                                    it[username] = request.userName
                                    it[bio] = request.bio
                                    it[statusMessage] = request.statusMessage
                                    it[password] = PasswordHasher.hash(request.newPassword)
                                }
                            } else {
                                UsersTable.update({ UsersTable.id eq actingUserId }) {
                                    it[username] = request.userName
                                    it[bio] = request.bio
                                    it[statusMessage] = request.statusMessage
                                }
                            }
                            true
                        }
                    }
                    if (isSuccess) call.respond(HttpStatusCode.OK, true)
                    else call.respond(HttpStatusCode.BadRequest, false)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, false)
                }
            }

            // ── UPLOAD avatar ──────────────────────────────────────────────────
            post("/api/user/avatar") {
                val avatarMaxBytes = 5L * 1024 * 1024 // 5 MB
                try {
                    val actingUserId = call.authenticatedUserId()
                    val multipart = call.receiveMultipart()
                    var savedUrl: String? = null

                    multipart.forEachPart { part ->
                        if (part is PartData.FileItem) {
                            val bytes = part.streamProvider().readBytes()
                            if (bytes.size > avatarMaxBytes) throw IllegalArgumentException("Avatar exceeds 5 MB limit")
                            val ext = part.originalFileName?.substringAfterLast('.', "jpg") ?: "jpg"
                            val filename = "avatar_${actingUserId}_${UUID.randomUUID()}.$ext"
                            val dir = File("local_files_upload/avatars").apply { mkdirs() }
                            File(dir, filename).writeBytes(bytes)
                            savedUrl = "/avatars/$filename"
                        }
                        part.dispose()
                    }

                    if (savedUrl != null) {
                        dbQuery {
                            UsersTable.update({ UsersTable.id eq actingUserId }) {
                                it[avatarUrl] = savedUrl
                            }
                        }
                        call.respond(HttpStatusCode.OK, AvatarUploadResponse(avatarUrl = savedUrl!!))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "No file received")
                    }
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.PayloadTooLarge, e.message ?: "File too large")
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Avatar upload failed")
                }
            }

            // ── DELETE avatar (revert to generated default) ────────────────────
            delete("/api/user/avatar") {
                try {
                    val actingUserId = call.authenticatedUserId()
                    dbQuery {
                        UsersTable.update({ UsersTable.id eq actingUserId }) {
                            it[avatarUrl] = null
                        }
                    }
                    call.respond(HttpStatusCode.OK, AvatarUploadResponse(avatarUrl = AvatarGenerator.avatarUrlFor(actingUserId, null)))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Failed to remove avatar")
                }
            }

            // ── CHANGE email ───────────────────────────────────────────────────
            put("/api/user/email") {
                try {
                    val actingUserId = call.authenticatedUserId()
                    val request = call.receive<ChangeEmailRequest>()

                    val result = dbQuery {
                        val row = UsersTable.selectAll().where { UsersTable.id eq actingUserId }.singleOrNull()
                            ?: return@dbQuery "NOT_FOUND"
                        if (!PasswordHasher.matches(request.currentPassword, row[UsersTable.password]))
                            return@dbQuery "WRONG_PASSWORD"
                        val emailTaken = UsersTable.selectAll().where { UsersTable.email eq request.newEmail }.count() > 0
                        if (emailTaken) return@dbQuery "EMAIL_TAKEN"
                        UsersTable.update({ UsersTable.id eq actingUserId }) {
                            it[email] = request.newEmail
                            it[isEmailVerified] = false // must re-verify new email
                        }
                        "OK"
                    }
                    when (result) {
                        "OK"           -> call.respond(HttpStatusCode.OK, "Email updated")
                        "WRONG_PASSWORD" -> call.respond(HttpStatusCode.Unauthorized, "Incorrect password")
                        "EMAIL_TAKEN"  -> call.respond(HttpStatusCode.Conflict, "Email already in use")
                        else           -> call.respond(HttpStatusCode.NotFound, "User not found")
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Failed to update email")
                }
            }

            // ── GET another user's public profile (privacy-aware) ─────────────
            get("/api/user/{userId}") {
                try {
                    val actingUserId = call.authenticatedUserId()
                    val targetId = call.parameters["userId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid userId")

                    val response = dbQuery {
                        val isBlocked = UserBlocksTable.selectAll().where {
                            (UserBlocksTable.blockerId eq targetId) and (UserBlocksTable.blockedId eq actingUserId)
                        }.count() > 0
                        if (isBlocked) return@dbQuery null

                        UsersTable.selectAll().where { UsersTable.id eq targetId }.singleOrNull()?.let { row ->
                            val isOnlineNow = activeDmSessions.containsKey(targetId.toLong())
                            PublicProfileResponse(
                                id = row[UsersTable.id],
                                username = row[UsersTable.username],
                                email = if (row[UsersTable.showEmail]) row[UsersTable.email] else null,
                                avatarUrl = AvatarGenerator.avatarUrlFor(row[UsersTable.id], row[UsersTable.avatarUrl]),
                                bio = row[UsersTable.bio],
                                statusMessage = row[UsersTable.statusMessage],
                                isOnline = if (row[UsersTable.showOnlineStatus]) isOnlineNow else null,
                                lastSeen = if (row[UsersTable.showLastSeen]) row[UsersTable.lastSeen] else null
                            )
                        }
                    }
                    if (response != null) call.respond(HttpStatusCode.OK, response)
                    else call.respond(HttpStatusCode.NotFound, "User not found or you are blocked")
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Error fetching profile")
                }
            }

            // ── SEARCH users ───────────────────────────────────────────────────
            get("/api/user/search") {
                try {
                    val actingUserId = call.authenticatedUserId()
                    val query = call.request.queryParameters["q"]?.trim() ?: ""
                    if (query.length < 2) {
                        call.respond(HttpStatusCode.BadRequest, "Search query must be at least 2 characters")
                        return@get
                    }

                    val results = dbQuery {
                        val blockedIds = UserBlocksTable.selectAll().where {
                            (UserBlocksTable.blockerId eq actingUserId) or (UserBlocksTable.blockedId eq actingUserId)
                        }.map { if (it[UserBlocksTable.blockerId] == actingUserId) it[UserBlocksTable.blockedId] else it[UserBlocksTable.blockerId] }.toSet()

                        UsersTable.selectAll().where {
                            (UsersTable.username like "%$query%") or (UsersTable.email like "%$query%")
                        }.filter { it[UsersTable.id] !in blockedIds && it[UsersTable.id] != actingUserId }
                            .map { row ->
                                SearchUserResult(
                                    id = row[UsersTable.id],
                                    username = row[UsersTable.username],
                                    email = if (row[UsersTable.showEmail]) row[UsersTable.email] else null,
                                    avatarUrl = AvatarGenerator.avatarUrlFor(row[UsersTable.id], row[UsersTable.avatarUrl])
                                )
                            }
                    }
                    call.respond(HttpStatusCode.OK, results)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Error searching users")
                }
            }

            // ── DELETE own account ─────────────────────────────────────────────
            delete("/api/user/account") {
                try {
                    val actingUserId = call.authenticatedUserId()
                    val request = call.receive<DeleteAccountRequest>()
                    val result = dbQuery {
                        val row = UsersTable.selectAll().where { UsersTable.id eq actingUserId }.singleOrNull()
                            ?: return@dbQuery "NOT_FOUND"
                        if (!PasswordHasher.matches(request.password, row[UsersTable.password]))
                            return@dbQuery "WRONG_PASSWORD"
                        UsersTable.deleteWhere { UsersTable.id eq actingUserId }
                        "OK"
                    }
                    when (result) {
                        "OK"             -> call.respond(HttpStatusCode.OK, "Account deleted")
                        "WRONG_PASSWORD" -> call.respond(HttpStatusCode.Unauthorized, "Incorrect password")
                        else             -> call.respond(HttpStatusCode.NotFound, "User not found")
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Failed to delete account")
                }
            }

            // ── BLOCK a user ───────────────────────────────────────────────────
            post("/api/user/block/{targetUserId}") {
                try {
                    val actingUserId = call.authenticatedUserId()
                    val targetId = call.parameters["targetUserId"]?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid targetUserId")
                    if (actingUserId == targetId)
                        return@post call.respond(HttpStatusCode.BadRequest, "Cannot block yourself")

                    dbQuery {
                        val alreadyBlocked = UserBlocksTable.selectAll().where {
                            (UserBlocksTable.blockerId eq actingUserId) and (UserBlocksTable.blockedId eq targetId)
                        }.count() > 0
                        if (!alreadyBlocked) {
                            UserBlocksTable.insert {
                                it[blockerId] = actingUserId
                                it[blockedId] = targetId
                            }
                        }
                    }
                    call.respond(HttpStatusCode.OK, BlockUserResponse(actingUserId, targetId, "blocked"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Failed to block user")
                }
            }

            // ── UNBLOCK a user ─────────────────────────────────────────────────
            delete("/api/user/block/{targetUserId}") {
                try {
                    val actingUserId = call.authenticatedUserId()
                    val targetId = call.parameters["targetUserId"]?.toIntOrNull()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid targetUserId")
                    dbQuery {
                        UserBlocksTable.deleteWhere {
                            (UserBlocksTable.blockerId eq actingUserId) and (UserBlocksTable.blockedId eq targetId)
                        }
                    }
                    call.respond(HttpStatusCode.OK, BlockUserResponse(actingUserId, targetId, "unblocked"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Failed to unblock user")
                }
            }

            // ── LIST blocked users ─────────────────────────────────────────────
            get("/api/user/blocks") {
                try {
                    val actingUserId = call.authenticatedUserId()
                    val blocked = dbQuery {
                        (UserBlocksTable innerJoin UsersTable.alias("blocked_user"))
                        UserBlocksTable.selectAll().where { UserBlocksTable.blockerId eq actingUserId }
                            .mapNotNull { row ->
                                val blockedId = row[UserBlocksTable.blockedId]
                                UsersTable.selectAll().where { UsersTable.id eq blockedId }.singleOrNull()?.let { ur ->
                                    SearchUserResult(
                                        id = ur[UsersTable.id],
                                        username = ur[UsersTable.username],
                                        email = null,
                                        avatarUrl = AvatarGenerator.avatarUrlFor(ur[UsersTable.id], ur[UsersTable.avatarUrl])
                                    )
                                }
                            }
                    }
                    call.respond(HttpStatusCode.OK, blocked)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Failed to fetch blocked users")
                }
            }

            // ── UPDATE privacy settings ────────────────────────────────────────
            put("/api/user/privacy") {
                try {
                    val actingUserId = call.authenticatedUserId()
                    val request = call.receive<PrivacySettingsRequest>()
                    val validVisibility = setOf("public", "members_only")
                    if (request.profileVisibility !in validVisibility)
                        return@put call.respond(HttpStatusCode.BadRequest, "profileVisibility must be 'public' or 'members_only'")

                    dbQuery {
                        UsersTable.update({ UsersTable.id eq actingUserId }) {
                            it[showEmail] = request.showEmail
                            it[showOnlineStatus] = request.showOnlineStatus
                            it[showLastSeen] = request.showLastSeen
                            it[profileVisibility] = request.profileVisibility
                        }
                    }
                    call.respond(HttpStatusCode.OK, "Privacy settings updated")
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Failed to update privacy settings")
                }
            }

            // ── SEND email verification (stubbed — token logged, not emailed) ──
            post("/api/user/verify-email/send") {
                try {
                    val actingUserId = call.authenticatedUserId()
                    val token = UUID.randomUUID().toString()
                    val expiresAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000L // 24 hours

                    dbQuery {
                        UserVerificationTable.deleteWhere { UserVerificationTable.userId eq actingUserId }
                        UserVerificationTable.insert {
                            it[userId] = actingUserId
                            it[UserVerificationTable.token] = token
                            it[UserVerificationTable.expiresAt] = expiresAt
                        }
                    }
                    // STUB: log token instead of emailing
                    println("[EMAIL VERIFICATION STUB] userId=$actingUserId token=$token")
                    call.respond(HttpStatusCode.OK, "Verification token sent (check server logs)")
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Failed to send verification")
                }
            }

            // ── CONFIRM email verification ─────────────────────────────────────
            post("/api/user/verify-email/confirm") {
                try {
                    val actingUserId = call.authenticatedUserId()
                    val request = call.receive<EmailVerifyConfirmRequest>()
                    val result = dbQuery {
                        val row = UserVerificationTable.selectAll().where {
                            UserVerificationTable.userId eq actingUserId
                        }.singleOrNull() ?: return@dbQuery "NOT_FOUND"

                        if (row[UserVerificationTable.token] != request.token)
                            return@dbQuery "WRONG_TOKEN"
                        if (System.currentTimeMillis() > row[UserVerificationTable.expiresAt])
                            return@dbQuery "EXPIRED"

                        UsersTable.update({ UsersTable.id eq actingUserId }) {
                            it[isEmailVerified] = true
                        }
                        UserVerificationTable.deleteWhere { UserVerificationTable.userId eq actingUserId }
                        "OK"
                    }
                    when (result) {
                        "OK"          -> call.respond(HttpStatusCode.OK, "Email verified")
                        "WRONG_TOKEN" -> call.respond(HttpStatusCode.BadRequest, "Invalid token")
                        "EXPIRED"     -> call.respond(HttpStatusCode.Gone, "Token expired — request a new one")
                        else          -> call.respond(HttpStatusCode.NotFound, "No pending verification")
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Failed to confirm verification")
                }
            }

            // ════════════════════════════════════════════════════════════════
            // NOTIFICATIONS
            // ════════════════════════════════════════════════════════════════

            route("/api/notifications") {

                // ── LIST: GET /api/notifications?unread=true&limit=50&offset=0 ──
                get {
                    try {
                        val actingUserId = call.authenticatedUserId()
                        val onlyUnread = call.request.queryParameters["unread"] == "true"
                        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
                        val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L

                        val notifications = dbQuery {
                            val query = NotificationsTable.selectAll()
                                .where {
                                    if (onlyUnread)
                                        (NotificationsTable.recipientId eq actingUserId) and (NotificationsTable.isRead eq false)
                                    else
                                        NotificationsTable.recipientId eq actingUserId
                                }
                                .orderBy(NotificationsTable.createdAt, SortOrder.DESC)
                                .limit(limit, offset)

                            query.map { row ->
                                val aId = row[NotificationsTable.actorId]
                                val actorRow = aId?.let {
                                    UsersTable.selectAll().where { UsersTable.id eq it }.singleOrNull()
                                }
                                NotificationResponse(
                                    id = row[NotificationsTable.id],
                                    recipientId = row[NotificationsTable.recipientId],
                                    actorId = aId,
                                    actorUsername = actorRow?.get(UsersTable.username),
                                    actorAvatarUrl = actorRow?.let { r -> AvatarGenerator.avatarUrlFor(r[UsersTable.id], r[UsersTable.avatarUrl]) },
                                    type = row[NotificationsTable.type],
                                    title = row[NotificationsTable.title],
                                    body = row[NotificationsTable.body],
                                    workspaceId = row[NotificationsTable.workspaceId],
                                    referenceId = row[NotificationsTable.referenceId],
                                    isRead = row[NotificationsTable.isRead],
                                    createdAt = row[NotificationsTable.createdAt]
                                )
                            }
                        }
                        call.respond(HttpStatusCode.OK, notifications)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to fetch notifications")
                    }
                }

                // ── COUNT: GET /api/notifications/count ───────────────────────
                get("/count") {
                    try {
                        val actingUserId = call.authenticatedUserId()
                        val total = dbQuery {
                            NotificationsTable.selectAll()
                                .where { NotificationsTable.recipientId eq actingUserId }.count().toInt()
                        }
                        val unread = dbQuery {
                            NotificationsTable.selectAll()
                                .where {
                                    (NotificationsTable.recipientId eq actingUserId) and
                                    (NotificationsTable.isRead eq false)
                                }.count().toInt()
                        }
                        call.respond(HttpStatusCode.OK, NotificationCountResponse(total = total, unread = unread))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to fetch notification count")
                    }
                }

                // ── MARK READ: PUT /api/notifications/read ────────────────────
                put("/read") {
                    try {
                        val actingUserId = call.authenticatedUserId()
                        val request = call.receive<MarkReadRequest>()
                        dbQuery {
                            if (request.ids.isNullOrEmpty()) {
                                // Mark all as read
                                NotificationsTable.update({
                                    NotificationsTable.recipientId eq actingUserId
                                }) { it[NotificationsTable.isRead] = true }
                            } else {
                                // Mark specific ids as read (only own)
                                NotificationsTable.update({
                                    (NotificationsTable.recipientId eq actingUserId) and
                                    (NotificationsTable.id inList request.ids)
                                }) { it[NotificationsTable.isRead] = true }
                            }
                        }
                        call.respond(HttpStatusCode.OK, "Marked as read")
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to mark notifications as read")
                    }
                }

                // ── DELETE ONE: DELETE /api/notifications/{id} ────────────────
                delete("/{id}") {
                    try {
                        val actingUserId = call.authenticatedUserId()
                        val notifId = call.parameters["id"]?.toIntOrNull()
                            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid id")
                        val deleted = dbQuery {
                            NotificationsTable.deleteWhere {
                                (NotificationsTable.id eq notifId) and (NotificationsTable.recipientId eq actingUserId)
                            }
                        }
                        if (deleted > 0) call.respond(HttpStatusCode.OK, "Deleted")
                        else call.respond(HttpStatusCode.NotFound, "Notification not found")
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to delete notification")
                    }
                }

                // ── CLEAR ALL: DELETE /api/notifications ──────────────────────
                delete {
                    try {
                        val actingUserId = call.authenticatedUserId()
                        dbQuery {
                            NotificationsTable.deleteWhere { NotificationsTable.recipientId eq actingUserId }
                        }
                        call.respond(HttpStatusCode.OK, "All notifications cleared")
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to clear notifications")
                    }
                }
            }

            route("/api/workspace") {

                post("/create") {
                    try {
                        val actingUserId = call.authenticatedUserId()
                        val request = call.receive<WorkspaceRequest>()

                        val response = dbQuery {
                            val insertedId = WorkspacesTable.insert {
                                it[userId] = actingUserId
                                it[workspaceName] = request.workspaceName
                                it[workspaceOwner] = request.workspaceOwner
                                it[workspacePassword] = PasswordHasher.hash(request.workspacePassword)
                                it[isDeleted] = false
                                it[updatedAt] = System.currentTimeMillis()
                            }[WorkspacesTable.id]

                            WorkspaceMembersTable.insert {
                                it[workspaceId] = insertedId
                                it[userId] = actingUserId
                            }

                            WorkspaceResponse(
                                id = insertedId,
                                userId = actingUserId,
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
                        val actingUserId = call.authenticatedUserId()

                        val request = call.receive<AddMemberRequest>()

                        val response = dbQuery {
                            if (!isMember(actingUserId, workspaceIdParam)) {
                                return@dbQuery "FORBIDDEN"
                            }

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

                        when (response) {
                            "FORBIDDEN" -> call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                            null -> call.respond(HttpStatusCode.NotFound, "No user found with email: ${request.email}")
                            else -> call.respond(HttpStatusCode.Created, response)
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, "Error adding member")
                    }
                }

                get("/user/{userId}") {
                    try {
                        val actingUserId = call.authenticatedUserId()

                        val workspaces = dbQuery {
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
                        val actingUserId = call.authenticatedUserId()

                        val members = dbQuery {
                            if (!isMember(actingUserId, workspaceIdParam)) {
                                return@dbQuery null
                            }
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

                        if (members == null) {
                            call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                        } else {
                            call.respond(HttpStatusCode.OK, members)
                        }
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            "Error fetching workspace members"
                        )
                    }
                }

                get("/sync/{userId}") {
                    try {
                        val actingUserId = call.authenticatedUserId()
                        val sinceTimestamp = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L

                        val updates = dbQuery {
                            (WorkspacesTable innerJoin WorkspaceMembersTable)
                                .selectAll()
                                .where {
                                    (WorkspaceMembersTable.userId eq actingUserId) and
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
                        val workspaceName = call.request.queryParameters["workspaceName"]

                        if (workspaceId == null && workspaceName.isNullOrBlank()) {
                            return@delete call.respond(
                                HttpStatusCode.BadRequest,
                                "Missing workspaceId or workspaceName"
                            )
                        }

                        val actingUserId = call.authenticatedUserId()

                        val password = call.request.queryParameters["workspacePassword"]
                            ?: return@delete call.respond(
                                HttpStatusCode.BadRequest,
                                "Missing password"
                            )

                        val deletedIds = dbQuery {
                            val condition = if (workspaceId != null) {
                                (WorkspacesTable.id eq workspaceId) and (WorkspacesTable.userId eq actingUserId)
                            } else {
                                (WorkspacesTable.workspaceName eq workspaceName!!) and (WorkspacesTable.userId eq actingUserId)
                            }

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

                        if (deletedIds.isNotEmpty()) {
                            call.respond(HttpStatusCode.OK, DeleteWorkspaceResponse(deletedIds))
                        } else {
                            call.respond(HttpStatusCode.NotFound, DeleteWorkspaceResponse(emptyList()))
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, DeleteWorkspaceResponse(emptyList()))
                    }
                }
            }

            route("/api/channels") {
                post {
                    try {
                        val actingUserId = call.authenticatedUserId()
                        val request = call.receive<ChannelRequest>()

                        val response = dbQuery {
                            if (!isMember(actingUserId, request.workspaceId)) {
                                return@dbQuery null
                            }
                            val insertedId = ChannelsTable.insert {
                                it[userId] = actingUserId
                                it[channelName] = request.channelName
                                it[workspaceId] = request.workspaceId
                                it[description] = request.description
                                it[updatedAt] = System.currentTimeMillis()
                                it[isDeleted] = false
                            }[ChannelsTable.id]

                            ChannelResponse(
                                id = insertedId,
                                userId = actingUserId,
                                channelName = request.channelName,
                                workspaceId = request.workspaceId,
                                description = request.description
                            )
                        }
                        if (response == null) {
                            call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                        } else {
                            call.respond(HttpStatusCode.Created, response)
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, "Failed to create channel")
                    }
                }

                delete("/{channelName}/{workspaceId}/{userId}") {
                    try {
                        val channelNameParam = call.parameters["channelName"]
                        val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                        val actingUserId = call.authenticatedUserId()

                        if (channelNameParam == null || workspaceIdParam == null) {
                            call.respond(HttpStatusCode.BadRequest, "Missing parameters")
                            return@delete
                        }

                        val updatedRows = dbQuery {
                            if (!isMember(actingUserId, workspaceIdParam)) {
                                return@dbQuery -1
                            }
                            ChannelsTable.update({
                                (ChannelsTable.channelName eq channelNameParam) and
                                        (ChannelsTable.workspaceId eq workspaceIdParam) and
                                        ((ChannelsTable.userId eq actingUserId) or ChannelsTable.userId.isNull())
                            }) {
                                it[isDeleted] = true
                                it[updatedAt] = System.currentTimeMillis()
                            }
                        }

                        when {
                            updatedRows == -1 -> call.respond(HttpStatusCode.Forbidden, false)
                            updatedRows > 0 -> call.respond(HttpStatusCode.OK, true)
                            else -> call.respond(HttpStatusCode.NotFound, false)
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, false)
                    }
                }

                get("/sync/{workspaceId}") {
                    try {
                        val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                        val actingUserId = call.authenticatedUserId()
                        val sinceTimestamp = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L

                        if (workspaceIdParam == null) {
                            call.respond(HttpStatusCode.BadRequest, "Missing workspaceId")
                            return@get
                        }

                        val updates = dbQuery {
                            if (!isMember(actingUserId, workspaceIdParam)) {
                                return@dbQuery null
                            }
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
                        if (updates == null) {
                            call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                        } else {
                            call.respond(HttpStatusCode.OK, updates)
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Sync Error")
                    }
                }

                get("/workspace/{workspaceId}") {
                    try {
                        val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                        val actingUserId = call.authenticatedUserId()
                        if (workspaceIdParam == null) {
                            call.respond(HttpStatusCode.BadRequest, "Missing workspaceId")
                            return@get
                        }

                        val channels = dbQuery {
                            if (!isMember(actingUserId, workspaceIdParam)) {
                                return@dbQuery null
                            }
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
                        if (channels == null) {
                            call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                        } else {
                            call.respond(HttpStatusCode.OK, channels)
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Error retrieving channels")
                    }
                }
            }

            route("/api/tasks") {

                post {
                    try {
                        val actingUserId = call.authenticatedUserId()
                        val request = call.receive<TaskRequest>()

                        val newTask = dbQuery {
                            if (!isMember(actingUserId, request.workspaceId)) {
                                return@dbQuery null
                            }
                            val insertedId = TasksTable.insert {
                                it[createdByUserId] = actingUserId
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
                                createdByUserId = actingUserId,
                                assignedToUserId = request.assignedToUserId,
                                workspaceId = request.workspaceId,
                                taskName = request.taskName,
                                taskDescription = request.taskDescription,
                                status = request.status
                            )
                        }
                        if (newTask == null) {
                            call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                        } else {
                            call.respond(HttpStatusCode.Created, newTask)
                            // ── Notification: task assigned ───────────────────────────────────
                            val assigneeId = request.assignedToUserId
                            if (assigneeId != null && assigneeId != actingUserId) {
                                val creatorName = dbQuery {
                                    UsersTable.selectAll().where { UsersTable.id eq actingUserId }
                                        .singleOrNull()?.get(UsersTable.username) ?: "Someone"
                                }
                                createAndPushNotification(
                                    recipientId = assigneeId,
                                    actorId = actingUserId,
                                    type = "TASK_ASSIGNED",
                                    title = "$creatorName assigned you a task",
                                    body = "\"${request.taskName}\" — ${request.taskDescription.take(120)}",
                                    workspaceId = request.workspaceId,
                                    referenceId = newTask.id
                                )
                            }
                        }
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
                        val actingUserId = call.authenticatedUserId()

                        val userTasks = dbQuery {
                            val memberWorkspaceIds = WorkspaceMembersTable.selectAll()
                                .where { WorkspaceMembersTable.userId eq actingUserId }
                                .map { it[WorkspaceMembersTable.workspaceId] }

                            if (memberWorkspaceIds.isEmpty()) {
                                emptyList()
                            } else {
                                TasksTable.selectAll()
                                    .where {
                                        (TasksTable.assignedToUserId eq userIdParam) and
                                                (TasksTable.isDeleted eq false) and
                                                (TasksTable.workspaceId inList memberWorkspaceIds)
                                    }
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
                        }
                        call.respond(HttpStatusCode.OK, userTasks)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            "Failed to retrieve user tasks"
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
                        val actingUserId = call.authenticatedUserId()

                        val updatedRows = dbQuery {
                            val task = TasksTable.selectAll().where { TasksTable.id eq taskIdParam }.singleOrNull()
                                ?: return@dbQuery -1
                            if (!isMember(actingUserId, task[TasksTable.workspaceId])) {
                                return@dbQuery -2
                            }
                            TasksTable.update({ TasksTable.id eq taskIdParam }) {
                                it[isDeleted] = true
                                it[updatedAt] = System.currentTimeMillis()
                            }
                        }

                        when {
                            updatedRows == -2 -> call.respond(HttpStatusCode.Forbidden, false)
                            updatedRows > 0 -> call.respond(HttpStatusCode.OK, true)
                            else -> call.respond(HttpStatusCode.NotFound, false)
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
                    val actingUserId = call.authenticatedUserId()

                    val request = try {
                        call.receive<TaskRequest>()
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid request body")
                        return@put
                    }

                    val updateResult = dbQuery {
                        val existingTask = TasksTable.selectAll().where { TasksTable.id eq taskId }.singleOrNull()
                            ?: return@dbQuery -1
                        if (!isMember(actingUserId, existingTask[TasksTable.workspaceId]) ||
                            !isMember(actingUserId, request.workspaceId)
                        ) {
                            return@dbQuery -2
                        }

                        // Mirror the client's business rules server-side: without this, any workspace
                        // member could bypass the UI's assignee/creator restrictions by calling the API directly.
                        val reassigning = request.assignedToUserId != existingTask[TasksTable.assignedToUserId]
                        val editingContentOrStatus = request.taskName != existingTask[TasksTable.taskName] ||
                                request.taskDescription != existingTask[TasksTable.taskDescription] ||
                                request.status != existingTask[TasksTable.status]

                        if (reassigning && actingUserId != existingTask[TasksTable.createdByUserId]) {
                            return@dbQuery -3
                        }
                        if (editingContentOrStatus && actingUserId != existingTask[TasksTable.assignedToUserId]) {
                            return@dbQuery -3
                        }

                        TasksTable.update({ TasksTable.id eq taskId }) {
                            it[taskName] = request.taskName
                            it[taskDescription] = request.taskDescription
                            it[assignedToUserId] = request.assignedToUserId
                            it[workspaceId] = request.workspaceId
                            it[status] = request.status
                            it[updatedAt] = System.currentTimeMillis()
                        }
                    }

                    if (updateResult == -1) {
                        call.respond(HttpStatusCode.NotFound, "Task not found")
                        return@put
                    }
                    if (updateResult == -2) {
                        call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                        return@put
                    }
                    if (updateResult == -3) {
                        call.respond(HttpStatusCode.Forbidden, "Only the assignee can edit or move this task; only the creator can reassign it")
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
                        // ── Notification: task updated ────────────────────────────────────
                        val assigneeId = updatedTask.assignedToUserId
                        if (assigneeId != null && assigneeId != actingUserId) {
                            val updaterName = dbQuery {
                                UsersTable.selectAll().where { UsersTable.id eq actingUserId }
                                    .singleOrNull()?.get(UsersTable.username) ?: "Someone"
                            }
                            createAndPushNotification(
                                recipientId = assigneeId,
                                actorId = actingUserId,
                                type = "TASK_UPDATED",
                                title = "$updaterName updated your task",
                                body = "\"${updatedTask.taskName}\" is now ${updatedTask.status}",
                                workspaceId = updatedTask.workspaceId,
                                referenceId = updatedTask.id
                            )
                        }
                    }
                }

                get("/workspace/{workspaceId}") {
                    val workspaceId = call.parameters["workspaceId"]?.toIntOrNull()
                    if (workspaceId == null) {
                        call.respond(HttpStatusCode.BadRequest, "Missing workspaceId")
                        return@get
                    }
                    val actingUserId = call.authenticatedUserId()

                    try {
                        val workspaceTasks = dbQuery {
                            if (!isMember(actingUserId, workspaceId)) {
                                return@dbQuery null
                            }
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
                        if (workspaceTasks == null) {
                            call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                        } else {
                            call.respond(HttpStatusCode.OK, workspaceTasks)
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Error retrieving tasks")
                    }
                }

                get("/sync/{workspaceId}") {
                    try {
                        val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                        val actingUserId = call.authenticatedUserId()
                        val sinceTimestamp = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L

                        if (workspaceIdParam == null) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                "Missing structural context arguments."
                            )
                            return@get
                        }

                        val deltaUpdates = dbQuery {
                            if (!isMember(actingUserId, workspaceIdParam)) {
                                return@dbQuery null
                            }
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
                        if (deltaUpdates == null) {
                            call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                        } else {
                            call.respond(HttpStatusCode.OK, deltaUpdates)
                        }
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
                        val actingUserId = call.authenticatedUserId()
                        val request = call.receive<NotesRequest>()
                        val newNotes = dbQuery {
                            if (!isMember(actingUserId, request.workspaceId)) {
                                return@dbQuery null
                            }
                            val insertedId = NotesTable.insert {
                                it[NotesTable.userIdNotes] = actingUserId
                                it[NotesTable.workspaceId] = request.workspaceId
                                it[NotesTable.notesName] = request.notesName
                                it[NotesTable.notesDescription] = request.description
                                it[NotesTable.isDeleted] = false
                                it[NotesTable.updatedAt] = System.currentTimeMillis()
                            }[NotesTable.id]

                            NotesResponse(
                                id = insertedId,
                                userId = actingUserId,
                                notesName = request.notesName,
                                workspaceId = request.workspaceId,
                                description = request.description
                            )
                        }
                        if (newNotes == null) {
                            call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                        } else {
                            call.respond(HttpStatusCode.Created, newNotes)
                        }
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
                        val actingUserId = call.authenticatedUserId()
                        val request = call.receive<NotesRequest>()

                        val updateResult = dbQuery {
                            val existingNote = NotesTable.selectAll().where { NotesTable.id eq noteIdParam }.singleOrNull()
                                ?: return@dbQuery -1
                            if (!isMember(actingUserId, existingNote[NotesTable.workspaceId]) ||
                                !isMember(actingUserId, request.workspaceId)
                            ) {
                                return@dbQuery -2
                            }
                            NotesTable.update({ NotesTable.id eq noteIdParam }) {
                                it[NotesTable.notesName] = request.notesName
                                it[NotesTable.workspaceId] = request.workspaceId
                                it[NotesTable.notesDescription] = request.description
                                it[NotesTable.updatedAt] = System.currentTimeMillis()
                            }
                        }

                        when {
                            updateResult == -1 -> call.respond(HttpStatusCode.NotFound, "Note not found to update")
                            updateResult == -2 -> call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                            updateResult > 0 -> call.respond(
                                HttpStatusCode.OK,
                                NotesResponse(
                                    id = noteIdParam,
                                    userId = actingUserId,
                                    notesName = request.notesName,
                                    workspaceId = request.workspaceId,
                                    description = request.description
                                )
                            )
                            else -> call.respond(HttpStatusCode.NotFound, "Note not found to update")
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
                        val actingUserId = call.authenticatedUserId()

                        val updatedRows = dbQuery {
                            val existingNote = NotesTable.selectAll().where { NotesTable.id eq noteIdParam }.singleOrNull()
                                ?: return@dbQuery -1
                            if (!isMember(actingUserId, existingNote[NotesTable.workspaceId])) {
                                return@dbQuery -2
                            }
                            NotesTable.update({ NotesTable.id eq noteIdParam }) {
                                it[NotesTable.isDeleted] = true
                                it[NotesTable.updatedAt] = System.currentTimeMillis()
                            }
                        }

                        when {
                            updatedRows == -2 -> call.respond(HttpStatusCode.Forbidden, false)
                            updatedRows > 0 -> call.respond(HttpStatusCode.OK, true)
                            else -> call.respond(HttpStatusCode.NotFound, false)
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
                    val actingUserId = call.authenticatedUserId()

                    try {
                        val workspaceNotes = dbQuery {
                            if (!isMember(actingUserId, workspaceId)) {
                                return@dbQuery null
                            }
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
                        if (workspaceNotes == null) {
                            call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                        } else {
                            call.respond(HttpStatusCode.OK, workspaceNotes)
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Error retrieving notes")
                    }
                }

                get("/sync/{workspaceId}") {
                    try {
                        val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                        val actingUserId = call.authenticatedUserId()
                        val sinceTimestamp = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L

                        if (workspaceIdParam == null) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                "Missing structural context arguments."
                            )
                            return@get
                        }

                        val deltaUpdates = dbQuery {
                            if (!isMember(actingUserId, workspaceIdParam)) {
                                return@dbQuery null
                            }
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
                        if (deltaUpdates == null) {
                            call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                        } else {
                            call.respond(HttpStatusCode.OK, deltaUpdates)
                        }
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
                        val actingUserId = call.authenticatedUserId()
                        val request = call.receive<MessageRequest>()
                        val newMessage = dbQuery {
                            if (!isMember(actingUserId, request.workspaceId)) {
                                return@dbQuery null
                            }
                            val insertedId = MessageTable.insert {
                                it[MessageTable.userId] = actingUserId
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
                                userId = actingUserId,
                                workspaceId = request.workspaceId,
                                channelId = request.channelId,
                                userName = request.userName,
                                content = request.content,
                                status = request.status
                            )
                        }
                        if (newMessage == null) {
                            call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                        } else {
                            call.respond(HttpStatusCode.Created, newMessage)
                            // ── Notification hooks ────────────────────────────────────────────
                            val senderRow = dbQuery {
                                UsersTable.selectAll().where { UsersTable.id eq actingUserId }.singleOrNull()
                            }
                            val senderName = senderRow?.get(UsersTable.username) ?: "Someone"

                            // Parse @mentions from content
                            val mentionedUsernames = MENTION_REGEX.findAll(request.content)
                                .map { it.groupValues[1].lowercase() }.toSet()

                            // Find all channel members (excluding sender)
                            val channelMembers = dbQuery {
                                (WorkspaceMembersTable innerJoin UsersTable)
                                    .selectAll()
                                    .where { WorkspaceMembersTable.workspaceId eq request.workspaceId }
                                    .filter { it[UsersTable.id] != actingUserId }
                                    .map { it[UsersTable.id] to it[UsersTable.username].lowercase() }
                            }

                            channelMembers.forEach { (memberId, memberUsername) ->
                                val isMentioned = memberUsername in mentionedUsernames
                                if (isMentioned) {
                                    createAndPushNotification(
                                        recipientId = memberId,
                                        actorId = actingUserId,
                                        type = "MENTION",
                                        title = "$senderName mentioned you",
                                        body = request.content.take(200),
                                        workspaceId = request.workspaceId,
                                        referenceId = newMessage.id
                                    )
                                } else {
                                    createAndPushNotification(
                                        recipientId = memberId,
                                        actorId = actingUserId,
                                        type = "CHANNEL_MESSAGE",
                                        title = "New message from $senderName",
                                        body = request.content.take(200),
                                        workspaceId = request.workspaceId,
                                        referenceId = newMessage.id
                                    )
                                }
                            }
                        }
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
                    val actingUserId = call.authenticatedUserId()

                    if (workspaceId == null || channelId == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            "Missing or invalid workspaceId or channelId"
                        )
                        return@get
                    }

                    val channelmessage = dbQuery {
                        if (!isMember(actingUserId, workspaceId)) {
                            return@dbQuery null
                        }
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
                    if (channelmessage == null) {
                        call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                    } else {
                        call.respond(HttpStatusCode.OK, channelmessage)
                    }
                }

                put("/{messageId}") {
                    try {
                        val messageIdParam = call.parameters["messageId"]?.toIntOrNull()
                        if (messageIdParam == null) {
                            call.respond(HttpStatusCode.BadRequest, "Missing or invalid messageId")
                            return@put
                        }
                        val actingUserId = call.authenticatedUserId()
                        val request = call.receive<MessageRequest>()

                        val updateResult = dbQuery {
                            val existing = MessageTable.selectAll().where { MessageTable.id eq messageIdParam }.singleOrNull()
                                ?: return@dbQuery -1
                            if (existing[MessageTable.userId] != actingUserId) {
                                return@dbQuery -2
                            }
                            MessageTable.update({ MessageTable.id eq messageIdParam }) {
                                it[MessageTable.content] = request.content
                                it[MessageTable.status] = request.status
                                it[MessageTable.updatedAt] = System.currentTimeMillis()
                            }
                        }

                        when {
                            updateResult == -1 -> call.respond(HttpStatusCode.NotFound, "Message not found to update")
                            updateResult == -2 -> call.respond(HttpStatusCode.Forbidden, "Only the sender can edit this message")
                            updateResult > 0 -> call.respond(
                                HttpStatusCode.OK,
                                MessageResponse(
                                    id = messageIdParam,
                                    userId = actingUserId,
                                    workspaceId = request.workspaceId,
                                    channelId = request.channelId,
                                    userName = request.userName,
                                    content = request.content,
                                    status = request.status
                                )
                            )
                            else -> call.respond(HttpStatusCode.NotFound, "Message not found to update")
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Error updating message")
                    }
                }

                delete("/{messageId}/{userId}/{workspaceId}/{channelId}") {
                    try {
                        val messageIdParam = call.parameters["messageId"]?.toIntOrNull()
                        val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                        val channelIdParam = call.parameters["channelId"]?.toIntOrNull()
                        val actingUserId = call.authenticatedUserId()

                        if (messageIdParam == null || workspaceIdParam == null || channelIdParam == null) {
                            call.respond(HttpStatusCode.BadRequest, false)
                            return@delete
                        }

                        val updatedRows = dbQuery {
                            MessageTable.update({
                                (MessageTable.id eq messageIdParam) and
                                        (MessageTable.userId eq actingUserId) and
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
                        val actingUserId = call.authenticatedUserId()
                        val sinceTimestamp = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L

                        if (workspaceIdParam == null || channelIdParam == null) {
                            call.respond(HttpStatusCode.BadRequest, "Missing structural parameters")
                            return@get
                        }

                        val updates = dbQuery {
                            if (!isMember(actingUserId, workspaceIdParam)) {
                                return@dbQuery null
                            }
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
                        if (updates == null) {
                            call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                        } else {
                            call.respond(HttpStatusCode.OK, updates)
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Sync Error")
                    }
                }
            }

            route("/api/file") {
                post {
                    try {
                        val actingUserId = call.authenticatedUserId()
                        val multipart = call.receiveMultipart()
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
                                        "workspaceId" -> workspaceId = part.value.toIntOrNull()
                                        "userName" -> userName = part.value
                                        "localpath" -> localpath = part.value
                                    }
                                    part.dispose()
                                }
                                is PartData.FileItem -> {
                                    // File(...).name strips any directory components (e.g. "../../etc/passwd" -> "passwd"),
                                    // so a malicious client-supplied filename can't escape uploadDir below.
                                    fileName = part.originalFileName?.let { File(it).name }?.ifBlank { null }
                                    contentType = part.contentType?.toString()
                                    // Bounded read regardless of what Content-Length claims (chunked transfer has none) —
                                    // caps memory use instead of buffering an arbitrarily large upload wholesale.
                                    fileBytes = part.streamProvider().use { input ->
                                        val buffer = java.io.ByteArrayOutputStream()
                                        val chunk = ByteArray(8192)
                                        var total = 0L
                                        while (true) {
                                            val read = input.read(chunk)
                                            if (read == -1) break
                                            total += read
                                            if (total > MAX_UPLOAD_BYTES) throw UploadTooLargeException()
                                            buffer.write(chunk, 0, read)
                                        }
                                        buffer.toByteArray()
                                    }
                                    part.dispose()
                                }
                                else -> part.dispose()
                            }
                        }

                        if (workspaceId == null || userName == null || fileBytes == null || fileName == null) {
                            val missingFields = mutableListOf<String>()
                            if (workspaceId == null) missingFields.add("workspaceId")
                            if (userName == null) missingFields.add("userName")
                            if (fileBytes == null) missingFields.add("fileBytes")
                            if (fileName == null) missingFields.add("fileName")

                            call.respond(HttpStatusCode.BadRequest, "Missing multipart assets: ${missingFields.joinToString(", ")}")
                            return@post
                        }

                        if (!dbQuery { isMember(actingUserId, workspaceId!!) }) {
                            call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                            return@post
                        }

                        val uploadDir = File(System.getenv("UPLOAD_DIR") ?: "local_files_upload")
                        if (!uploadDir.exists()) {
                            uploadDir.mkdirs()
                        }

                        val uniqueFileName = "${UUID.randomUUID()}_$fileName"
                        val physicalFile = File(uploadDir, uniqueFileName)
                        physicalFile.writeBytes(fileBytes!!)

                        val generatedFileLocation = physicalFile.absolutePath
                        val scheme = call.request.headers["X-Forwarded-Proto"] ?: "http"
                        val host = call.request.headers["Host"] ?: "127.0.0.1:8080"
                        val generatedUrl = "$scheme://$host/api/file/download/$uniqueFileName"
                        val fileSize = physicalFile.length()
                        val finalMimeType = contentType ?: "application/octet-stream"
                        val currentTimeMil = System.currentTimeMillis()

                        val insertedId = dbQuery {
                            LocalFilesTable.insert {
                                it[LocalFilesTable.userId] = actingUserId
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
                            userId = actingUserId,
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
                    } catch (e: UploadTooLargeException) {
                        call.respond(HttpStatusCode.PayloadTooLarge, e.message ?: "File too large")
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(HttpStatusCode.InternalServerError, "File upload failed")
                    }
                }

                get("/workspace/{workspaceId}") {
                    try {
                        val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                        val actingUserId = call.authenticatedUserId()
                        if (workspaceIdParam == null) {
                            call.respond(HttpStatusCode.BadRequest, "Invalid workspaceId")
                            return@get
                        }

                        val filesList = dbQuery {
                            if (!isMember(actingUserId, workspaceIdParam)) {
                                return@dbQuery null
                            }
                            LocalFilesTable.selectAll().where {
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
                        if (filesList == null) {
                            call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                        } else {
                            call.respond(HttpStatusCode.OK, filesList)
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Error retrieving files list")
                    }
                }

                get("/updates") {
                    try {
                        val workspaceIdParam = call.request.queryParameters["workspaceId"]?.toIntOrNull()
                        val lastSyncTimeParam = call.request.queryParameters["lastSyncTime"]?.toLongOrNull()
                        val actingUserId = call.authenticatedUserId()

                        if (workspaceIdParam == null || lastSyncTimeParam == null) {
                            call.respond(HttpStatusCode.BadRequest, "Missing or invalid workspaceId or lastSyncTime tracking values.")
                            return@get
                        }

                        val deltaUpdatesList = dbQuery {
                            if (!isMember(actingUserId, workspaceIdParam)) {
                                return@dbQuery null
                            }
                            LocalFilesTable.selectAll().where {
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
                        if (deltaUpdatesList == null) {
                            call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                        } else {
                            call.respond(HttpStatusCode.OK, deltaUpdatesList)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(HttpStatusCode.InternalServerError, "Error fetching delta updates loop context.")
                    }
                }

                get("/download/{fileName}") {
                    try {
                        val rawFileNameParam = call.parameters["fileName"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing filename")
                        // Strip any directory components a crafted path segment might smuggle in (e.g. "..%2F..%2Fetc%2Fpasswd").
                        val fileNameParam = File(rawFileNameParam).name
                        if (fileNameParam.isBlank()) {
                            return@get call.respond(HttpStatusCode.BadRequest, "Invalid filename")
                        }
                        val actingUserId = call.authenticatedUserId()

                        val fileRow = dbQuery {
                            LocalFilesTable.selectAll()
                                .where {
                                    (LocalFilesTable.url like "%/$fileNameParam") and (LocalFilesTable.isDeleted eq false)
                                }
                                .singleOrNull()
                        }

                        if (fileRow == null) {
                            call.respond(HttpStatusCode.NotFound, "File not found")
                            return@get
                        }

                        val allowed = dbQuery { isMember(actingUserId, fileRow[LocalFilesTable.workspaceId]) }
                        if (!allowed) {
                            call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                            return@get
                        }

                        val uploadDir = File(System.getenv("UPLOAD_DIR") ?: "local_files_upload").canonicalFile
                        val file = File(uploadDir, fileNameParam).canonicalFile
                        if (!file.path.startsWith(uploadDir.path + File.separator)) {
                            return@get call.respond(HttpStatusCode.BadRequest, "Invalid filename")
                        }

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
                        val actingUserId = call.authenticatedUserId()

                        val (updatedRows, fileLocation) = dbQuery {
                            val fileRow = LocalFilesTable.selectAll().where { LocalFilesTable.id eq fileIdParam }.singleOrNull()
                                ?: return@dbQuery -1 to null
                            if (!isMember(actingUserId, fileRow[LocalFilesTable.workspaceId])) {
                                return@dbQuery -2 to null
                            }
                            val updated = LocalFilesTable.update({ LocalFilesTable.id eq fileIdParam }) {
                                it[LocalFilesTable.isDeleted] = true
                                it[LocalFilesTable.updatedAt] = System.currentTimeMillis()
                            }
                            updated to fileRow[LocalFilesTable.fileLocation]
                        }

                        when {
                            updatedRows == -2 -> call.respond(HttpStatusCode.Forbidden, false)
                            updatedRows > 0 -> {
                                // Best-effort physical cleanup — the row is already soft-deleted either way.
                                fileLocation?.let { runCatching { File(it).delete() } }
                                call.respond(HttpStatusCode.OK, true)
                            }
                            else -> call.respond(HttpStatusCode.NotFound, false)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(HttpStatusCode.InternalServerError, false)
                    }
                }
            }

            route("/ws") {
                webSocket("/dm") {
                    val userIdParam = call.authenticatedUserId().toLong()

                    addDmSession(userIdParam, this)

                    // Mark user online on WS connect
                    dbQuery {
                        UsersTable.update({ UsersTable.id eq userIdParam.toInt() }) {
                            it[UsersTable.lastSeen] = System.currentTimeMillis()
                        }
                    }

                    try {
                        val initialHistoryPayloads = dbQuery {
                            DirectMessagesTable.selectAll().where {
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
                                        // Only the authenticated connection owner may send as themselves.
                                        val outgoingDto = dmDto.copy(senderId = userIdParam.toInt())
                                        val savedMessageDto = dbQuery {
                                            val insertedStatement = DirectMessagesTable.insert {
                                                it[workspaceId] = outgoingDto.workspaceId
                                                it[senderId] = outgoingDto.senderId
                                                it[receiverId] = outgoingDto.receiverId
                                                it[content] = outgoingDto.content
                                                it[timestamp] = outgoingDto.timestamp
                                            }

                                            val rawId = insertedStatement[DirectMessagesTable.id]
                                            val generatedId = when (rawId) {
                                                is org.jetbrains.exposed.dao.id.EntityID<*> -> (rawId.value as Number).toInt()
                                                is Number -> rawId.toInt()
                                                else -> rawId.toString().toInt()
                                            }
                                            outgoingDto.copy(id = generatedId)
                                        }

                                        val receiverPayload =
                                            savedMessageDto.copy(action = "RECEIVE_MESSAGE")
                                        val receiverJson =
                                            Json.encodeToString(DmDto.serializer(), receiverPayload)
                                        sendToUser(savedMessageDto.receiverId.toLong(), receiverJson)

                                        if (this.isActive) {
                                            val senderAcknowledgementPayload =
                                                savedMessageDto.copy(action = "MESSAGE_DELIVERED")
                                            val senderJson = Json.encodeToString(
                                                DmDto.serializer(),
                                                senderAcknowledgementPayload
                                            )
                                            this.send(Frame.Text(senderJson))
                                        }

                                        // ── Notification: DM received ─────────────────────────────────
                                        val senderUsername = dbQuery {
                                            UsersTable.selectAll()
                                                .where { UsersTable.id eq savedMessageDto.senderId }
                                                .singleOrNull()?.get(UsersTable.username) ?: "Someone"
                                        }
                                        createAndPushNotification(
                                            recipientId = savedMessageDto.receiverId,
                                            actorId = savedMessageDto.senderId,
                                            type = "DM",
                                            title = "$senderUsername sent you a message",
                                            body = savedMessageDto.content.take(200),
                                            workspaceId = savedMessageDto.workspaceId,
                                            referenceId = savedMessageDto.id
                                        )
                                    } else if (dmDto.action == "UPDATE_MESSAGE") {
                                        val messageId = dmDto.id
                                        var targetReceiverId = dmDto.receiverId
                                        var authorized = false
                                        if (messageId != null && messageId != 0) {
                                            dbQuery {
                                                val existing = DirectMessagesTable.selectAll().where { DirectMessagesTable.id eq messageId }.singleOrNull()
                                                if (existing != null && existing[DirectMessagesTable.senderId] == userIdParam.toInt()) {
                                                    authorized = true
                                                    val sId = existing[DirectMessagesTable.senderId]
                                                    val rId = existing[DirectMessagesTable.receiverId]
                                                    targetReceiverId = if (targetReceiverId != 0) targetReceiverId else if (sId == userIdParam.toInt()) rId else sId
                                                    DirectMessagesTable.update({ DirectMessagesTable.id eq messageId }) {
                                                        it[content] = dmDto.content
                                                    }
                                                }
                                            }
                                        }
                                        if (authorized) {
                                            val updatedPayload = dmDto.copy(action = "UPDATE_MESSAGE", receiverId = targetReceiverId)
                                            val updatedJson = Json.encodeToString(DmDto.serializer(), updatedPayload)
                                            sendToUser(targetReceiverId.toLong(), updatedJson)
                                            if (this.isActive) {
                                                this.send(Frame.Text(updatedJson))
                                            }
                                        }
                                    } else if (dmDto.action == "DELETE_MESSAGE") {
                                        val messageId = dmDto.id
                                        var targetReceiverId = dmDto.receiverId
                                        var authorized = false
                                        if (messageId != null && messageId != 0) {
                                            dbQuery {
                                                val existing = DirectMessagesTable.selectAll().where { DirectMessagesTable.id eq messageId }.singleOrNull()
                                                if (existing != null && existing[DirectMessagesTable.senderId] == userIdParam.toInt()) {
                                                    authorized = true
                                                    val sId = existing[DirectMessagesTable.senderId]
                                                    val rId = existing[DirectMessagesTable.receiverId]
                                                    targetReceiverId = if (targetReceiverId != 0) targetReceiverId else if (sId == userIdParam.toInt()) rId else sId
                                                    DirectMessagesTable.deleteWhere { DirectMessagesTable.id eq messageId }
                                                }
                                            }
                                        }
                                        if (authorized) {
                                            val deletePayload = dmDto.copy(action = "DELETE_MESSAGE", receiverId = targetReceiverId)
                                            val deleteJson = Json.encodeToString(DmDto.serializer(), deletePayload)
                                            sendToUser(targetReceiverId.toLong(), deleteJson)
                                            if (this.isActive) {
                                                this.send(Frame.Text(deleteJson))
                                            }
                                        }
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        removeDmSession(userIdParam, this)
                        // Mark last seen on WS disconnect
                        dbQuery {
                            UsersTable.update({ UsersTable.id eq userIdParam.toInt() }) {
                                it[UsersTable.lastSeen] = System.currentTimeMillis()
                            }
                        }
                    }
                }
            }
        }
    }
}
