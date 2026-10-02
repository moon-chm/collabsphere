package com.collabsphere.util

import com.collabsphere.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Feature E: Cross-Platform Assignee Synchronization.
 *
 * Source-of-truth model:
 * - GitHub is authoritative for GitHub issue data.
 * - CollabSphere is authoritative for CollabSphere task data.
 * - This service is the integration layer that reconciles the two.
 *
 * Loop prevention:
 * - Every sync records its source ("GITHUB" or "COLLABSPHERE") and the
 *   resulting assignee set hash.
 * - Before performing a write, the service compares the current set hash
 *   against the stored hash. If identical, no write occurs.
 * - When a write is caused by sync, the source is recorded so that the
 *   subsequent webhook/event is recognized as an echo and ignored.
 *
 * Conflict strategy: last-writer-wins at the set level, but no silent
 * overwrites — partial failures are recorded with PARTIAL status.
 */
object GitHubAssigneeSyncService {

    private const val MAX_GITHUB_ASSIGNEES = 10

    /**
     * Compute a deterministic hash of an assignee set (sorted GitHub user IDs).
     * Used to detect no-op synchronizations.
     */
    fun assigneeSetHash(githubUserIds: Collection<Long>): String =
        githubUserIds.sorted().joinToString(",")

    // ─── Identity Mapping ─────────────────────────────────────────────────

    /**
     * Ensure identity mappings exist for all workspace members who have
     * a GitHub connection. Called lazily when sync is enabled.
     */
    fun refreshIdentityMappings(workspaceId: Int) {
        val members = (WorkspaceMembersTable innerJoin UsersTable)
            .selectAll()
            .where { WorkspaceMembersTable.workspaceId eq workspaceId }
            .map { it[UsersTable.id] }

        val connections = GitHubConnectionsTable.selectAll()
            .where { GitHubConnectionsTable.userId inList members }
            .map {
                Triple(
                    it[GitHubConnectionsTable.userId],
                    it[GitHubConnectionsTable.githubUserId],
                    it[GitHubConnectionsTable.githubUsername]
                )
            }

        connections.forEach { (userId, ghId, ghLogin) ->
            val existing = GitHubIdentityMappingTable.selectAll()
                .where {
                    (GitHubIdentityMappingTable.workspaceId eq workspaceId) and
                        (GitHubIdentityMappingTable.userId eq userId)
                }.singleOrNull()

            if (existing == null) {
                GitHubIdentityMappingTable.insertIgnore {
                    it[GitHubIdentityMappingTable.userId] = userId
                    it[GitHubIdentityMappingTable.githubUserId] = ghId
                    it[GitHubIdentityMappingTable.githubLogin] = ghLogin
                    it[GitHubIdentityMappingTable.workspaceId] = workspaceId
                }
            } else if (existing[GitHubIdentityMappingTable.githubLogin] != ghLogin) {
                // GitHub login changed — update
                GitHubIdentityMappingTable.update({
                    (GitHubIdentityMappingTable.workspaceId eq workspaceId) and
                        (GitHubIdentityMappingTable.userId eq userId)
                }) {
                    it[GitHubIdentityMappingTable.githubLogin] = ghLogin
                    it[GitHubIdentityMappingTable.updatedAt] = System.currentTimeMillis()
                }
            }
        }
    }

    /** Map a GitHub user ID to a CollabSphere user ID within a workspace. */
    fun resolveCollabSphereUser(workspaceId: Int, githubUserId: Long): Int? =
        GitHubIdentityMappingTable.selectAll()
            .where {
                (GitHubIdentityMappingTable.workspaceId eq workspaceId) and
                    (GitHubIdentityMappingTable.githubUserId eq githubUserId)
            }
            .singleOrNull()
            ?.get(GitHubIdentityMappingTable.userId)

    /** Map a CollabSphere user ID to a GitHub login within a workspace. */
    fun resolveGitHubLogin(workspaceId: Int, userId: Int): String? =
        GitHubIdentityMappingTable.selectAll()
            .where {
                (GitHubIdentityMappingTable.workspaceId eq workspaceId) and
                    (GitHubIdentityMappingTable.userId eq userId)
            }
            .singleOrNull()
            ?.get(GitHubIdentityMappingTable.githubLogin)

