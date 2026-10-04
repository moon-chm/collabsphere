package plugins

import com.collabsphere.dto.*
import dto.*
import com.collabsphere.model.*
import com.collabsphere.util.CloudinaryService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger("DmRoutes")

internal fun Route.dmRoutes() {
    // ── DM Media Upload ────────────────────────────────────────────────────────
    get("/api/link-preview") {
        val url = call.request.queryParameters["url"]?.trim()
        if (url.isNullOrEmpty() || url.length > 2048) {
            call.respond(HttpStatusCode.BadRequest, "Missing or invalid url")
            return@get
        }
        val preview = com.collabsphere.util.LinkPreviewService.preview(url)
        if (preview == null) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.OK, preview)
        }
    }

    get("/api/dm/history/{workspaceId}/{partnerId}") {
        try {
            val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
            val partnerIdParam = call.parameters["partnerId"]?.toIntOrNull()
            if (workspaceIdParam == null || partnerIdParam == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing or invalid workspaceId or partnerId")
                return@get
            }
            val beforeId = call.request.queryParameters["before"]?.toIntOrNull()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, MAX_HISTORY_PAGE) ?: DEFAULT_HISTORY_PAGE
            val actingUserId = call.authenticatedUserId()

            val page = dbQuery {
                DirectMessagesTable.selectAll()
                    .where {
                        var condition = (DirectMessagesTable.workspaceId eq workspaceIdParam) and
                                (DirectMessagesTable.isDeleted eq false) and (
                                ((DirectMessagesTable.senderId eq actingUserId) and (DirectMessagesTable.receiverId eq partnerIdParam)) or
                                        ((DirectMessagesTable.senderId eq partnerIdParam) and (DirectMessagesTable.receiverId eq actingUserId))
                                )
                        if (beforeId != null) {
                            condition = condition and (DirectMessagesTable.id less beforeId)
                        }
                        condition
                    }
                    .orderBy(DirectMessagesTable.id, SortOrder.DESC)
                    .limit(limit)
                    .map { it.toDmHistoryDto() }
            }
            call.respond(HttpStatusCode.OK, page)
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            call.respond(HttpStatusCode.InternalServerError, "History Error")
        }
    }

    // ── DM delta sync: edits, read state and deletions (as tombstones) a device missed ──
    get("/api/dm/sync") {
        try {
            val actingUserId = call.authenticatedUserId()
            val cursor = call.request.queryParameters["cursor"]?.toLongOrNull()?.takeIf { it >= 0 }
            val knownUpToId = call.request.queryParameters["sinceId"]?.toIntOrNull() ?: 0
            val page = dbReadQuery { dmDeltaSync(actingUserId, cursor, knownUpToId) }
            call.appendSyncHeaders(page.nextCursor, page.reset)
            call.respond(HttpStatusCode.OK, page.rows)
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            logger.error("[Sync] DM sync failed", e)
            call.respond(HttpStatusCode.InternalServerError, "Sync Error")
        }
    }

    post("/api/media/upload") {
        val actingUserId = call.authenticatedUserId()
        var staged: File? = null
        try {
            // Checked before anything is read: a declared oversize body is refused outright, and an
            // undeclared (chunked) one is cut off by stageToTempFile the moment it passes the cap.
            if (call.declaredBodyExceeds(MAX_UPLOAD_BYTES)) throw UploadTooLargeException(MAX_UPLOAD_BYTES)
            val multipart = call.receiveMultipart()

            multipart.forEachPart { part ->
                if (part is PartData.FileItem && staged == null) {
                    staged = part.stageToTempFile(MAX_UPLOAD_BYTES)
                }
                part.dispose()
            }

            val mediaFile = staged ?: return@post call.respond(HttpStatusCode.BadRequest, "No file provided")

            val publicId = "dm_${actingUserId}_${System.currentTimeMillis()}"
            val uploadedUrl = CloudinaryService.uploadAvatar(mediaFile, publicId)
            call.respond(HttpStatusCode.OK, mapOf("url" to uploadedUrl))
        } catch (e: UploadTooLargeException) {
            call.respond(HttpStatusCode.PayloadTooLarge, e.message ?: "File too large")
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            logger.error("[MediaUpload] Upload failed", e)
            call.respond(HttpStatusCode.InternalServerError, "Upload failed")
        } finally {
            staged?.delete()
        }
    }
}
