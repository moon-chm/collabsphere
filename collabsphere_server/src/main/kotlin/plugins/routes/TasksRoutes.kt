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
import plugins.progression.*

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
                val request = call.receive<TaskRequest>().withCanonicalStatus()

                if (request.taskName.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, "Task name cannot be blank")
                if (request.taskName.length > 255) return@post call.respond(HttpStatusCode.BadRequest, "Task name too long")
                if (request.status !in listOf("TODO", "IN_PROGRESS", "DONE")) return@post call.respond(HttpStatusCode.BadRequest, "Invalid status")

                val newTask = dbQuery {
                    if (!isMember(actingUserId, request.workspaceId)) return@dbQuery null
                    if (request.assignedToUserId != null && !isMember(request.assignedToUserId, request.workspaceId)) {
                        throw TaskValidationException("Assignee is not a member of the workspace")
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
                        val now = System.currentTimeMillis()
                        it[TasksTable.updatedAt] = now
                        it[TasksTable.idempotencyKey] = request.idempotencyKey
                        // Provenance for reward rules: the creator set these at creation time.
                        it[TasksTable.prioritySetBy] = actingUserId
                        if (newDueDate != null) {
                            it[TasksTable.dueDateSetBy] = actingUserId
                            it[TasksTable.dueDateSetAt] = now
                        }
                        if (request.status == RewardPolicy.DONE) it[TasksTable.completedAt] = now
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
                // The app's sync worker drops a create on 4xx and retries it on 5xx, so only genuine
                // client errors may be reported as 4xx; anything unexpected must stay retryable.
                when {
                    // The 201 already went out; this was the follow-up notification.
                    call.response.isCommitted -> logger.error("[Tasks] Post-create notification failed", e)
                    e is TaskValidationException -> call.respond(HttpStatusCode.BadRequest, e.message ?: "Invalid task")
                    e.causedByPayloadTooLarge() -> call.respond(HttpStatusCode.PayloadTooLarge, "Request body too large")
                    e is io.ktor.server.plugins.UnsupportedMediaTypeException ->
                        call.respond(HttpStatusCode.UnsupportedMediaType, "Expected a JSON body")
                    e is io.ktor.server.plugins.BadRequestException -> call.respond(HttpStatusCode.BadRequest, "Malformed request body")
                    e.isForeignKeyViolation() -> call.respond(HttpStatusCode.BadRequest, "Unknown workspace or user")
                    else -> {
                        logger.error("[Tasks] Failed to create task", e)
                        call.respond(HttpStatusCode.InternalServerError, "Failed to create task")
                    }
                }
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
                val updatedRows = dbQuery {
                    val task = TasksTable.selectAll().where { TasksTable.id eq taskIdParam }.singleOrNull()
                        ?: return@dbQuery -1
                    if (!isMember(actingUserId, task[TasksTable.workspaceId])) return@dbQuery -2
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
                call.receive<TaskRequest>().withCanonicalStatus()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.BadRequest, "Invalid request body")
                return@put
            }

            if (request.taskName.isBlank()) return@put call.respond(HttpStatusCode.BadRequest, "Task name cannot be blank")
            if (request.taskName.length > 255) return@put call.respond(HttpStatusCode.BadRequest, "Task name too long")
            if (request.status !in listOf("TODO", "IN_PROGRESS", "DONE")) return@put call.respond(HttpStatusCode.BadRequest, "Invalid status")

            var previousStatus: String? = null
            var progression: RecordResult? = null
            val updateResult = dbQuery {
                progression = null // dbQuery may re-run this block after a serialization retry
                val existingTask = TasksTable.selectAll().where { TasksTable.id eq taskId }.singleOrNull()
                    ?: return@dbQuery -1
                previousStatus = existingTask[TasksTable.status]
                if (!isMember(actingUserId, existingTask[TasksTable.workspaceId]) ||
                    !isMember(actingUserId, request.workspaceId)
                ) return@dbQuery -2
                if (request.assignedToUserId != null && !isMember(request.assignedToUserId, request.workspaceId)) {
                    return@dbQuery -4
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

                if (reassigning && actingUserId != existingTask[TasksTable.createdByUserId]) return@dbQuery -3
                if (editingContentOrStatus && actingUserId != existingTask[TasksTable.assignedToUserId]) return@dbQuery -3
                if (editingPlanning &&
                    actingUserId != existingTask[TasksTable.assignedToUserId] &&
                    actingUserId != existingTask[TasksTable.createdByUserId]
                ) return@dbQuery -3

                val now = System.currentTimeMillis()
                val dueDateChanged = nextDueDate != currentDueDate
                val priorityChanged = nextPriority != currentPriority
                val enteringDone = request.status == RewardPolicy.DONE && existingTask[TasksTable.status] != RewardPolicy.DONE
                val leavingDone = request.status != RewardPolicy.DONE && existingTask[TasksTable.status] == RewardPolicy.DONE

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
                    if (dueDateChanged) {
                        it[TasksTable.reminderSentAt] = null
                        it[TasksTable.dueDateSetBy] = actingUserId
                        it[TasksTable.dueDateSetAt] = now
                    }
                    if (priorityChanged) it[TasksTable.prioritySetBy] = actingUserId
                    if (enteringDone) it[TasksTable.completedAt] = now
                    if (leavingDone) it[TasksTable.completedAt] = null
                    it[TasksTable.updatedAt] = now
                }

                // Career progression: rewarded in this transaction, so it commits with the completion.
                if (enteringDone) {
                    val decision = RewardPolicy.taskCompletion(
                        TaskCompletionFacts(
                            actorId = actingUserId,
                            assigneeId = request.assignedToUserId,
                            createdById = existingTask[TasksTable.createdByUserId],
                            isDeleted = existingTask[TasksTable.isDeleted],
                            previousStatus = existingTask[TasksTable.status],
                            newStatus = request.status,
                            priority = nextPriority,
                            prioritySetBy = if (priorityChanged) actingUserId else existingTask[TasksTable.prioritySetBy],
                            checklistSize = nextChecklist.size,
                            labels = nextLabels,
                            dueDate = nextDueDate,
                            dueDateSetBy = if (dueDateChanged) actingUserId else existingTask[TasksTable.dueDateSetBy],
                            dueDateSetAt = if (dueDateChanged) now else existingTask[TasksTable.dueDateSetAt],
                            completedAt = now,
                            teamApproved = ProgressionService.isTeamApprovedTask(taskId)
                        ),
                        ProgressionCatalogs.current.policy
                    )
                    if (decision != null) {
                        progression = ProgressionService.record(
                            ContributionEvent(
                                idempotencyKey = "task-complete:$taskId",
                                userId = actingUserId,
                                eventType = "TASK_COMPLETED",
                                source = "TASKS",
                                referenceType = "TASK",
                                referenceId = taskId.toString(),
                                workspaceId = request.workspaceId,
                                occurredAt = now,
                                decision = decision
                            )
                        )
                    }
                }
            }

            when (updateResult) {
                -1   -> return@put call.respond(HttpStatusCode.NotFound, "Task not found")
                -2   -> return@put call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                -3   -> return@put call.respond(HttpStatusCode.Forbidden,
                    "Only the assignee can edit or move this task; only the creator can reassign it; due date and priority can be changed by the creator or assignee")
                -4   -> return@put call.respond(HttpStatusCode.BadRequest, "Assignee is not a member of the workspace")
            }

            val updatedTask = dbQuery {
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
                progression?.let { publishProgressionNotifications(it.notifications) }
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
                    val updaterName = dbQuery {
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
                    recordSyncPage("tasks", page.rows.size)
                    call.appendSyncHeaders(page.nextCursor, page.reset, page.nextPageToken)
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

/** A task request rejected for a reason the client can fix (reported as 400). */
private class TaskValidationException(message: String) : Exception(message)

/**
 * Android builds before this fix sent the enum name "TO_DO" instead of "TODO", so moving a task back
 * to To Do was rejected. Accept that spelling and store the canonical value, so installed apps work.
 */
private fun TaskRequest.withCanonicalStatus(): TaskRequest =
    if (status == "TO_DO") copy(status = "TODO") else this