    /** Map a CollabSphere user ID to a GitHub user ID within a workspace. */
    fun resolveGitHubUserId(workspaceId: Int, userId: Int): Long? =
        GitHubIdentityMappingTable.selectAll()
            .where {
                (GitHubIdentityMappingTable.workspaceId eq workspaceId) and
                    (GitHubIdentityMappingTable.userId eq userId)
            }
            .singleOrNull()
            ?.get(GitHubIdentityMappingTable.githubUserId)

    // ─── Sync State ───────────────────────────────────────────────────────

    data class SyncRecord(
        val taskId: Int,
        val repositoryId: Int,
        val issueNumber: Int,
        val enabled: Boolean,
        val lastSyncSource: String,
        val lastSyncHash: String,
        val syncStatus: String
    )

    /** Get or create a sync record for a linked task/issue pair. */
    fun getOrCreateSyncRecord(taskId: Int, repositoryId: Int, issueNumber: Int): SyncRecord {
        val existing = GitHubAssigneeSyncTable.selectAll()
            .where {
                (GitHubAssigneeSyncTable.taskId eq taskId) and
                    (GitHubAssigneeSyncTable.repositoryId eq repositoryId) and
                    (GitHubAssigneeSyncTable.issueNumber eq issueNumber)
            }.singleOrNull()

        if (existing != null) {
            return SyncRecord(
                taskId = existing[GitHubAssigneeSyncTable.taskId],
                repositoryId = existing[GitHubAssigneeSyncTable.repositoryId],
                issueNumber = existing[GitHubAssigneeSyncTable.issueNumber],
                enabled = existing[GitHubAssigneeSyncTable.enabled],
                lastSyncSource = existing[GitHubAssigneeSyncTable.lastSyncSource],
                lastSyncHash = existing[GitHubAssigneeSyncTable.lastSyncHash],
                syncStatus = existing[GitHubAssigneeSyncTable.syncStatus]
            )
        }

        GitHubAssigneeSyncTable.insertIgnore {
            it[GitHubAssigneeSyncTable.taskId] = taskId
            it[GitHubAssigneeSyncTable.repositoryId] = repositoryId
            it[GitHubAssigneeSyncTable.issueNumber] = issueNumber
        }

        return SyncRecord(taskId, repositoryId, issueNumber, true, "INITIAL", "", "SYNCED")
    }

    /** Update sync state after a synchronization attempt. */
    fun updateSyncState(
        taskId: Int,
        repositoryId: Int,
        issueNumber: Int,
        source: String,
        hash: String,
        status: String,
        error: String? = null
    ) {
        GitHubAssigneeSyncTable.update({
            (GitHubAssigneeSyncTable.taskId eq taskId) and
                (GitHubAssigneeSyncTable.repositoryId eq repositoryId) and
                (GitHubAssigneeSyncTable.issueNumber eq issueNumber)
        }) {
            it[GitHubAssigneeSyncTable.lastSyncSource] = source
            it[GitHubAssigneeSyncTable.lastSyncHash] = hash
            it[GitHubAssigneeSyncTable.lastSyncAt] = System.currentTimeMillis()
            it[GitHubAssigneeSyncTable.syncStatus] = status
            it[GitHubAssigneeSyncTable.syncError] = error
        }
    }

    // ─── GitHub → CollabSphere ────────────────────────────────────────────

