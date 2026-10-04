package plugins

import com.collabsphere.dto.*
import dto.*
import com.collabsphere.model.*
import com.collabsphere.util.CloudinaryService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("MessagesRoutes")

internal fun Route.messagesRoutes() {
    route("/api/message") {
        post {
            try {
                val actingUserId = call.authenticatedUserId()
                val request = call.receive<MessageRequest>()
                val newMessage = dbQuery {
                    if (!isMember(actingUserId, request.workspaceId)) {
                        return@dbQuery null
                    }
                    val validReplyToId = request.replyToId?.takeIf { targetId ->
                        MessageTable.selectAll().where {
                            (MessageTable.id eq targetId) and
                                    (MessageTable.workspaceId eq request.workspaceId) and
                                    (MessageTable.channelId eq request.channelId)
                        }.count() > 0
                    }
                    val validMediaUrl = request.mediaUrl?.takeIf { CloudinaryService.isCloudinaryUrl(it) }
                    val insertedId = MessageTable.insert {
                        it[MessageTable.userId] = actingUserId
                        it[MessageTable.workspaceId] = request.workspaceId
                        it[MessageTable.channelId] = request.channelId
                        it[MessageTable.userName] = request.userName
                        it[MessageTable.content] = request.content
                        it[MessageTable.replyToId] = validReplyToId
                        it[MessageTable.mediaUrl] = validMediaUrl
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
                        status = request.status,
                        replyToId = validReplyToId,
                        mediaUrl = validMediaUrl
                    )
                }
                if (newMessage == null) {
                    call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                } else {
                    call.respond(HttpStatusCode.Created, newMessage)
                    broadcastChannelMessageChange(newMessage.id)
                    // ── Notification hooks ────────────────────────────────────────────
                    val senderRow = dbQuery {
                        UsersTable.selectAll().where { UsersTable.id eq actingUserId }.singleOrNull()
                    }
                    val senderName = senderRow?.get(UsersTable.username) ?: "Someone"

                    val notificationBody = request.content.ifBlank { if (newMessage.mediaUrl != null) "📷 Photo" else "" }.take(200)
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

                    val mutedMemberIds = dbQuery {
                        NotificationMutesTable
                            .select(NotificationMutesTable.userId)
                            .where {
                                (NotificationMutesTable.workspaceId eq request.workspaceId) and
                                        ((NotificationMutesTable.channelId eq 0) or (NotificationMutesTable.channelId eq request.channelId))
                            }
                            .map { it[NotificationMutesTable.userId] }
                            .toSet()
                    }

                    channelMembers.forEach { (memberId, memberUsername) ->
                        val isMentioned = memberUsername in mentionedUsernames
                        if (!isMentioned && memberId in mutedMemberIds) return@forEach
                        if (isMentioned) {
                            createAndPushNotification(
                                recipientId = memberId,
                                actorId = actingUserId,
                                type = "MENTION",
                                title = "$senderName mentioned you",
                                body = notificationBody,
                                workspaceId = request.workspaceId,
                                referenceId = newMessage.id
                            )
                        } else {
                            createAndPushNotification(
                                recipientId = memberId,
                                actorId = actingUserId,
                                type = "CHANNEL_MESSAGE",
                                title = "New message from $senderName",
                                body = notificationBody,
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
                            status = it[MessageTable.status],
                            replyToId = it[MessageTable.replyToId],
                            mediaUrl = it[MessageTable.mediaUrl],
                            pinnedAt = it[MessageTable.pinnedAt]
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
                    if (!isMember(actingUserId, existing[MessageTable.workspaceId])) {
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
                    updateResult > 0 -> {
                        call.respond(
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
                        broadcastChannelMessageChange(messageIdParam)
                    }
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
                    broadcastChannelMessageChange(messageIdParam)
                } else {
                    call.respond(HttpStatusCode.NotFound, false)
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, false)
            }
        }

        post("/{messageId}/pin") {
            try {
                val messageIdParam = call.parameters["messageId"]?.toIntOrNull()
                if (messageIdParam == null) {
                    call.respond(HttpStatusCode.BadRequest, false)
                    return@post
                }
                val actingUserId = call.authenticatedUserId()
                val request = call.receive<PinRequest>()
                val updated = dbQuery {
                    val message = MessageTable.selectAll()
                        .where { (MessageTable.id eq messageIdParam) and (MessageTable.isDeleted eq false) }
                        .singleOrNull() ?: return@dbQuery -1
                    if (!isMember(actingUserId, message[MessageTable.workspaceId])) {
                        return@dbQuery -2
                    }
                    val now = System.currentTimeMillis()
                    MessageTable.update({ MessageTable.id eq messageIdParam }) {
                        it[MessageTable.pinnedAt] = if (request.pinned) now else null
                        it[MessageTable.pinnedByUserId] = if (request.pinned) actingUserId else null
                        it[MessageTable.updatedAt] = now
                    }
                }
                when {
                    updated == -2 -> call.respond(HttpStatusCode.Forbidden, false)
                    updated > 0 -> {
                        call.respond(HttpStatusCode.OK, true)
                        broadcastChannelMessageChange(messageIdParam)
                    }
                    else -> call.respond(HttpStatusCode.NotFound, false)
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, false)
            }
        }

        post("/read/{workspaceId}/{channelId}") {
            try {
                val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                val channelIdParam = call.parameters["channelId"]?.toIntOrNull()
                if (workspaceIdParam == null || channelIdParam == null) {
                    call.respond(HttpStatusCode.BadRequest, false)
                    return@post
                }
                val actingUserId = call.authenticatedUserId()
                val request = call.receive<ChannelReadRequest>()

                val event = dbQuery {
                    if (!isMember(actingUserId, workspaceIdParam)) return@dbQuery null
                    val messageExists = MessageTable.selectAll().where {
                        (MessageTable.id eq request.lastReadMessageId) and
                                (MessageTable.workspaceId eq workspaceIdParam) and
                                (MessageTable.channelId eq channelIdParam)
                    }.count() > 0
                    if (!messageExists) return@dbQuery null
                    val existing = ChannelReadStateTable.selectAll().where {
                        (ChannelReadStateTable.userId eq actingUserId) and (ChannelReadStateTable.channelId eq channelIdParam)
                    }.singleOrNull()
                    val previous = existing?.get(ChannelReadStateTable.lastReadMessageId) ?: 0
                    if (request.lastReadMessageId <= previous) return@dbQuery null
                    val now = System.currentTimeMillis()
                    if (existing == null) {
                        ChannelReadStateTable.insert {
                            it[ChannelReadStateTable.userId] = actingUserId
                            it[ChannelReadStateTable.channelId] = channelIdParam
                            it[ChannelReadStateTable.lastReadMessageId] = request.lastReadMessageId
                            it[ChannelReadStateTable.updatedAt] = now
                        }
                    } else {
                        ChannelReadStateTable.update({
                            (ChannelReadStateTable.userId eq actingUserId) and (ChannelReadStateTable.channelId eq channelIdParam)
                        }) {
                            it[ChannelReadStateTable.lastReadMessageId] = request.lastReadMessageId
                            it[ChannelReadStateTable.updatedAt] = now
                        }
                    }
                    val userName = UsersTable.selectAll().where { UsersTable.id eq actingUserId }
                        .singleOrNull()?.get(UsersTable.username) ?: "Someone"
                    ChannelReadState(
                        workspaceId = workspaceIdParam,
                        channelId = channelIdParam,
                        userId = actingUserId,
                        userName = userName,
                        lastReadMessageId = request.lastReadMessageId
                    ) to workspaceMemberIds(workspaceIdParam)
                }

                call.respond(HttpStatusCode.OK, true)
                if (event != null) {
                    val json = Json.encodeToString(event.first)
                    event.second.forEach { sendToChannelCapableUser(it.toLong(), json) }
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, false)
            }
        }

        get("/read/{workspaceId}/{channelId}") {
            try {
                val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                val channelIdParam = call.parameters["channelId"]?.toIntOrNull()
                if (workspaceIdParam == null || channelIdParam == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing or invalid workspaceId or channelId")
                    return@get
                }
                val actingUserId = call.authenticatedUserId()
                val states = dbQuery {
                    if (!isMember(actingUserId, workspaceIdParam)) return@dbQuery null
                    (ChannelReadStateTable innerJoin UsersTable)
                        .selectAll()
                        .where { ChannelReadStateTable.channelId eq channelIdParam }
                        .map {
                            ChannelReadState(
                                workspaceId = workspaceIdParam,
                                channelId = channelIdParam,
                                userId = it[ChannelReadStateTable.userId],
                                userName = it[UsersTable.username],
                                lastReadMessageId = it[ChannelReadStateTable.lastReadMessageId]
                            )
                        }
                }
                if (states == null) {
                    call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                } else {
                    call.respond(HttpStatusCode.OK, states)
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Read state error")
            }
        }

        get("/pinned/{workspaceId}/{channelId}") {
            try {
                val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                val channelIdParam = call.parameters["channelId"]?.toIntOrNull()
                if (workspaceIdParam == null || channelIdParam == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing or invalid workspaceId or channelId")
                    return@get
                }
                val actingUserId = call.authenticatedUserId()
                val pinned = dbQuery {
                    if (!isMember(actingUserId, workspaceIdParam)) {
                        return@dbQuery null
                    }
                    MessageTable.selectAll()
                        .where {
                            (MessageTable.workspaceId eq workspaceIdParam) and
                                    (MessageTable.channelId eq channelIdParam) and
                                    (MessageTable.isDeleted eq false) and
                                    MessageTable.pinnedAt.isNotNull()
                        }
                        .orderBy(MessageTable.pinnedAt, SortOrder.DESC)
                        .limit(MAX_HISTORY_PAGE)
                        .map { it.toMessageSyncResponse() }
                }
                if (pinned == null) {
                    call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                } else {
                    call.respond(HttpStatusCode.OK, pinned)
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Pinned Error")
            }
        }

        post("/{messageId}/reactions") {
            try {
                val messageIdParam = call.parameters["messageId"]?.toIntOrNull()
                if (messageIdParam == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid messageId")
                    return@post
                }
                val actingUserId = call.authenticatedUserId()
                val request = call.receive<ChannelReactionRequest>()
                val emoji = request.emoji.trim()
                if (emoji.isEmpty() || emoji.length > 16) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid emoji")
                    return@post
                }

                val summary = dbQuery {
                    val message = MessageTable.selectAll()
                        .where { (MessageTable.id eq messageIdParam) and (MessageTable.isDeleted eq false) }
                        .singleOrNull() ?: return@dbQuery null
                    val workspaceId = message[MessageTable.workspaceId]
                    if (!isMember(actingUserId, workspaceId)) {
                        return@dbQuery null
                    }
                    if (request.add) {
                        ChannelReactionsTable.insertIgnore {
                            it[ChannelReactionsTable.messageId] = messageIdParam
                            it[ChannelReactionsTable.userId] = actingUserId
                            it[ChannelReactionsTable.emoji] = emoji
                        }
                    } else {
                        ChannelReactionsTable.deleteWhere {
                            (ChannelReactionsTable.messageId eq messageIdParam) and
                                    (ChannelReactionsTable.userId eq actingUserId) and
                                    (ChannelReactionsTable.emoji eq emoji)
                        }
                    }
                    channelReactionSummary(messageIdParam, message[MessageTable.channelId], workspaceId) to
                            workspaceMemberIds(workspaceId)
                }

                if (summary == null) {
                    call.respond(HttpStatusCode.NotFound, "Message not found")
                    return@post
                }
                call.respond(HttpStatusCode.OK, summary.first)
                if (summary.first.workspaceId > 0) {
                    val json = Json.encodeToString(summary.first)
                    summary.second.forEach { sendToChannelCapableUser(it.toLong(), json) }
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Reaction failed")
            }
        }

        get("/reactions/{workspaceId}/{channelId}") {
            try {
                val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                val channelIdParam = call.parameters["channelId"]?.toIntOrNull()
                if (workspaceIdParam == null || channelIdParam == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing or invalid workspaceId or channelId")
                    return@get
                }
                val fromId = call.request.queryParameters["fromId"]?.toIntOrNull() ?: 0
                val actingUserId = call.authenticatedUserId()

                val summaries = dbQuery {
                    if (!isMember(actingUserId, workspaceIdParam)) {
                        return@dbQuery null
                    }
                    (ChannelReactionsTable innerJoin MessageTable)
                        .selectAll()
                        .where {
                            (MessageTable.workspaceId eq workspaceIdParam) and
                                    (MessageTable.channelId eq channelIdParam) and
                                    (MessageTable.isDeleted eq false) and
                                    (MessageTable.id greaterEq fromId)
                        }
                        .groupBy { it[ChannelReactionsTable.messageId] }
                        .map { (messageId, rows) ->
                            ChannelReactionSummary(
                                messageId = messageId,
                                channelId = channelIdParam,
                                workspaceId = workspaceIdParam,
                                reactors = rows.groupBy({ it[ChannelReactionsTable.emoji] }, { it[ChannelReactionsTable.userId] })
                            )
                        }
                }
                if (summaries == null) {
                    call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                } else {
                    call.respond(HttpStatusCode.OK, summaries)
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Reactions Error")
            }
        }

        get("/history/{workspaceId}/{channelId}") {
            try {
                val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                val channelIdParam = call.parameters["channelId"]?.toIntOrNull()
                if (workspaceIdParam == null || channelIdParam == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing or invalid workspaceId or channelId")
                    return@get
                }
                val beforeId = call.request.queryParameters["before"]?.toIntOrNull()
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, MAX_HISTORY_PAGE) ?: DEFAULT_HISTORY_PAGE
                val actingUserId = call.authenticatedUserId()

                val result = dbQuery {
                    if (!isMember(actingUserId, workspaceIdParam)) {
                        return@dbQuery null
                    }
                    // Read under the same snapshot as the page, so a client loading its first
                    // page can start cursor sync from here without missing anything in between.
                    val snapshot = currentSyncSnapshot()
                    val rows = MessageTable.selectAll()
                        .where {
                            var condition = (MessageTable.workspaceId eq workspaceIdParam) and
                                    (MessageTable.channelId eq channelIdParam) and
                                    (MessageTable.isDeleted eq false)
                            if (beforeId != null) {
                                condition = condition and (MessageTable.id less beforeId)
                            }
                            condition
                        }
                        .orderBy(MessageTable.id, SortOrder.DESC)
                        .limit(limit)
                        .map { it.toMessageSyncResponse() }
                    rows to snapshot.xmin
                }
                if (result == null) {
                    call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                } else {
                    call.appendSyncHeaders(result.second)
                    call.respond(HttpStatusCode.OK, result.first)
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "History Error")
            }
        }

        get("/sync/{workspaceId}/{channelId}") {
            try {
                val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                val channelIdParam = call.parameters["channelId"]?.toIntOrNull()
                val actingUserId = call.authenticatedUserId()

                if (workspaceIdParam == null || channelIdParam == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing structural parameters")
                    return@get
                }

                val page = dbQuery {
                    if (!isMember(actingUserId, workspaceIdParam)) {
                        return@dbQuery null
                    }
                    messageDeltaSync(workspaceIdParam, channelIdParam, call.syncRequest())
                }
                if (page == null) {
                    call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                } else {
                    call.appendSyncHeaders(page.nextCursor, page.reset)
                    call.respond(HttpStatusCode.OK, page.rows)
                }
            } catch (e: Exception) {
                logger.error("[Sync] Message sync failed", e)
                call.respond(HttpStatusCode.InternalServerError, "Sync Error")
            }
        }
    }
}
