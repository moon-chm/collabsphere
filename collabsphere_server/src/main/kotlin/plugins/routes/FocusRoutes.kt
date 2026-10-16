package plugins

import com.collabsphere.dto.FocusStatus
import com.collabsphere.dto.HoldMessageRequest
import com.collabsphere.dto.SetFocusRequest
import com.collabsphere.model.ScheduledMessagesTable
import com.collabsphere.model.UserBlocksTable
import com.collabsphere.model.UsersTable
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Focus")

private sealed interface HoldOutcome {
    data class Held(val item: com.collabsphere.dto.ScheduledMessageItem) : HoldOutcome
    data object NotFocusing : HoldOutcome
    data object Forbidden : HoldOutcome
}

private fun Transaction.storedFocusUntil(userId: Int): Long? =
    UsersTable.select(UsersTable.focusUntil).where { UsersTable.id eq userId }.singleOrNull()?.get(UsersTable.focusUntil)

internal fun Route.focusRoutes() {
    route("/api/focus") {
        get("/me") {
            val userId = call.authenticatedUserId()
            try {
                val stored = dbReadQuery { storedFocusUntil(userId) }
                call.respond(HttpStatusCode.OK, FocusStatus(userId, FocusRules.activeUntil(stored, System.currentTimeMillis())))
            } catch (e: Exception) {
                val failure = classifyFailure(e, logger, "Focus read")
                call.respond(failure.status, failure.message)
            }
        }

        put {
            val userId = call.authenticatedUserId()
            try {
                val request = call.receive<SetFocusRequest>()
                val now = System.currentTimeMillis()
                FocusRules.untilRejection(request.until, now)?.let {
                    return@put call.respond(HttpStatusCode.BadRequest, it)
                }
                val releaseAt = FocusRules.releaseAt(request.until, now)
                dbQuery {
                    UsersTable.update({ UsersTable.id eq userId }) { it[focusUntil] = request.until }
                    ScheduledMessagesTable.update({
                        (ScheduledMessagesTable.receiverId eq userId) and
                            (ScheduledMessagesTable.kind eq SAVED_KIND_DM) and
                            (ScheduledMessagesTable.status eq SCHEDULE_PENDING) and
                            (ScheduledMessagesTable.heldForFocus eq true)
                    }) { it[sendAt] = releaseAt }
                }
                if (releaseAt <= now) {
                    runCatching { dispatchDueScheduledMessages(now) }
                        .onFailure { logger.error("[Focus] Releasing held messages failed; the dispatcher will retry", it) }
                }
                call.respond(HttpStatusCode.OK, FocusStatus(userId, FocusRules.activeUntil(request.until, now)))
            } catch (e: Exception) {
                val failure = classifyFailure(e, logger, "Focus update")
                call.respond(failure.status, failure.message)
            }
        }

        get("/{userId}") {
            val me = call.authenticatedUserId()
            val target = call.parameters["userId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid user")
            val workspaceId = call.request.queryParameters["workspaceId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "workspaceId is required")
            try {
                val allowed = isMember(me, workspaceId) && isMember(target, workspaceId)
                if (!allowed) return@get call.respond(HttpStatusCode.Forbidden, "Not a teammate")
                val stored = dbReadQuery { storedFocusUntil(target) }
                call.respond(HttpStatusCode.OK, FocusStatus(target, FocusRules.activeUntil(stored, System.currentTimeMillis())))
            } catch (e: Exception) {
                val failure = classifyFailure(e, logger, "Focus lookup")
                call.respond(failure.status, failure.message)
            }
        }

        post("/hold") {
            val userId = call.authenticatedUserId()
            try {
                val request = call.receive<HoldMessageRequest>()
                val content = request.content.trim()
                FocusRules.contentRejection(content)?.let {
                    return@post call.respond(HttpStatusCode.BadRequest, it)
                }
                val now = System.currentTimeMillis()
                val outcome = dbQuery<HoldOutcome> {
                    val receiverId = request.receiverId
                    if (receiverId == userId || !isMember(userId, request.workspaceId) || !isMember(receiverId, request.workspaceId)) {
                        return@dbQuery HoldOutcome.Forbidden
                    }
                    val blocked = UserBlocksTable.selectAll().where {
                        ((UserBlocksTable.blockerId eq userId) and (UserBlocksTable.blockedId eq receiverId)) or
                            ((UserBlocksTable.blockerId eq receiverId) and (UserBlocksTable.blockedId eq userId))
                    }.count() > 0
                    if (blocked) return@dbQuery HoldOutcome.Forbidden
                    val sendAt = FocusRules.holdSendAt(storedFocusUntil(receiverId), now)
                        ?: return@dbQuery HoldOutcome.NotFocusing
                    val id = ScheduledMessagesTable.insert {
                        it[ScheduledMessagesTable.userId] = userId
                        it[kind] = SAVED_KIND_DM
                        it[workspaceId] = request.workspaceId
                        it[channelId] = null
                        it[ScheduledMessagesTable.receiverId] = receiverId
                        it[ScheduledMessagesTable.content] = content
                        it[ScheduledMessagesTable.sendAt] = sendAt
                        it[createdAt] = now
                        it[status] = SCHEDULE_PENDING
                        it[heldForFocus] = true
                    }[ScheduledMessagesTable.id]
                    HoldOutcome.Held(ScheduledMessagesTable.selectAll().where { ScheduledMessagesTable.id eq id }.single().toScheduledItem())
                }
                when (outcome) {
                    is HoldOutcome.Held -> call.respond(HttpStatusCode.Created, outcome.item)
                    HoldOutcome.NotFocusing -> call.respond(HttpStatusCode.Conflict, "They're not focusing any more")
                    HoldOutcome.Forbidden -> call.respond(HttpStatusCode.Forbidden, "You can't message them")
                }
            } catch (e: Exception) {
                val failure = classifyFailure(e, logger, "Focus hold")
                call.respond(failure.status, failure.message)
            }
        }
    }
}
