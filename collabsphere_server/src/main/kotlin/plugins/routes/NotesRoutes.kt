package plugins

import com.collabsphere.dto.*
import dto.WorkspaceRoles
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
import plugins.progression.*

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
                if (request.notesName.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, "Note name cannot be blank")
                if (request.notesName.length > 255) return@post call.respond(HttpStatusCode.BadRequest, "Note name too long")
                if (request.description.length > 10000) return@post call.respond(HttpStatusCode.BadRequest, "Note description too long")
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
                val failure = classifyFailure(e, logger, "Note create")
                call.respond(failure.status, failure.message)
            }
        }

        // ── PIN / UNPIN ────────────────────────────────────────────────────────
        post("/{noteId}/pin") {
            try {
                val noteIdParam = call.parameters["noteId"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, false)
                val actingUserId = call.authenticatedUserId()
                val request = call.receive<PinRequest>()
                var progression: RecordResult? = null
                val updated = dbQuery {
                    progression = null // dbQuery may re-run this block after a serialization retry
                    val note = NotesTable.selectAll()
                        .where { (NotesTable.id eq noteIdParam) and (NotesTable.isDeleted eq false) }
                        .singleOrNull() ?: return@dbQuery -1
                    if (!isMember(actingUserId, note[NotesTable.workspaceId])) return@dbQuery -2
                    val now = System.currentTimeMillis()
                    val changed = NotesTable.update({ NotesTable.id eq noteIdParam }) {
                        it[NotesTable.isPinned] = request.pinned
                        it[NotesTable.pinnedByUserId] = if (request.pinned) actingUserId else null
                        it[NotesTable.updatedAt] = now
                    }
                    // A teammate pinning a substantial note is the documentation-approval signal; it
                    // rewards the author once per note (unpinning later does not take it back).
                    val decision = RewardPolicy.noteApproval(
                        authorId = note[NotesTable.userIdNotes],
                        pinnerId = actingUserId,
                        pinned = request.pinned,
                        isDeleted = note[NotesTable.isDeleted],
                        description = note[NotesTable.notesDescription],
                        policy = ProgressionCatalogs.current.policy
                    )
                    if (decision != null) {
                        progression = ProgressionService.record(
                            ContributionEvent(
                                idempotencyKey = "note-approved:$noteIdParam",
                                userId = note[NotesTable.userIdNotes],
                                eventType = "NOTE_APPROVED",
                                source = "NOTES",
                                referenceType = "NOTE",
                                referenceId = noteIdParam.toString(),
                                workspaceId = note[NotesTable.workspaceId],
                                occurredAt = now,
                                decision = decision
                            )
                        )
                    }
                    changed
                }
                when {
                    updated == -2 -> call.respond(HttpStatusCode.Forbidden, false)
                    updated > 0  -> {
                        call.respond(HttpStatusCode.OK, true)
                        progression?.let { publishProgressionNotifications(it.notifications) }
                    }
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
                if (request.notesName.isBlank()) return@put call.respond(HttpStatusCode.BadRequest, "Note name cannot be blank")
                if (request.notesName.length > 255) return@put call.respond(HttpStatusCode.BadRequest, "Note name too long")
                if (request.description.length > 10000) return@put call.respond(HttpStatusCode.BadRequest, "Note description too long")
                // -1 not found (or deleted), -2 not a member; otherwise the stored note.
                val updateResult: Pair<Int, NotesResponse?> = dbQuery {
                    val existingNote = NotesTable.selectAll()
                        .where { (NotesTable.id eq noteIdParam) and (NotesTable.isDeleted eq false) }
                        .singleOrNull() ?: return@dbQuery -1 to null
                    if (!isMember(actingUserId, existingNote[NotesTable.workspaceId]) ||
                        !isMember(actingUserId, request.workspaceId)
                    ) return@dbQuery -2 to null
                    NotesTable.update({ NotesTable.id eq noteIdParam }) {
                        it[NotesTable.notesName] = request.notesName
                        it[NotesTable.workspaceId] = request.workspaceId
                        it[NotesTable.notesDescription] = request.description
                        it[NotesTable.updatedAt] = System.currentTimeMillis()
                    }
                    // The author and pin state are the stored ones, not the editor's.
                    1 to NotesResponse(
                        id = noteIdParam,
                        userId = existingNote[NotesTable.userIdNotes],
                        notesName = request.notesName,
                        workspaceId = request.workspaceId,
                        description = request.description,
                        isPinned = existingNote[NotesTable.isPinned]
                    )
                }
                when (updateResult.first) {
                    -1 -> call.respond(HttpStatusCode.NotFound, "Note not found to update")
                    -2 -> call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                    else -> call.respond(HttpStatusCode.OK, updateResult.second!!)
                }
            } catch (e: Exception) {
                val failure = classifyFailure(e, logger, "Note edit")
                call.respond(failure.status, failure.message)
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
                    // Same rule as channels: the note's author, or a workspace owner/admin.
                    val role = workspaceRole(actingUserId, existingNote[NotesTable.workspaceId]) ?: return@dbQuery -2
                    if (existingNote[NotesTable.userIdNotes] != actingUserId && !WorkspaceRoles.canModerate(role)) {
                        return@dbQuery -2
                    }
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
                    recordSyncPage("notes", page.rows.size)
                    call.appendSyncHeaders(page.nextCursor, page.reset, page.nextPageToken)
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
