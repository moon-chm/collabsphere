package plugins

import com.collabsphere.dto.*
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

private val logger = LoggerFactory.getLogger("NotesRoutes")


/**
 * Notes feature routes — extracted from Routing.kt.
 * Mounted inside `authenticate("auth-jwt")` in configureRouting().
 * All shared helpers (dbQuery, isMember, logger) are internal in Routing.kt.
 */
internal fun Route.notesRoutes() {
    route("/api/notes") {
        // ── CREATE note ────────────────────────────────────────────────────────
        post {
            try {
                val actingUserId = call.authenticatedUserId()
                val request = call.receive<NotesRequest>()
                val newNotes = dbQuery {
                    if (!isMember(actingUserId, request.workspaceId)) return@dbQuery null

                    // Idempotency: a WorkManager retry re-sends the same key — return existing row
                    val existing = request.idempotencyKey?.let { key ->
                        NotesTable.selectAll().where { NotesTable.idempotencyKey eq key }.singleOrNull()
                    }
                    if (existing != null) {
                        return@dbQuery NotesResponse(
                            id = existing[NotesTable.id],
                            userId = existing[NotesTable.userIdNotes],
                            notesName = existing[NotesTable.notesName],
                            workspaceId = existing[NotesTable.workspaceId],
                            description = existing[NotesTable.notesDescription],
                            isPinned = existing[NotesTable.isPinned]
                        )
                    }

                    val insertedId = NotesTable.insert {
                        it[NotesTable.userIdNotes] = actingUserId
                        it[NotesTable.workspaceId] = request.workspaceId
                        it[NotesTable.notesName] = request.notesName
                        it[NotesTable.notesDescription] = request.description
                        it[NotesTable.isDeleted] = false
                        it[NotesTable.updatedAt] = System.currentTimeMillis()
                        it[NotesTable.idempotencyKey] = request.idempotencyKey
                    }[NotesTable.id]

                    NotesResponse(
                        id = insertedId,
                        userId = actingUserId,
                        notesName = request.notesName,
                        workspaceId = request.workspaceId,
                        description = request.description
                    )
                }
                if (newNotes == null) call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                else call.respond(HttpStatusCode.Created, newNotes)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.BadRequest, "Database structure mismatch or missing foreign row.")
            }
        }

        // ── PIN / UNPIN ────────────────────────────────────────────────────────
        post("/{noteId}/pin") {
            try {
                val noteIdParam = call.parameters["noteId"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, false)
                val actingUserId = call.authenticatedUserId()
                val request = call.receive<PinRequest>()
                val updated = dbQuery {
                    val note = NotesTable.selectAll()
                        .where { (NotesTable.id eq noteIdParam) and (NotesTable.isDeleted eq false) }
                        .singleOrNull() ?: return@dbQuery -1
                    if (!isMember(actingUserId, note[NotesTable.workspaceId])) return@dbQuery -2
                    NotesTable.update({ NotesTable.id eq noteIdParam }) {
                        it[NotesTable.isPinned] = request.pinned
                        it[NotesTable.updatedAt] = System.currentTimeMillis()
                    }
                }
                when {
                    updated == -2 -> call.respond(HttpStatusCode.Forbidden, false)
                    updated > 0  -> call.respond(HttpStatusCode.OK, true)
                    else         -> call.respond(HttpStatusCode.NotFound, false)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.InternalServerError, false)
            }
        }

        // ── UPDATE ─────────────────────────────────────────────────────────────
        put("/{noteId}") {
            try {
                val noteIdParam = call.parameters["noteId"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing or invalid noteId")
                val actingUserId = call.authenticatedUserId()
                val request = call.receive<NotesRequest>()
                val updateResult = dbQuery {
                    val existingNote = NotesTable.selectAll().where { NotesTable.id eq noteIdParam }.singleOrNull()
                        ?: return@dbQuery -1
                    if (!isMember(actingUserId, existingNote[NotesTable.workspaceId]) ||
                        !isMember(actingUserId, request.workspaceId)
                    ) return@dbQuery -2
                    NotesTable.update({ NotesTable.id eq noteIdParam }) {
                        it[NotesTable.notesName] = request.notesName
                        it[NotesTable.workspaceId] = request.workspaceId
                        it[NotesTable.notesDescription] = request.description
                        it[NotesTable.updatedAt] = System.currentTimeMillis()
                    }
                }
                when {
                    updateResult == -1 -> call.respond(HttpStatusCode.NotFound, "Note not found to update")
                    updateResult == -2 -> call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                    updateResult > 0   -> call.respond(HttpStatusCode.OK, NotesResponse(
                        id = noteIdParam,
                        userId = actingUserId,
                        notesName = request.notesName,
                        workspaceId = request.workspaceId,
                        description = request.description
                    ))
                    else -> call.respond(HttpStatusCode.NotFound, "Note not found to update")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.InternalServerError, "Error updating note")
            }
        }

        // ── SOFT DELETE ────────────────────────────────────────────────────────
        delete("/{noteId}") {
            try {
                val noteIdParam = call.parameters["noteId"]?.toIntOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing or invalid noteId parameter")
                val actingUserId = call.authenticatedUserId()
                val updatedRows = dbQuery {
                    val existingNote = NotesTable.selectAll().where { NotesTable.id eq noteIdParam }.singleOrNull()
                        ?: return@dbQuery -1
                    if (!isMember(actingUserId, existingNote[NotesTable.workspaceId])) return@dbQuery -2
                    NotesTable.update({ NotesTable.id eq noteIdParam }) {
                        it[NotesTable.isDeleted] = true
                        it[NotesTable.updatedAt] = System.currentTimeMillis()
                    }
                }
                when {
                    updatedRows == -2 -> call.respond(HttpStatusCode.Forbidden, false)
                    updatedRows > 0   -> call.respond(HttpStatusCode.OK, true)
                    else              -> call.respond(HttpStatusCode.NotFound, false)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.InternalServerError, false)
            }
        }

        // ── LIST by workspace ──────────────────────────────────────────────────
        get("/workspace/{workspaceId}") {
            val workspaceId = call.parameters["workspaceId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing or invalid workspaceId")
            val actingUserId = call.authenticatedUserId()
            try {
                val workspaceNotes = dbQuery {
                    if (!isMember(actingUserId, workspaceId)) return@dbQuery null
                    NotesTable.selectAll()
                        .where { (NotesTable.workspaceId eq workspaceId) and (NotesTable.isDeleted eq false) }
                        .map {
                            NotesResponse(
                                id = it[NotesTable.id],
                                userId = it[NotesTable.userIdNotes],
                                workspaceId = it[NotesTable.workspaceId],
                                notesName = it[NotesTable.notesName],
                                description = it[NotesTable.notesDescription],
                                isPinned = it[NotesTable.isPinned]
                            )
                        }
                }
                if (workspaceNotes == null) call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                else call.respond(HttpStatusCode.OK, workspaceNotes)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.InternalServerError, "Error retrieving notes")
            }
        }

        // ── DELTA SYNC ─────────────────────────────────────────────────────────
        get("/sync/{workspaceId}") {
            try {
                val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing structural context arguments.")
                val actingUserId = call.authenticatedUserId()
                val page = dbQuery {
                    if (!isMember(actingUserId, workspaceIdParam)) return@dbQuery null
                    noteDeltaSync(workspaceIdParam, call.syncRequest())
                }
                if (page == null) call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                else {
                    call.appendSyncHeaders(page.nextCursor, page.reset)
                    call.respond(HttpStatusCode.OK, page.rows)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.error("[Sync] Note sync failed", e)
                call.respond(HttpStatusCode.InternalServerError, "Sync Error processing delta operations query request loop.")
            }
        }
    }
}