    /**
     * Called when a GitHub issue webhook arrives with assignee changes.
     * Resolves linked tasks and updates CollabSphere task assignees.
     *
     * Loop prevention: if lastSyncSource == "COLLABSPHERE" and the hash matches,
     * this is an echo of our own write — skip.
     */
    fun syncFromGitHub(
        repositoryId: Int,
        issueNumber: Int,
        githubAssigneeIds: List<Long>,
        workspaceId: Int
    ): String {
        val newHash = assigneeSetHash(githubAssigneeIds)

        // Find linked tasks
        val linkedTasks = GitHubTaskLinksTable.selectAll()
            .where {
                (GitHubTaskLinksTable.repositoryId eq repositoryId) and
                    (GitHubTaskLinksTable.kind eq "issue") and
                    (GitHubTaskLinksTable.ref eq issueNumber.toString())
            }
            .map { it[GitHubTaskLinksTable.taskId] }

        if (linkedTasks.isEmpty()) return "NO_LINKED_TASKS"

        var syncResult = "SYNCED"

        for (taskId in linkedTasks) {
            val syncRecord = getOrCreateSyncRecord(taskId, repositoryId, issueNumber)
            if (!syncRecord.enabled) continue

            // Loop prevention: if the hash hasn't changed, no-op
            if (syncRecord.lastSyncHash == newHash) continue

            // Echo detection: if we just wrote this to GitHub and the webhook echoes it back, skip
            if (syncRecord.lastSyncSource == "COLLABSPHERE" && syncRecord.lastSyncHash == newHash) continue

            // Map GitHub users to CollabSphere users
            val primaryAssignee = if (githubAssigneeIds.isNotEmpty()) {
                resolveCollabSphereUser(workspaceId, githubAssigneeIds.first())
            } else null

            // CollabSphere tasks have a single assignee (assignedToUserId)
            // We map the first GitHub assignee that has a mapping
            val mappedUserId = githubAssigneeIds
                .firstNotNullOfOrNull { resolveCollabSphereUser(workspaceId, it) }

            val task = TasksTable.selectAll()
                .where { (TasksTable.id eq taskId) and (TasksTable.isDeleted eq false) }
                .singleOrNull() ?: continue

            val currentAssignee = task[TasksTable.assignedToUserId]

            // Only update if different
            if (currentAssignee != mappedUserId) {
                TasksTable.update({ TasksTable.id eq taskId }) {
                    it[TasksTable.assignedToUserId] = mappedUserId
                    it[TasksTable.updatedAt] = System.currentTimeMillis()
                }
            }

            updateSyncState(taskId, repositoryId, issueNumber, "GITHUB", newHash, "SYNCED")
        }

        return syncResult
    }

    // ─── CollabSphere → GitHub ────────────────────────────────────────────

    data class GitHubSyncTarget(
        val repositoryId: Int,
        val issueNumber: Int,
        val repoFullName: String,
        val installationId: Long,
        val userToken: String?
    )

