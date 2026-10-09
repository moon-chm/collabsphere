package plugins

import com.collabsphere.dto.*
import dto.*
import com.collabsphere.model.*
import com.collabsphere.util.AvatarGenerator
import com.collabsphere.util.EmailService
import com.collabsphere.util.JwtConfig
import com.collabsphere.util.PasswordHasher
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import com.collabsphere.DatabaseFactory
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
private val logger = LoggerFactory.getLogger("Routing")

/** Postgres SQLSTATE codes for transient conflicts worth retrying instead of surfacing as a 500. */
private val RETRYABLE_SQLSTATES = setOf("40001", "40P01") // serialization_failure, deadlock_detected

/** Serializes a user-pair block/unblock with DM writes involving the same pair. */
internal fun Transaction.lockUserPair(firstUserId: Int, secondUserId: Int) {
    val low = minOf(firstUserId, secondUserId)
    val high = maxOf(firstUserId, secondUserId)
    val lockKey = (low.toLong() shl 32) or (high.toLong() and 0xffff_ffffL)
    exec("SELECT pg_advisory_xact_lock($lockKey)") { result ->
        result.next()
        Unit
    }
}

/** Serializes verification-code confirmation, replacement, and email-target changes per account. */
private fun Transaction.lockVerificationCode(userId: Int) {
    val lockKey = Long.MIN_VALUE + userId.toLong()
    exec("SELECT pg_advisory_xact_lock($lockKey)") { result ->
        result.next()
        Unit
    }
}

suspend fun <T> dbQuery(block: suspend () -> T): T {
    var attempt = 0
    while (true) {
        try {
            return newSuspendedTransaction(Dispatchers.IO, db = DatabaseFactory.writeDatabase) { block() }
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            val sqlState = (e as? java.sql.SQLException)?.sqlState
                ?: (e.cause as? java.sql.SQLException)?.sqlState
            attempt++
            if (sqlState !in RETRYABLE_SQLSTATES || attempt >= 3) throw e
            kotlinx.coroutines.delay(100L * attempt)
        }
    }
}

suspend fun <T> dbReadQuery(block: suspend () -> T): T {
    var attempt = 0
    while (true) {
        try {
            return newSuspendedTransaction(Dispatchers.IO, db = DatabaseFactory.readDatabase) { block() }
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            val sqlState = (e as? java.sql.SQLException)?.sqlState
                ?: (e.cause as? java.sql.SQLException)?.sqlState
            attempt++
            if (sqlState !in RETRYABLE_SQLSTATES || attempt >= 3) throw e
            kotlinx.coroutines.delay(100L * attempt)
        }
    }
}

/** Escapes LIKE wildcards in a literal so it can be safely embedded in a pattern (Postgres's default LIKE escape char is `\`). */
internal fun escapeLikeLiteral(value: String): String =
    value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

// â”€â”€ WebSocket session management â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Delegated to WebSocketBroker which:
//   â€¢ On single instance (current): identical behaviour to the previous ConcurrentHashMap approach
//   â€¢ On multi-instance (future):   routes cross-instance messages via Redis pub/sub
//
// The `activeDmSessions` reference is kept as a read-only shim so any external code
// that only reads the map (e.g. presence checks) continues to compile.
val activeDmSessions get() = emptyMap<Long, Set<WebSocketServerSession>>() // presence via WebSocketBroker.isUserConnected

private fun addDmSession(userId: Long, session: WebSocketServerSession) =
    WebSocketBroker.addSession(userId, session, supportsChannelEvents = false)

private fun removeDmSession(userId: Long, session: WebSocketServerSession) =
    WebSocketBroker.removeSession(userId, session)

internal suspend fun sendToUser(userId: Long, text: String) =
    WebSocketBroker.sendToUser(userId, text, requireChannelCapable = false)

internal suspend fun sendToChannelCapableUser(userId: Long, text: String) =
    WebSocketBroker.sendToUser(userId, text, requireChannelCapable = true)

/**
 * Returns workspace member user IDs.
 * L1: JVM cache (60 s TTL) â€” zero DB hit on warm cache.
 * L2: Redis cache (120 s TTL) â€” shared across instances when Redis configured.
 * L3: PostgreSQL â€” source of truth on full cache miss.
 * Must be called from inside an Exposed transaction / dbQuery block.
 */
internal fun workspaceMemberIds(workspaceId: Int): List<Int> =
    WorkspaceMemberCache.getMembers(workspaceId)

internal fun channelReactionSummary(messageId: Int, channelId: Int, workspaceId: Int): ChannelReactionSummary {
    val reactors = ChannelReactionsTable.selectAll()
        .where { ChannelReactionsTable.messageId eq messageId }
        .groupBy({ it[ChannelReactionsTable.emoji] }, { it[ChannelReactionsTable.userId] })
    return ChannelReactionSummary(
        messageId = messageId,
        channelId = channelId,
        workspaceId = workspaceId,
        reactors = reactors
    )
}

internal suspend fun broadcastChannelMessageChange(messageId: Int) {
    try {
        val (snapshot, memberIds) = dbQuery {
            val row = MessageTable.selectAll().where { MessageTable.id eq messageId }.singleOrNull()
                ?: return@dbQuery null
            row.toMessageSyncResponse() to workspaceMemberIds(row[MessageTable.workspaceId])
        } ?: return
        val json = Json.encodeToString(ChannelMessageEvent(message = snapshot))
        memberIds.forEach { sendToChannelCapableUser(it.toLong(), json) }
    } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
        logger.error("[broadcastChannelMessageChange] Failed for messageId=$messageId", e)
    }
}

internal const val MAX_UPLOAD_BYTES = 25L * 1024 * 1024
internal const val DEFAULT_HISTORY_PAGE = 50
internal const val DM_CATCH_UP_PAGE = 500
internal const val MAX_HISTORY_PAGE = 100

internal fun ResultRow.toDmHistoryDto() = DmDto(
    action = "HISTORY",
    id = this[DirectMessagesTable.id],
    workspaceId = this[DirectMessagesTable.workspaceId],
    senderId = this[DirectMessagesTable.senderId],
    receiverId = this[DirectMessagesTable.receiverId],
    content = this[DirectMessagesTable.content],
    timestamp = this[DirectMessagesTable.timestamp],
    mediaUrl = this[DirectMessagesTable.mediaUrl],
    isRead = this[DirectMessagesTable.isRead],
    replyToId = this[DirectMessagesTable.replyToId]
)


/** The caller's identity, established by a verified JWT. Only valid inside an `authenticate("auth-jwt")` block. */
fun ApplicationCall.authenticatedUserId(): Int =
    principal<JWTPrincipal>()!!.payload.getClaim("userId").asInt()

/**
 * Must be called from inside an existing `dbQuery`/transaction block.
 * L1: Redis cache (30s TTL) â€” zero DB hit on warm cache.
 * L2: PostgreSQL â€” source of truth on cache miss.
 * Falls back to bare DB query when Redis is not configured (single-instance / no REDIS_URL).
 */
internal fun isMember(userId: Int, workspaceId: Int): Boolean = isMemberCached(userId, workspaceId)

internal fun workspaceOwnerId(workspaceId: Int): Int? =
    WorkspacesTable.select(WorkspacesTable.userId)
        .where { WorkspacesTable.id eq workspaceId }
        .singleOrNull()
        ?.get(WorkspacesTable.userId)

internal fun workspaceRole(userId: Int, workspaceId: Int): String? {
    val workspaceExists = WorkspacesTable.select(WorkspacesTable.id)
        .where { (WorkspacesTable.id eq workspaceId) and (WorkspacesTable.isDeleted eq false) }
        .singleOrNull() != null
    if (!workspaceExists) return null

    val memberRole = WorkspaceMembersTable.select(WorkspaceMembersTable.role)
        .where { (WorkspaceMembersTable.workspaceId eq workspaceId) and (WorkspaceMembersTable.userId eq userId) }
        .singleOrNull()
        ?.get(WorkspaceMembersTable.role)
        ?: return null
    return if (workspaceOwnerId(workspaceId) == userId) WorkspaceRoles.OWNER else memberRole
}

/** Regex to detect @username mentions in message content. */
internal val MENTION_REGEX = Regex("@(\\w{2,})") 

/**
 * Inserts a notification row and, if the recipient has an active WebSocket session,
 * pushes the notification frame in real-time. Safe to call from any coroutine context.
 */
