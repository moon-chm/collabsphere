package plugins

import com.collabsphere.dto.GitHubAnalyticsResponse
import com.collabsphere.dto.GitHubChannelOption
import com.collabsphere.dto.GitHubNotifyChannelRequest
import com.collabsphere.dto.GitHubTaskLinkResponse
import com.collabsphere.model.ChannelsTable
import com.collabsphere.model.GitHubTaskLinksTable
import com.collabsphere.model.TasksTable
import com.collabsphere.dto.GitHubCommitItem
import com.collabsphere.dto.GitHubContributorStats
import com.collabsphere.dto.GitHubDailyCount
import com.collabsphere.dto.GitHubPullRequestItem
import com.collabsphere.dto.GitHubPullRequestPage
import com.collabsphere.dto.GitHubIssueItem
import com.collabsphere.dto.GitHubLinkedRepo
import com.collabsphere.model.GitHubIssuesTable
import com.collabsphere.util.toRecord
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import com.collabsphere.model.GitHubCommitsTable
import com.collabsphere.model.GitHubConnectionsTable
import com.collabsphere.model.GitHubPullRequestsTable
import com.collabsphere.model.GitHubRepositoriesTable
import com.collabsphere.model.WorkspacesTable
import com.collabsphere.model.UsersTable
import com.collabsphere.model.WorkspaceMembersTable
import com.collabsphere.util.AvatarGenerator
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.max
import com.collabsphere.util.CryptoService
import com.collabsphere.util.GitHubAuthService
import com.collabsphere.util.GitHubDataStore
import com.collabsphere.util.GitHubRepository
import com.collabsphere.util.GitHubService
import com.collabsphere.util.GitHubSyncManager
import com.collabsphere.util.SyncTrigger
import com.collabsphere.util.GitHubWebhookService
import com.collabsphere.util.JwtConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@Serializable
data class LinkRepoRequest(
    val repoId: Long,
    val fullName: String,
    val owner: String,
    val name: String,
    val isPrivate: Boolean,
    val htmlUrl: String,
    val defaultBranch: String
)

@Serializable
data class AvailableRepo(
    val id: Long,
    val fullName: String,
    val owner: String,
    val name: String,
    val isPrivate: Boolean,
    val htmlUrl: String,
    val defaultBranch: String
)

private data class GitHubWorkspaceAccess(
    val workspaceId: Int,
    val ownerId: Int,
    val callerId: Int
) {
    val isOwner: Boolean get() = ownerId == callerId
}

private suspend fun ApplicationCall.resolveGitHubAccess(requireOwner: Boolean): GitHubWorkspaceAccess? {
    val workspaceId = parameters["workspaceId"]?.toIntOrNull()
    if (workspaceId == null) {
        respond(HttpStatusCode.BadRequest, "Invalid workspace ID")
        return null
    }
    val callerId = authenticatedUserId()
    val ownerId = dbQuery {
        val ownerId = WorkspacesTable.selectAll()
            .where { (WorkspacesTable.id eq workspaceId) and (WorkspacesTable.isDeleted eq false) }
            .singleOrNull()
            ?.get(WorkspacesTable.userId)
        ownerId?.takeIf { it == callerId || isMember(callerId, workspaceId) }
    }
    if (ownerId == null) {
        respond(HttpStatusCode.NotFound, "Workspace not found")
        return null
    }
    if (requireOwner && ownerId != callerId) {
        respond(HttpStatusCode.Forbidden, "Only the workspace owner can manage GitHub")
        return null
    }
    return GitHubWorkspaceAccess(workspaceId, ownerId, callerId)
}

private data class MatchableMember(
    val userId: Int,
    val username: String,
    val avatarUrl: String
)

private class WorkspaceMemberIndex(
    private val byEmail: Map<String, MatchableMember>,
    private val byGitHubLogin: Map<String, MatchableMember>
) {
    fun match(email: String?): MatchableMember? {
        val normalized = email?.trim()?.lowercase() ?: return null
        byEmail[normalized]?.let { return it }
        val login = GITHUB_NOREPLY_EMAIL.matchEntire(normalized)?.groupValues?.get(1) ?: return null
        return byGitHubLogin[login]
    }
}

private val GITHUB_NOREPLY_EMAIL = Regex("(?:\\d+\\+)?([a-z0-9-]+)@users\\.noreply\\.github\\.com")

private fun workspaceMembersForMatching(workspaceId: Int): WorkspaceMemberIndex {
    val members = (WorkspaceMembersTable innerJoin UsersTable)
        .selectAll()
        .where { WorkspaceMembersTable.workspaceId eq workspaceId }
        .map {
            MatchableMember(
                userId = it[UsersTable.id],
                username = it[UsersTable.username],
                avatarUrl = AvatarGenerator.avatarUrlFor(it[UsersTable.id], it[UsersTable.avatarUrl])
            ) to it[UsersTable.email].trim().lowercase()
        }
    val byId = members.associate { (member, _) -> member.userId to member }
    val byGitHubLogin = GitHubConnectionsTable.selectAll()
        .where { GitHubConnectionsTable.userId inList byId.keys.toList() }
        .mapNotNull { row ->
            val member = byId[row[GitHubConnectionsTable.userId]] ?: return@mapNotNull null
            row[GitHubConnectionsTable.githubUsername].lowercase() to member
        }
        .toMap()
    return WorkspaceMemberIndex(
        byEmail = members.associate { (member, email) -> email to member },
        byGitHubLogin = byGitHubLogin
    )
}

private fun pullRequestItems(rows: List<ResultRow>, repositoryId: Int, repoUrl: String): List<GitHubPullRequestItem> {
    val ci = GitHubDataStore.ciStatusFor(repositoryId, rows.mapNotNull { it[GitHubPullRequestsTable.headSha] })
    return rows.map {
        GitHubPullRequestItem(
            number = it[GitHubPullRequestsTable.number],
            title = it[GitHubPullRequestsTable.title],
            state = it[GitHubPullRequestsTable.state],
            authorUsername = it[GitHubPullRequestsTable.authorUsername],
            createdAt = it[GitHubPullRequestsTable.createdAt],
            mergedAt = it[GitHubPullRequestsTable.mergedAt],
            url = "$repoUrl/pull/${it[GitHubPullRequestsTable.number]}",
            ciStatus = it[GitHubPullRequestsTable.headSha]?.let(ci::get)
        )
    }
}

