package plugins.progression

import com.collabsphere.model.UsersTable
import dto.TimeZoneRequest
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import plugins.authenticatedUserId
import plugins.createAndPushNotification
import plugins.dbQuery
import plugins.dbReadQuery
import java.time.ZoneId

private val logger = LoggerFactory.getLogger("ProgressionRoutes")

/**
 * Read-only progression API for the authenticated user. Nothing here accepts SP amounts, levels or
 * badges: those change only through server-verified activities (see [ProgressionService]).
 * Mounted inside `authenticate("auth-jwt")`.
 */
internal fun Route.progressionRoutes() {
    route("/api/progression") {
        get("/summary") {
            val userId = call.authenticatedUserId()
            call.respond(HttpStatusCode.OK, dbReadQuery { ProgressionQueries.summary(userId) })
        }

        // Static per deployment, so clients can cache it and revalidate with If-None-Match.
        get("/catalog") {
            val catalog = ProgressionCatalogs.current
            val etag = "\"progression-catalog-v${catalog.version}\""
            if (call.request.headers[HttpHeaders.IfNoneMatch] == etag) {
                call.respond(HttpStatusCode.NotModified)
                return@get
            }
            call.response.header(HttpHeaders.ETag, etag)
            call.respond(HttpStatusCode.OK, catalog)
        }

        get("/badges") {
            val userId = call.authenticatedUserId()
            call.respond(HttpStatusCode.OK, dbReadQuery { ProgressionQueries.badges(userId) })
        }

        get("/badges/{familyKey}/evidence") {
            val userId = call.authenticatedUserId()
            val family = ProgressionCatalogs.current.badges.firstOrNull { it.key == call.parameters["familyKey"] }
                ?: return@get call.respond(HttpStatusCode.NotFound, "Unknown badge")
            call.respond(HttpStatusCode.OK, dbReadQuery { ProgressionQueries.evidence(userId, family) })
        }

        get("/history") {
            val userId = call.authenticatedUserId()
            val beforeId = call.request.queryParameters["beforeId"]?.let {
                it.toLongOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid beforeId")
            }
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 30).coerceIn(1, 100)
            call.respond(HttpStatusCode.OK, dbReadQuery { ProgressionQueries.history(userId, beforeId, limit) })
        }
    }

    // The device's zone decides which calendar day an activity counts toward (streaks, daily caps).
    put("/api/user/timezone") {
        val userId = call.authenticatedUserId()
        val zone = call.receive<TimeZoneRequest>().timeZone.trim()
        val valid = zone.length <= 64 && runCatching { ZoneId.of(zone) }.isSuccess
        if (!valid) return@put call.respond(HttpStatusCode.BadRequest, "Unknown time zone")
        dbQuery {
            UsersTable.update({ UsersTable.id eq userId }) { it[UsersTable.timeZone] = zone }
        }
        call.respond(HttpStatusCode.OK, true)
    }
}

/**
 * Pushes level-up and badge notifications that were already persisted in a committed reward
 * transaction. Re-sending is safe: the dedupe key resolves to the existing row. Delivery is best
 * effort — the notification list and progression endpoints remain the source of truth.
 */
internal suspend fun publishProgressionNotifications(notifications: List<PendingNotification>) {
    for (notification in notifications) {
        try {
            createAndPushNotification(
                recipientId = notification.recipientId,
                actorId = null,
                type = notification.type,
                title = notification.title,
                body = notification.body,
                workspaceId = notification.workspaceId,
                dedupeKey = notification.dedupeKey
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            logger.warn("[Progression] Could not push ${notification.dedupeKey}", e)
        }
    }
}
