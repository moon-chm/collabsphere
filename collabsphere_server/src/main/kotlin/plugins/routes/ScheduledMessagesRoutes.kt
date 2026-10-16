package plugins

import dto.DmDto
import com.collabsphere.util.ExternalProviderPolicy
import com.collabsphere.dto.MessageResponse
import com.collabsphere.dto.ScheduleMessageRequest
import com.collabsphere.dto.ScheduledMessageItem
import com.collabsphere.model.ChannelsTable
import com.collabsphere.model.DirectMessagesTable
import com.collabsphere.model.MessageTable
import com.collabsphere.model.ScheduledMessagesTable
import com.collabsphere.model.UserBlocksTable
import com.collabsphere.model.UsersTable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.vendors.ForUpdateOption
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ScheduledMessages")

internal const val SCHEDULE_PENDING = "PENDING"
internal const val SCHEDULE_SENT = "SENT"
internal const val SCHEDULE_FAILED = "FAILED"
internal const val SCHEDULE_CANCELLED = "CANCELLED"
private const val MAX_SCHEDULE_AHEAD_MS = 365L * 24 * 60 * 60 * 1000
private const val SCHEDULE_DISPATCH_INTERVAL_MS = 15_000L
private const val SCHEDULE_BATCH = 50

internal fun Route.scheduledMessagesRoutes() {
    route("/api/scheduled") {
        post {
            val userId = call.authenticatedUserId()
            try {
                val request = call.receive<ScheduleMessageRequest>()
                val content = request.content.trim()
                val now = System.currentTimeMillis()
                when {
                    content.isEmpty() -> return@post call.respond(HttpStatusCode.BadRequest, "Message cannot be empty")
                    content.length > 4000 -> return@post call.respond(HttpStatusCode.BadRequest, "Message too long")
                    request.sendAt < now + 30_000L -> return@post call.respond(HttpStatusCode.BadRequest, "Pick a time at least a minute from now")
                    request.sendAt > now + MAX_SCHEDULE_AHEAD_MS -> return@post call.respond(HttpStatusCode.BadRequest, "Pick a time within a year")
                }
                val created = dbQuery {
                    if (!isMember(userId, request.workspaceId)) return@dbQuery null
                    when (request.kind) {
                        SAVED_KIND_CHANNEL -> {
                            val channelId = request.channelId ?: return@dbQuery null
                            ChannelsTable.selectAll().where {
                                (ChannelsTable.id eq channelId) and (ChannelsTable.workspaceId eq request.workspaceId) and (ChannelsTable.isDeleted eq false)
                            }.singleOrNull() ?: return@dbQuery null
                        }
                        SAVED_KIND_DM -> {
                            val receiverId = request.receiverId ?: return@dbQuery null
                            if (receiverId == userId || !isMember(receiverId, request.workspaceId)) return@dbQuery null
                        }
                        else -> return@dbQuery null
                    }
                    val id = ScheduledMessagesTable.insert {
                        it[ScheduledMessagesTable.userId] = userId
                        it[kind] = request.kind
                        it[workspaceId] = request.workspaceId
                        it[channelId] = if (request.kind == SAVED_KIND_CHANNEL) request.channelId else null
                        it[receiverId] = if (request.kind == SAVED_KIND_DM) request.receiverId else null
                        it[ScheduledMessagesTable.content] = content
                        it[sendAt] = request.sendAt
                        it[createdAt] = now
                        it[status] = SCHEDULE_PENDING
                    }[ScheduledMessagesTable.id]
                    ScheduledMessagesTable.selectAll().where { ScheduledMessagesTable.id eq id }.single().toScheduledItem()
                }
                if (created == null) call.respond(HttpStatusCode.Forbidden, "You can't message there")
                else call.respond(HttpStatusCode.Created, created)
            } catch (e: Exception) {
                val failure = classifyFailure(e, logger, "Schedule message")
                call.respond(failure.status, failure.message)
            }
        }

        get {
            val userId = call.authenticatedUserId()
            val workspaceId = call.request.queryParameters["workspaceId"]?.toIntOrNull()
            val channelId = call.request.queryParameters["channelId"]?.toIntOrNull()
            val receiverId = call.request.queryParameters["receiverId"]?.toIntOrNull()
            try {
                val items = dbReadQuery {
                    ScheduledMessagesTable.selectAll()
                        .where {
                            var condition = (ScheduledMessagesTable.userId eq userId) and (ScheduledMessagesTable.status eq SCHEDULE_PENDING)
                            if (workspaceId != null) condition = condition and (ScheduledMessagesTable.workspaceId eq workspaceId)
                            if (channelId != null) condition = condition and (ScheduledMessagesTable.channelId eq channelId)
                            if (receiverId != null) condition = condition and (ScheduledMessagesTable.receiverId eq receiverId)
                            condition
                        }
                        .orderBy(ScheduledMessagesTable.sendAt, SortOrder.ASC)
                        .map { it.toScheduledItem() }
                }
                call.respond(HttpStatusCode.OK, items)
            } catch (e: Exception) {
                val failure = classifyFailure(e, logger, "Scheduled list")
                call.respond(failure.status, failure.message)
            }
        }

        delete("/{id}") {
            val userId = call.authenticatedUserId()
            val id = call.parameters["id"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest, false)
            try {
                val updated = dbQuery {
                    ScheduledMessagesTable.update({
                        (ScheduledMessagesTable.id eq id) and
                            (ScheduledMessagesTable.userId eq userId) and
                            (ScheduledMessagesTable.status eq SCHEDULE_PENDING)
                    }) { it[status] = SCHEDULE_CANCELLED }
                }
                if (updated > 0) call.respond(HttpStatusCode.OK, true) else call.respond(HttpStatusCode.NotFound, false)
            } catch (e: Exception) {
                val failure = classifyFailure(e, logger, "Scheduled cancel")
                call.respond(failure.status, failure.message)
            }
        }
    }
}

