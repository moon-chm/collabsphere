package plugins

import com.collabsphere.dto.CreatePollRequest
import com.collabsphere.dto.PollOptionView
import com.collabsphere.dto.PollView
import com.collabsphere.dto.PollVoteRequest
import com.collabsphere.model.MessageTable
import com.collabsphere.model.PollVotesTable
import com.collabsphere.model.PollsTable
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("PollRoutes")
private val optionsSerializer = ListSerializer(String.serializer())

internal const val MAX_POLL_OPTIONS = 10
internal const val MAX_POLL_TEXT = 200

internal fun Route.pollRoutes() {
    route("/api/message") {
        post("/{messageId}/poll") {
            val messageId = call.parameters["messageId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid messageId")
            val userId = call.authenticatedUserId()
            try {
                val request = call.receive<CreatePollRequest>()
                val question = request.question.trim()
                val options = request.options.map { it.trim() }.filter { it.isNotEmpty() }
                if (question.isEmpty() || question.length > MAX_POLL_TEXT) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Question must be 1-$MAX_POLL_TEXT characters")
                }
                if (options.size !in 2..MAX_POLL_OPTIONS || options.any { it.length > MAX_POLL_TEXT } || options.distinct().size != options.size) {
                    return@post call.respond(HttpStatusCode.BadRequest, "A poll needs 2-$MAX_POLL_OPTIONS different options")
                }
                val outcome = dbQuery {
                    val message = MessageTable.selectAll()
                        .where { (MessageTable.id eq messageId) and (MessageTable.isDeleted eq false) }
                        .singleOrNull() ?: return@dbQuery HttpStatusCode.NotFound
                    if (message[MessageTable.userId] != userId) return@dbQuery HttpStatusCode.Forbidden
                    if (message[MessageTable.threadRootId] != null) return@dbQuery HttpStatusCode.BadRequest
                    PollsTable.insertIgnore {
                        it[PollsTable.messageId] = messageId
                        it[PollsTable.question] = question
                        it[PollsTable.optionsJson] = Json.encodeToString(optionsSerializer, options)
                        it[allowsMultiple] = request.allowsMultiple
                        it[createdAt] = System.currentTimeMillis()
                    }
                    HttpStatusCode.Created
                }
                if (outcome != HttpStatusCode.Created) return@post call.respond(outcome, false)
                val view = dbQuery { pollViews(listOf(messageId), userId).firstOrNull() }
                call.respond(HttpStatusCode.Created, view ?: false)
                broadcastChannelMessageChange(messageId)
            } catch (e: Exception) {
                val failure = classifyFailure(e, logger, "Poll create")
                call.respond(failure.status, failure.message)
            }
        }

        get("/polls/{workspaceId}/{channelId}") {
            val workspaceId = call.parameters["workspaceId"]?.toIntOrNull()
            val channelId = call.parameters["channelId"]?.toIntOrNull()
            if (workspaceId == null || channelId == null) return@get call.respond(HttpStatusCode.BadRequest, "Invalid ids")
            val userId = call.authenticatedUserId()
            try {
                val views = dbReadQuery {
                    if (!isMember(userId, workspaceId)) return@dbReadQuery null
                    val pollMessageIds = (PollsTable innerJoin MessageTable)
                        .select(PollsTable.messageId)
                        .where {
                            (MessageTable.workspaceId eq workspaceId) and
                                (MessageTable.channelId eq channelId) and
                                (MessageTable.isDeleted eq false)
                        }
                        .map { it[PollsTable.messageId] }
                    pollViews(pollMessageIds, userId)
                }
                if (views == null) call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                else call.respond(HttpStatusCode.OK, views)
            } catch (e: Exception) {
                val failure = classifyFailure(e, logger, "Poll list")
                call.respond(failure.status, failure.message)
            }
        }

        post("/{messageId}/poll/vote") {
            val messageId = call.parameters["messageId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid messageId")
            val userId = call.authenticatedUserId()
            try {
                val request = call.receive<PollVoteRequest>()
                val result = dbQuery {
                    val (poll, message) = pollWithMessage(messageId) ?: return@dbQuery HttpStatusCode.NotFound to null
                    if (!isMember(userId, message[MessageTable.workspaceId])) return@dbQuery HttpStatusCode.Forbidden to null
                    if (poll[PollsTable.closedAt] != null) return@dbQuery HttpStatusCode.Conflict to null
                    val optionCount = Json.decodeFromString(optionsSerializer, poll[PollsTable.optionsJson]).size
                    val choices = request.optionIndexes.distinct()
                    if (choices.any { it !in 0 until optionCount }) return@dbQuery HttpStatusCode.BadRequest to null
                    if (!poll[PollsTable.allowsMultiple] && choices.size > 1) return@dbQuery HttpStatusCode.BadRequest to null
                    PollVotesTable.deleteWhere { (PollVotesTable.messageId eq messageId) and (PollVotesTable.userId eq userId) }
                    PollVotesTable.batchInsert(choices) { index ->
                        this[PollVotesTable.messageId] = messageId
                        this[PollVotesTable.userId] = userId
                        this[PollVotesTable.optionIndex] = index
                    }
                    HttpStatusCode.OK to pollViews(listOf(messageId), userId).firstOrNull()
                }
                if (result.second == null) return@post call.respond(result.first, false)
                call.respond(HttpStatusCode.OK, result.second!!)
                broadcastChannelMessageChange(messageId)
            } catch (e: Exception) {
                val failure = classifyFailure(e, logger, "Poll vote")
                call.respond(failure.status, failure.message)
            }
        }

        post("/{messageId}/poll/close") {
            val messageId = call.parameters["messageId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid messageId")
            val userId = call.authenticatedUserId()
            try {
                val result = dbQuery {
                    val (_, message) = pollWithMessage(messageId) ?: return@dbQuery HttpStatusCode.NotFound to null
                    if (message[MessageTable.userId] != userId) return@dbQuery HttpStatusCode.Forbidden to null
                    PollsTable.update({ (PollsTable.messageId eq messageId) and PollsTable.closedAt.isNull() }) {
                        it[closedAt] = System.currentTimeMillis()
                    }
                    HttpStatusCode.OK to pollViews(listOf(messageId), userId).firstOrNull()
                }
                if (result.second == null) return@post call.respond(result.first, false)
                call.respond(HttpStatusCode.OK, result.second!!)
                broadcastChannelMessageChange(messageId)
            } catch (e: Exception) {
                val failure = classifyFailure(e, logger, "Poll close")
                call.respond(failure.status, failure.message)
            }
        }
    }
}

