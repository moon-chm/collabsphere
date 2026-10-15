package plugins

import com.collabsphere.dto.SaveMessageRequest
import com.collabsphere.dto.SavedMessageItem
import com.collabsphere.model.ChannelsTable
import com.collabsphere.model.DirectMessagesTable
import com.collabsphere.model.MessageTable
import com.collabsphere.model.SavedMessagesTable
import com.collabsphere.model.UsersTable
import com.collabsphere.model.WorkspacesTable
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("SavedMessagesRoutes")

internal const val SAVED_KIND_CHANNEL = "CHANNEL"
internal const val SAVED_KIND_DM = "DM"
private const val MAX_SAVED_LISTED = 500

internal fun Route.savedMessagesRoutes() {
    route("/api/saved") {
        get {
            val userId = call.authenticatedUserId()
            try {
                call.respond(HttpStatusCode.OK, dbReadQuery { savedMessagesFor(userId) })
            } catch (e: Exception) {
                val failure = classifyFailure(e, logger, "Saved messages list")
                call.respond(failure.status, failure.message)
            }
        }

        post {
            val userId = call.authenticatedUserId()
            try {
                val request = call.receive<SaveMessageRequest>()
                if (request.kind != SAVED_KIND_CHANNEL && request.kind != SAVED_KIND_DM) {
                    return@post call.respond(HttpStatusCode.BadRequest, "kind must be CHANNEL or DM")
                }
                val allowed = dbQuery {
                    if (!canSee(userId, request.kind, request.messageId)) return@dbQuery false
                    SavedMessagesTable.insertIgnore {
                        it[SavedMessagesTable.userId] = userId
                        it[kind] = request.kind
                        it[messageId] = request.messageId
                        it[savedAt] = System.currentTimeMillis()
                    }
                    true
                }
                if (allowed) call.respond(HttpStatusCode.OK, true) else call.respond(HttpStatusCode.NotFound, false)
            } catch (e: Exception) {
                val failure = classifyFailure(e, logger, "Save message")
                call.respond(failure.status, failure.message)
            }
        }

        delete("/{kind}/{messageId}") {
            val userId = call.authenticatedUserId()
            val kind = call.parameters["kind"]
            val messageId = call.parameters["messageId"]?.toIntOrNull()
            if (kind == null || messageId == null) return@delete call.respond(HttpStatusCode.BadRequest, false)
            try {
                dbQuery {
                    SavedMessagesTable.deleteWhere {
                        (SavedMessagesTable.userId eq userId) and
                            (SavedMessagesTable.kind eq kind) and
                            (SavedMessagesTable.messageId eq messageId)
                    }
                }
                call.respond(HttpStatusCode.OK, true)
            } catch (e: Exception) {
                val failure = classifyFailure(e, logger, "Unsave message")
                call.respond(failure.status, failure.message)
            }
        }
    }
}

private fun canSee(userId: Int, kind: String, messageId: Int): Boolean = when (kind) {
    SAVED_KIND_CHANNEL -> MessageTable.selectAll()
        .where { (MessageTable.id eq messageId) and (MessageTable.isDeleted eq false) }
        .singleOrNull()
        ?.let { isMember(userId, it[MessageTable.workspaceId]) } == true
    SAVED_KIND_DM -> DirectMessagesTable.selectAll()
        .where { (DirectMessagesTable.id eq messageId) and (DirectMessagesTable.isDeleted eq false) }
        .singleOrNull()
        ?.let { it[DirectMessagesTable.senderId] == userId || it[DirectMessagesTable.receiverId] == userId } == true
    else -> false
}