internal suspend fun createAndPushNotification(
    recipientId: Int,
    actorId: Int?,
    type: String,
    title: String,
    body: String,
    workspaceId: Int? = null,
    referenceId: Int? = null,
    dedupeKey: String? = null,
    dispatchPush: Boolean = true
): NotificationResponse {
    val notification = dbQuery {
        val actorRow = actorId?.let {
            UsersTable.selectAll().where { UsersTable.id eq it }.singleOrNull()
        }
        val insertedId = if (dedupeKey == null) {
            NotificationsTable.insert {
                it[NotificationsTable.recipientId] = recipientId
                it[NotificationsTable.actorId] = actorId
                it[NotificationsTable.type] = type
                it[NotificationsTable.title] = title
                it[NotificationsTable.body] = body
                it[NotificationsTable.workspaceId] = workspaceId
                it[NotificationsTable.referenceId] = referenceId
            }[NotificationsTable.id]
        } else {
            NotificationsTable.insertIgnore {
                it[NotificationsTable.recipientId] = recipientId
                it[NotificationsTable.actorId] = actorId
                it[NotificationsTable.type] = type
                it[NotificationsTable.title] = title
                it[NotificationsTable.body] = body
                it[NotificationsTable.workspaceId] = workspaceId
                it[NotificationsTable.referenceId] = referenceId
                it[NotificationsTable.dedupeKey] = dedupeKey
            }
            NotificationsTable.selectAll().where { NotificationsTable.dedupeKey eq dedupeKey }
                .single()[NotificationsTable.id]
        }
        val notificationRow = NotificationsTable.selectAll()
            .where { NotificationsTable.id eq insertedId }
            .single()
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
            isRead = notificationRow[NotificationsTable.isRead],
            createdAt = notificationRow[NotificationsTable.createdAt]
        )
    }
    val frame = NotificationPushFrame(notification = notification)
    val json = Json.encodeToString(frame)
    sendToUser(recipientId.toLong(), json)

    // Durable outbox callers dispatch FCM synchronously so transient failures can be retried.
    if (dispatchPush) {
        if (type == "DM") {
            com.collabsphere.util.FcmService.sendDmPush(
                recipientUserId = recipientId,
                senderId = actorId ?: 0,
                senderUsername = notification.actorUsername ?: "Someone",
                workspaceId = workspaceId ?: 0,
                messageId = referenceId ?: 0,
                content = body,
                timestamp = notification.createdAt
            )
        } else {
            com.collabsphere.util.FcmService.sendGenericPush(
                recipientUserId = recipientId,
                notificationId = notification.id,
                type = notification.type,
                title = notification.title,
                body = notification.body,
                workspaceId = notification.workspaceId,
                actorUsername = notification.actorUsername,
                actorAvatarUrl = notification.actorAvatarUrl
            )
        }
    }
    return notification
}

