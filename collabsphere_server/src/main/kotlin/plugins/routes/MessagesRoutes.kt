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

private const val MAX_MESSAGE_LENGTH = 4000

private fun ResultRow.toMessageResponse() = MessageResponse(
    id = this[MessageTable.id],
    userId = this[MessageTable.userId],
    workspaceId = this[MessageTable.workspaceId],
    channelId = this[MessageTable.channelId],
    userName = this[MessageTable.userName],
    content = this[MessageTable.content],
    status = this[MessageTable.status],
    replyToId = this[MessageTable.replyToId],
    mediaUrl = this[MessageTable.mediaUrl],
    pinnedAt = this[MessageTable.pinnedAt],
    editedAt = this[MessageTable.editedAt],
    createdAt = this[MessageTable.createdAt] ?: this[MessageTable.updatedAt],
    threadRootId = this[MessageTable.threadRootId]
)

internal fun Route.messagesRoutes() {
    route("/api/message") {
        post {
            try {
                val actingUserId = call.authenticatedUserId()
                val request = call.receive<MessageRequest>()
                val createResult = dbQuery {
                    if (!isMember(actingUserId, request.workspaceId)) {
                        return@dbQuery null
                    }
                    val validMediaUrl = request.mediaUrl?.takeIf { CloudinaryService.isCloudinaryUrl(it) }
                    
                    if (request.content.isBlank() && validMediaUrl == null) {
                        throw RequestRejected(HttpStatusCode.BadRequest, "Message content cannot be blank")
                    }
                    if (request.content.length > MAX_MESSAGE_LENGTH) {
                        throw RequestRejected(HttpStatusCode.BadRequest, "Message content too long")
                    }
                    
                    val channel = ChannelsTable.selectAll().where {
                        (ChannelsTable.id eq request.channelId) and
                                (ChannelsTable.workspaceId eq request.workspaceId) and
                                (ChannelsTable.isDeleted eq false)
                    }.singleOrNull() ?: throw RequestRejected(HttpStatusCode.BadRequest, "Channel is missing or deleted")

                    val priorByKey = request.idempotencyKey?.let { key ->
                        MessageTable.selectAll().where { MessageTable.idempotencyKey eq key }.singleOrNull()
                    }
                    if (priorByKey != null) {
                        val sameOperation = priorByKey[MessageTable.userId] == actingUserId &&
                            priorByKey[MessageTable.workspaceId] == request.workspaceId &&
                            priorByKey[MessageTable.channelId] == request.channelId &&
                            priorByKey[MessageTable.content] == request.content &&
                            priorByKey[MessageTable.status] == request.status &&
                            priorByKey[MessageTable.mediaUrl] == validMediaUrl
                        if (!sameOperation) throw RequestRejected(HttpStatusCode.Conflict, "Idempotency key was already used for a different message")
                        val prior = priorByKey.toMessageResponse()
                        return@dbQuery prior to false
                    }
                    val validReplyToId = request.replyToId?.let { targetId ->
                        val targetExists = MessageTable.selectAll().where {
                            (MessageTable.id eq targetId) and
                                    (MessageTable.workspaceId eq request.workspaceId) and
                                    (MessageTable.channelId eq request.channelId) and
                                    (MessageTable.isDeleted eq false)
                        }.count() > 0L
                        if (!targetExists) throw RequestRejected(HttpStatusCode.BadRequest, "Reply target is missing, deleted, or in another channel")
                        targetId
                    }
                    val validThreadRootId = request.threadRootId?.let { rootId ->
                        val root = MessageTable.selectAll().where {
                            (MessageTable.id eq rootId) and
                                    (MessageTable.workspaceId eq request.workspaceId) and
                                    (MessageTable.channelId eq request.channelId) and
                                    (MessageTable.isDeleted eq false)
                        }.singleOrNull() ?: throw RequestRejected(HttpStatusCode.BadRequest, "Thread is missing, deleted, or in another channel")
                        root[MessageTable.threadRootId] ?: rootId
                    }
                    // The author name is the account's, never the client-supplied one: otherwise anyone
                    // could post as "Workspace Admin".
                    val authorName = UsersTable.select(UsersTable.username).where { UsersTable.id eq actingUserId }
                        .singleOrNull()?.get(UsersTable.username) ?: request.userName
                    val insertResult = MessageTable.insertIgnore {
                        it[MessageTable.userId] = actingUserId
                        it[MessageTable.workspaceId] = request.workspaceId
                        it[MessageTable.channelId] = request.channelId
                        it[MessageTable.userName] = authorName
                        it[MessageTable.content] = request.content
                        it[MessageTable.replyToId] = validReplyToId
                        it[MessageTable.threadRootId] = validThreadRootId
                        it[MessageTable.mediaUrl] = validMediaUrl
                        it[MessageTable.status] = request.status
                        it[MessageTable.idempotencyKey] = request.idempotencyKey
                        it[MessageTable.isDeleted] = false
                        it[MessageTable.updatedAt] = System.currentTimeMillis()
                    }
                    val insertedId = insertResult.resultedValues?.singleOrNull()?.get(MessageTable.id)
                    if (insertedId == null) {
                        val rowAfterConflict = request.idempotencyKey?.let { key ->
                            MessageTable.selectAll().where { MessageTable.idempotencyKey eq key }.singleOrNull()
                        } ?: error("Message insert conflicted without an idempotency row")
                        if (rowAfterConflict[MessageTable.userId] != actingUserId ||
                            rowAfterConflict[MessageTable.workspaceId] != request.workspaceId ||
                            rowAfterConflict[MessageTable.channelId] != request.channelId ||
                            rowAfterConflict[MessageTable.content] != request.content ||
                            rowAfterConflict[MessageTable.status] != request.status ||
                            rowAfterConflict[MessageTable.mediaUrl] != validMediaUrl
                        ) throw RequestRejected(HttpStatusCode.Conflict, "Idempotency key was already used for a different message")
                        return@dbQuery rowAfterConflict.toMessageResponse() to false
                    }

                    val created = MessageResponse(
                        id = insertedId,
                        userId = actingUserId,
                        workspaceId = request.workspaceId,
                        channelId = request.channelId,
                        userName = authorName,
                        content = request.content,
                        status = request.status,
                        replyToId = validReplyToId,
                        mediaUrl = validMediaUrl,
                        createdAt = insertResult.resultedValues?.singleOrNull()?.get(MessageTable.createdAt),
                        threadRootId = validThreadRootId
                    )
                    created to true
                }
                if (createResult == null) {
                    call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                } else {
                    val (newMessage, wasInserted) = createResult
                    call.respond(HttpStatusCode.Created, newMessage)
                    if (wasInserted) broadcastChannelMessageChange(newMessage.id)
                    if (wasInserted) notifyChannelMessage(actingUserId, request.workspaceId, request.channelId, request.content, newMessage)
                }
            } catch (e: Exception) {
                // The 201 may already be out: what failed then was a follow-up notification.
                if (call.response.isCommitted) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    logger.error("[Messages] Post-create notification failed", e)
                } else {
                    val failure = classifyFailure(e, logger, "Message create")
                    call.respond(failure.status, failure.message)
                }
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
                            pinnedAt = it[MessageTable.pinnedAt],
                            editedAt = it[MessageTable.editedAt],
                            createdAt = it[MessageTable.createdAt] ?: it[MessageTable.updatedAt],
                            threadRootId = it[MessageTable.threadRootId]
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
                if (request.content.length > MAX_MESSAGE_LENGTH) {
                    return@put call.respond(HttpStatusCode.BadRequest, "Message content too long")
                }

                // -1 not found (or deleted), -2 not the sender, -3 would leave an empty message.
                val updateResult: Pair<Int, MessageResponse?> = dbQuery {
                    val existing = MessageTable.selectAll()
                        .where { (MessageTable.id eq messageIdParam) and (MessageTable.isDeleted eq false) }
                        .singleOrNull() ?: return@dbQuery -1 to null
                    if (existing[MessageTable.userId] != actingUserId) {
                        return@dbQuery -2 to null
                    }
                    if (!isMember(actingUserId, existing[MessageTable.workspaceId])) {
                        return@dbQuery -2 to null
                    }
                    if (request.content.isBlank() && existing[MessageTable.mediaUrl] == null) return@dbQuery -3 to null
                    val now = System.currentTimeMillis()
                    MessageTable.update({ MessageTable.id eq messageIdParam }) {
                        it[MessageTable.content] = request.content
                        it[MessageTable.status] = request.status
                        it[MessageTable.updatedAt] = now
                        // This endpoint also carries status-only updates; only a text change is an edit.
                        if (request.content != existing[MessageTable.content]) it[MessageTable.editedAt] = now
                    }
                    1 to MessageTable.selectAll().where { MessageTable.id eq messageIdParam }.single().toMessageResponse()
                }

                when (updateResult.first) {
                    -1 -> call.respond(HttpStatusCode.NotFound, "Message not found to update")
                    -2 -> call.respond(HttpStatusCode.Forbidden, "Only the sender can edit this message")
                    -3 -> call.respond(HttpStatusCode.BadRequest, "Message content cannot be blank")
                    else -> {
                        call.respond(HttpStatusCode.OK, updateResult.second!!)
                        broadcastChannelMessageChange(messageIdParam)
                    }
                }
            } catch (e: Exception) {
                val failure = classifyFailure(e, logger, "Message edit")
                call.respond(failure.status, failure.message)
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

                // -1 not found (or already deleted), -2 neither the author nor an owner/admin.
                val updatedRows = dbQuery {
                    val existing = MessageTable.selectAll().where {
                        (MessageTable.id eq messageIdParam) and
                                (MessageTable.workspaceId eq workspaceIdParam) and
                                (MessageTable.channelId eq channelIdParam) and
                                (MessageTable.isDeleted eq false)
                    }.singleOrNull() ?: return@dbQuery -1
                    // Same rule as notes and channels: the author, or a workspace owner/admin moderating.
                    val role = workspaceRole(actingUserId, workspaceIdParam) ?: return@dbQuery -2
                    if (existing[MessageTable.userId] != actingUserId && !WorkspaceRoles.canModerate(role)) {
                        return@dbQuery -2
                    }
                    MessageTable.update({ MessageTable.id eq messageIdParam }) {
                        it[MessageTable.isDeleted] = true
                        it[MessageTable.updatedAt] = System.currentTimeMillis()
                    }
                }

                when {
                    updatedRows > 0 -> {
                        call.respond(HttpStatusCode.OK, true)
                        broadcastChannelMessageChange(messageIdParam)
                    }
                    updatedRows == -2 -> call.respond(HttpStatusCode.Forbidden, false)
                    else -> call.respond(HttpStatusCode.NotFound, false)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.InternalServerError, false)
            }
        }

        // Peer recognition (career badges). Idempotent: marking the same message again is a no-op.
        post("/{messageId}/helpful") {
            val messageIdParam = call.parameters["messageId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid messageId")
            val actingUserId = call.authenticatedUserId()
            val outcome = dbQuery { plugins.progression.HelpfulMarks.mark(messageIdParam, actingUserId) }
            when (outcome.status) {
                plugins.progression.HelpfulMarkStatus.MARKED -> {
                    call.respond(HttpStatusCode.OK, dto.HelpfulMarkResponse(alreadyMarked = false))
                    plugins.progression.publishProgressionNotifications(outcome.notifications)
                }
                plugins.progression.HelpfulMarkStatus.ALREADY_MARKED ->
                    call.respond(HttpStatusCode.OK, dto.HelpfulMarkResponse(alreadyMarked = true))
                plugins.progression.HelpfulMarkStatus.OWN_MESSAGE ->
                    call.respond(HttpStatusCode.BadRequest, "You can't mark your own message as helpful")
                plugins.progression.HelpfulMarkStatus.NOT_MEMBER ->
                    call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                plugins.progression.HelpfulMarkStatus.NOT_FOUND ->
                    call.respond(HttpStatusCode.NotFound, "Message not found")
                plugins.progression.HelpfulMarkStatus.DAILY_LIMIT ->
                    call.respond(HttpStatusCode.TooManyRequests, "You've marked many messages helpful today; try again tomorrow")
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
                if (e is kotlinx.coroutines.CancellationException) throw e
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
                    }.count() > 0L
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
                if (call.response.isCommitted) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    logger.error("[Messages] Read-state broadcast failed", e)
                } else {
                    val failure = classifyFailure(e, logger, "Read-state update")
                    call.respond(failure.status, false)
                }
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
                // null: not a member; second == false: the channel isn't in that workspace.
                val states = dbQuery {
                    if (!isMember(actingUserId, workspaceIdParam)) return@dbQuery null
                    // Membership of the named workspace says nothing about a channel id from another one.
                    val channelInWorkspace = ChannelsTable.select(ChannelsTable.id)
                        .where { (ChannelsTable.id eq channelIdParam) and (ChannelsTable.workspaceId eq workspaceIdParam) }
                        .singleOrNull() != null
                    if (!channelInWorkspace) return@dbQuery emptyList<ChannelReadState>() to false
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
                        } to true
                }
                when {
                    states == null -> call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                    !states.second -> call.respond(HttpStatusCode.NotFound, "Channel not found in this workspace")
                    else -> call.respond(HttpStatusCode.OK, states.first)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
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
                if (e is kotlinx.coroutines.CancellationException) throw e
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
                if (e is kotlinx.coroutines.CancellationException) throw e
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
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.InternalServerError, "Reactions Error")
            }
        }

        // Unread channel messages per channel for the caller: messages by others after their read
        // position (all of them if they never opened the channel). Feeds the channel-list badges.
        get("/unread/{workspaceId}") {
            val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid workspaceId")
            val actingUserId = call.authenticatedUserId()
            try {
                val counts = dbReadQuery {
                    if (!isMember(actingUserId, workspaceIdParam)) return@dbReadQuery null
                    channelUnreadCounts(actingUserId, workspaceIdParam)
                }
                if (counts == null) call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                else call.respond(HttpStatusCode.OK, counts)
            } catch (e: Exception) {
                val failure = classifyFailure(e, logger, "Channel unread counts")
                call.respond(failure.status, failure.message)
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
                if (e is kotlinx.coroutines.CancellationException) throw e
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
                    recordSyncPage("messages", page.rows.size)
                    call.appendSyncHeaders(page.nextCursor, page.reset, page.nextPageToken)
                    call.respond(HttpStatusCode.OK, page.rows)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.error("[Sync] Message sync failed", e)
                call.respond(HttpStatusCode.InternalServerError, "Sync Error")
            }
        }
    }
}