private fun findConnection(userId: Int): ResultRow? =
    GitHubConnectionsTable.selectAll()
        .where { GitHubConnectionsTable.userId eq userId }
        .singleOrNull()

private const val GITHUB_APP_SLUG = "collabspheregithubfeat"
private const val ACTIVITY_DAYS = 14
private const val TOP_CONTRIBUTORS = 5
private const val RECENT_ITEMS = 10

private const val STALE_SYNC_MS = 10 * 60 * 1000L

private data class SyncTarget(
    val repositoryId: Int,
    val installationId: Long,
    val userToken: String?,
    val repoFullName: String,
    val branch: String,
    val lastSyncedAt: Long
) {
    fun start(trigger: SyncTrigger): Boolean =
        GitHubSyncManager.start(repositoryId, installationId, userToken, repoFullName, branch, trigger)
}

private const val MAX_REPOS_PER_WORKSPACE = 5

private fun ApplicationCall.requestedRepoId(): Int? = request.queryParameters["repoId"]?.toIntOrNull()

private fun selectedRepoRow(workspaceId: Int, repoId: Int?): ResultRow? =
    GitHubRepositoriesTable.selectAll()
        .where {
            val inWorkspace = GitHubRepositoriesTable.workspaceId eq workspaceId
            if (repoId != null) inWorkspace and (GitHubRepositoriesTable.id eq repoId) else inWorkspace
        }
        .orderBy(GitHubRepositoriesTable.id, SortOrder.ASC)
        .limit(1)
        .firstOrNull()

private sealed interface LinkResult {
    data object NoConnection : LinkResult
    data object AlreadyLinked : LinkResult
    data object LimitReached : LinkResult
    data class Linked(val repositoryId: Int, val installationId: Long, val userToken: String?) : LinkResult
}

private suspend fun syncTarget(workspaceId: Int, repoId: Int? = null): SyncTarget? = dbQuery {
    val repoRow = selectedRepoRow(workspaceId, repoId) ?: return@dbQuery null
    val connRow = GitHubConnectionsTable.selectAll()
        .where { GitHubConnectionsTable.id eq repoRow[GitHubRepositoriesTable.connectionId] }
        .singleOrNull() ?: return@dbQuery null
    SyncTarget(
        repositoryId = repoRow[GitHubRepositoriesTable.id],
        installationId = connRow[GitHubConnectionsTable.installationId],
        userToken = CryptoService.decrypt(connRow[GitHubConnectionsTable.accessTokenEncrypted]),
        repoFullName = repoRow[GitHubRepositoriesTable.fullName],
        branch = repoRow[GitHubRepositoriesTable.defaultBranch],
        lastSyncedAt = repoRow[GitHubRepositoriesTable.lastSyncedAt]
    )
}

private fun installPageUrl(state: String): String =
    "https://github.com/apps/$GITHUB_APP_SLUG/installations/new?state=${state.encodeURLQueryComponent()}"

private suspend fun ApplicationCall.redirectGitHubResult(workspaceId: Int?, error: String?) {
    val params = listOfNotNull(
        workspaceId?.let { "workspace_id=$it" },
        error?.let { "error=$it" }
    ).joinToString("&")
    respondRedirect("collabsphere://github-auth-success?$params")
}

private suspend fun installationRepositories(ownerId: Int): List<GitHubRepository>? {
    val connection = dbQuery {
        findConnection(ownerId)?.let {
            Triple(
                it[GitHubConnectionsTable.id],
                it[GitHubConnectionsTable.installationId],
                CryptoService.decrypt(it[GitHubConnectionsTable.accessTokenEncrypted])
            )
        }
    } ?: return emptyList()
    val (connectionId, installationId, userToken) = connection
    if (userToken != null && GitHubAuthService.getInstallationToken(installationId) != null) {
        dbQuery {
            GitHubConnectionsTable.update({ GitHubConnectionsTable.id eq connectionId }) {
                it[GitHubConnectionsTable.accessTokenEncrypted] = null
                it[GitHubConnectionsTable.accessTokenExpiresAt] = null
            }
        }
        return GitHubService.listInstallationRepositories(installationId, null)
    }
    return GitHubService.listInstallationRepositories(installationId, userToken)
}