fun Application.configureRouting() {
    val avatarStorage = attributes.getOrNull(AvatarStorageAttribute) ?: CloudinaryAvatarStorage
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

        // â”€â”€ Public avatar endpoint (no auth required) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

        // â”€â”€ Legacy local-file avatar route â€” no longer used (Cloudinary stores avatars now) â”€â”€
        get("/avatars/{filename}") {
            call.respond(HttpStatusCode.Gone, "Local file serving removed â€” avatars are now served directly from Cloudinary")
        }

        rateLimit(RateLimitName("auth")) {
            post("/api/login") {
            try {
                val request = call.receive<LoginRequest>()
                val trimmedEmail = AuthRules.normalizeEmail(request.email)

                val userRow = dbQuery {
                    UsersTable.selectAll()
                        .where { UsersTable.email.lowerCase() eq trimmedEmail }
                        .singleOrNull()
                }

                if (userRow == null || !PasswordHasher.matches(request.password, userRow[UsersTable.password])) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
                    return@post
                }

                // Strict Login Gate: Unverified users cannot log in
                if (!userRow[UsersTable.isEmailVerified]) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf(
                            "error" to "EMAIL_NOT_VERIFIED",
                            "message" to "Please verify your email before logging in.",
                            "email" to userRow[UsersTable.email]
                        )
                    )
                    return@post
                }

                if (!PasswordHasher.isHashed(userRow[UsersTable.password])) {
                    dbQuery {
                        UsersTable.update({ UsersTable.id eq userRow[UsersTable.id] }) { stmt ->
                            stmt[password] = PasswordHasher.hash(request.password)
                        }
                    }
                }

                val uid = userRow[UsersTable.id]
                val response = LoginResponse(
                    id = uid,
                    userName = userRow[UsersTable.username],
                    email = userRow[UsersTable.email],
                    token = JwtConfig.generateToken(uid, userRow[UsersTable.tokenVersion]),
                    avatarUrl = AvatarGenerator.avatarUrlFor(uid, userRow[UsersTable.avatarUrl]),
                    isEmailVerified = true
                )
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.BadRequest, "Malformed request body or server error")
            }
        }

        post("/api/register") {
            try {
                val request = call.receive<RegisterRequest>()
                val trimmedEmail = AuthRules.normalizeEmail(request.email)
                val trimmedUsername = request.userName.trim()

                if (trimmedEmail.isBlank() || trimmedUsername.isBlank() || request.password.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "All fields are required")
                    return@post
                }

                val passwordError = AuthRules.passwordError(request.password)
                if (!AuthRules.isValidEmail(trimmedEmail) ||
                    trimmedUsername.length !in AuthRules.USERNAME_MIN_LENGTH..AuthRules.USERNAME_MAX_LENGTH ||
                    passwordError != null
                ) {
                    call.respond(HttpStatusCode.BadRequest, passwordError ?: "Invalid email or username.")
                    return@post
                }

                val userExists = dbQuery {
                    UsersTable.selectAll().where { UsersTable.email.lowerCase() eq trimmedEmail }.count() > 0
                }

                if (userExists) {
                    call.respond(HttpStatusCode.Conflict, "Email already registered")
                    return@post
                }

                val generatedId = dbQuery {
                    val insertStatement = UsersTable.insert {
                        it[email] = trimmedEmail
                        it[username] = trimmedUsername
                        it[password] = PasswordHasher.hash(request.password)
                        it[isEmailVerified] = false
                    }
                    insertStatement[UsersTable.id]
                }

                // Generate 6-digit OTP valid for 15 minutes
                val otp = AuthRules.generateOtp()
                val expiresAt = System.currentTimeMillis() + AuthRules.OTP_TTL_MS

                dbQuery {
                    lockVerificationCode(generatedId)
                    UserVerificationTable.deleteWhere { UserVerificationTable.userId eq generatedId }
                    UserVerificationTable.insert {
                        it[userId] = generatedId
                        it[token] = otp
                        it[UserVerificationTable.targetEmail] = trimmedEmail
                        it[UserVerificationTable.expiresAt] = expiresAt
                    }
                }

                if (!EmailService.sendVerificationOtp(trimmedEmail, otp)) {
                    call.respond(
                        HttpStatusCode.Created,
                        RegisterResponse(
                            userId = generatedId,
                            email = trimmedEmail,
                            message = "Account created, but email delivery failed. Wait a minute, then resend the verification code."
                        )
                    )
                    return@post
                }

                call.respond(
                    HttpStatusCode.Created,
                    RegisterResponse(
                        userId = generatedId,
                        email = trimmedEmail,
                        message = "Registration successful! A 6-digit verification code has been sent to your email."
                    )
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (e.isUniqueViolation()) call.respond(HttpStatusCode.Conflict, "Email already registered")
                else call.respond(HttpStatusCode.BadRequest, "Invalid registration request")
            }
        }

        post("/api/auth/verify-registration") {
            try {
                val request = call.receive<VerifyRegistrationRequest>()
                val trimmedEmail = AuthRules.normalizeEmail(request.email)
                val trimmedOtp = request.otp.trim()

                val result = dbQuery {
                    val uidForLock = UsersTable.selectAll()
                        .where { UsersTable.email.lowerCase() eq trimmedEmail }
                        .singleOrNull()?.get(UsersTable.id) ?: return@dbQuery "NOT_FOUND"
                    lockVerificationCode(uidForLock)
                    val userRow = UsersTable.selectAll()
                        .where { UsersTable.email.lowerCase() eq trimmedEmail }
                        .singleOrNull() ?: return@dbQuery "NOT_FOUND"

                    val uid = userRow[UsersTable.id]
                    val verificationRow = UserVerificationTable.selectAll()
                        .where { UserVerificationTable.userId eq uid }
                        .singleOrNull() ?: return@dbQuery "NO_CODE"

                    if (System.currentTimeMillis() >= verificationRow[UserVerificationTable.expiresAt]) {
                        UserVerificationTable.update({ UserVerificationTable.userId eq uid }) {
                            it[UserVerificationTable.consumed] = true
                        }
                        return@dbQuery "EXPIRED"
                    }
                    if (verificationRow[UserVerificationTable.consumed]) return@dbQuery "INVALID"

                    if (verificationRow[UserVerificationTable.token] != trimmedOtp) {
                        val attempts = verificationRow[UserVerificationTable.attempts] + 1
                        UserVerificationTable.update({ UserVerificationTable.userId eq uid }) {
                            it[UserVerificationTable.attempts] = UserVerificationTable.attempts + 1
                            if (attempts >= AuthRules.OTP_MAX_ATTEMPTS) it[UserVerificationTable.consumed] = true
                        }
                        if (attempts >= AuthRules.OTP_MAX_ATTEMPTS) return@dbQuery "TOO_MANY_ATTEMPTS"
                        return@dbQuery "INVALID"
                    }

                    val expectedTargetEmail = userRow[UsersTable.pendingEmail] ?: userRow[UsersTable.email]
                    val codeTargetEmail = verificationRow[UserVerificationTable.targetEmail]
                    if ((userRow[UsersTable.pendingEmail] != null && codeTargetEmail != expectedTargetEmail) ||
                        (codeTargetEmail != null && codeTargetEmail != expectedTargetEmail)
                    ) {
                        UserVerificationTable.update({ UserVerificationTable.userId eq uid }) {
                            it[UserVerificationTable.consumed] = true
                        }
                        return@dbQuery "INVALID"
                    }

                    val consumed = UserVerificationTable.update({
                        (UserVerificationTable.userId eq uid) and
                            (UserVerificationTable.token eq trimmedOtp) and
                            (UserVerificationTable.expiresAt greater System.currentTimeMillis()) and
                            (UserVerificationTable.consumed eq false)
                    }) {
                        it[UserVerificationTable.consumed] = true
                    }
                    if (consumed != 1) return@dbQuery "INVALID"

                    UsersTable.update({ UsersTable.id eq uid }) {
                        it[isEmailVerified] = true
                    }
                    "OK"
                }

                when (result) {
                    "OK" -> call.respond(HttpStatusCode.OK, AuthMessageResponse(true, "Email verified successfully! You can now log in."))
                    "EXPIRED" -> call.respond(HttpStatusCode.BadRequest, AuthMessageResponse(false, "Verification code expired. Please request a new code."))
                    "INVALID" -> call.respond(HttpStatusCode.BadRequest, AuthMessageResponse(false, "Invalid verification code. Please check and try again."))
                    "TOO_MANY_ATTEMPTS" -> call.respond(HttpStatusCode.TooManyRequests, AuthMessageResponse(false, "Too many incorrect codes. Request a new code."))
                    "NO_CODE" -> call.respond(HttpStatusCode.BadRequest, AuthMessageResponse(false, "No pending verification found for this account."))
                    else -> call.respond(HttpStatusCode.NotFound, AuthMessageResponse(false, "Account not found."))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.BadRequest, AuthMessageResponse(false, "Malformed request"))
            }
        }

        post("/api/auth/resend-verification") {
            try {
                val request = call.receive<ResendVerificationRequest>()
                val trimmedEmail = AuthRules.normalizeEmail(request.email)

                val userRow = dbQuery {
                    UsersTable.selectAll()
                        .where { UsersTable.email.lowerCase() eq trimmedEmail }
                        .singleOrNull()
                }

                if (userRow == null) {
                    call.respond(HttpStatusCode.NotFound, AuthMessageResponse(false, "Account not found."))
                    return@post
                }

                if (userRow[UsersTable.isEmailVerified]) {
                    call.respond(HttpStatusCode.OK, AuthMessageResponse(true, "Email is already verified. You can log in."))
                    return@post
                }

                val uid = userRow[UsersTable.id]
                val otp = AuthRules.generateOtp()
                val expiresAt = System.currentTimeMillis() + AuthRules.OTP_TTL_MS

                val issued = dbQuery {
                    lockVerificationCode(uid)
                    val latestUser = UsersTable.selectAll().where { UsersTable.id eq uid }.singleOrNull()
                        ?: return@dbQuery "NOT_FOUND"
                    if (latestUser[UsersTable.isEmailVerified]) return@dbQuery "VERIFIED"
                    val current = UserVerificationTable.selectAll()
                        .where { UserVerificationTable.userId eq uid }
                        .singleOrNull()
                    if (AuthRules.inResendCooldown(current?.get(UserVerificationTable.expiresAt))) return@dbQuery "COOLDOWN"
                    UserVerificationTable.deleteWhere { UserVerificationTable.userId eq uid }
                    UserVerificationTable.insert {
                        it[userId] = uid
                        it[token] = otp
                        it[UserVerificationTable.targetEmail] = userRow[UsersTable.email]
                        it[UserVerificationTable.expiresAt] = expiresAt
                        it[UserVerificationTable.attempts] = 0
                    }
                    "OK"
                }
                when (issued) {
                    "NOT_FOUND" -> return@post call.respond(HttpStatusCode.NotFound, AuthMessageResponse(false, "Account not found."))
                    "VERIFIED" -> return@post call.respond(HttpStatusCode.OK, AuthMessageResponse(true, "Email is already verified. You can log in."))
                    "COOLDOWN" -> return@post call.respond(HttpStatusCode.TooManyRequests, AuthMessageResponse(false, "Please wait before requesting another code."))
                }

                if (!EmailService.sendVerificationOtp(userRow[UsersTable.email], otp)) {
                    dbQuery {
                        UserVerificationTable.deleteWhere {
                            (UserVerificationTable.userId eq uid) and (UserVerificationTable.token eq otp)
                        }
                    }
                    call.respond(HttpStatusCode.BadGateway, AuthMessageResponse(false, "Couldn't send the verification email. Please try again."))
                    return@post
                }
                call.respond(HttpStatusCode.OK, AuthMessageResponse(true, "A new 6-digit verification code has been sent to your email."))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.BadRequest, AuthMessageResponse(false, "Malformed request"))
            }
        }

        post("/api/auth/forgot-password") {
            try {
                val request = call.receive<ForgotPasswordRequest>()
                val trimmedEmail = AuthRules.normalizeEmail(request.email)

                val userExists = dbQuery {
                    UsersTable.selectAll()
                        .where { UsersTable.email.lowerCase() eq trimmedEmail }
                        .count() > 0
                }

                if (userExists) {
                    val otp = AuthRules.generateOtp()
                    val expiresAt = System.currentTimeMillis() + AuthRules.OTP_TTL_MS

                    val canIssue = dbQuery {
                        val existing = PasswordResetTable.selectAll()
                            .where { PasswordResetTable.email eq trimmedEmail }
                            .singleOrNull()
                        if (AuthRules.inResendCooldown(existing?.get(PasswordResetTable.expiresAt))) {
                            false
                        } else {
                            PasswordResetTable.deleteWhere { PasswordResetTable.email eq trimmedEmail }
                            PasswordResetTable.insert {
                                it[email] = trimmedEmail
                                it[PasswordResetTable.otp] = otp
                                it[PasswordResetTable.expiresAt] = expiresAt
                                it[PasswordResetTable.attempts] = 0
                            }
                            true
                        }
                    }

                    if (canIssue && !EmailService.sendPasswordResetOtp(trimmedEmail, otp)) {
                        dbQuery {
                            PasswordResetTable.deleteWhere {
                                (PasswordResetTable.email eq trimmedEmail) and (PasswordResetTable.otp eq otp)
                            }
                        }
                    }
                }

                call.respond(HttpStatusCode.OK, AuthMessageResponse(true, "If an account exists, a reset code has been sent."))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.BadRequest, AuthMessageResponse(false, "Malformed request"))
            }
        }

        post("/api/auth/reset-password") {
            try {
                val request = call.receive<ResetPasswordRequest>()
                val trimmedEmail = AuthRules.normalizeEmail(request.email)
                val trimmedOtp = request.otp.trim()

                val passwordError = AuthRules.passwordError(request.newPassword)
                if (passwordError != null) {
                    call.respond(HttpStatusCode.BadRequest, AuthMessageResponse(false, passwordError))
                    return@post
                }
                if (!AuthRules.isValidEmail(trimmedEmail) || !Regex("^\\d{6}$").matches(trimmedOtp)) {
                    call.respond(HttpStatusCode.BadRequest, AuthMessageResponse(false, "Invalid reset request."))
                    return@post
                }

                val resetResult = dbQuery {
                    val resetRow = PasswordResetTable.selectAll()
                        .where { PasswordResetTable.email eq trimmedEmail }
                        .singleOrNull() ?: return@dbQuery "INVALID"
                    val now = System.currentTimeMillis()
                    if (resetRow[PasswordResetTable.expiresAt] <= now) {
                        PasswordResetTable.update({ PasswordResetTable.email eq trimmedEmail }) {
                            it[PasswordResetTable.consumed] = true
                        }
                        return@dbQuery "EXPIRED"
                    }
                    if (resetRow[PasswordResetTable.consumed]) return@dbQuery "CONSUMED"
                    if (resetRow[PasswordResetTable.attempts] >= AuthRules.OTP_MAX_ATTEMPTS) {
                        PasswordResetTable.update({ PasswordResetTable.email eq trimmedEmail }) {
                            it[PasswordResetTable.consumed] = true
                        }
                        return@dbQuery "TOO_MANY_ATTEMPTS"
                    }
                    if (resetRow[PasswordResetTable.otp] != trimmedOtp) {
                        PasswordResetTable.update({ PasswordResetTable.email eq trimmedEmail }) {
                        it[PasswordResetTable.attempts] = PasswordResetTable.attempts + 1
                            if (resetRow[PasswordResetTable.attempts] + 1 >= AuthRules.OTP_MAX_ATTEMPTS) {
                                it[PasswordResetTable.consumed] = true
                            }
                        }
                        if (resetRow[PasswordResetTable.attempts] + 1 >= AuthRules.OTP_MAX_ATTEMPTS) {
                            return@dbQuery "TOO_MANY_ATTEMPTS"
                        }
                        return@dbQuery "INVALID"
                    }

                    val userRow = UsersTable.selectAll().where { UsersTable.email eq trimmedEmail }.singleOrNull()
                        ?: return@dbQuery "USER_NOT_FOUND"

                    // Mark the matching, still-live code consumed before changing credentials. A
                    // concurrent request cannot reuse it after this conditional update succeeds.
                    val consumed = PasswordResetTable.update({
                        (PasswordResetTable.email eq trimmedEmail) and
                            (PasswordResetTable.otp eq trimmedOtp) and
                            (PasswordResetTable.expiresAt greater now) and
                            (PasswordResetTable.consumed eq false)
                    }) {
                        it[PasswordResetTable.consumed] = true
                    }
                    if (consumed != 1) return@dbQuery "INVALID"

                    val updated = UsersTable.update({ UsersTable.id eq userRow[UsersTable.id] }) {
                        it[password] = PasswordHasher.hash(request.newPassword)
                        it[tokenVersion] = userRow[UsersTable.tokenVersion] + 1
                    }

                    TokenVersions.invalidate(userRow[UsersTable.id])
                    if (updated > 0) "OK" else "USER_NOT_FOUND"
                }

                when (resetResult) {
                    "OK" -> call.respond(HttpStatusCode.OK, AuthMessageResponse(true, "Password reset successfully! You can now log in."))
                    "EXPIRED" -> call.respond(HttpStatusCode.BadRequest, AuthMessageResponse(false, "Reset code expired. Please request a new code."))
                    "INVALID" -> call.respond(HttpStatusCode.BadRequest, AuthMessageResponse(false, "Invalid reset code. Please check and try again."))
                    "TOO_MANY_ATTEMPTS" -> call.respond(HttpStatusCode.TooManyRequests, AuthMessageResponse(false, "Too many incorrect codes. Request a new reset code."))
                    else -> call.respond(HttpStatusCode.BadRequest, AuthMessageResponse(false, "Invalid reset request."))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.BadRequest, AuthMessageResponse(false, "Malformed request"))
            }
        }
        }

        authenticate("auth-jwt") {

            // â”€â”€ Register this device for push notifications â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            // Backward-compatible: existing clients send {"fcmToken": "..."}.
            // New clients can additionally send {"deviceId": "..."} to enable per-device token rows.
            // The token is always bound to the JWT user â€” any userId field in the body is ignored.
            post("/api/user/fcm-token") {
                try {
                    val actingUserId = call.authenticatedUserId()
                    val body = call.receive<Map<String, String>>()
                    val fcmToken = body["fcmToken"]
                    val deviceId = body["deviceId"]?.take(128)  // client-supplied stable device id

                    if (fcmToken.isNullOrBlank() || fcmToken.length > 500) {
                        call.respond(HttpStatusCode.BadRequest, "Missing or invalid fcmToken")
                        return@post
                    }
                    dbQuery {
                        // â”€â”€ Legacy single-token path (backward compat for all existing clients) â”€â”€
                        // Detach this token from any other account (handed-over phone), then assign to current user.
                        UsersTable.update({ (UsersTable.fcmToken eq fcmToken) and (UsersTable.id neq actingUserId) }) {
                            it[UsersTable.fcmToken] = null
                        }
                        UsersTable.update({ UsersTable.id eq actingUserId }) {
                            it[UsersTable.fcmToken] = fcmToken
                        }

                        // â”€â”€ Multi-device token path (when client supplies a deviceId) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                        if (!deviceId.isNullOrBlank()) {
                            // Remove this token from any OTHER user's device rows
                            // (hands-over: someone else's account used to own this device)
                            UserFcmTokensTable.deleteWhere {
                                (UserFcmTokensTable.token eq fcmToken)
                            }
                            // Re-insert for the current user (we just cleared it above if it existed elsewhere)
                            UserFcmTokensTable.upsert(
                                UserFcmTokensTable.userId,
                                UserFcmTokensTable.deviceId
                            ) {
                                it[UserFcmTokensTable.userId] = actingUserId
                                it[UserFcmTokensTable.deviceId] = deviceId
                                it[UserFcmTokensTable.token] = fcmToken
                                it[UserFcmTokensTable.updatedAt] = System.currentTimeMillis()
                            }
                        }
                    }
                    call.respond(HttpStatusCode.OK, mapOf("status" to "success"))
                } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                    logger.warn("[FCM] Token registration failed", e)
                    call.respond(HttpStatusCode.BadRequest, "Invalid request body")
                }
            }

            // â”€â”€ Unregister this device (logout) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            // Clears both the legacy single-token and the per-device row (if deviceId supplied).
            delete("/api/user/fcm-token") {
                try {
                    val actingUserId = call.authenticatedUserId()
                    val body = runCatching { call.receive<Map<String, String>>() }.getOrNull()
                    val deviceId = body?.get("deviceId")?.take(128)

                    dbQuery {
                        // Always clear legacy token
                        UsersTable.update({ UsersTable.id eq actingUserId }) {
                            it[UsersTable.fcmToken] = null
                        }
                        // If device-aware client: remove just this device's row
                        // If legacy client (no deviceId): remove ALL device rows for this user (full logout)
                        if (!deviceId.isNullOrBlank()) {
                            UserFcmTokensTable.deleteWhere {
                                (UserFcmTokensTable.userId eq actingUserId) and
                                (UserFcmTokensTable.deviceId eq deviceId)
                            }
                        } else {
                            UserFcmTokensTable.deleteWhere {
                                UserFcmTokensTable.userId eq actingUserId
                            }
                        }
                    }
                    call.respond(HttpStatusCode.OK, mapOf("status" to "success"))
                } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                    call.respond(HttpStatusCode.OK, mapOf("status" to "success")) // idempotent logout
                }
            }

            // â”€â”€ GET own full profile â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
                if (e is kotlinx.coroutines.CancellationException) throw e
                    call.respond(HttpStatusCode.InternalServerError, "Error fetching profile")
                }
            }

            // â”€â”€ UPDATE own profile (username, bio, status, password) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            post("/api/user/profile") {
                try {
                    val request = call.receive<UpdateProfileRequest>()
                    val actingUserId = call.authenticatedUserId()
                    val username = request.userName.trim()
                    val newPassword = request.newPassword
                    val currentPassword = request.currentPassword
                    val changingPassword = !newPassword.isNullOrEmpty() || !currentPassword.isNullOrEmpty()
                    val passwordError = newPassword?.let { AuthRules.passwordError(it) }
                    if (username.length !in AuthRules.USERNAME_MIN_LENGTH..AuthRules.USERNAME_MAX_LENGTH ||
                        (request.bio?.length ?: 0) > AuthRules.BIO_MAX_LENGTH ||
                        (request.statusMessage?.length ?: 0) > AuthRules.STATUS_MAX_LENGTH ||
                        (changingPassword && (newPassword.isNullOrEmpty() || currentPassword.isNullOrEmpty() || passwordError != null))
                    ) {
                        call.respond(HttpStatusCode.BadRequest, passwordError ?: "Invalid profile fields.")
                        return@post
                    }
                    val updateResult = dbQuery {
                        val userRow = UsersTable.selectAll().where { UsersTable.id eq actingUserId }.singleOrNull()
                        if (userRow == null) {
                            "NOT_FOUND"
                        } else {
                            if (changingPassword) {
                                if (!PasswordHasher.matches(currentPassword!!, userRow[UsersTable.password])) {
                                    return@dbQuery "UNAUTHORIZED"
                                }
                                UsersTable.update({ UsersTable.id eq actingUserId }) {
                                    it[UsersTable.username] = username
                                    it[bio] = request.bio
                                    it[statusMessage] = request.statusMessage
                                    it[password] = PasswordHasher.hash(request.newPassword)
                                    it[tokenVersion] = userRow[UsersTable.tokenVersion] + 1
                                }
                                TokenVersions.invalidate(actingUserId)
                            } else {
                                UsersTable.update({ UsersTable.id eq actingUserId }) {
                                    it[UsersTable.username] = username
                                    it[bio] = request.bio
                                    it[statusMessage] = request.statusMessage
                                }
                            }
                            "OK"
                        }
                    }
                    when (updateResult) {
                        "OK" -> call.respond(HttpStatusCode.OK, true)
                        "UNAUTHORIZED" -> call.respond(HttpStatusCode.Unauthorized, false)
                        else -> call.respond(HttpStatusCode.NotFound, false)
                    }
                } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                    call.respond(HttpStatusCode.InternalServerError, false)
                }
            }

            // â”€â”€ UPLOAD avatar (stored permanently on Cloudinary) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            post("/api/user/avatar") {
                val avatarMaxBytes = 5L * 1024 * 1024 // 5 MB
                var staged: File? = null
                var newlyUploadedPublicId: String? = null
                try {
                    val actingUserId = call.authenticatedUserId()
                    if (call.declaredBodyExceeds(avatarMaxBytes)) throw UploadTooLargeException(avatarMaxBytes)
                    val multipart = call.receiveMultipart()

                    multipart.forEachPart { part ->
                        if (part is PartData.FileItem && staged == null) {
                            staged = part.stageToTempFile(avatarMaxBytes)
                        }
                        part.dispose()
                    }

                    val imageFile = staged
                    if (imageFile != null) {
                        if (!isValidAvatarImage(imageFile)) {
                            call.respond(HttpStatusCode.BadRequest, "Uploaded file is not a supported image")
                            return@post
                        }
                        // Upload under a fresh ID so a failed DB write cannot replace the currently referenced avatar.
                        val publicId = "avatar_${actingUserId}_${UUID.randomUUID()}"
                        newlyUploadedPublicId = publicId
                        val cloudUrl = avatarStorage.upload(imageFile, publicId)
                        val oldPublicId = dbQuery {
                            val userRow = UsersTable.selectAll().where { UsersTable.id eq actingUserId }.singleOrNull()
                                ?: throw IllegalStateException("User profile disappeared during avatar upload")
                            val updated = UsersTable.update({ UsersTable.id eq actingUserId }) {
                                it[avatarUrl] = cloudUrl
                                it[avatarPublicId] = publicId
                            }
                            check(updated == 1) { "Avatar profile update did not affect the user" }
                            userRow[UsersTable.avatarPublicId]
                                ?: if (userRow[UsersTable.avatarUrl] != null) "avatar_$actingUserId" else null
                        }
                        newlyUploadedPublicId = null
                        oldPublicId?.let { avatarStorage.delete(it) }
                        call.respond(HttpStatusCode.OK, AvatarUploadResponse(avatarUrl = cloudUrl))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "No file received")
                    }
                } catch (e: UploadTooLargeException) {
                    call.respond(HttpStatusCode.PayloadTooLarge, e.message ?: "File too large")
                } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                    logger.error("[Avatar] Upload failed", e)
                    call.respond(HttpStatusCode.InternalServerError, "Avatar upload failed")
                } finally {
                    newlyUploadedPublicId?.let { publicId ->
                        withContext(NonCancellable) { avatarStorage.delete(publicId) }
                    }
                    staged?.delete()
                }
            }

            // â”€â”€ DELETE avatar (remove from Cloudinary + revert to generated default) â”€â”€
            delete("/api/user/avatar") {
                try {
                    val actingUserId = call.authenticatedUserId()
                    val oldPublicId = dbQuery {
                        val row = UsersTable.selectAll().where { UsersTable.id eq actingUserId }.singleOrNull()
                            ?: return@dbQuery null
                        UsersTable.update({ UsersTable.id eq actingUserId }) {
                            it[avatarUrl] = null
                            it[avatarPublicId] = null
                        }
                        row[UsersTable.avatarPublicId] ?: if (row[UsersTable.avatarUrl] != null) "avatar_$actingUserId" else null
                    }
                    oldPublicId?.let { avatarStorage.delete(it) }
                    call.respond(HttpStatusCode.OK, AvatarUploadResponse(avatarUrl = AvatarGenerator.avatarUrlFor(actingUserId, null)))
                } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                    call.respond(HttpStatusCode.InternalServerError, "Failed to remove avatar")
                }
            }

            // â”€â”€ UPDATE email (change email request) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            put("/api/user/email") {
                try {
                    val request = call.receive<ChangeEmailRequest>()
                    val actingUserId = call.authenticatedUserId()
                    val newEmail = AuthRules.normalizeEmail(request.newEmail)
                    if (!AuthRules.isValidEmail(newEmail)) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid email address")
                        return@put
                    }

                    val result = dbQuery {
                        lockVerificationCode(actingUserId)
                        val userRow = UsersTable.selectAll().where { UsersTable.id eq actingUserId }.singleOrNull()
                            ?: return@dbQuery "NOT_FOUND"

                        if (!PasswordHasher.matches(request.currentPassword, userRow[UsersTable.password])) {
                            return@dbQuery "UNAUTHORIZED"
                        }

                        if (newEmail == userRow[UsersTable.email]) return@dbQuery "SAME"

                        if (UsersTable.selectAll().where {
                                ((UsersTable.email.lowerCase() eq newEmail) or (UsersTable.pendingEmail.lowerCase() eq newEmail)) and
                                    (UsersTable.id neq actingUserId)
                            }.count() > 0
                        ) {
                            return@dbQuery "CONFLICT"
                        }

                        if (newEmail == userRow[UsersTable.pendingEmail]) return@dbQuery "OK"

                        UserVerificationTable.update({ UserVerificationTable.userId eq actingUserId }) {
                            it[UserVerificationTable.consumed] = true
                        }
                        UsersTable.update({ UsersTable.id eq actingUserId }) {
                            it[pendingEmail] = newEmail
                        }
                        "OK"
                    }

                    when (result) {
                        "OK" -> call.respond(HttpStatusCode.OK, "Email change requested")
                        "UNAUTHORIZED" -> call.respond(HttpStatusCode.Unauthorized, "Incorrect password")
                        "CONFLICT" -> call.respond(HttpStatusCode.Conflict, "Email already in use")
                        "SAME" -> call.respond(HttpStatusCode.BadRequest, "Email is already the current address")
                        else -> call.respond(HttpStatusCode.NotFound, "User not found")
                    }
                } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                    if (e.isUniqueViolation()) call.respond(HttpStatusCode.Conflict, "Email already in use")
                    else call.respond(HttpStatusCode.BadRequest, "Malformed request")
                }
            }

            post("/api/user/verify-email/send") {
                try {
                    val actingUserId = call.authenticatedUserId()
                    val userRow = dbQuery {
                        UsersTable.selectAll().where { UsersTable.id eq actingUserId }.singleOrNull()
                    } ?: return@post call.respond(HttpStatusCode.NotFound, "User not found")

                    if (userRow[UsersTable.isEmailVerified] && userRow[UsersTable.pendingEmail] == null) {
                        return@post call.respond(HttpStatusCode.Conflict, "Email is already verified")
                    }

                    val otp = AuthRules.generateOtp()
                    val expiresAt = System.currentTimeMillis() + AuthRules.OTP_TTL_MS

                    val issuance = dbQuery {
                        lockVerificationCode(actingUserId)
                        val currentUser = UsersTable.selectAll().where { UsersTable.id eq actingUserId }.singleOrNull()
                            ?: return@dbQuery "NOT_FOUND" to null
                        if (currentUser[UsersTable.isEmailVerified] && currentUser[UsersTable.pendingEmail] == null) {
                            return@dbQuery "VERIFIED" to null
                        }
                        val emailTarget = currentUser[UsersTable.pendingEmail] ?: currentUser[UsersTable.email]
                        val current = UserVerificationTable.selectAll()
                            .where { UserVerificationTable.userId eq actingUserId }
                            .singleOrNull()
                        val existingTarget = current?.get(UserVerificationTable.targetEmail)
                        val sameTarget = existingTarget == emailTarget ||
                            (existingTarget == null && currentUser[UsersTable.pendingEmail] == null)
                        if (sameTarget && AuthRules.inResendCooldown(current?.get(UserVerificationTable.expiresAt))) {
                            return@dbQuery "COOLDOWN" to null
                        }
                        UserVerificationTable.deleteWhere { UserVerificationTable.userId eq actingUserId }
                        UserVerificationTable.insert {
                            it[userId] = actingUserId
                            it[token] = otp
                            it[UserVerificationTable.targetEmail] = emailTarget
                            it[UserVerificationTable.expiresAt] = expiresAt
                            it[UserVerificationTable.attempts] = 0
                        }
                        "OK" to emailTarget
                    }
                    when (issuance.first) {
                        "NOT_FOUND" -> return@post call.respond(HttpStatusCode.NotFound, "User not found")
                        "VERIFIED" -> return@post call.respond(HttpStatusCode.Conflict, "Email is already verified")
                        "COOLDOWN" -> return@post call.respond(HttpStatusCode.TooManyRequests, "Please wait before requesting another code")
                    }
                    val emailTarget = issuance.second ?: return@post call.respond(HttpStatusCode.InternalServerError, "Failed to issue verification code")

                    if (EmailService.sendVerificationOtp(emailTarget, otp)) {
                        call.respond(HttpStatusCode.OK, "Verification email sent")
                    } else {
                        dbQuery {
                            UserVerificationTable.deleteWhere {
                                (UserVerificationTable.userId eq actingUserId) and (UserVerificationTable.token eq otp)
                            }
                        }
                        call.respond(HttpStatusCode.BadGateway, "Couldn't send the verification email. Please try again.")
                    }
                } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                    call.respond(HttpStatusCode.InternalServerError, "Failed to send email")
                }
            }

            post("/api/user/verify-email/confirm") {
                try {
                    val request = call.receive<EmailVerifyConfirmRequest>()
                    val actingUserId = call.authenticatedUserId()
                    val trimmedOtp = request.token.trim()

                    val result = dbQuery {
                        lockVerificationCode(actingUserId)
                        val userRow = UsersTable.selectAll().where { UsersTable.id eq actingUserId }.singleOrNull()
                            ?: return@dbQuery "NOT_FOUND"

                        val verificationRow = UserVerificationTable.selectAll()
                            .where { UserVerificationTable.userId eq actingUserId }
                            .singleOrNull() ?: return@dbQuery "NO_CODE"

                        if (System.currentTimeMillis() >= verificationRow[UserVerificationTable.expiresAt]) {
                            UserVerificationTable.update({ UserVerificationTable.userId eq actingUserId }) {
                                it[UserVerificationTable.consumed] = true
                            }
                            return@dbQuery "EXPIRED"
                        }
                        if (verificationRow[UserVerificationTable.consumed]) return@dbQuery "INVALID"

                        if (verificationRow[UserVerificationTable.token] != trimmedOtp) {
                            val attempts = verificationRow[UserVerificationTable.attempts] + 1
                            UserVerificationTable.update({ UserVerificationTable.userId eq actingUserId }) {
                                it[UserVerificationTable.attempts] = UserVerificationTable.attempts + 1
                                if (attempts >= AuthRules.OTP_MAX_ATTEMPTS) it[UserVerificationTable.consumed] = true
                            }
                            if (attempts >= AuthRules.OTP_MAX_ATTEMPTS) return@dbQuery "TOO_MANY_ATTEMPTS"
                            return@dbQuery "INVALID"
                        }

                        val expectedTargetEmail = userRow[UsersTable.pendingEmail] ?: userRow[UsersTable.email]
                        val codeTargetEmail = verificationRow[UserVerificationTable.targetEmail]
                        if ((userRow[UsersTable.pendingEmail] != null && codeTargetEmail != expectedTargetEmail) ||
                            (codeTargetEmail != null && codeTargetEmail != expectedTargetEmail)
                        ) {
                            UserVerificationTable.update({ UserVerificationTable.userId eq actingUserId }) {
                                it[UserVerificationTable.consumed] = true
                            }
                            return@dbQuery "INVALID"
                        }

                        val consumed = UserVerificationTable.update({
                            (UserVerificationTable.userId eq actingUserId) and
                                (UserVerificationTable.token eq trimmedOtp) and
                                (UserVerificationTable.expiresAt greater System.currentTimeMillis()) and
                                (UserVerificationTable.consumed eq false)
                        }) {
                            it[UserVerificationTable.consumed] = true
                        }
                        if (consumed != 1) return@dbQuery "INVALID"

                        UsersTable.update({ UsersTable.id eq actingUserId }) {
                            it[isEmailVerified] = true
                            if (userRow[UsersTable.pendingEmail] != null) {
                                it[email] = userRow[UsersTable.pendingEmail]!!
                                it[pendingEmail] = null
                            }
                        }
                        "OK"
                    }

                    when (result) {
                        "OK" -> call.respond(HttpStatusCode.OK, "Email verified")
                        "EXPIRED" -> call.respond(HttpStatusCode.Gone, "Code expired")
                        "INVALID" -> call.respond(HttpStatusCode.BadRequest, "Invalid code")
                        "TOO_MANY_ATTEMPTS" -> call.respond(HttpStatusCode.TooManyRequests, "Too many incorrect codes. Request a new code.")
                        else -> call.respond(HttpStatusCode.NotFound, "User not found")
                    }
                } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                    if (e.isUniqueViolation()) call.respond(HttpStatusCode.Conflict, "Email address is already in use")
                    else call.respond(HttpStatusCode.BadRequest, "Malformed request")
                }
            }

            delete("/api/user/account") {
                try {
                    val request = call.receive<DeleteAccountRequest>()
                    val actingUserId = call.authenticatedUserId()

                    val result = dbQuery {
                        val userRow = UsersTable.selectAll().where { UsersTable.id eq actingUserId }.singleOrNull()
                            ?: return@dbQuery "NOT_FOUND"

                        if (!PasswordHasher.matches(request.password, userRow[UsersTable.password])) {
                            return@dbQuery "UNAUTHORIZED"
                        }

                        val ownedWorkspaceIds = WorkspacesTable.select(WorkspacesTable.id)
                            .where { WorkspacesTable.userId eq actingUserId }
                            .map { it[WorkspacesTable.id] }
                        val ownsSharedWorkspace = ownedWorkspaceIds.any { workspaceId ->
                            WorkspaceMembersTable.selectAll().where {
                                (WorkspaceMembersTable.workspaceId eq workspaceId) and
                                    (WorkspaceMembersTable.userId neq actingUserId)
                            }.count() > 0
                        }
                        if (ownsSharedWorkspace) return@dbQuery "OWNED_SHARED_WORKSPACES"

                        PasswordResetTable.deleteWhere { PasswordResetTable.email eq userRow[UsersTable.email] }
                        
                        UsersTable.deleteWhere { UsersTable.id eq actingUserId }
                        TokenVersions.invalidate(actingUserId)
                        "OK"
                    }

                    when (result) {
                        "OK" -> call.respond(HttpStatusCode.OK, "Account deleted")
                        "UNAUTHORIZED" -> call.respond(HttpStatusCode.Unauthorized, "Incorrect password")
                        "OWNED_SHARED_WORKSPACES" -> call.respond(HttpStatusCode.Conflict, "Transfer or delete your shared workspaces before deleting this account")
                        else -> call.respond(HttpStatusCode.NotFound, "User not found")
                    }
                } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                    call.respond(HttpStatusCode.BadRequest, "Malformed request")
                }
            }

            // â”€â”€ GET workspace active/online users â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            get("/api/presence/{workspaceId}") {
                try {
                    val actingUserId = call.authenticatedUserId()
                    val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid workspaceId")

                    val onlineMemberIds = dbQuery {
                        if (!isMember(actingUserId, workspaceIdParam)) return@dbQuery null
                        val memberIds = WorkspaceMembersTable
                            .select(WorkspaceMembersTable.userId)
                            .where { WorkspaceMembersTable.workspaceId eq workspaceIdParam }
                            .map { it[WorkspaceMembersTable.userId] }
                            .toSet()

                        memberIds.filter { uid ->
                            WebSocketBroker.isUserConnected(uid.toLong())
                        }
                    }

                    if (onlineMemberIds == null) {
                        call.respond(HttpStatusCode.Forbidden, emptyList<Int>())
                    } else {
                        call.respond(HttpStatusCode.OK, onlineMemberIds)
                    }
                } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                    call.respond(HttpStatusCode.InternalServerError, emptyList<Int>())
                }
            }

            // â”€â”€ CHANGE email â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            get("/api/user/{userId}") {
                try {
                    val actingUserId = call.authenticatedUserId()
                    val targetId = call.parameters["userId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid userId")

                    val response = dbQuery {
                        val isBlocked = UserBlocksTable.selectAll().where {
                            ((UserBlocksTable.blockerId eq targetId) and (UserBlocksTable.blockedId eq actingUserId)) or
                                ((UserBlocksTable.blockerId eq actingUserId) and (UserBlocksTable.blockedId eq targetId))
                        }.count() > 0
                        if (isBlocked) return@dbQuery null

                        UsersTable.selectAll().where { UsersTable.id eq targetId }.singleOrNull()?.let { row ->
                            if (row[UsersTable.profileVisibility] == "members_only" && targetId != actingUserId) {
                                val viewerWorkspaces = WorkspaceMembersTable.select(WorkspaceMembersTable.workspaceId)
                                    .where { WorkspaceMembersTable.userId eq actingUserId }
                                    .map { it[WorkspaceMembersTable.workspaceId] }
                                    .toSet()
                                val sharesWorkspace = viewerWorkspaces.isNotEmpty() && WorkspaceMembersTable.selectAll().where {
                                    (WorkspaceMembersTable.userId eq targetId) and
                                        (WorkspaceMembersTable.workspaceId inList viewerWorkspaces)
                                }.count() > 0
                                if (!sharesWorkspace) return@dbQuery null
                            }
                            val isOnlineNow = WebSocketBroker.isUserConnected(targetId.toLong())
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
                if (e is kotlinx.coroutines.CancellationException) throw e
                    call.respond(HttpStatusCode.InternalServerError, "Error fetching profile")
                }
            }

            // â”€â”€ SEARCH users â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            get("/api/user/search") {
                try {
                    val actingUserId = call.authenticatedUserId()
                    val query = call.request.queryParameters["q"]?.trim() ?: ""
                    if (query.length !in 2..100 || '%' in query || '_' in query) {
                        call.respond(HttpStatusCode.BadRequest, "Search query must be 2 to 100 characters and cannot contain wildcard characters")
                        return@get
                    }

                    val results = dbQuery {
                        val blockedIds = UserBlocksTable.selectAll().where {
                            (UserBlocksTable.blockerId eq actingUserId) or (UserBlocksTable.blockedId eq actingUserId)
                        }.map { if (it[UserBlocksTable.blockerId] == actingUserId) it[UserBlocksTable.blockedId] else it[UserBlocksTable.blockerId] }.toSet()

                        val matches = UsersTable.selectAll().where {
                            (UsersTable.username like "%$query%") or
                                ((UsersTable.showEmail eq true) and (UsersTable.email like "%$query%"))
                        }.toList()
                        val viewerWorkspaces = WorkspaceMembersTable.select(WorkspaceMembersTable.workspaceId)
                            .where { WorkspaceMembersTable.userId eq actingUserId }
                            .map { it[WorkspaceMembersTable.workspaceId] }
                            .toSet()
                        val membersOnlyVisibleIds = if (viewerWorkspaces.isEmpty()) emptySet() else {
                            WorkspaceMembersTable.select(WorkspaceMembersTable.userId).where {
                                WorkspaceMembersTable.workspaceId inList viewerWorkspaces
                            }.map { it[WorkspaceMembersTable.userId] }.toSet()
                        }
                        matches.filter {
                            it[UsersTable.id] !in blockedIds &&
                                it[UsersTable.id] != actingUserId &&
                                (it[UsersTable.profileVisibility] != "members_only" || it[UsersTable.id] in membersOnlyVisibleIds) &&
                                it[UsersTable.email] != com.collabsphere.util.GitHubBot.EMAIL
                        }
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
                if (e is kotlinx.coroutines.CancellationException) throw e
                    call.respond(HttpStatusCode.InternalServerError, "Error searching users")
                }
            }

            // â”€â”€ DELETE own account â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            post("/api/user/block/{targetUserId}") {
                try {
                    val actingUserId = call.authenticatedUserId()
                    val targetId = call.parameters["targetUserId"]?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid targetUserId")
                    if (actingUserId == targetId)
                        return@post call.respond(HttpStatusCode.BadRequest, "Cannot block yourself")

                    val targetExists = dbQuery {
                        lockUserPair(actingUserId, targetId)
                        if (UsersTable.selectAll().where { UsersTable.id eq targetId }.count() == 0) return@dbQuery false
                        UserBlocksTable.insertIgnore {
                            it[blockerId] = actingUserId
                            it[blockedId] = targetId
                        }
                        true
                    }
                    if (!targetExists) return@post call.respond(HttpStatusCode.NotFound, "User not found")
                    call.respond(HttpStatusCode.OK, BlockUserResponse(actingUserId, targetId, "blocked"))
                } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                    call.respond(HttpStatusCode.InternalServerError, "Failed to block user")
                }
            }

            // â”€â”€ UNBLOCK a user â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            delete("/api/user/block/{targetUserId}") {
                try {
                    val actingUserId = call.authenticatedUserId()
                    val targetId = call.parameters["targetUserId"]?.toIntOrNull()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid targetUserId")
                    dbQuery {
                        lockUserPair(actingUserId, targetId)
                        UserBlocksTable.deleteWhere {
                            (UserBlocksTable.blockerId eq actingUserId) and (UserBlocksTable.blockedId eq targetId)
                        }
                    }
                    call.respond(HttpStatusCode.OK, BlockUserResponse(actingUserId, targetId, "unblocked"))
                } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                    call.respond(HttpStatusCode.InternalServerError, "Failed to unblock user")
                }
            }

            // â”€â”€ LIST blocked users â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
                if (e is kotlinx.coroutines.CancellationException) throw e
                    call.respond(HttpStatusCode.InternalServerError, "Failed to fetch blocked users")
                }
            }

            // â”€â”€ UPDATE privacy settings â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
                if (e is kotlinx.coroutines.CancellationException) throw e
                    call.respond(HttpStatusCode.InternalServerError, "Failed to update privacy settings")
                }
            }

            // â”€â”€ SEND email verification â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            notificationsRoutes()

            // â”€â”€ Workspace â”€â”€ extracted to plugins/routes/WorkspaceRoutes.kt â”€â”€
            workspaceRoutes()

            // â”€â”€ Channels â”€â”€ extracted to plugins/routes/ChannelsRoutes.kt â”€â”€â”€â”€â”€â”€â”€â”€
            channelsRoutes()

            // â”€â”€ Tasks â”€â”€ extracted to plugins/routes/TasksRoutes.kt â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            tasksRoutes()

            // â”€â”€ Notes â”€â”€ extracted to plugins/routes/NotesRoutes.kt â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            notesRoutes()


            // â”€â”€ Messages â”€â”€ extracted to plugins/routes/MessagesRoutes.kt â”€â”€â”€â”€â”€â”€â”€â”€
            messagesRoutes()

            // â”€â”€ Files â”€â”€ extracted to plugins/routes/FilesRoutes.kt â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            filesRoutes()

            // â”€â”€ DMs & Media â”€â”€ extracted to plugins/routes/DmRoutes.kt â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            dmRoutes()

            route("/ws") {
                webSocket("/dm") {
                    val userIdParam = call.authenticatedUserId().toLong()
                    val supportsChannelEvents = call.request.queryParameters["caps"]
                        ?.split(",")
                        ?.any { it.trim() == "channel" } == true

                    // Register session â€” passes supportsChannelEvents so only one registration happens
                    WebSocketBroker.addSession(userIdParam, this, supportsChannelEvents = supportsChannelEvents)
                    var cachedUsername: String? = null

                    // Mark user online on WS connect
                    try {
                        dbQuery {
                            UsersTable.update({ UsersTable.id eq userIdParam.toInt() }) {
                                it[UsersTable.lastSeen] = System.currentTimeMillis()
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore concurrent update exception
                    }

                    // Teammate IDs across all shared workspaces for presence broadcast
                    val teammateIds = runCatching {
                        dbQuery {
                            val userWorkspaces = WorkspaceMembersTable
                                .select(WorkspaceMembersTable.workspaceId)
                                .where { WorkspaceMembersTable.userId eq userIdParam.toInt() }
                                .map { it[WorkspaceMembersTable.workspaceId] }

                            WorkspaceMembersTable
                                .select(WorkspaceMembersTable.userId)
                                .where { (WorkspaceMembersTable.workspaceId inList userWorkspaces) and (WorkspaceMembersTable.userId neq userIdParam.toInt()) }
                                .map { it[WorkspaceMembersTable.userId] }
                                .toSet()
                        }
                    }.getOrDefault(emptySet())

                    // Broadcast USER_ONLINE to teammates currently connected
                    try {
                        val onlineDto = DmDto(action = "USER_ONLINE", senderId = userIdParam.toInt())
                        val onlineJson = Json.encodeToString(DmDto.serializer(), onlineDto)
                        teammateIds.forEach { sendToUser(it.toLong(), onlineJson) }
                    } catch (_: Exception) {}

                    try {
                        val sinceIdParam = call.request.queryParameters["sinceId"]?.toIntOrNull() ?: 0
                        suspend fun sendHistory(rows: List<DmDto>) {
                            rows.forEach { historyDto ->
                                if (this.isActive) {
                                    this.send(Frame.Text(Json.encodeToString(DmDto.serializer(), historyDto)))
                                }
                            }
                        }
                        if (sinceIdParam > 0) {
                            // Catch up on everything newer than what the device holds, a bounded page at a
                            // time â€” a device offline for months shouldn't pull its whole backlog into memory.
                            var afterId = sinceIdParam
                            while (this.isActive) {
                                val page = dbQuery { dmCatchUpPage(userIdParam.toInt(), afterId, DM_CATCH_UP_PAGE) }
                                sendHistory(page)
                                if (page.size < DM_CATCH_UP_PAGE) break
                                afterId = page.last().id ?: break
                            }
                        } else {
                            // Fresh device: only the newest page of each conversation, picked in SQL.
                            sendHistory(dbQuery { initialDmHistory(userIdParam.toInt(), DEFAULT_HISTORY_PAGE) })
                        }

                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val receivedText = frame.readText()
                                try {
                                    val dmDto = Json.decodeFromString(DmDto.serializer(), receivedText)

                                    if (dmDto.action == "SEND_MESSAGE") {
                                        // Only the authenticated connection owner may send as themselves.
                                        val requestedDto = dmDto.copy(senderId = userIdParam.toInt())
                                        val savedMessageDto = dbQuery {
                                            lockUserPair(requestedDto.senderId, requestedDto.receiverId)
                                            val rejection = DmRules.sendRejection(
                                                senderIsMember = isMember(requestedDto.senderId, requestedDto.workspaceId),
                                                receiverIsMember = isMember(requestedDto.receiverId, requestedDto.workspaceId),
                                                blockedEitherWay = UserBlocksTable.selectAll().where {
                                                    ((UserBlocksTable.blockerId eq requestedDto.senderId) and (UserBlocksTable.blockedId eq requestedDto.receiverId)) or
                                                        ((UserBlocksTable.blockerId eq requestedDto.receiverId) and (UserBlocksTable.blockedId eq requestedDto.senderId))
                                                }.count() > 0,
                                                content = requestedDto.content,
                                                hasMedia = !requestedDto.mediaUrl.isNullOrBlank()
                                            )
                                            if (rejection != null) {
                                                logger.info("[DM] Refused SEND_MESSAGE from userId=${requestedDto.senderId} to ${requestedDto.receiverId}: $rejection")
                                                return@dbQuery null
                                            }
                                            val validReplyToId = requestedDto.replyToId?.takeIf { targetId ->
                                                DirectMessagesTable.selectAll().where {
                                                    (DirectMessagesTable.id eq targetId) and
                                                            (DirectMessagesTable.isDeleted eq false) and
                                                            (DirectMessagesTable.workspaceId eq requestedDto.workspaceId) and (
                                                            ((DirectMessagesTable.senderId eq requestedDto.senderId) and (DirectMessagesTable.receiverId eq requestedDto.receiverId)) or
                                                                    ((DirectMessagesTable.senderId eq requestedDto.receiverId) and (DirectMessagesTable.receiverId eq requestedDto.senderId))
                                                            )
                                                }.count() > 0
                                            }
                                            val outgoingDto = requestedDto.copy(replyToId = validReplyToId)
                                            val insertedStatement = DirectMessagesTable.insert {
                                                it[workspaceId] = outgoingDto.workspaceId
                                                it[senderId] = outgoingDto.senderId
                                                it[receiverId] = outgoingDto.receiverId
                                                it[content] = outgoingDto.content
                                                it[timestamp] = outgoingDto.timestamp
                                                it[mediaUrl] = outgoingDto.mediaUrl
                                                it[replyToId] = outgoingDto.replyToId
                                            }

                                            val rawId = insertedStatement[DirectMessagesTable.id]
                                            val generatedId = when (rawId) {
                                                is org.jetbrains.exposed.dao.id.EntityID<*> -> (rawId.value as Number).toInt()
                                                is Number -> rawId.toInt()
                                                else -> rawId.toString().toInt()
                                            }
                                            outgoingDto.copy(id = generatedId)
                                        }

                                        if (savedMessageDto != null) {
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

                                            // â”€â”€ Notification: DM received â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
                                        }
                                    } else if (dmDto.action == "UPDATE_MESSAGE" || dmDto.action == "DELETE_MESSAGE") {
                                        val isDelete = dmDto.action == "DELETE_MESSAGE"
                                        val messageId = dmDto.id
                                        // Only the original sender may edit/delete, and the fan-out target is always the stored
                                        // recipient â€” never the client-supplied receiverId, which could point at anyone.
                                        val isValidRequest = messageId != null && messageId != 0 &&
                                            (isDelete || dmDto.content.length <= DmRules.MAX_CONTENT_LENGTH)
                                        val targetReceiverId: Int? = if (!isValidRequest) null else dbQuery {
                                            val existing = DirectMessagesTable.selectAll().where { DirectMessagesTable.id eq messageId }.singleOrNull()
                                                ?: return@dbQuery null
                                            if (existing[DirectMessagesTable.senderId] != userIdParam.toInt()) return@dbQuery null
                                            if (isDelete) {
                                                // Tombstone instead of a hard delete so the other participant's
                                                // offline devices still learn about it via /api/dm/sync.
                                                DirectMessagesTable.update({ DirectMessagesTable.id eq messageId }) {
                                                    it[DirectMessagesTable.isDeleted] = true
                                                    it[content] = ""
                                                    it[mediaUrl] = null
                                                }
                                                DmReactionsTable.deleteWhere { DmReactionsTable.messageId eq messageId }
                                            } else {
                                                if (existing[DirectMessagesTable.isDeleted]) return@dbQuery null
                                                DirectMessagesTable.update({ DirectMessagesTable.id eq messageId }) {
                                                    it[content] = dmDto.content
                                                }
                                            }
                                            existing[DirectMessagesTable.receiverId]
                                        }
                                        if (targetReceiverId != null) {
                                            val payload = dmDto.copy(senderId = userIdParam.toInt(), receiverId = targetReceiverId)
                                            val payloadJson = Json.encodeToString(DmDto.serializer(), payload)
                                            sendToUser(targetReceiverId.toLong(), payloadJson)
                                            if (this.isActive) {
                                                this.send(Frame.Text(payloadJson))
                                            }
                                        }
                                    } else if (dmDto.action == "CHANNEL_TYPING_START" || dmDto.action == "CHANNEL_TYPING_STOP") {
                                        val typingChannelId = dmDto.channelId
                                        if (typingChannelId != null && typingChannelId > 0) {
                                            val recipients = dbQuery {
                                                if (!isMember(userIdParam.toInt(), dmDto.workspaceId)) return@dbQuery emptyList()
                                                if (cachedUsername == null) {
                                                    cachedUsername = UsersTable.selectAll()
                                                        .where { UsersTable.id eq userIdParam.toInt() }
                                                        .singleOrNull()?.get(UsersTable.username)
                                                }
                                                workspaceMemberIds(dmDto.workspaceId).filter { it != userIdParam.toInt() }
                                            }
                                            val typingJson = Json.encodeToString(
                                                ChannelTypingEvent(
                                                    workspaceId = dmDto.workspaceId,
                                                    channelId = typingChannelId,
                                                    userId = userIdParam.toInt(),
                                                    userName = cachedUsername ?: "Someone",
                                                    isTyping = dmDto.action == "CHANNEL_TYPING_START"
                                                )
                                            )
                                            recipients.forEach { sendToChannelCapableUser(it.toLong(), typingJson) }
                                        }
                                    } else if (dmDto.action == "TYPING_START" || dmDto.action == "TYPING_STOP") {
                                        // Forward typing status in real time to the intended chat partner
                                        val typingPayload = dmDto.copy(senderId = userIdParam.toInt())
                                        val typingJson = Json.encodeToString(DmDto.serializer(), typingPayload)
                                        sendToUser(dmDto.receiverId.toLong(), typingJson)
                                    } else if (dmDto.action == "REACT_MESSAGE" || dmDto.action == "UNREACT_MESSAGE") {
                                        val messageId = dmDto.id ?: 0
                                        val emoji = dmDto.emoji ?: ""
                                        if (messageId != 0 && emoji.isNotBlank()) {
                                            val reactionOutcome = dbQuery {
                                                val existing = DirectMessagesTable.selectAll()
                                                    .where { (DirectMessagesTable.id eq messageId) and (DirectMessagesTable.isDeleted eq false) }
                                                    .singleOrNull()
                                                    ?: return@dbQuery null
                                                // Only the two people in the conversation may react to (or see counts for) it.
                                                val computedTarget = DmRules.partnerOf(
                                                    senderId = existing[DirectMessagesTable.senderId],
                                                    receiverId = existing[DirectMessagesTable.receiverId],
                                                    actingUserId = userIdParam.toInt()
                                                ) ?: return@dbQuery null
                                                if (dmDto.action == "REACT_MESSAGE") {
                                                    DmReactionsTable.insertIgnore {
                                                        it[DmReactionsTable.messageId] = messageId
                                                        it[DmReactionsTable.userId] = userIdParam.toInt()
                                                        it[DmReactionsTable.emoji] = emoji
                                                    }
                                                } else {
                                                    DmReactionsTable.deleteWhere {
                                                        (DmReactionsTable.messageId eq messageId) and
                                                        (DmReactionsTable.userId eq userIdParam.toInt()) and
                                                        (DmReactionsTable.emoji eq emoji)
                                                    }
                                                }
                                                // Build aggregated counts
                                                val counts = DmReactionsTable.selectAll()
                                                    .where { DmReactionsTable.messageId eq messageId }
                                                    .groupBy { it[DmReactionsTable.emoji] }
                                                    .mapValues { (_, rows) -> rows.size }
                                                Pair(computedTarget, counts)
                                            }
                                            if (reactionOutcome != null) {
                                                val (targetReceiverId, aggregated) = reactionOutcome
                                                val reactPayload = dmDto.copy(
                                                    senderId = userIdParam.toInt(),
                                                    receiverId = targetReceiverId,
                                                    reactions = aggregated
                                                )
                                                val reactJson = Json.encodeToString(DmDto.serializer(), reactPayload)
                                                sendToUser(targetReceiverId.toLong(), reactJson)
                                                if (this.isActive) this.send(Frame.Text(reactJson))
                                            }
                                        }
                                    } else if (dmDto.action == "MARK_READ") {
                                        val wsId = dmDto.workspaceId
                                        val readerId = userIdParam.toInt()   // the one who just read
                                        val senderId = dmDto.receiverId       // the original sender
                                        dbQuery {
                                            DirectMessagesTable.update({
                                                (DirectMessagesTable.workspaceId eq wsId) and
                                                (DirectMessagesTable.senderId eq senderId) and
                                                (DirectMessagesTable.receiverId eq readerId) and
                                                (DirectMessagesTable.isRead eq false)
                                            }) {
                                                it[DirectMessagesTable.isRead] = true
                                            }
                                        }
                                        // Notify the original sender their messages were read
                                        val receiptPayload = DmDto(
                                            action = "READ_RECEIPT",
                                            workspaceId = wsId,
                                            senderId = readerId,
                                            receiverId = senderId
                                        )
                                        val receiptJson = Json.encodeToString(DmDto.serializer(), receiptPayload)
                                        sendToUser(senderId.toLong(), receiptJson)
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        WebSocketBroker.removeSession(userIdParam, this)
                        // No separate channelCapableSessions.remove needed â€” removeDmSession delegates to WebSocketBroker.removeSession which handles it

                        // If no other live sessions remain for this user, broadcast USER_OFFLINE to teammates
                        val hasRemainingSessions = WebSocketBroker.isUserConnected(userIdParam)
                        if (!hasRemainingSessions) {
                            try {
                                val offlineDto = DmDto(action = "USER_OFFLINE", senderId = userIdParam.toInt())
                                val offlineJson = Json.encodeToString(DmDto.serializer(), offlineDto)
                                teammateIds.forEach { sendToUser(it.toLong(), offlineJson) }
                            } catch (_: Exception) {}
                        }

                        // Mark last seen on WS disconnect
                        try {
                            dbQuery {
                                UsersTable.update({ UsersTable.id eq userIdParam.toInt() }) {
                                    it[UsersTable.lastSeen] = System.currentTimeMillis()
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore concurrent update exception
                        }
                    }
                }
            }
        }
    }
}
