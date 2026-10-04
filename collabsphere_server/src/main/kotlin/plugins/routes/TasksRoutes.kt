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
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("TasksRoutes")

/**
 * Tasks feature routes — extracted from Routing.kt.
 * Mounted inside `authenticate("auth-jwt")` in configureRouting().
 */
internal fun Route.tasksRoutes() {
    route("/api/tasks") {

        // ── CREATE task ────────────────────────────────────────────────────────
        post {
            try {
                val actingUserId = call.authenticatedUserId()
                val request = call.receive<TaskRequest>()

                val newTask = dbQuery {
                    if (!isMember(actingUserId, request.workspaceId)) return@dbQuery null
                    if (request.assignedToUserId != null && !isMember(request.assignedToUserId, request.workspaceId)) {
                        throw IllegalArgumentException("Assignee is not a member of the workspace")
                    }

                    // Idempotency: WorkManager retries re-send same key — return existing row
                    val existing = request.idempotencyKey?.let { key ->
                        TasksTable.selectAll().where { TasksTable.idempotencyKey eq key }.singleOrNull()
                    }
                    if (existing != null) {
                        return@dbQuery TaskResponse(
                            id = existing[TasksTable.id],
                            createdByUserId = existing[TasksTable.createdByUserId],
                            assignedToUserId = existing[TasksTable.assignedToUserId],
                            workspaceId = existing[TasksTable.workspaceId],
                            taskName = existing[TasksTable.taskName],
                            taskDescription = existing[TasksTable.taskDescription],
                            status = existing[TasksTable.status],
                            dueDate = existing[TasksTable.dueDate],
                            priority = existing[TasksTable.priority],
                            checklist = TaskExtras.decodeChecklist(existing[TasksTable.checklist]),
                            labels = TaskExtras.decodeLabels(existing[TasksTable.labels])
                        )
                    }

                    val newDueDate = request.dueDate?.takeIf { it > 0 }
                    val newPriority = TaskPriorities.normalize(request.priority) ?: "MEDIUM"
                    val newChecklist = TaskExtras.normalizeChecklist(request.checklist.orEmpty())
                    val newLabels = TaskExtras.normalizeLabels(request.labels.orEmpty())
                    val insertedId = TasksTable.insert {
                        it[TasksTable.createdByUserId] = actingUserId
                        it[TasksTable.assignedToUserId] = request.assignedToUserId
                        it[TasksTable.workspaceId] = request.workspaceId
                        it[TasksTable.taskName] = request.taskName
                        it[TasksTable.taskDescription] = request.taskDescription
                        it[TasksTable.status] = request.status
                        it[TasksTable.dueDate] = newDueDate
                        it[TasksTable.priority] = newPriority
                        it[TasksTable.checklist] = TaskExtras.encodeChecklist(newChecklist)
                        it[TasksTable.labels] = TaskExtras.encodeLabels(newLabels)
                        it[TasksTable.isDeleted] = false
                        it[TasksTable.updatedAt] = System.currentTimeMillis()
                        it[TasksTable.idempotencyKey] = request.idempotencyKey
                    }[TasksTable.id]

                    TaskResponse(
                        id = insertedId,
                        createdByUserId = actingUserId,
                        assignedToUserId = request.assignedToUserId,
                        workspaceId = request.workspaceId,
                        taskName = request.taskName,
                        taskDescription = request.taskDescription,
                        status = request.status,
                        dueDate = newDueDate,
                        priority = newPriority,
                        checklist = newChecklist,
                        labels = newLabels
                    )
                }

                if (newTask == null) {
                    call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                } else {
                    call.respond(HttpStatusCode.Created, newTask)
                    // ── Notification: task assigned ───────────────────────────
                    val assigneeId = request.assignedToUserId
                    if (assigneeId != null && assigneeId != actingUserId) {
                        val creatorName = dbQuery {
                            UsersTable.selectAll().where { UsersTable.id eq actingUserId }
                                .singleOrNull()?.get(UsersTable.username) ?: "Someone"
                        }
                        createAndPushNotification(
                            recipientId = assigneeId,
                            actorId = actingUserId,
                            type = "TASK_ASSIGNED",
                            title = "$creatorName assigned you a task",
                            body = "\"${request.taskName}\" — ${request.taskDescription.take(120)}",
                            workspaceId = request.workspaceId,
                            referenceId = newTask.id
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.BadRequest, "Foreign key violation: Verify workspaceId and userIds exist.")
            }
        }

        // ── LIST by user ───────────────────────────────────────────────────────
        get("/user/{userId}") {
            try {
                val userIdParam = call.parameters["userId"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing or invalid userId path parameter")
                val actingUserId = call.authenticatedUserId()
                val userTasks = dbReadQuery {
                    val memberWorkspaceIds = WorkspaceMembersTable.selectAll()
                        .where { WorkspaceMembersTable.userId eq actingUserId }
                        .map { it[WorkspaceMembersTable.workspaceId] }
                    if (memberWorkspaceIds.isEmpty()) emptyList()
                    else TasksTable.selectAll()
                        .where {
                            (TasksTable.assignedToUserId eq userIdParam) and
                                    (TasksTable.isDeleted eq false) and
                                    (TasksTable.workspaceId inList memberWorkspaceIds)
                        }
                        .map {
                            TaskResponse(
                                id = it[TasksTable.id],
                                createdByUserId = it[TasksTable.createdByUserId],
                                assignedToUserId = it[TasksTable.assignedToUserId],
                                workspaceId = it[TasksTable.workspaceId],
                                taskName = it[TasksTable.taskName],
                                taskDescription = it[TasksTable.taskDescription],
                                status = it[TasksTable.status],
                                dueDate = it[TasksTable.dueDate],
                                priority = it[TasksTable.priority],
                                checklist = TaskExtras.decodeChecklist(it[TasksTable.checklist]),
                                labels = TaskExtras.decodeLabels(it[TasksTable.labels])
                            )
                        }
                }
                call.respond(HttpStatusCode.OK, userTasks)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.InternalServerError, "Failed to retrieve user tasks")
            }
        }

        // ── SOFT DELETE ────────────────────────────────────────────────────────
        delete("/{taskId}") {
            try {
                val taskIdParam = call.parameters["taskId"]?.toIntOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing or invalid taskId")
                val actingUserId = call.authenticatedUserId()
                val updatedRows = dbReadQuery {
                    val task = TasksTable.selectAll().where { TasksTable.id eq taskIdParam }.singleOrNull()
                        ?: return@dbReadQuery -1
                    if (!isMember(actingUserId, task[TasksTable.workspaceId])) return@dbReadQuery -2
                    TasksTable.update({ TasksTable.id eq taskIdParam }) {
                        it[isDeleted] = true
                        it[updatedAt] = System.currentTimeMillis()
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

        // ── UPDATE task ────────────────────────────────────────────────────────
        put("/{taskId}") {
            val taskId = call.parameters["taskId"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing or invalid taskId")
            val actingUserId = call.authenticatedUserId()
            val request = try {
                call.receive<TaskRequest>()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.BadRequest, "Invalid request body")
                return@put
            }

            var previousStatus: String? = null
            val updateResult = dbReadQuery {
                val existingTask = TasksTable.selectAll().where { TasksTable.id eq taskId }.singleOrNull()
                    ?: return@dbReadQuery -1
                previousStatus = existingTask[TasksTable.status]
                if (!isMember(actingUserId, existingTask[TasksTable.workspaceId]) ||
                    !isMember(actingUserId, request.workspaceId)
                ) return@dbReadQuery -2
                if (request.assignedToUserId != null && !isMember(request.assignedToUserId, request.workspaceId)) {
                    return@dbReadQuery -4
                }

                // Business rules: mirror client-side restrictions server-side
                val reassigning = request.assignedToUserId != existingTask[TasksTable.assignedToUserId]
                val editingContentOrStatus = request.taskName != existingTask[TasksTable.taskName] ||
                        request.taskDescription != existingTask[TasksTable.taskDescription] ||
                        request.status != existingTask[TasksTable.status]

                val currentDueDate = existingTask[TasksTable.dueDate]
                val currentPriority = existingTask[TasksTable.priority]
                val nextDueDate = when (val requested = request.dueDate) {
                    null -> currentDueDate
                    else -> requested.takeIf { it > 0 }
                }
                val nextPriority = TaskPriorities.normalize(request.priority) ?: currentPriority
                val currentChecklist = TaskExtras.decodeChecklist(existingTask[TasksTable.checklist])
                val currentLabels = TaskExtras.decodeLabels(existingTask[TasksTable.labels])
                val nextChecklist = request.checklist?.let { TaskExtras.normalizeChecklist(it) } ?: currentChecklist
                val nextLabels = request.labels?.let { TaskExtras.normalizeLabels(it) } ?: currentLabels
                val editingPlanning = nextDueDate != currentDueDate || nextPriority != currentPriority ||
                        nextChecklist != currentChecklist || nextLabels != currentLabels

                if (reassigning && actingUserId != existingTask[TasksTable.createdByUserId]) return@dbReadQuery -3
                if (editingContentOrStatus && actingUserId != existingTask[TasksTable.assignedToUserId]) return@dbReadQuery -3
                if (editingPlanning &&
                    actingUserId != existingTask[TasksTable.assignedToUserId] &&
                    actingUserId != existingTask[TasksTable.createdByUserId]
                ) return@dbReadQuery -3

                TasksTable.update({ TasksTable.id eq taskId }) {
                    it[TasksTable.taskName] = request.taskName
                    it[TasksTable.taskDescription] = request.taskDescription
                    it[TasksTable.assignedToUserId] = request.assignedToUserId
                    it[TasksTable.workspaceId] = request.workspaceId
                    it[TasksTable.status] = request.status
                    it[TasksTable.dueDate] = nextDueDate
                    it[TasksTable.priority] = nextPriority
                    it[TasksTable.checklist] = TaskExtras.encodeChecklist(nextChecklist)
                    it[TasksTable.labels] = TaskExtras.encodeLabels(nextLabels)
                    if (nextDueDate != currentDueDate) it[TasksTable.reminderSentAt] = null
                    it[TasksTable.updatedAt] = System.currentTimeMillis()
                }
            }

            when (updateResult) {
                -1   -> return@put call.respond(HttpStatusCode.NotFound, "Task not found")
                -2   -> return@put call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                -3   -> return@put call.respond(HttpStatusCode.Forbidden,
                    "Only the assignee can edit or move this task; only the creator can reassign it; due date and priority can be changed by the creator or assignee")
                -4   -> return@put call.respond(HttpStatusCode.BadRequest, "Assignee is not a member of the workspace")
            }

            val updatedTask = dbReadQuery {
                TasksTable.selectAll().where { TasksTable.id eq taskId }.map {
                    TaskResponse(
                        id = it[TasksTable.id],
                        createdByUserId = it[TasksTable.createdByUserId],
                        assignedToUserId = it[TasksTable.assignedToUserId],
                        workspaceId = it[TasksTable.workspaceId],
                        taskName = it[TasksTable.taskName],
                        taskDescription = it[TasksTable.taskDescription],
                        status = it[TasksTable.status],
                        dueDate = it[TasksTable.dueDate],
                        priority = it[TasksTable.priority],
                        checklist = TaskExtras.decodeChecklist(it[TasksTable.checklist]),
                        labels = TaskExtras.decodeLabels(it[TasksTable.labels])
                    )
                }.singleOrNull()
            }

            if (updatedTask == null) {
                call.respond(HttpStatusCode.InternalServerError, "Failed to retrieve updated task")
            } else {
                call.respond(HttpStatusCode.OK, updatedTask)
                syncLinkedIssuesWithTask(updatedTask.id, previousStatus, updatedTask.status)
                call.application.launch {
                    try {
                        com.collabsphere.util.GitHubAssigneeSyncService.syncToGitHub(
                            updatedTask.id, updatedTask.workspaceId, updatedTask.assignedToUserId
                        )
                    } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                        logger.warn("[GitHub] Assignee sync to GitHub failed", e)
                    }
                }
                // ── Notification: task updated ────────────────────────────────
                val assigneeId = updatedTask.assignedToUserId
                if (assigneeId != null && assigneeId != actingUserId) {
                    val updaterName = dbReadQuery {
                        UsersTable.selectAll().where { UsersTable.id eq actingUserId }
                            .singleOrNull()?.get(UsersTable.username) ?: "Someone"
                    }
                    createAndPushNotification(
                        recipientId = assigneeId,
                        actorId = actingUserId,
                        type = "TASK_UPDATED",
                        title = "$updaterName updated your task",
                        body = "\"${updatedTask.taskName}\" is now ${updatedTask.status}",
                        workspaceId = updatedTask.workspaceId,
                        referenceId = updatedTask.id
                    )
                }
            }
        }

        // ── LIST by workspace ──────────────────────────────────────────────────
        get("/workspace/{workspaceId}") {
            val workspaceId = call.parameters["workspaceId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing workspaceId")
            val actingUserId = call.authenticatedUserId()
            try {
                val workspaceTasks = dbReadQuery {
                    if (!isMember(actingUserId, workspaceId)) return@dbReadQuery null
                    TasksTable.selectAll()
                        .where { (TasksTable.workspaceId eq workspaceId) and (TasksTable.isDeleted eq false) }
                        .map {
                            TaskResponse(
                                id = it[TasksTable.id],
                                createdByUserId = it[TasksTable.createdByUserId],
                                assignedToUserId = it[TasksTable.assignedToUserId],
                                workspaceId = it[TasksTable.workspaceId],
                                taskName = it[TasksTable.taskName],
                                taskDescription = it[TasksTable.taskDescription],
                                status = it[TasksTable.status],
                                dueDate = it[TasksTable.dueDate],
                                priority = it[TasksTable.priority],
                                checklist = TaskExtras.decodeChecklist(it[TasksTable.checklist]),
                                labels = TaskExtras.decodeLabels(it[TasksTable.labels])
                            )
                        }
                }
                if (workspaceTasks == null) call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                else call.respond(HttpStatusCode.OK, workspaceTasks)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.InternalServerError, "Error retrieving tasks")
            }
        }

        // ── DELTA SYNC ─────────────────────────────────────────────────────────
        get("/sync/{workspaceId}") {
            try {
                val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing structural context arguments.")
                val actingUserId = call.authenticatedUserId()
                val page = dbReadQuery {
                    if (!isMember(actingUserId, workspaceIdParam)) return@dbReadQuery null
                    taskDeltaSync(workspaceIdParam, call.syncRequest())
                }
                if (page == null) call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                else {
                    call.appendSyncHeaders(page.nextCursor, page.reset)
                    call.respond(HttpStatusCode.OK, page.rows)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.error("[Sync] Task sync failed", e)
                call.respond(HttpStatusCode.InternalServerError, "Sync Error processing delta operations query request loop.")
            }
        }
    }
}