fun Application.configureGitHubRoutes() {
    GitHubSyncManager.activityHandler = { processGitHubActivities(it) }
    startGitHubDigestScheduler()
    startGitHubWebhookProcessor()
    routing {

        route("/auth/github") {

            get("/callback") {
                val code = call.request.queryParameters["code"]
                val rawState = call.request.queryParameters["state"]
                val state = rawState?.let { JwtConfig.verifyGitHubState(it) }

                if (code == null || state == null) {
                    call.redirectGitHubResult(state?.second, "invalid_request")
                    return@get
                }
                val (userId, workspaceId) = state

                val tokenResponse = GitHubAuthService.exchangeCodeForUserToken(code)
                if (tokenResponse == null) {
                    call.redirectGitHubResult(workspaceId, "token_exchange_failed")
                    return@get
                }
                val userToken = tokenResponse.access_token

                val githubUser = GitHubService.getAuthenticatedUser(userToken)
                val installations = GitHubService.getUserInstallations(userToken).orEmpty()
                val requestedInstallationId = call.request.queryParameters["installation_id"]?.toLongOrNull()
                if (requestedInstallationId == null && installations.isEmpty()) {
                    call.respondRedirect(installPageUrl(rawState))
                    return@get
                }
                val installation = if (requestedInstallationId != null) {
                    installations.firstOrNull { it.id == requestedInstallationId }
                } else {
                    installations.firstOrNull { it.account.login.equals(githubUser?.login, ignoreCase = true) }
                        ?: installations.firstOrNull()
                }
                if (installation == null) {
                    call.redirectGitHubResult(workspaceId, "installation_not_found")
                    return@get
                }

                val storedUserToken = userToken.takeIf { GitHubAuthService.getInstallationToken(installation.id) == null }
                val tokenExpiresAt = tokenResponse.expires_in
                    .takeIf { it > 0 && storedUserToken != null }
                    ?.let { System.currentTimeMillis() + it * 1000 }

                val saved = try {
                    dbQuery {
                        val isOwner = WorkspacesTable.selectAll()
                            .where {
                                (WorkspacesTable.id eq workspaceId) and
                                    (WorkspacesTable.userId eq userId) and
                                    (WorkspacesTable.isDeleted eq false)
                            }
                            .count() > 0
                        if (!isOwner) return@dbQuery false

                        if (findConnection(userId) == null) {
                            GitHubConnectionsTable.insert {
                                it[GitHubConnectionsTable.userId] = userId
                                it[GitHubConnectionsTable.githubUserId] = githubUser?.id ?: 0L
                                it[GitHubConnectionsTable.githubUsername] = githubUser?.login ?: installation.account.login
                                it[GitHubConnectionsTable.installationId] = installation.id
                                it[GitHubConnectionsTable.accessTokenEncrypted] = CryptoService.encrypt(storedUserToken)
                                it[GitHubConnectionsTable.accessTokenExpiresAt] = tokenExpiresAt
                            }
                        } else {
                            GitHubConnectionsTable.update({ GitHubConnectionsTable.userId eq userId }) {
                                if (githubUser != null) {
                                    it[GitHubConnectionsTable.githubUserId] = githubUser.id
                                    it[GitHubConnectionsTable.githubUsername] = githubUser.login
                                }
                                it[GitHubConnectionsTable.installationId] = installation.id
                                it[GitHubConnectionsTable.accessTokenEncrypted] = CryptoService.encrypt(storedUserToken)
                                it[GitHubConnectionsTable.accessTokenExpiresAt] = tokenExpiresAt
                                it[GitHubConnectionsTable.updatedAt] = System.currentTimeMillis()
                            }
                        }
                        true
                    }
                } catch (e: Exception) {
                    println("[GitHub] Failed to save connection: ${e.message}")
                    null
                }

                when (saved) {
                    true -> call.redirectGitHubResult(workspaceId, null)
                    false -> call.redirectGitHubResult(workspaceId, "not_workspace_owner")
                    null -> call.redirectGitHubResult(workspaceId, "save_failed")
                }
            }
        }

        post("/webhook/github") {
            val payload = call.receive<ByteArray>()
            val signature = call.request.header("X-Hub-Signature-256")
            val eventType = call.request.header("X-GitHub-Event")
            val deliveryId = call.request.header("X-GitHub-Delivery")

            if (eventType == null || deliveryId == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing required headers")
                return@post
            }

            if (!GitHubWebhookService.verifySignature(payload, signature)) {
                println("[GitHub] Rejected webhook with invalid signature")
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }
            
            try {
                dbQuery {
                    com.collabsphere.model.GitHubWebhookEventsTable.insertIgnore {
                        it[com.collabsphere.model.GitHubWebhookEventsTable.deliveryId] = deliveryId
                        it[com.collabsphere.model.GitHubWebhookEventsTable.eventType] = eventType
                        it[com.collabsphere.model.GitHubWebhookEventsTable.payload] = payload.decodeToString()
                        it[com.collabsphere.model.GitHubWebhookEventsTable.status] = "QUEUED"
                    }
                }
                call.respond(HttpStatusCode.Accepted)
            } catch (e: Exception) {
                println("[GitHub] Webhook enqueue failed: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError)
            }
        }

        authenticate("auth-jwt") {
            route("/api/workspace/{workspaceId}/github") {

                get("/install-url") {
                    val access = call.resolveGitHubAccess(requireOwner = true) ?: return@get
                    val state = JwtConfig.generateGitHubState(access.callerId, access.workspaceId)
                    val url = GitHubAuthService.authorizeUrl(state) ?: installPageUrl(state)
                    call.respond(HttpStatusCode.OK, mapOf("url" to url))
                }

                get("/available-repos") {
                    val access = call.resolveGitHubAccess(requireOwner = true) ?: return@get

                    try {
                        val repos = installationRepositories(access.ownerId)
                        if (repos == null) {
                            call.respond(HttpStatusCode.BadGateway, "GitHub did not return the repository list")
                            return@get
                        }
                        val available = repos.map { repo ->
                            AvailableRepo(
                                id = repo.id,
                                fullName = repo.full_name,
                                owner = repo.owner.login,
                                name = repo.name,
                                isPrivate = repo.private,
                                htmlUrl = repo.html_url,
                                defaultBranch = repo.default_branch
                            )
                        }
                        call.respond(HttpStatusCode.OK, available)
                    } catch (e: Exception) {
                        println("[GitHub] Error fetching available repos: ${e.message}")
                        call.respond(HttpStatusCode.InternalServerError, "Failed to fetch repos")
                    }
                }

                post("/link-repo") {
                    val access = call.resolveGitHubAccess(requireOwner = true) ?: return@post
                    val workspaceId = access.workspaceId
                    val request = call.receive<LinkRepoRequest>()

                    try {
                        val repo = installationRepositories(access.ownerId)?.firstOrNull { it.id == request.repoId }
                        if (repo == null) {
                            call.respond(HttpStatusCode.NotFound, "This repository is not accessible to the GitHub App installation.")
                            return@post
                        }

                        val linked = dbQuery {
                            val connRow = findConnection(access.ownerId) ?: return@dbQuery LinkResult.NoConnection
                            val linkedIds = GitHubRepositoriesTable.selectAll()
                                .where { GitHubRepositoriesTable.workspaceId eq workspaceId }
                                .map { it[GitHubRepositoriesTable.githubRepoId] }
                            if (repo.id in linkedIds) return@dbQuery LinkResult.AlreadyLinked
                            if (linkedIds.size >= MAX_REPOS_PER_WORKSPACE) return@dbQuery LinkResult.LimitReached

                            val repoId = GitHubRepositoriesTable.insert {
                                it[GitHubRepositoriesTable.connectionId] = connRow[GitHubConnectionsTable.id]
                                it[GitHubRepositoriesTable.workspaceId] = workspaceId
                                it[GitHubRepositoriesTable.githubRepoId] = repo.id
                                it[GitHubRepositoriesTable.fullName] = repo.full_name
                                it[GitHubRepositoriesTable.owner] = repo.owner.login
                                it[GitHubRepositoriesTable.name] = repo.name
                                it[GitHubRepositoriesTable.isPrivate] = repo.private
                                it[GitHubRepositoriesTable.htmlUrl] = repo.html_url
                                it[GitHubRepositoriesTable.defaultBranch] = repo.default_branch
                            } get GitHubRepositoriesTable.id

                            LinkResult.Linked(repoId, connRow[GitHubConnectionsTable.installationId], CryptoService.decrypt(connRow[GitHubConnectionsTable.accessTokenEncrypted]))
                        }

                        when (linked) {
                            LinkResult.NoConnection -> call.respond(HttpStatusCode.NotFound, "No GitHub connection found. Please connect to GitHub first.")
                            LinkResult.AlreadyLinked -> call.respond(HttpStatusCode.Conflict, "${repo.full_name} is already linked to this workspace.")
                            LinkResult.LimitReached -> call.respond(HttpStatusCode.Conflict, "A workspace can link up to $MAX_REPOS_PER_WORKSPACE repositories.")
                            is LinkResult.Linked -> {
                                GitHubSyncManager.start(linked.repositoryId, linked.installationId, linked.userToken, repo.full_name, repo.default_branch)
                                call.respond(
                                    HttpStatusCode.OK,
                                    mapOf("status" to "linked", "repo" to repo.full_name, "repositoryId" to linked.repositoryId.toString())
                                )
                            }
                        }
                    } catch (e: Exception) {
                        println("[GitHub] Error linking repo: ${e.message}")
                        call.respond(HttpStatusCode.InternalServerError, "Failed to link repo")
                    }
                }

                post("/sync") {
                    val access = call.resolveGitHubAccess(requireOwner = false) ?: return@post
                    val target = syncTarget(access.workspaceId, call.requestedRepoId())
                    if (target == null) {
                        call.respond(HttpStatusCode.NotFound, "No repository linked to this workspace")
                        return@post
                    }
                    if (GitHubSyncManager.isSyncing(target.repositoryId)) {
                        call.respond(HttpStatusCode.Accepted, mapOf("status" to "syncing"))
                        return@post
                    }
                    if (target.start(SyncTrigger.MANUAL)) {
                        call.respond(HttpStatusCode.Accepted, mapOf("status" to "syncing"))
                    } else {
                        call.respond(HttpStatusCode.TooManyRequests, "Synced recently. Please wait a moment before syncing again.")
                    }
                }

                get("/pull-requests") {
                    val access = call.resolveGitHubAccess(requireOwner = false) ?: return@get
                    val filter = call.request.queryParameters["state"] ?: "all"
                    val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull()?.coerceIn(1, 50) ?: 20
                    val requestedRepoId = call.requestedRepoId()

                    val result = dbQuery {
                        val repoRow = selectedRepoRow(access.workspaceId, requestedRepoId) ?: return@dbQuery null
                        val repoId = repoRow[GitHubRepositoriesTable.id]
                        val repoUrl = repoRow[GitHubRepositoriesTable.htmlUrl]

                        val rows = GitHubPullRequestsTable.selectAll()
                            .where {
                                val inRepo = GitHubPullRequestsTable.repositoryId eq repoId
                                when (filter) {
                                    "open" -> inRepo and (GitHubPullRequestsTable.state eq "open")
                                    "merged" -> inRepo and GitHubPullRequestsTable.mergedAt.isNotNull()
                                    "closed" -> inRepo and (GitHubPullRequestsTable.state eq "closed") and GitHubPullRequestsTable.mergedAt.isNull()
                                    else -> inRepo
                                }
                            }
                            .orderBy(GitHubPullRequestsTable.createdAt, SortOrder.DESC)
                            .limit(pageSize + 1, offset = ((page - 1) * pageSize).toLong())
                            .toList()
                            .let { pullRequestItems(it, repoId, repoUrl) }
                        GitHubPullRequestPage(items = rows.take(pageSize), hasMore = rows.size > pageSize)
                    }

                    if (result == null) {
                        call.respond(HttpStatusCode.NotFound, "No repository linked to this workspace")
                    } else {
                        call.respond(HttpStatusCode.OK, result)
                    }
                }

                post("/notify-channel") {
                    val access = call.resolveGitHubAccess(requireOwner = true) ?: return@post
                    val request = try {
                        call.receive<GitHubNotifyChannelRequest>()
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid request body")
                        return@post
                    }
                    val requestedRepoId = call.requestedRepoId()
                    val updated = dbQuery {
                        val channelId = request.channelId
                        if (channelId != null) {
                            val valid = ChannelsTable.selectAll()
                                .where {
                                    (ChannelsTable.id eq channelId) and
                                        (ChannelsTable.workspaceId eq access.workspaceId) and
                                        (ChannelsTable.isDeleted eq false)
                                }
                                .count() > 0
                            if (!valid) return@dbQuery false
                        }
                        GitHubRepositoriesTable.update({
                            val inWorkspace = GitHubRepositoriesTable.workspaceId eq access.workspaceId
                            if (requestedRepoId != null) inWorkspace and (GitHubRepositoriesTable.id eq requestedRepoId) else inWorkspace
                        }) {
                            it[GitHubRepositoriesTable.notifyChannelId] = channelId
                        } > 0
                    }
                    if (updated) {
                        call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Channel or linked repository not found")
                    }
                }

                post("/tasks/{taskId}/issue") {
                    val access = call.resolveGitHubAccess(requireOwner = false) ?: return@post
                    val taskId = call.parameters["taskId"]?.toIntOrNull()
                    if (taskId == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid task ID")
                        return@post
                    }

                    val context = dbQuery {
                        val task = TasksTable.selectAll()
                            .where {
                                (TasksTable.id eq taskId) and
                                    (TasksTable.workspaceId eq access.workspaceId) and
                                    (TasksTable.isDeleted eq false)
                            }
                            .singleOrNull() ?: return@dbQuery null
                        val existingIssue = GitHubTaskLinksTable.selectAll()
                            .where { (GitHubTaskLinksTable.taskId eq taskId) and (GitHubTaskLinksTable.kind eq "issue") }
                            .firstOrNull()
                        Triple(task[TasksTable.taskName], task[TasksTable.taskDescription], existingIssue != null)
                    }
                    if (context == null) {
                        call.respond(HttpStatusCode.NotFound, "Task not found")
                        return@post
                    }
                    val (taskName, taskDescription, alreadyLinked) = context
                    if (alreadyLinked) {
                        call.respond(HttpStatusCode.Conflict, "This task already has a GitHub issue")
                        return@post
                    }

                    val target = syncTarget(access.workspaceId, call.requestedRepoId())
                    if (target == null) {
                        call.respond(HttpStatusCode.NotFound, "No repository linked to this workspace")
                        return@post
                    }
                    val token = GitHubService.repoAccessToken(target.installationId, target.userToken)
                    if (token == null) {
                        call.respond(HttpStatusCode.BadGateway, "GitHub access is not available. Reconnect GitHub and try again.")
                        return@post
                    }

                    val body = listOf(taskDescription.trim(), "Tracked in CollabSphere as T-$taskId")
                        .filter { it.isNotBlank() }
                        .joinToString("\n\n---\n")
                    val (issue, status) = GitHubService.createIssue(token, target.repoFullName, taskName, body)
                    if (issue == null) {
                        if (status == HttpStatusCode.Forbidden || status == HttpStatusCode.NotFound) {
                            call.respond(
                                HttpStatusCode.Forbidden,
                                "The GitHub App needs the 'Issues: Read & write' permission to create issues."
                            )
                        } else {
                            call.respond(HttpStatusCode.BadGateway, "GitHub could not create the issue")
                        }
                        return@post
                    }

                    val link = GitHubTaskLinkResponse(
                        kind = "issue",
                        ref = issue.number.toString(),
                        title = "#${issue.number} ${issue.title}".take(500),
                        url = issue.html_url,
                        createdAt = System.currentTimeMillis()
                    )
                    dbQuery {
                        GitHubDataStore.saveIssue(target.repositoryId, issue.toRecord())
                        GitHubTaskLinksTable.insertIgnore {
                            it[GitHubTaskLinksTable.taskId] = taskId
                            it[GitHubTaskLinksTable.repositoryId] = target.repositoryId
                            it[GitHubTaskLinksTable.kind] = link.kind
                            it[GitHubTaskLinksTable.ref] = link.ref
                            it[GitHubTaskLinksTable.title] = link.title
                            it[GitHubTaskLinksTable.url] = link.url.take(500)
                            it[GitHubTaskLinksTable.createdAt] = link.createdAt
                        }
                    }
                    call.respond(HttpStatusCode.Created, link)
                }

                get("/tasks/{taskId}/links") {
                    val access = call.resolveGitHubAccess(requireOwner = false) ?: return@get
                    val taskId = call.parameters["taskId"]?.toIntOrNull()
                    if (taskId == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid task ID")
                        return@get
                    }
                    val links = dbQuery {
                        val taskInWorkspace = TasksTable.selectAll()
                            .where { (TasksTable.id eq taskId) and (TasksTable.workspaceId eq access.workspaceId) }
                            .count() > 0
                        if (!taskInWorkspace) return@dbQuery null
                        GitHubTaskLinksTable.selectAll()
                            .where { GitHubTaskLinksTable.taskId eq taskId }
                            .orderBy(GitHubTaskLinksTable.createdAt, SortOrder.DESC)
                            .map {
                                GitHubTaskLinkResponse(
                                    kind = it[GitHubTaskLinksTable.kind],
                                    ref = it[GitHubTaskLinksTable.ref],
                                    title = it[GitHubTaskLinksTable.title],
                                    url = it[GitHubTaskLinksTable.url],
                                    createdAt = it[GitHubTaskLinksTable.createdAt]
                                )
                            }
                    }
                    if (links == null) {
                        call.respond(HttpStatusCode.NotFound, "Task not found")
                    } else {
                        call.respond(HttpStatusCode.OK, links)
                    }
                }

                post("/unlink-repo") {
                    val access = call.resolveGitHubAccess(requireOwner = true) ?: return@post
                    val requestedRepoId = call.requestedRepoId()
                    try {
                        dbQuery {
                            GitHubRepositoriesTable.deleteWhere {
                                val inWorkspace = GitHubRepositoriesTable.workspaceId eq access.workspaceId
                                if (requestedRepoId != null) inWorkspace and (GitHubRepositoriesTable.id eq requestedRepoId) else inWorkspace
                            }
                        }
                        call.respond(HttpStatusCode.OK, mapOf("status" to "unlinked"))
                    } catch (e: Exception) {
                        println("[GitHub] Error unlinking repo: ${e.message}")
                        call.respond(HttpStatusCode.InternalServerError, "Failed to unlink repo")
                    }
                }

                post("/disconnect") {
                    val access = call.resolveGitHubAccess(requireOwner = true) ?: return@post
                    try {
                        val revokedToken = dbQuery {
                            GitHubRepositoriesTable.deleteWhere { GitHubRepositoriesTable.workspaceId eq access.workspaceId }
                            val connRow = findConnection(access.ownerId) ?: return@dbQuery null
                            val connectionId = connRow[GitHubConnectionsTable.id]
                            val stillUsed = GitHubRepositoriesTable.selectAll()
                                .where { GitHubRepositoriesTable.connectionId eq connectionId }
                                .count() > 0
                            if (stillUsed) return@dbQuery null
                            GitHubConnectionsTable.deleteWhere { GitHubConnectionsTable.id eq connectionId }
                            CryptoService.decrypt(connRow[GitHubConnectionsTable.accessTokenEncrypted])
                        }
                        if (!revokedToken.isNullOrBlank()) {
                            GitHubAuthService.revokeUserGrant(revokedToken)
                        }
                        call.respond(HttpStatusCode.OK, mapOf("status" to "disconnected"))
                    } catch (e: Exception) {
                        println("[GitHub] Error disconnecting GitHub: ${e.message}")
                        call.respond(HttpStatusCode.InternalServerError, "Failed to disconnect GitHub")
                    }
                }

                get("/analytics") {
                    val access = call.resolveGitHubAccess(requireOwner = false) ?: return@get
                    val zone = call.request.queryParameters["tz"]
                        ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
                        ?: ZoneOffset.UTC
                    val requestedRepoId = call.requestedRepoId()

                    val analytics = dbQuery {
                        val hasConnection = findConnection(access.ownerId) != null
                        val repoRow = selectedRepoRow(access.workspaceId, requestedRepoId)
                            ?: selectedRepoRow(access.workspaceId, null)
                        val linkedRepos = GitHubRepositoriesTable.selectAll()
                            .where { GitHubRepositoriesTable.workspaceId eq access.workspaceId }
                            .orderBy(GitHubRepositoriesTable.id, SortOrder.ASC)
                            .map { GitHubLinkedRepo(it[GitHubRepositoriesTable.id], it[GitHubRepositoriesTable.fullName]) }

                        if (repoRow == null) {
                            return@dbQuery GitHubAnalyticsResponse(
                                isConnected = false,
                                hasConnection = hasConnection,
                                canManage = access.isOwner,
                                repositoryName = null,
                                totalCommits = 0,
                                openPullRequests = 0,
                                mergedPullRequests = 0,
                                topContributors = emptyList()
                            )
                        }

                        val repoId = repoRow[GitHubRepositoriesTable.id]

                        val totalCommits = GitHubCommitsTable.selectAll()
                            .where { GitHubCommitsTable.repositoryId eq repoId }
                            .count().toInt()

                        val openPrs = GitHubPullRequestsTable.selectAll()
                            .where { (GitHubPullRequestsTable.repositoryId eq repoId) and (GitHubPullRequestsTable.state eq "open") }
                            .count().toInt()

                        val mergedPrs = GitHubPullRequestsTable.selectAll()
                            .where { (GitHubPullRequestsTable.repositoryId eq repoId) and GitHubPullRequestsTable.mergedAt.isNotNull() }
                            .count().toInt()

                        val members = workspaceMembersForMatching(access.workspaceId)
                        val commitCount = GitHubCommitsTable.id.count()
                        val authorName = GitHubCommitsTable.authorName.max()
                        val topContributors = GitHubCommitsTable
                            .select(GitHubCommitsTable.authorEmail, authorName, commitCount)
                            .where { GitHubCommitsTable.repositoryId eq repoId }
                            .groupBy(GitHubCommitsTable.authorEmail)
                            .orderBy(commitCount, SortOrder.DESC)
                            .limit(TOP_CONTRIBUTORS)
                            .map {
                                val email = it[GitHubCommitsTable.authorEmail]
                                val name = it[authorName]
                                val member = members.match(email)
                                GitHubContributorStats(
                                    username = member?.username ?: name ?: email ?: "Unknown",
                                    commits = it[commitCount].toInt(),
                                    memberUserId = member?.userId,
                                    avatarUrl = member?.avatarUrl,
                                    githubName = name
                                )
                            }

                        val today = LocalDate.now(zone)
                        val since = today.minusDays(ACTIVITY_DAYS - 1L).atStartOfDay(zone).toInstant().toEpochMilli()
                        val countsByDay = GitHubCommitsTable
                            .select(GitHubCommitsTable.commitDate)
                            .where { (GitHubCommitsTable.repositoryId eq repoId) and (GitHubCommitsTable.commitDate greaterEq since) }
                            .map { Instant.ofEpochMilli(it[GitHubCommitsTable.commitDate]).atZone(zone).toLocalDate() }
                            .groupingBy { it }
                            .eachCount()
                        val commitActivity = (ACTIVITY_DAYS - 1 downTo 0).map { daysAgo ->
                            val day = today.minusDays(daysAgo.toLong())
                            GitHubDailyCount(day.toString(), countsByDay[day] ?: 0)
                        }

                        val recentCommits = GitHubCommitsTable.selectAll()
                            .where { GitHubCommitsTable.repositoryId eq repoId }
                            .orderBy(GitHubCommitsTable.commitDate, SortOrder.DESC)
                            .limit(RECENT_ITEMS)
                            .map {
                                GitHubCommitItem(
                                    sha = it[GitHubCommitsTable.sha],
                                    message = it[GitHubCommitsTable.message].lineSequence().first().take(200),
                                    authorName = it[GitHubCommitsTable.authorName],
                                    commitDate = it[GitHubCommitsTable.commitDate],
                                    url = "${repoRow[GitHubRepositoriesTable.htmlUrl]}/commit/${it[GitHubCommitsTable.sha]}"
                                )
                            }
                            .let { commits ->
                                val ci = GitHubDataStore.ciStatusFor(repoId, commits.map { it.sha })
                                commits.map { it.copy(ciStatus = ci[it.sha]) }
                            }

                        val recentPullRequests = GitHubPullRequestsTable.selectAll()
                            .where { GitHubPullRequestsTable.repositoryId eq repoId }
                            .orderBy(GitHubPullRequestsTable.createdAt, SortOrder.DESC)
                            .limit(RECENT_ITEMS)
                            .toList()
                            .let { pullRequestItems(it, repoId, repoRow[GitHubRepositoriesTable.htmlUrl]) }

                        val openIssues = GitHubIssuesTable.selectAll()
                            .where { (GitHubIssuesTable.repositoryId eq repoId) and (GitHubIssuesTable.state eq "open") }
                            .count().toInt()

                        val recentIssues = GitHubIssuesTable.selectAll()
                            .where { GitHubIssuesTable.repositoryId eq repoId }
                            .orderBy(GitHubIssuesTable.createdAt, SortOrder.DESC)
                            .limit(RECENT_ITEMS)
                            .map {
                                GitHubIssueItem(
                                    number = it[GitHubIssuesTable.number],
                                    title = it[GitHubIssuesTable.title],
                                    state = it[GitHubIssuesTable.state],
                                    authorUsername = it[GitHubIssuesTable.authorUsername],
                                    createdAt = it[GitHubIssuesTable.createdAt],
                                    url = it[GitHubIssuesTable.url]
                                )
                            }

                        val recentCheckSuites = com.collabsphere.model.GitHubCheckSuitesTable.selectAll()
                            .where { com.collabsphere.model.GitHubCheckSuitesTable.repositoryId eq repoId }
                            .orderBy(com.collabsphere.model.GitHubCheckSuitesTable.updatedAt, SortOrder.DESC)
                            .limit(RECENT_ITEMS)
                            .map {
                                com.collabsphere.dto.GitHubCheckSuiteItem(
                                    id = it[com.collabsphere.model.GitHubCheckSuitesTable.githubSuiteId],
                                    headSha = it[com.collabsphere.model.GitHubCheckSuitesTable.headSha],
                                    headBranch = it[com.collabsphere.model.GitHubCheckSuitesTable.headBranch],
                                    status = it[com.collabsphere.model.GitHubCheckSuitesTable.status],
                                    conclusion = it[com.collabsphere.model.GitHubCheckSuitesTable.conclusion],
                                    appName = it[com.collabsphere.model.GitHubCheckSuitesTable.appName],
                                    url = it[com.collabsphere.model.GitHubCheckSuitesTable.url],
                                    createdAt = it[com.collabsphere.model.GitHubCheckSuitesTable.createdAt],
                                    updatedAt = it[com.collabsphere.model.GitHubCheckSuitesTable.updatedAt]
                                )
                            }

                        GitHubAnalyticsResponse(
                            isConnected = true,
                            hasConnection = hasConnection,
                            canManage = access.isOwner,
                            repositoryName = repoRow[GitHubRepositoriesTable.fullName],
                            repositoryUrl = repoRow[GitHubRepositoriesTable.htmlUrl],
                            lastSyncedAt = repoRow[GitHubRepositoriesTable.lastSyncedAt],
                            syncState = if (GitHubSyncManager.isSyncing(repoId)) "SYNCING" else repoRow[GitHubRepositoriesTable.syncState],
                            syncError = repoRow[GitHubRepositoriesTable.lastSyncError],
                            totalCommits = totalCommits,
                            openPullRequests = openPrs,
                            mergedPullRequests = mergedPrs,
                            topContributors = topContributors,
                            commitActivity = commitActivity,
                            recentCommits = recentCommits,
                            recentPullRequests = recentPullRequests,
                            notifyChannelId = repoRow[GitHubRepositoriesTable.notifyChannelId],
                            openIssues = openIssues,
                            recentIssues = recentIssues,
                            repositoryId = repoId,
                            repositories = linkedRepos,
                            recentCheckSuites = recentCheckSuites,
                            channels = if (access.isOwner) {
                                ChannelsTable.selectAll()
                                    .where { (ChannelsTable.workspaceId eq access.workspaceId) and (ChannelsTable.isDeleted eq false) }
                                    .orderBy(ChannelsTable.channelName)
                                    .map { GitHubChannelOption(it[ChannelsTable.id], it[ChannelsTable.channelName]) }
                            } else {
                                emptyList()
                            }
                        )
                    }

                    val refreshed = if (analytics.isConnected && analytics.syncState != "SYNCING") {
                        val target = syncTarget(access.workspaceId, analytics.repositoryId)
                        val stale = target != null &&
                            System.currentTimeMillis() - target.lastSyncedAt > STALE_SYNC_MS
                        if (stale && target.start(SyncTrigger.AUTO)) analytics.copy(syncState = "SYNCING") else analytics
                    } else {
                        analytics
                    }

                    call.respond(HttpStatusCode.OK, refreshed)
                }

                post("/unfurl") {
                    val access = call.resolveGitHubAccess(false) ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    val request = call.receiveNullable<com.collabsphere.dto.GitHubUnfurlRequest>()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid request")
                    
                    val previews = com.collabsphere.util.GitHubUnfurlService.unfurl(access.workspaceId, request.urls)
                    call.respond(com.collabsphere.dto.GitHubUnfurlResponse(previews))
                }

                post("/action") {
                    val access = call.resolveGitHubAccess(false) ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    val idempotencyKey = call.request.headers["Idempotency-Key"]
                    if (idempotencyKey.isNullOrBlank()) {
                        return@post call.respond(HttpStatusCode.BadRequest, "Missing Idempotency-Key header")
                    }

                    val request = call.receiveNullable<com.collabsphere.dto.GitHubActionRequest>()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid request")

                    val response = com.collabsphere.util.GitHubActionService.processAction(
                        workspaceId = access.workspaceId,
                        userId = access.callerId,
                        idempotencyKey = idempotencyKey,
                        request = request
                    )

                    // For UX, return HTTP 200 even for logical failures, letting the ActionResponse status dictate
                    // Actually, if it's AuthenticationRequired we can return 401, but the JSON body is enough
                    call.respond(response)
                }

                get("/releases") {
                    val access = call.resolveGitHubAccess(requireOwner = false) ?: return@get
                    val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val repoId = call.request.queryParameters["repositoryId"]?.toIntOrNull()
                    val pageSize = 20

                    newSuspendedTransaction {
                        // Validate that requested repositoryId belongs to this workspace
                        val linkedRepoIds = GitHubRepositoriesTable.selectAll()
                            .where { GitHubRepositoriesTable.workspaceId eq access.workspaceId }
                            .associate { it[GitHubRepositoriesTable.id] to it[GitHubRepositoriesTable.fullName] }

                        val targetRepoIds = if (repoId != null) {
                            if (!linkedRepoIds.containsKey(repoId)) {
                                return@newSuspendedTransaction call.respond(HttpStatusCode.Forbidden)
                            }
                            listOf(repoId)
                        } else {
                            linkedRepoIds.keys.toList()
                        }

                        val offset = page.toLong() * pageSize
                        val totalFetch = pageSize + 1

                        val allReleases = targetRepoIds.flatMap { rid ->
                            val repoFullName = linkedRepoIds[rid] ?: ""
                            GitHubDataStore.getRecentReleases(rid, totalFetch, if (targetRepoIds.size == 1) offset else 0)
                                .map { it to repoFullName }
                        }
                            .sortedByDescending { (r, _) -> r.publishedAt ?: r.createdAt }

                        val hasMore = allReleases.size > pageSize
                        val page_items = allReleases.take(pageSize)

                        val items = page_items.map { (r, repoFullName) ->
                            com.collabsphere.dto.GitHubReleaseItem(
                                id = r.githubReleaseId,
                                tagName = r.tagName,
                                name = r.name,
                                author = r.author,
                                htmlUrl = r.htmlUrl,
                                draft = r.draft,
                                prerelease = r.prerelease,
                                publishedAt = r.publishedAt,
                                repositoryFullName = repoFullName
                            )
                        }

                        call.respond(com.collabsphere.dto.GitHubReleasesResponse(releases = items, hasMore = hasMore))
                    }
                }

                // ── Feature E: Assignee Sync ────────────────────────────────

                route("/assignee-sync") {
                    /** Toggle assignee sync for a task/issue link. */
                    post("/toggle") {
                        val access = call.resolveGitHubAccess(requireOwner = false) ?: return@post
                        @kotlinx.serialization.Serializable
                        data class ToggleRequest(val taskId: Int, val repositoryId: Int, val issueNumber: Int, val enabled: Boolean)
                        val request = call.receive<ToggleRequest>()
                        newSuspendedTransaction {
                            com.collabsphere.util.GitHubAssigneeSyncService.setSyncEnabled(
                                request.taskId, request.repositoryId, request.issueNumber, request.enabled
                            )
                        }
                        call.respond(HttpStatusCode.OK, mapOf("enabled" to request.enabled))
                    }

                    /** Get sync status for a task. */
                    get("/status/{taskId}") {
                        val access = call.resolveGitHubAccess(requireOwner = false) ?: return@get
                        val taskId = call.parameters["taskId"]?.toIntOrNull()
                            ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid taskId")
                        val syncRecords = newSuspendedTransaction {
                            com.collabsphere.model.GitHubAssigneeSyncTable.selectAll()
                                .where { com.collabsphere.model.GitHubAssigneeSyncTable.taskId eq taskId }
                                .map {
                                    mapOf(
                                        "repositoryId" to it[com.collabsphere.model.GitHubAssigneeSyncTable.repositoryId],
                                        "issueNumber" to it[com.collabsphere.model.GitHubAssigneeSyncTable.issueNumber],
                                        "enabled" to it[com.collabsphere.model.GitHubAssigneeSyncTable.enabled],
                                        "syncStatus" to it[com.collabsphere.model.GitHubAssigneeSyncTable.syncStatus],
                                        "lastSyncSource" to it[com.collabsphere.model.GitHubAssigneeSyncTable.lastSyncSource],
                                        "lastSyncAt" to it[com.collabsphere.model.GitHubAssigneeSyncTable.lastSyncAt],
                                        "syncError" to it[com.collabsphere.model.GitHubAssigneeSyncTable.syncError]
                                    )
                                }
                        }
                        call.respond(syncRecords)
                    }

                    /** Get identity mappings for the workspace. */
                    get("/identity-map") {
                        val access = call.resolveGitHubAccess(requireOwner = false) ?: return@get
                        val mappings = newSuspendedTransaction {
                            com.collabsphere.util.GitHubAssigneeSyncService.refreshIdentityMappings(access.workspaceId)
                            com.collabsphere.model.GitHubIdentityMappingTable.selectAll()
                                .where { com.collabsphere.model.GitHubIdentityMappingTable.workspaceId eq access.workspaceId }
                                .map {
                                    mapOf(
                                        "userId" to it[com.collabsphere.model.GitHubIdentityMappingTable.userId],
                                        "githubUserId" to it[com.collabsphere.model.GitHubIdentityMappingTable.githubUserId],
                                        "githubLogin" to it[com.collabsphere.model.GitHubIdentityMappingTable.githubLogin]
                                    )
                                }
                        }
                        call.respond(mappings)
                    }
                }

                // ── Feature G: Code Snippets ────────────────────────────────

                route("/code") {
                    /** Resolve a GitHub code URL into a snippet with content. */
                    post("/resolve") {
                        val access = call.resolveGitHubAccess(requireOwner = false) ?: return@post
                        @kotlinx.serialization.Serializable
                        data class ResolveRequest(val url: String)
                        val request = call.receive<ResolveRequest>()
                        val result = com.collabsphere.util.GitHubCodeSnippetService.resolveSnippet(
                            access.workspaceId, request.url
                        )
                        call.respond(result)
                    }

                    /** Attach a code reference to a task/message/DM. */
                    post("/attach") {
                        val access = call.resolveGitHubAccess(requireOwner = false) ?: return@post
                        @kotlinx.serialization.Serializable
                        data class AttachRequest(val url: String, val referenceType: String, val referenceId: Int)
                        val request = call.receive<AttachRequest>()

                        if (request.referenceType !in listOf("TASK", "CHANNEL_MESSAGE", "DM")) {
                            return@post call.respond(HttpStatusCode.BadRequest, "Invalid referenceType")
                        }

                        val ref = com.collabsphere.util.GitHubCodeSnippetService.attachCodeReference(
                            access.workspaceId, request.url, request.referenceType, request.referenceId, access.callerId
                        )
                        if (ref != null) {
                            call.respond(HttpStatusCode.Created, ref)
                        } else {
                            call.respond(HttpStatusCode.BadRequest, "Invalid code URL or repository not linked")
                        }
                    }

                    /** List code references for a given target. */
                    get("/references/{referenceType}/{referenceId}") {
                        val access = call.resolveGitHubAccess(requireOwner = false) ?: return@get
                        val referenceType = call.parameters["referenceType"]
                            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing referenceType")
                        val referenceId = call.parameters["referenceId"]?.toIntOrNull()
                            ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid referenceId")

                        val refs = com.collabsphere.util.GitHubCodeSnippetService.getCodeReferences(
                            access.workspaceId, referenceType, referenceId
                        )
                        call.respond(refs)
                    }

                    /** Delete a code reference. */
                    delete("/references/{id}") {
                        val access = call.resolveGitHubAccess(requireOwner = false) ?: return@delete
                        val id = call.parameters["id"]?.toIntOrNull()
                            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid id")
                        val deleted = com.collabsphere.util.GitHubCodeSnippetService.deleteCodeReference(id, access.callerId)
                        if (deleted) {
                            call.respond(HttpStatusCode.OK, mapOf("deleted" to true))
                        } else {
                            call.respond(HttpStatusCode.NotFound, "Reference not found or not authorized")
                        }
                    }
                }
            }
        }
    }
}
