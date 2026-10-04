package plugins

import com.collabsphere.dto.*
import dto.*
import com.collabsphere.model.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ChannelsRoutes")

/**
 * Channels feature routes — extracted from Routing.kt.
 * Mounted inside `authenticate("auth-jwt")` in configureRouting().
 */
internal fun Route.channelsRoutes() {
    route("/api/channels") {

        // ── CREATE channel ─────────────────────────────────────────────────────
        post {
            try {
                val actingUserId = call.authenticatedUserId()
                val request = call.receive<ChannelRequest>()

                val response = dbQuery {
                    if (!isMember(actingUserId, request.workspaceId)) {
                        return@dbQuery null
                    }
                    val insertedId = ChannelsTable.insert {
                        it[ChannelsTable.userId] = actingUserId
                        it[ChannelsTable.channelName] = request.channelName
                        it[ChannelsTable.workspaceId] = request.workspaceId
                        it[ChannelsTable.description] = request.description
                        it[ChannelsTable.updatedAt] = System.currentTimeMillis()
                        it[ChannelsTable.isDeleted] = false
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
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.BadRequest, "Failed to create channel")
            }
        }

        // ── SOFT DELETE channel ────────────────────────────────────────────────
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
                    val role = workspaceRole(actingUserId, workspaceIdParam) ?: return@dbQuery -1
                    val channel = ChannelsTable.selectAll().where {
                        (ChannelsTable.channelName eq channelNameParam) and
                                (ChannelsTable.workspaceId eq workspaceIdParam) and
                                (ChannelsTable.isDeleted eq false)
                    }.firstOrNull() ?: return@dbQuery 0
                    if (!WorkspaceRoles.canModerate(role) && channel[ChannelsTable.userId] != actingUserId) {
                        return@dbQuery -1
                    }
                    ChannelsTable.update({ ChannelsTable.id eq channel[ChannelsTable.id] }) {
                        it[ChannelsTable.isDeleted] = true
                        it[ChannelsTable.updatedAt] = System.currentTimeMillis()
                    }
                }

                when {
                    updatedRows == -1 -> call.respond(HttpStatusCode.Forbidden, false)
                    updatedRows > 0   -> call.respond(HttpStatusCode.OK, true)
                    else              -> call.respond(HttpStatusCode.NotFound, false)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.InternalServerError, false)
            }
        }

        // ── DELTA SYNC ─────────────────────────────────────────────────────────
        get("/sync/{workspaceId}") {
            try {
                val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                val actingUserId = call.authenticatedUserId()

                if (workspaceIdParam == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing workspaceId")
                    return@get
                }

                val page = dbQuery {
                    if (!isMember(actingUserId, workspaceIdParam)) {
                        return@dbQuery null
                    }
                    channelDeltaSync(workspaceIdParam, call.syncRequest())
                }
                if (page == null) {
                    call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                } else {
                    call.appendSyncHeaders(page.nextCursor, page.reset)
                    call.respond(HttpStatusCode.OK, page.rows)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.error("[Sync] Channel sync failed", e)
                call.respond(HttpStatusCode.InternalServerError, "Sync Error")
            }
        }

        // ── LIST by workspace ──────────────────────────────────────────────────
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
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.InternalServerError, "Error retrieving channels")
            }
        }
    }
}