internal fun ResultRow.toScheduledItem() = ScheduledMessageItem(
    id = this[ScheduledMessagesTable.id],
    kind = this[ScheduledMessagesTable.kind],
    workspaceId = this[ScheduledMessagesTable.workspaceId],
    channelId = this[ScheduledMessagesTable.channelId],
    receiverId = this[ScheduledMessagesTable.receiverId],
    content = this[ScheduledMessagesTable.content],
    sendAt = this[ScheduledMessagesTable.sendAt],
    status = this[ScheduledMessagesTable.status]
)

fun Application.startScheduledMessageDispatcher() {
    if (ExternalProviderPolicy.areDisabled()) return
    launch(Dispatchers.IO) {
        delay(10_000L)
        while (isActive) {
            try {
                dispatchDueScheduledMessages(System.currentTimeMillis())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.error("[Scheduled] Dispatch failed", e)
            }
            delay(SCHEDULE_DISPATCH_INTERVAL_MS)
        }
    }
}

private sealed interface Delivered {
    data class Channel(val authorId: Int, val workspaceId: Int, val channelId: Int, val content: String, val message: MessageResponse) : Delivered
    data class Dm(val dto: DmDto, val senderName: String) : Delivered
}

internal suspend fun dispatchDueScheduledMessages(now: Long): Int {
    val delivered = dbQuery {
        val due = ScheduledMessagesTable.selectAll()
            .where { (ScheduledMessagesTable.status eq SCHEDULE_PENDING) and (ScheduledMessagesTable.sendAt lessEq now) }
            .orderBy(ScheduledMessagesTable.sendAt, SortOrder.ASC)
            .limit(SCHEDULE_BATCH)
            .forUpdate(ForUpdateOption.PostgreSQL.ForUpdate(mode = ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED))
            .toList()
        due.mapNotNull { row ->
            val id = row[ScheduledMessagesTable.id]
            val result = runCatching { deliver(row, now) }.getOrNull()
            ScheduledMessagesTable.update({ ScheduledMessagesTable.id eq id }) {
                it[status] = if (result != null) SCHEDULE_SENT else SCHEDULE_FAILED
            }
            result
        }
    }
    delivered.forEach { item ->
        runCatching {
            when (item) {
                is Delivered.Channel -> {
                    broadcastChannelMessageChange(item.message.id)
                    notifyChannelMessage(item.authorId, item.workspaceId, item.channelId, item.content, item.message)
                }
                is Delivered.Dm -> {
                    val json = Json.encodeToString(DmDto.serializer(), item.dto)
                    sendToUser(item.dto.receiverId.toLong(), json)
                    sendToUser(item.dto.senderId.toLong(), json)
                    createAndPushNotification(
                        recipientId = item.dto.receiverId,
                        actorId = item.dto.senderId,
                        type = "DM",
                        title = "${item.senderName} sent you a message",
                        body = item.dto.content.take(200),
                        workspaceId = item.dto.workspaceId,
                        referenceId = item.dto.id
                    )
                }
            }
        }.onFailure { logger.error("[Scheduled] Post-send fan-out failed", it) }
    }
    return delivered.size
}