internal fun mediaNotificationLabel(mediaUrl: String?): String = when {
    mediaUrl == null -> ""
    mediaUrl.contains("/voice/") -> "🎤 Voice message"
    else -> "📷 Photo"
}

internal suspend fun notifyChannelMessage(
    actingUserId: Int,
    workspaceId: Int,
    channelId: Int,
    content: String,
    newMessage: MessageResponse
) {
    val senderRow = dbQuery {
        UsersTable.selectAll().where { UsersTable.id eq actingUserId }.singleOrNull()
    }
    val senderName = senderRow?.get(UsersTable.username) ?: "Someone"
    val channelName = dbQuery {
        ChannelsTable.select(ChannelsTable.channelName)
            .where { ChannelsTable.id eq channelId }
            .singleOrNull()?.get(ChannelsTable.channelName)
    }

    val notificationBody = content.ifBlank { mediaNotificationLabel(newMessage.mediaUrl) }.take(200)
    // Parse @mentions from content
    val mentionedUsernames = MENTION_REGEX.findAll(content)
        .map { it.groupValues[1].lowercase() }.toSet()

    // Find all channel members (excluding sender)
    val channelMembers = dbQuery {
        (WorkspaceMembersTable innerJoin UsersTable)
            .selectAll()
            .where { WorkspaceMembersTable.workspaceId eq workspaceId }
            .filter { it[UsersTable.id] != actingUserId }
            .map { it[UsersTable.id] to it[UsersTable.username].lowercase() }
    }

    val mutedMemberIds = dbQuery {
        NotificationMutesTable
            .select(NotificationMutesTable.userId)
            .where {
                (NotificationMutesTable.workspaceId eq workspaceId) and
                        ((NotificationMutesTable.channelId eq 0) or (NotificationMutesTable.channelId eq channelId))
            }
            .map { it[NotificationMutesTable.userId] }
            .toSet()
    }

    val threadRootId = newMessage.threadRootId
    val threadParticipants = if (threadRootId == null) emptySet() else dbQuery {
        val rootAuthor = MessageTable.select(MessageTable.userId)
            .where { MessageTable.id eq threadRootId }
            .singleOrNull()?.get(MessageTable.userId)
        val repliers = MessageTable.select(MessageTable.userId)
            .where { (MessageTable.threadRootId eq threadRootId) and (MessageTable.isDeleted eq false) }
            .map { it[MessageTable.userId] }
        (repliers + listOfNotNull(rootAuthor)).toSet() - actingUserId
    }

    channelMembers.forEach { (memberId, memberUsername) ->
        val isMentioned = memberUsername in mentionedUsernames
        if (threadRootId != null && !isMentioned) {
            if (memberId in threadParticipants) {
                createAndPushNotification(
                    recipientId = memberId,
                    actorId = actingUserId,
                    type = "CHANNEL_MESSAGE",
                    title = "$senderName replied in a thread",
                    body = notificationBody,
                    workspaceId = workspaceId,
                    referenceId = newMessage.id,
                    channelId = channelId,
                    channelName = channelName
                )
            }
            return@forEach
        }
        if (!isMentioned && memberId in mutedMemberIds) return@forEach
        if (isMentioned) {
            createAndPushNotification(
                recipientId = memberId,
                actorId = actingUserId,
                type = "MENTION",
                title = "$senderName mentioned you",
                body = notificationBody,
                workspaceId = workspaceId,
                referenceId = newMessage.id,
                channelId = channelId,
                channelName = channelName
            )
        } else {
            createAndPushNotification(
                recipientId = memberId,
                actorId = actingUserId,
                type = "CHANNEL_MESSAGE",
                title = "New message from $senderName",
                body = notificationBody,
                workspaceId = workspaceId,
                referenceId = newMessage.id,
                channelId = channelId,
                channelName = channelName
            )
        }
    }
}