internal fun savedMessagesFor(userId: Int): List<SavedMessageItem> {
    val saved = SavedMessagesTable.selectAll()
        .where { SavedMessagesTable.userId eq userId }
        .orderBy(SavedMessagesTable.savedAt, SortOrder.DESC)
        .limit(MAX_SAVED_LISTED)
        .map { Triple(it[SavedMessagesTable.kind], it[SavedMessagesTable.messageId], it[SavedMessagesTable.savedAt]) }
    if (saved.isEmpty()) return emptyList()

    val channelIds = saved.filter { it.first == SAVED_KIND_CHANNEL }.map { it.second }
    val dmIds = saved.filter { it.first == SAVED_KIND_DM }.map { it.second }

    val channelRows = if (channelIds.isEmpty()) emptyMap() else MessageTable.selectAll()
        .where { (MessageTable.id inList channelIds) and (MessageTable.isDeleted eq false) }
        .associateBy { it[MessageTable.id] }
    val dmRows = if (dmIds.isEmpty()) emptyMap() else DirectMessagesTable.selectAll()
        .where { (DirectMessagesTable.id inList dmIds) and (DirectMessagesTable.isDeleted eq false) }
        .associateBy { it[DirectMessagesTable.id] }

    val workspaceIds = (channelRows.values.map { it[MessageTable.workspaceId] } +
        dmRows.values.map { it[DirectMessagesTable.workspaceId] }).distinct()
    val visibleWorkspaces = workspaceIds.filter { isMember(userId, it) }.toSet()
    val workspaceNames = if (visibleWorkspaces.isEmpty()) emptyMap() else WorkspacesTable.selectAll()
        .where { WorkspacesTable.id inList visibleWorkspaces.toList() }
        .associate { it[WorkspacesTable.id] to it[WorkspacesTable.workspaceName] }
    val channelNames = channelRows.values.map { it[MessageTable.channelId] }.distinct().let { ids ->
        if (ids.isEmpty()) emptyMap() else ChannelsTable.selectAll()
            .where { ChannelsTable.id inList ids }
            .associate { it[ChannelsTable.id] to it[ChannelsTable.channelName] }
    }
    val dmPeopleIds = dmRows.values.flatMap { listOf(it[DirectMessagesTable.senderId], it[DirectMessagesTable.receiverId]) }.distinct()
    val userNames = if (dmPeopleIds.isEmpty()) emptyMap() else UsersTable.selectAll()
        .where { UsersTable.id inList dmPeopleIds }
        .associate { it[UsersTable.id] to it[UsersTable.username] }

    return saved.mapNotNull { (kind, messageId, savedAt) ->
        when (kind) {
            SAVED_KIND_CHANNEL -> channelRows[messageId]?.takeIf { it[MessageTable.workspaceId] in visibleWorkspaces }?.let { row ->
                SavedMessageItem(
                    kind = kind,
                    messageId = messageId,
                    workspaceId = row[MessageTable.workspaceId],
                    workspaceName = workspaceNames[row[MessageTable.workspaceId]].orEmpty(),
                    channelId = row[MessageTable.channelId],
                    channelName = channelNames[row[MessageTable.channelId]],
                    authorId = row[MessageTable.userId],
                    authorName = row[MessageTable.userName],
                    content = row[MessageTable.content],
                    mediaUrl = row[MessageTable.mediaUrl],
                    sentAt = row[MessageTable.createdAt] ?: row[MessageTable.updatedAt],
                    savedAt = savedAt
                )
            }
            SAVED_KIND_DM -> dmRows[messageId]?.takeIf { it[DirectMessagesTable.workspaceId] in visibleWorkspaces }?.let { row ->
                val senderId = row[DirectMessagesTable.senderId]
                val partnerId = if (senderId == userId) row[DirectMessagesTable.receiverId] else senderId
                SavedMessageItem(
                    kind = kind,
                    messageId = messageId,
                    workspaceId = row[DirectMessagesTable.workspaceId],
                    workspaceName = workspaceNames[row[DirectMessagesTable.workspaceId]].orEmpty(),
                    partnerId = partnerId,
                    partnerName = userNames[partnerId],
                    authorId = senderId,
                    authorName = userNames[senderId].orEmpty(),
                    content = row[DirectMessagesTable.content],
                    mediaUrl = row[DirectMessagesTable.mediaUrl],
                    sentAt = row[DirectMessagesTable.timestamp],
                    savedAt = savedAt
                )
            }
            else -> null
        }
    }
}
