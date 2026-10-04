package plugins

import com.collabsphere.dto.*
import dto.*
import com.collabsphere.model.*
import com.collabsphere.util.AvatarGenerator
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal fun Route.notificationsRoutes() {
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
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.InternalServerError, "Failed to fetch notifications")
            }
        }

        get("/mutes") {
            try {
                val actingUserId = call.authenticatedUserId()
                val mutes = dbReadQuery {
                    NotificationMutesTable.selectAll()
                        .where { NotificationMutesTable.userId eq actingUserId }
                        .map {
                            MuteSetting(
                                workspaceId = it[NotificationMutesTable.workspaceId],
                                channelId = it[NotificationMutesTable.channelId].takeIf { id -> id > 0 }
                            )
                        }
                }
                call.respond(HttpStatusCode.OK, mutes)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.InternalServerError, "Failed to load mute settings")
            }
        }

        put("/mutes") {
            try {
                val actingUserId = call.authenticatedUserId()
                val request = call.receive<MuteRequest>()
                val channelKey = request.channelId?.takeIf { it > 0 } ?: 0
                val allowed = dbQuery {
                    if (!isMember(actingUserId, request.workspaceId)) return@dbQuery false
                    if (request.muted) {
                        NotificationMutesTable.insertIgnore {
                            it[NotificationMutesTable.userId] = actingUserId
                            it[NotificationMutesTable.workspaceId] = request.workspaceId
                            it[NotificationMutesTable.channelId] = channelKey
                        }
                    } else {
                        NotificationMutesTable.deleteWhere {
                            (NotificationMutesTable.userId eq actingUserId) and
                                    (NotificationMutesTable.workspaceId eq request.workspaceId) and
                                    (NotificationMutesTable.channelId eq channelKey)
                        }
                    }
                    true
                }
                if (allowed) {
                    call.respond(HttpStatusCode.OK, true)
                } else {
                    call.respond(HttpStatusCode.Forbidden, false)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.BadRequest, false)
            }
        }

        // ── COUNT: GET /api/notifications/count ───────────────────────
        get("/count") {
            try {
                val actingUserId = call.authenticatedUserId()
                val total = dbReadQuery {
                    NotificationsTable.selectAll()
                        .where { NotificationsTable.recipientId eq actingUserId }.count().toInt()
                }
                val unread = dbReadQuery {
                    NotificationsTable.selectAll()
                        .where {
                            (NotificationsTable.recipientId eq actingUserId) and
                            (NotificationsTable.isRead eq false)
                        }.count().toInt()
                }
                call.respond(HttpStatusCode.OK, NotificationCountResponse(total = total, unread = unread))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
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
                if (e is kotlinx.coroutines.CancellationException) throw e
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
                if (e is kotlinx.coroutines.CancellationException) throw e
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
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.InternalServerError, "Failed to clear notifications")
            }
        }
    }
}