private fun pollWithMessage(messageId: Int): Pair<ResultRow, ResultRow>? {
    val poll = PollsTable.selectAll().where { PollsTable.messageId eq messageId }.singleOrNull() ?: return null
    val message = MessageTable.selectAll()
        .where { (MessageTable.id eq messageId) and (MessageTable.isDeleted eq false) }
        .singleOrNull() ?: return null
    return poll to message
}

internal fun pollViews(messageIds: List<Int>, viewerId: Int): List<PollView> {
    if (messageIds.isEmpty()) return emptyList()
    val votes = PollVotesTable.selectAll()
        .where { PollVotesTable.messageId inList messageIds }
        .map { Triple(it[PollVotesTable.messageId], it[PollVotesTable.userId], it[PollVotesTable.optionIndex]) }
        .groupBy { it.first }
    return PollsTable.selectAll()
        .where { PollsTable.messageId inList messageIds }
        .map { row ->
            val messageId = row[PollsTable.messageId]
            val options = Json.decodeFromString(optionsSerializer, row[PollsTable.optionsJson])
            val pollVotes = votes[messageId].orEmpty()
            PollView(
                messageId = messageId,
                question = row[PollsTable.question],
                options = options.mapIndexed { index, text ->
                    PollOptionView(text = text, votes = pollVotes.count { it.third == index })
                },
                allowsMultiple = row[PollsTable.allowsMultiple],
                myVotes = pollVotes.filter { it.second == viewerId }.map { it.third },
                totalVoters = pollVotes.map { it.second }.distinct().size,
                closed = row[PollsTable.closedAt] != null
            )
        }
}