    /**
     * Called after a CollabSphere task assignee changes.
     * Finds linked GitHub issues and updates their assignees.
     *
     * Returns a list of sync results for reporting.
     */
    suspend fun syncToGitHub(taskId: Int, workspaceId: Int, newAssigneeUserId: Int?): List<String> {
        val results = mutableListOf<String>()

        val targets = transaction {
            refreshIdentityMappings(workspaceId)

            GitHubTaskLinksTable.selectAll()
                .where {
                    (GitHubTaskLinksTable.taskId eq taskId) and
                        (GitHubTaskLinksTable.kind eq "issue")
                }
                .mapNotNull { link ->
                    val repoRow = GitHubRepositoriesTable.selectAll()
                        .where { GitHubRepositoriesTable.id eq link[GitHubTaskLinksTable.repositoryId] }
                        .singleOrNull() ?: return@mapNotNull null
                    val connRow = GitHubConnectionsTable.selectAll()
                        .where { GitHubConnectionsTable.id eq repoRow[GitHubRepositoriesTable.connectionId] }
                        .singleOrNull() ?: return@mapNotNull null

                    val syncRecord = getOrCreateSyncRecord(
                        taskId,
                        link[GitHubTaskLinksTable.repositoryId],
                        link[GitHubTaskLinksTable.ref].toIntOrNull() ?: return@mapNotNull null
                    )
                    if (!syncRecord.enabled) return@mapNotNull null

                    GitHubSyncTarget(
                        repositoryId = link[GitHubTaskLinksTable.repositoryId],
                        issueNumber = link[GitHubTaskLinksTable.ref].toIntOrNull() ?: return@mapNotNull null,
                        repoFullName = repoRow[GitHubRepositoriesTable.fullName],
                        installationId = connRow[GitHubConnectionsTable.installationId],
                        userToken = CryptoService.decrypt(connRow[GitHubConnectionsTable.accessTokenEncrypted])
                    )
                }
        }

        for (target in targets) {
            val token = GitHubService.repoAccessToken(target.installationId, target.userToken)
            if (token == null) {
                results.add("FAILED:no_token")
                continue
            }

            // Determine desired GitHub assignee login
            val desiredLogins = if (newAssigneeUserId != null) {
                val login = transaction { resolveGitHubLogin(workspaceId, newAssigneeUserId) }
                if (login != null) listOf(login) else emptyList()
            } else {
                emptyList()
            }

            // Get current GitHub assignees
            val currentAssignees = GitHubService.getIssueAssignees(token, target.repoFullName, target.issueNumber)
            if (currentAssignees == null) {
                results.add("FAILED:api_error")
                transaction {
                    updateSyncState(taskId, target.repositoryId, target.issueNumber, "COLLABSPHERE", "", "FAILED", "Could not fetch current assignees")
                }
                continue
            }

            val currentLogins = currentAssignees.map { it.login }.toSet()
            val desiredSet = desiredLogins.toSet()

            // Compare sets — if identical, no-op
            if (currentLogins == desiredSet) {
                val hash = assigneeSetHash(currentAssignees.map { it.id })
                transaction {
                    updateSyncState(taskId, target.repositoryId, target.issueNumber, "COLLABSPHERE", hash, "SYNCED")
                }
                results.add("SYNCED:no_change")
                continue
            }

            // Compute diff
            val toAdd = desiredSet - currentLogins
            val toRemove = currentLogins - desiredSet

            var success = true

            if (toRemove.isNotEmpty()) {
                success = success && GitHubService.removeIssueAssignees(token, target.repoFullName, target.issueNumber, toRemove.toList())
            }
            if (toAdd.isNotEmpty()) {
                if (toAdd.size > MAX_GITHUB_ASSIGNEES) {
                    results.add("FAILED:too_many_assignees")
                    transaction {
                        updateSyncState(taskId, target.repositoryId, target.issueNumber, "COLLABSPHERE", "", "FAILED", "Too many assignees (max $MAX_GITHUB_ASSIGNEES)")
                    }
                    continue
                }
                success = success && GitHubService.addIssueAssignees(token, target.repoFullName, target.issueNumber, toAdd.toList())
            }

            // Compute the resulting hash
            val resultHash = if (success) {
                val desiredGhIds = transaction {
                    desiredLogins.mapNotNull { login ->
                        GitHubIdentityMappingTable.selectAll()
                            .where {
                                (GitHubIdentityMappingTable.workspaceId eq workspaceId) and
                                    (GitHubIdentityMappingTable.githubLogin eq login)
                            }.singleOrNull()?.get(GitHubIdentityMappingTable.githubUserId)
                    }
                }
                assigneeSetHash(desiredGhIds)
            } else ""

            val status = if (success) "SYNCED" else "PARTIAL"
            transaction {
                updateSyncState(taskId, target.repositoryId, target.issueNumber, "COLLABSPHERE", resultHash, status,
                    if (!success) "Partial failure during GitHub API calls" else null)
            }
            results.add("$status")
        }

        return results
    }

    // ─── Sync Toggle ──────────────────────────────────────────────────────

    fun setSyncEnabled(taskId: Int, repositoryId: Int, issueNumber: Int, enabled: Boolean) {
        val existing = GitHubAssigneeSyncTable.selectAll()
            .where {
                (GitHubAssigneeSyncTable.taskId eq taskId) and
                    (GitHubAssigneeSyncTable.repositoryId eq repositoryId) and
                    (GitHubAssigneeSyncTable.issueNumber eq issueNumber)
            }.singleOrNull()

        if (existing != null) {
            GitHubAssigneeSyncTable.update({
                (GitHubAssigneeSyncTable.taskId eq taskId) and
                    (GitHubAssigneeSyncTable.repositoryId eq repositoryId) and
                    (GitHubAssigneeSyncTable.issueNumber eq issueNumber)
            }) {
                it[GitHubAssigneeSyncTable.enabled] = enabled
            }
        } else {
            GitHubAssigneeSyncTable.insertIgnore {
                it[GitHubAssigneeSyncTable.taskId] = taskId
                it[GitHubAssigneeSyncTable.repositoryId] = repositoryId
                it[GitHubAssigneeSyncTable.issueNumber] = issueNumber
                it[GitHubAssigneeSyncTable.enabled] = enabled
            }
        }
    }
}
