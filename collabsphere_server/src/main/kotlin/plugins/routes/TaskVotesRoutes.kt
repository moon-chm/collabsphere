package plugins

import com.collabsphere.model.*
import dto.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.slf4j.LoggerFactory
import kotlin.math.ceil

private val logger = LoggerFactory.getLogger("TaskVotesRoutes")

/**
 * Task Voting System routes.
 * Handles proposals for task creation and deletion with democratic team voting.
 * Mounted inside `authenticate("auth-jwt")` in configureRouting().
 */
internal fun Route.taskVotesRoutes() {
    route("/api/task-votes") {

        // ── SUBMIT PROPOSAL ───────────────────────────────────────────────────
        post("/proposals") {
            try {
                val actingUserId = call.authenticatedUserId()
                val request = call.receive<TaskVoteProposalRequest>()

                if (request.type !in listOf("CREATE", "DELETE")) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Proposal type must be CREATE or DELETE")
                }

                if (request.type == "CREATE") {
                    if (request.taskName.isNullOrBlank()) {
                        return@post call.respond(HttpStatusCode.BadRequest, "Task name is required for CREATE proposal")
                    }
                    if (request.taskName.length > 255) {
                        return@post call.respond(HttpStatusCode.BadRequest, "Task name cannot exceed 255 characters")
                    }
                } else if (request.type == "DELETE") {
                    if (request.taskId == null) {
                        return@post call.respond(HttpStatusCode.BadRequest, "taskId is required for DELETE proposal")
                    }
                }

                val now = System.currentTimeMillis()
                val deadlineHours = (request.deadlineHours ?: 48).coerceIn(1, 168)
                val deadline = now + (deadlineHours * 3600 * 1000L)

                data class ProposalCreationResult(
                    val response: TaskVoteProposalResponse,
                    val eligibleVoters: List<Int>,
                    val isAutoApproved: Boolean
                )

                val result = dbQuery {
                    if (!isMember(actingUserId, request.workspaceId)) return@dbQuery null

                    if (request.type == "CREATE" && request.assignedToUserId != null) {
                        if (!isMember(request.assignedToUserId, request.workspaceId)) {
                            throw IllegalArgumentException("Assignee is not a member of the workspace")
                        }
                    }

                    if (request.type == "DELETE") {
                        val targetTask = TasksTable.selectAll().where {
                            (TasksTable.id eq request.taskId!!) and
                            (TasksTable.workspaceId eq request.workspaceId) and
                            (TasksTable.isDeleted eq false)
                        }.singleOrNull() ?: throw IllegalArgumentException("Target task not found or already deleted")

                        // Prevent duplicate active delete proposals for the same task
                        val activePendingDelete = TaskVoteProposalsTable.selectAll().where {
                            (TaskVoteProposalsTable.taskId eq request.taskId!!) and
                            (TaskVoteProposalsTable.type eq "DELETE") and
                            (TaskVoteProposalsTable.status eq "PENDING") and
                            (TaskVoteProposalsTable.deadline greater now)
                        }.singleOrNull()
                        if (activePendingDelete != null) {
                            throw IllegalStateException("A pending delete vote already exists for this task")
                        }
                    }

                    // Idempotency check
                    val existing = request.idempotencyKey?.let { key ->
                        TaskVoteProposalsTable.selectAll().where { TaskVoteProposalsTable.idempotencyKey eq key }.singleOrNull()
                    }
                    if (existing != null) {
                        val propId = existing[TaskVoteProposalsTable.id]
                        val myVote = TaskVotesTable.selectAll().where {
                            (TaskVotesTable.proposalId eq propId) and (TaskVotesTable.userId eq actingUserId)
                        }.singleOrNull()?.get(TaskVotesTable.vote)

                        return@dbQuery ProposalCreationResult(
                            response = TaskVoteProposalResponse(
                                id = propId,
                                workspaceId = existing[TaskVoteProposalsTable.workspaceId],
                                proposerId = existing[TaskVoteProposalsTable.proposerId],
                                type = existing[TaskVoteProposalsTable.type],
                                status = existing[TaskVoteProposalsTable.status],
                                taskId = existing[TaskVoteProposalsTable.taskId],
                                taskName = existing[TaskVoteProposalsTable.taskName],
                                taskDescription = existing[TaskVoteProposalsTable.taskDescription],
                                assignedToUserId = existing[TaskVoteProposalsTable.assignedToUserId],
                                priority = existing[TaskVoteProposalsTable.priority],
                                dueDate = existing[TaskVoteProposalsTable.dueDate],
                                checklist = TaskExtras.decodeChecklist(existing[TaskVoteProposalsTable.checklist]),
                                labels = TaskExtras.decodeLabels(existing[TaskVoteProposalsTable.labels]),
                                deleteReason = existing[TaskVoteProposalsTable.deleteReason],
                                eligibleCount = existing[TaskVoteProposalsTable.eligibleCount],
                                approvalThreshold = existing[TaskVoteProposalsTable.approvalThreshold],
                                approveCount = existing[TaskVoteProposalsTable.approveCount],
                                rejectCount = existing[TaskVoteProposalsTable.rejectCount],
                                resolvedAt = existing[TaskVoteProposalsTable.resolvedAt],
                                createdTaskId = existing[TaskVoteProposalsTable.createdTaskId],
                                deadline = existing[TaskVoteProposalsTable.deadline],
                                createdAt = existing[TaskVoteProposalsTable.createdAt],
                                myVote = myVote
                            ),
                            eligibleVoters = emptyList(),
                            isAutoApproved = false
                        )
                    }

                    // Count eligible members at proposal time
                    val memberUserIds = WorkspaceMembersTable.selectAll()
                        .where { WorkspaceMembersTable.workspaceId eq request.workspaceId }
                        .map { it[WorkspaceMembersTable.userId] }

                    val eligibleCount = memberUserIds.size.coerceAtLeast(1)
                    val approvalThreshold = (eligibleCount / 2) + 1
                    val isSoloWorkspace = (eligibleCount == 1)

                    val normalizedChecklist = TaskExtras.normalizeChecklist(request.checklist.orEmpty())
                    val normalizedLabels = TaskExtras.normalizeLabels(request.labels.orEmpty())
                    val normalizedPriority = TaskPriorities.normalize(request.priority) ?: "MEDIUM"
                    val normalizedDueDate = request.dueDate?.takeIf { it > 0 }

                    var initialStatus = "PENDING"
                    var resolvedAt: Long? = null
                    var createdTaskId: Int? = null

                    if (isSoloWorkspace) {
                        initialStatus = "APPROVED"
                        resolvedAt = now
                        if (request.type == "CREATE") {
                            createdTaskId = TasksTable.insert {
                                it[createdByUserId] = actingUserId
                                it[assignedToUserId] = request.assignedToUserId
                                it[workspaceId] = request.workspaceId
                                it[taskName] = request.taskName!!
                                it[taskDescription] = request.taskDescription.orEmpty()
                                it[status] = "TODO"
                                it[dueDate] = normalizedDueDate
                                it[priority] = normalizedPriority
                                it[checklist] = TaskExtras.encodeChecklist(normalizedChecklist)
                                it[labels] = TaskExtras.encodeLabels(normalizedLabels)
                                it[isDeleted] = false
                                it[updatedAt] = now
                            }[TasksTable.id]
                        } else if (request.type == "DELETE") {
                            TasksTable.update({ TasksTable.id eq request.taskId!! }) {
                                it[isDeleted] = true
                                it[updatedAt] = now
                            }
                        }
                    }

                    val proposalId = TaskVoteProposalsTable.insert {
                        it[TaskVoteProposalsTable.workspaceId] = request.workspaceId
                        it[TaskVoteProposalsTable.proposerId] = actingUserId
                        it[TaskVoteProposalsTable.type] = request.type
                        it[TaskVoteProposalsTable.status] = initialStatus
                        it[TaskVoteProposalsTable.taskId] = request.taskId
                        it[TaskVoteProposalsTable.taskName] = request.taskName
                        it[TaskVoteProposalsTable.taskDescription] = request.taskDescription
                        it[TaskVoteProposalsTable.assignedToUserId] = request.assignedToUserId
                        it[TaskVoteProposalsTable.priority] = normalizedPriority
                        it[TaskVoteProposalsTable.dueDate] = normalizedDueDate
                        it[TaskVoteProposalsTable.checklist] = TaskExtras.encodeChecklist(normalizedChecklist)
                        it[TaskVoteProposalsTable.labels] = TaskExtras.encodeLabels(normalizedLabels)
                        it[TaskVoteProposalsTable.deleteReason] = request.deleteReason
                        it[TaskVoteProposalsTable.eligibleCount] = eligibleCount
                        it[TaskVoteProposalsTable.approvalThreshold] = approvalThreshold
                        it[TaskVoteProposalsTable.approveCount] = 1 // Proposer automatically votes APPROVE
                        it[TaskVoteProposalsTable.rejectCount] = 0
                        it[TaskVoteProposalsTable.resolvedAt] = resolvedAt
                        it[TaskVoteProposalsTable.createdTaskId] = createdTaskId
                        it[TaskVoteProposalsTable.deadline] = deadline
                        it[TaskVoteProposalsTable.createdAt] = now
                        it[TaskVoteProposalsTable.updatedAt] = now
                        it[TaskVoteProposalsTable.idempotencyKey] = request.idempotencyKey
                    }[TaskVoteProposalsTable.id]

                    // Record proposer's automatic vote
                    TaskVotesTable.insert {
                        it[TaskVotesTable.proposalId] = proposalId
                        it[userId] = actingUserId
                        it[vote] = "APPROVE"
                        it[votedAt] = now
                    }

                    ProposalCreationResult(
                        response = TaskVoteProposalResponse(
                            id = proposalId,
                            workspaceId = request.workspaceId,
                            proposerId = actingUserId,
                            type = request.type,
                            status = initialStatus,
                            taskId = request.taskId,
                            taskName = request.taskName,
                            taskDescription = request.taskDescription,
                            assignedToUserId = request.assignedToUserId,
                            priority = normalizedPriority,
                            dueDate = normalizedDueDate,
                            checklist = normalizedChecklist,
                            labels = normalizedLabels,
                            deleteReason = request.deleteReason,
                            eligibleCount = eligibleCount,
                            approvalThreshold = approvalThreshold,
                            approveCount = 1,
                            rejectCount = 0,
                            resolvedAt = resolvedAt,
                            createdTaskId = createdTaskId,
                            deadline = deadline,
                            createdAt = now,
                            myVote = "APPROVE"
                        ),
                        eligibleVoters = memberUserIds.filter { it != actingUserId },
                        isAutoApproved = isSoloWorkspace
                    )
                }

                if (result == null) {
                    return@post call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                }

                // Push notifications to eligible voters if pending
                if (!result.isAutoApproved && result.eligibleVoters.isNotEmpty()) {
                    val proposerName = dbReadQuery {
                        UsersTable.selectAll().where { UsersTable.id eq actingUserId }
                            .singleOrNull()?.get(UsersTable.username) ?: "A team member"
                    }
                    val notifTitle = if (request.type == "CREATE") {
                        "$proposerName proposed a new task"
                    } else {
                        "$proposerName proposed deleting a task"
                    }
                    val notifBody = if (request.type == "CREATE") {
                        "\"${request.taskName}\" requires team vote"
                    } else {
                        "Deletion of task #${request.taskId}: ${request.deleteReason?.take(80) ?: "Requires team vote"}"
                    }

                    for (voterId in result.eligibleVoters) {
                        createAndPushNotification(
                            recipientId = voterId,
                            actorId = actingUserId,
                            type = "TASK_VOTE_PROPOSAL",
                            title = notifTitle,
                            body = notifBody,
                            workspaceId = request.workspaceId,
                            referenceId = result.response.id
                        )
                    }
                }

                call.respond(HttpStatusCode.Created, result.response)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.Conflict, e.message ?: "Conflict")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.error("[TaskVotes] Failed to create proposal", e)
                call.respond(HttpStatusCode.InternalServerError, "Failed to submit proposal")
            }
        }

        // ── CAST VOTE ─────────────────────────────────────────────────────────
        post("/{proposalId}/vote") {
            try {
                val proposalIdParam = call.parameters["proposalId"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing or invalid proposalId")
                val actingUserId = call.authenticatedUserId()
                val request = call.receive<CastVoteRequest>()

                if (request.vote !in listOf("APPROVE", "REJECT")) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Vote must be APPROVE or REJECT")
                }

                data class VoteResolution(
                    val response: TaskVoteProposalResponse,
                    val newOutcome: String,
                    val previousStatus: String,
                    val proposerId: Int,
                    val workspaceId: Int,
                    val taskName: String?
                )

                val now = System.currentTimeMillis()

                val resolution = dbQuery {
                    val proposal = TaskVoteProposalsTable.selectAll()
                        .where { TaskVoteProposalsTable.id eq proposalIdParam }
                        .singleOrNull() ?: return@dbQuery null

                    val workspaceId = proposal[TaskVoteProposalsTable.workspaceId]
                    if (!isMember(actingUserId, workspaceId)) {
                        throw IllegalAccessException("Not a member of this workspace")
                    }

                    val currentStatus = proposal[TaskVoteProposalsTable.status]
                    val deadline = proposal[TaskVoteProposalsTable.deadline]

                    if (currentStatus != "PENDING") {
                        throw IllegalStateException("Proposal has already been $currentStatus")
                    }

                    if (now > deadline) {
                        TaskVoteProposalsTable.update({ TaskVoteProposalsTable.id eq proposalIdParam }) {
                            it[status] = "EXPIRED"
                            it[resolvedAt] = now
                            it[updatedAt] = now
                        }
                        throw IllegalStateException("Proposal has expired")
                    }

                    // Check duplicate vote
                    val existingVote = TaskVotesTable.selectAll().where {
                        (TaskVotesTable.proposalId eq proposalIdParam) and
                        (TaskVotesTable.userId eq actingUserId)
                    }.singleOrNull()
                    if (existingVote != null) {
                        throw IllegalArgumentException("You have already voted on this proposal")
                    }

                    // Insert vote
                    TaskVotesTable.insert {
                        it[TaskVotesTable.proposalId] = proposalIdParam
                        it[userId] = actingUserId
                        it[vote] = request.vote
                        it[votedAt] = now
                    }

                    var approveCount = proposal[TaskVoteProposalsTable.approveCount]
                    var rejectCount = proposal[TaskVoteProposalsTable.rejectCount]
                    if (request.vote == "APPROVE") approveCount++ else rejectCount++

                    val eligibleCount = proposal[TaskVoteProposalsTable.eligibleCount]
                    val threshold = proposal[TaskVoteProposalsTable.approvalThreshold]

                    var newStatus = "PENDING"
                    var resolvedAt: Long? = null
                    var createdTaskId = proposal[TaskVoteProposalsTable.createdTaskId]

                    if (approveCount >= threshold) {
                        newStatus = "APPROVED"
                        resolvedAt = now
                        if (proposal[TaskVoteProposalsTable.type] == "CREATE") {
                            createdTaskId = TasksTable.insert {
                                it[createdByUserId] = proposal[TaskVoteProposalsTable.proposerId]
                                it[assignedToUserId] = proposal[TaskVoteProposalsTable.assignedToUserId]
                                it[TasksTable.workspaceId] = workspaceId
                                it[taskName] = proposal[TaskVoteProposalsTable.taskName].orEmpty()
                                it[taskDescription] = proposal[TaskVoteProposalsTable.taskDescription].orEmpty()
                                it[status] = "TODO"
                                it[dueDate] = proposal[TaskVoteProposalsTable.dueDate]
                                it[priority] = proposal[TaskVoteProposalsTable.priority] ?: "MEDIUM"
                                it[checklist] = proposal[TaskVoteProposalsTable.checklist]
                                it[labels] = proposal[TaskVoteProposalsTable.labels]
                                it[isDeleted] = false
                                it[updatedAt] = now
                            }[TasksTable.id]
                        } else if (proposal[TaskVoteProposalsTable.type] == "DELETE") {
                            val targetTaskId = proposal[TaskVoteProposalsTable.taskId]
                            if (targetTaskId != null) {
                                TasksTable.update({ TasksTable.id eq targetTaskId }) {
                                    it[isDeleted] = true
                                    it[updatedAt] = now
                                }
                            }
                        }
                    } else if (rejectCount > (eligibleCount - threshold)) {
                        // Mathematically impossible to reach approval threshold
                        newStatus = "REJECTED"
                        resolvedAt = now
                    }

                    TaskVoteProposalsTable.update({ TaskVoteProposalsTable.id eq proposalIdParam }) {
                        it[TaskVoteProposalsTable.approveCount] = approveCount
                        it[TaskVoteProposalsTable.rejectCount] = rejectCount
                        it[status] = newStatus
                        it[TaskVoteProposalsTable.resolvedAt] = resolvedAt
                        it[TaskVoteProposalsTable.createdTaskId] = createdTaskId
                        it[updatedAt] = now
                    }

                    VoteResolution(
                        response = TaskVoteProposalResponse(
                            id = proposalIdParam,
                            workspaceId = workspaceId,
                            proposerId = proposal[TaskVoteProposalsTable.proposerId],
                            type = proposal[TaskVoteProposalsTable.type],
                            status = newStatus,
                            taskId = proposal[TaskVoteProposalsTable.taskId],
                            taskName = proposal[TaskVoteProposalsTable.taskName],
                            taskDescription = proposal[TaskVoteProposalsTable.taskDescription],
                            assignedToUserId = proposal[TaskVoteProposalsTable.assignedToUserId],
                            priority = proposal[TaskVoteProposalsTable.priority],
                            dueDate = proposal[TaskVoteProposalsTable.dueDate],
                            checklist = TaskExtras.decodeChecklist(proposal[TaskVoteProposalsTable.checklist]),
                            labels = TaskExtras.decodeLabels(proposal[TaskVoteProposalsTable.labels]),
                            deleteReason = proposal[TaskVoteProposalsTable.deleteReason],
                            eligibleCount = eligibleCount,
                            approvalThreshold = threshold,
                            approveCount = approveCount,
                            rejectCount = rejectCount,
                            resolvedAt = resolvedAt,
                            createdTaskId = createdTaskId,
                            deadline = deadline,
                            createdAt = proposal[TaskVoteProposalsTable.createdAt],
                            myVote = request.vote
                        ),
                        newOutcome = newStatus,
                        previousStatus = currentStatus,
                        proposerId = proposal[TaskVoteProposalsTable.proposerId],
                        workspaceId = workspaceId,
                        taskName = proposal[TaskVoteProposalsTable.taskName]
                    )
                }

                if (resolution == null) {
                    return@post call.respond(HttpStatusCode.NotFound, "Proposal not found")
                }

                // If resolved, notify workspace members
                if (resolution.newOutcome in listOf("APPROVED", "REJECTED")) {
                    val outcomeTitle = if (resolution.newOutcome == "APPROVED") {
                        "Proposal Approved"
                    } else {
                        "Proposal Rejected"
                    }
                    val outcomeBody = "\"${resolution.taskName ?: "Task"}\" proposal was ${resolution.newOutcome.lowercase()} by team vote."

                    val membersToNotify = dbReadQuery {
                        WorkspaceMembersTable.selectAll()
                            .where { WorkspaceMembersTable.workspaceId eq resolution.workspaceId }
                            .map { it[WorkspaceMembersTable.userId] }
                    }

                    for (memberId in membersToNotify) {
                        createAndPushNotification(
                            recipientId = memberId,
                            actorId = actingUserId,
                            type = "TASK_VOTE_RESOLVED",
                            title = outcomeTitle,
                            body = outcomeBody,
                            workspaceId = resolution.workspaceId,
                            referenceId = resolution.response.id
                        )
                    }
                }

                call.respond(HttpStatusCode.OK, resolution.response)
            } catch (e: IllegalAccessException) {
                call.respond(HttpStatusCode.Forbidden, e.message ?: "Forbidden")
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.Conflict, e.message ?: "Proposal expired or already resolved")
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Invalid vote")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.error("[TaskVotes] Failed to cast vote", e)
                call.respond(HttpStatusCode.InternalServerError, "Failed to cast vote")
            }
        }

        // ── LIST PROPOSALS FOR WORKSPACE ───────────────────────────────────────
        get("/proposals/workspace/{workspaceId}") {
            try {
                val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing or invalid workspaceId")
                val actingUserId = call.authenticatedUserId()
                val statusFilter = call.request.queryParameters["status"] // optional

                val now = System.currentTimeMillis()

                val proposals = dbQuery {
                    if (!isMember(actingUserId, workspaceIdParam)) return@dbQuery null

                    // Auto-expire any overdue pending proposals
                    TaskVoteProposalsTable.update({
                        (TaskVoteProposalsTable.workspaceId eq workspaceIdParam) and
                        (TaskVoteProposalsTable.status eq "PENDING") and
                        (TaskVoteProposalsTable.deadline less now)
                    }) {
                        it[status] = "EXPIRED"
                        it[resolvedAt] = now
                        it[updatedAt] = now
                    }

                    val query = TaskVoteProposalsTable.selectAll()
                        .where { TaskVoteProposalsTable.workspaceId eq workspaceIdParam }

                    if (!statusFilter.isNullOrBlank()) {
                        query.andWhere { TaskVoteProposalsTable.status eq statusFilter }
                    }

                    query.orderBy(TaskVoteProposalsTable.createdAt, SortOrder.DESC)
                        .map { row ->
                            val proposalId = row[TaskVoteProposalsTable.id]
                            val myVote = TaskVotesTable.selectAll().where {
                                (TaskVotesTable.proposalId eq proposalId) and
                                (TaskVotesTable.userId eq actingUserId)
                            }.singleOrNull()?.get(TaskVotesTable.vote)

                            TaskVoteProposalResponse(
                                id = proposalId,
                                workspaceId = row[TaskVoteProposalsTable.workspaceId],
                                proposerId = row[TaskVoteProposalsTable.proposerId],
                                type = row[TaskVoteProposalsTable.type],
                                status = row[TaskVoteProposalsTable.status],
                                taskId = row[TaskVoteProposalsTable.taskId],
                                taskName = row[TaskVoteProposalsTable.taskName],
                                taskDescription = row[TaskVoteProposalsTable.taskDescription],
                                assignedToUserId = row[TaskVoteProposalsTable.assignedToUserId],
                                priority = row[TaskVoteProposalsTable.priority],
                                dueDate = row[TaskVoteProposalsTable.dueDate],
                                checklist = TaskExtras.decodeChecklist(row[TaskVoteProposalsTable.checklist]),
                                labels = TaskExtras.decodeLabels(row[TaskVoteProposalsTable.labels]),
                                deleteReason = row[TaskVoteProposalsTable.deleteReason],
                                eligibleCount = row[TaskVoteProposalsTable.eligibleCount],
                                approvalThreshold = row[TaskVoteProposalsTable.approvalThreshold],
                                approveCount = row[TaskVoteProposalsTable.approveCount],
                                rejectCount = row[TaskVoteProposalsTable.rejectCount],
                                resolvedAt = row[TaskVoteProposalsTable.resolvedAt],
                                createdTaskId = row[TaskVoteProposalsTable.createdTaskId],
                                deadline = row[TaskVoteProposalsTable.deadline],
                                createdAt = row[TaskVoteProposalsTable.createdAt],
                                myVote = myVote
                            )
                        }
                }

                if (proposals == null) {
                    call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                } else {
                    call.respond(HttpStatusCode.OK, proposals)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.error("[TaskVotes] Failed to fetch proposals", e)
                call.respond(HttpStatusCode.InternalServerError, "Failed to retrieve proposals")
            }
        }

        // ── DELTA SYNC ─────────────────────────────────────────────────────────
        get("/sync/{workspaceId}") {
            try {
                val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing or invalid workspaceId")
                val actingUserId = call.authenticatedUserId()
                val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L

                val syncItems = dbReadQuery {
                    if (!isMember(actingUserId, workspaceIdParam)) return@dbReadQuery null

                    TaskVoteProposalsTable.selectAll()
                        .where {
                            (TaskVoteProposalsTable.workspaceId eq workspaceIdParam) and
                            (TaskVoteProposalsTable.updatedAt greaterEq since)
                        }
                        .orderBy(TaskVoteProposalsTable.updatedAt, SortOrder.ASC)
                        .map { row ->
                            val proposalId = row[TaskVoteProposalsTable.id]
                            val myVote = TaskVotesTable.selectAll().where {
                                (TaskVotesTable.proposalId eq proposalId) and
                                (TaskVotesTable.userId eq actingUserId)
                            }.singleOrNull()?.get(TaskVotesTable.vote)

                            TaskVoteProposalSyncResponse(
                                id = proposalId,
                                workspaceId = row[TaskVoteProposalsTable.workspaceId],
                                proposerId = row[TaskVoteProposalsTable.proposerId],
                                type = row[TaskVoteProposalsTable.type],
                                status = row[TaskVoteProposalsTable.status],
                                taskId = row[TaskVoteProposalsTable.taskId],
                                taskName = row[TaskVoteProposalsTable.taskName],
                                taskDescription = row[TaskVoteProposalsTable.taskDescription],
                                assignedToUserId = row[TaskVoteProposalsTable.assignedToUserId],
                                priority = row[TaskVoteProposalsTable.priority],
                                dueDate = row[TaskVoteProposalsTable.dueDate],
                                checklist = TaskExtras.decodeChecklist(row[TaskVoteProposalsTable.checklist]),
                                labels = TaskExtras.decodeLabels(row[TaskVoteProposalsTable.labels]),
                                deleteReason = row[TaskVoteProposalsTable.deleteReason],
                                eligibleCount = row[TaskVoteProposalsTable.eligibleCount],
                                approvalThreshold = row[TaskVoteProposalsTable.approvalThreshold],
                                approveCount = row[TaskVoteProposalsTable.approveCount],
                                rejectCount = row[TaskVoteProposalsTable.rejectCount],
                                resolvedAt = row[TaskVoteProposalsTable.resolvedAt],
                                createdTaskId = row[TaskVoteProposalsTable.createdTaskId],
                                deadline = row[TaskVoteProposalsTable.deadline],
                                createdAt = row[TaskVoteProposalsTable.createdAt],
                                updatedAt = row[TaskVoteProposalsTable.updatedAt],
                                myVote = myVote
                            )
                        }
                }

                if (syncItems == null) {
                    call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                } else {
                    call.respond(HttpStatusCode.OK, syncItems)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.error("[TaskVotes] Sync failed", e)
                call.respond(HttpStatusCode.InternalServerError, "Failed to sync task proposals")
            }
        }
    }
}