private fun org.jetbrains.exposed.sql.Transaction.deliver(row: ResultRow, now: Long): Delivered? {
    val userId = row[ScheduledMessagesTable.userId]
    val workspaceId = row[ScheduledMessagesTable.workspaceId]
    val content = row[ScheduledMessagesTable.content]
    if (!isMember(userId, workspaceId)) return null
    val authorName = UsersTable.select(UsersTable.username).where { UsersTable.id eq userId }
        .singleOrNull()?.get(UsersTable.username) ?: return null

    return when (row[ScheduledMessagesTable.kind]) {
        SAVED_KIND_CHANNEL -> {
            val channelId = row[ScheduledMessagesTable.channelId] ?: return null
            ChannelsTable.selectAll().where {
                (ChannelsTable.id eq channelId) and (ChannelsTable.workspaceId eq workspaceId) and (ChannelsTable.isDeleted eq false)
            }.singleOrNull() ?: return null
            val messageId = MessageTable.insert {
                it[MessageTable.userId] = userId
                it[MessageTable.workspaceId] = workspaceId
                it[MessageTable.channelId] = channelId
                it[userName] = authorName
                it[MessageTable.content] = content
                it[status] = "Delivered"
                it[isDeleted] = false
                it[updatedAt] = now
                it[createdAt] = now
            }[MessageTable.id]
            Delivered.Channel(
                authorId = userId,
                workspaceId = workspaceId,
                channelId = channelId,
                content = content,
                message = MessageResponse(
                    id = messageId,
                    userId = userId,
                    workspaceId = workspaceId,
                    channelId = channelId,
                    userName = authorName,
                    content = content,
                    status = "Delivered",
                    createdAt = now
                )
            )
        }
        SAVED_KIND_DM -> {
            val receiverId = row[ScheduledMessagesTable.receiverId] ?: return null
            lockUserPair(userId, receiverId)
            val blocked = UserBlocksTable.selectAll().where {
                ((UserBlocksTable.blockerId eq userId) and (UserBlocksTable.blockedId eq receiverId)) or
                    ((UserBlocksTable.blockerId eq receiverId) and (UserBlocksTable.blockedId eq userId))
            }.count() > 0
            if (DmRules.sendRejection(true, isMember(receiverId, workspaceId), blocked, content, false) != null) return null
            val dmId = DirectMessagesTable.insert {
                it[DirectMessagesTable.workspaceId] = workspaceId
                it[senderId] = userId
                it[DirectMessagesTable.receiverId] = receiverId
                it[DirectMessagesTable.content] = content
                it[timestamp] = now
            }[DirectMessagesTable.id]
            Delivered.Dm(
                dto = DmDto(
                    id = dmId,
                    action = "RECEIVE_MESSAGE",
                    workspaceId = workspaceId,
                    senderId = userId,
                    receiverId = receiverId,
                    content = content,
                    timestamp = now
                ),
                senderName = authorName
            )
        }
        else -> null
    }
}
