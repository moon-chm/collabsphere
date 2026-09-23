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
import com.collabsphere.model.GitHubCommitsTable
import com.collabsphere.model.GitHubConnectionsTable
import com.collabsphere.model.GitHubPullRequestsTable
import com.collabsphere.model.GitHubRepositoriesTable
import com.collabsphere.model.WorkspacesTable
import com.collabsphere.util.GitHubAuthService
import com.collabsphere.util.GitHubDataStore
import com.collabsphere.util.GitHubRepository
import com.collabsphere.util.GitHubService
import com.collabsphere.util.GitHubSyncManager
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

private fun findConnection(userId: Int): ResultRow? =
    GitHubConnectionsTable.selectAll()
        .where { GitHubConnectionsTable.userId eq userId }
        .singleOrNull()

private const val GITHUB_APP_SLUG = "collabspheregithubfeat"
private const val ACTIVITY_DAYS = 14
private const val TOP_CONTRIBUTORS = 5
private const val RECENT_ITEMS = 10

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
            it[GitHubConnectionsTable.installationId] to it[GitHubConnectionsTable.accessTokenEncrypted]
        }
    } ?: return emptyList()
    val (installationId, userToken) = connection
    return GitHubService.listInstallationRepositories(installationId, userToken)
}

fun Application.configureGitHubRoutes() {
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
                if (requestedInstallationId == null && installations.isEmpty() && rawState != null) {
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

                val tokenExpiresAt = tokenResponse.expires_in
                    .takeIf { it > 0 }
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
                                it[GitHubConnectionsTable.accessTokenEncrypted] = userToken
                                it[GitHubConnectionsTable.accessTokenExpiresAt] = tokenExpiresAt
                            }
                        } else {
                            GitHubConnectionsTable.update({ GitHubConnectionsTable.userId eq userId }) {
                                if (githubUser != null) {
                                    it[GitHubConnectionsTable.githubUserId] = githubUser.id
                                    it[GitHubConnectionsTable.githubUsername] = githubUser.login
                                }
                                it[GitHubConnectionsTable.installationId] = installation.id
                                it[GitHubConnectionsTable.accessTokenEncrypted] = userToken
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
            if (!GitHubWebhookService.verifySignature(payload, signature)) {
                println("[GitHub] Rejected webhook with invalid signature")
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }
            val activities = try {
                GitHubWebhookService.handleWebhookEvent(call.request.header("X-GitHub-Event"), payload.decodeToString())
            } catch (e: Exception) {
                println("[GitHub] Webhook processing failed: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError)
                return@post
            }
            call.respond(HttpStatusCode.OK)
            processGitHubActivities(activities)
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
                            val connRow = findConnection(access.ownerId) ?: return@dbQuery null

                            GitHubRepositoriesTable.deleteWhere { GitHubRepositoriesTable.workspaceId eq workspaceId }

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

                            Triple(repoId, connRow[GitHubConnectionsTable.installationId], connRow[GitHubConnectionsTable.accessTokenEncrypted])
                        }

                        if (linked == null) {
                            call.respond(HttpStatusCode.NotFound, "No GitHub connection found. Please connect to GitHub first.")
                            return@post
                        }

                        val (insertedRepoId, installationId, userToken) = linked
                        GitHubSyncManager.start(insertedRepoId, installationId, userToken, repo.full_name, repo.default_branch)

                        call.respond(HttpStatusCode.OK, mapOf("status" to "linked", "repo" to repo.full_name))
                    } catch (e: Exception) {
                        println("[GitHub] Error linking repo: ${e.message}")
                        call.respond(HttpStatusCode.InternalServerError, "Failed to link repo")
                    }
                }

                post("/sync") {
                    val access = call.resolveGitHubAccess(requireOwner = false) ?: return@post
                    val target = dbQuery {
                        val repoRow = GitHubRepositoriesTable.selectAll()
                            .where { GitHubRepositoriesTable.workspaceId eq access.workspaceId }
                            .singleOrNull() ?: return@dbQuery null
                        val connRow = GitHubConnectionsTable.selectAll()
                            .where { GitHubConnectionsTable.id eq repoRow[GitHubRepositoriesTable.connectionId] }
                            .singleOrNull() ?: return@dbQuery null
                        repoRow to connRow
                    }
                    if (target == null) {
                        call.respond(HttpStatusCode.NotFound, "No repository linked to this workspace")
                        return@post
                    }
                    val (repoRow, connRow) = target
                    val repositoryId = repoRow[GitHubRepositoriesTable.id]
                    if (GitHubSyncManager.isSyncing(repositoryId)) {
                        call.respond(HttpStatusCode.Accepted, mapOf("status" to "syncing"))
                        return@post
                    }
                    val started = GitHubSyncManager.start(
                        repositoryId = repositoryId,
                        installationId = connRow[GitHubConnectionsTable.installationId],
                        userToken = connRow[GitHubConnectionsTable.accessTokenEncrypted],
                        repoFullName = repoRow[GitHubRepositoriesTable.fullName],
                        branch = repoRow[GitHubRepositoriesTable.defaultBranch],
                        manual = true
                    )
                    if (started) {
                        call.respond(HttpStatusCode.Accepted, mapOf("status" to "syncing"))
                    } else {
                        call.respond(HttpStatusCode.TooManyRequests, "Synced recently. Please wait a moment before syncing again.")
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
                        GitHubRepositoriesTable.update({ GitHubRepositoriesTable.workspaceId eq access.workspaceId }) {
                            it[GitHubRepositoriesTable.notifyChannelId] = channelId
                        } > 0
                    }
                    if (updated) {
                        call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Channel or linked repository not found")
                    }
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
                    try {
                        dbQuery {
                            GitHubRepositoriesTable.deleteWhere { GitHubRepositoriesTable.workspaceId eq access.workspaceId }
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
                            connRow[GitHubConnectionsTable.accessTokenEncrypted]
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

                    val analytics = dbQuery {
                        val hasConnection = findConnection(access.ownerId) != null
                        val repoRow = GitHubRepositoriesTable.selectAll()
                            .where { GitHubRepositoriesTable.workspaceId eq access.workspaceId }
                            .singleOrNull()

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

                        val commitCount = GitHubCommitsTable.id.count()
                        val topContributors = GitHubCommitsTable
                            .select(GitHubCommitsTable.authorName, commitCount)
                            .where { GitHubCommitsTable.repositoryId eq repoId }
                            .groupBy(GitHubCommitsTable.authorName)
                            .orderBy(commitCount, SortOrder.DESC)
                            .limit(TOP_CONTRIBUTORS)
                            .map {
                                GitHubContributorStats(
                                    username = it[GitHubCommitsTable.authorName] ?: "Unknown",
                                    commits = it[commitCount].toInt()
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
                                    commitDate = it[GitHubCommitsTable.commitDate]
                                )
                            }

                        val recentPullRequests = GitHubPullRequestsTable.selectAll()
                            .where { GitHubPullRequestsTable.repositoryId eq repoId }
                            .orderBy(GitHubPullRequestsTable.createdAt, SortOrder.DESC)
                            .limit(RECENT_ITEMS)
                            .map {
                                GitHubPullRequestItem(
                                    number = it[GitHubPullRequestsTable.number],
                                    title = it[GitHubPullRequestsTable.title],
                                    state = it[GitHubPullRequestsTable.state],
                                    authorUsername = it[GitHubPullRequestsTable.authorUsername],
                                    createdAt = it[GitHubPullRequestsTable.createdAt],
                                    mergedAt = it[GitHubPullRequestsTable.mergedAt]
                                )
                            }

                        GitHubAnalyticsResponse(
                            isConnected = true,
                            hasConnection = hasConnection,
                            canManage = access.isOwner,
                            repositoryName = repoRow[GitHubRepositoriesTable.fullName],
                            repositoryUrl = repoRow[GitHubRepositoriesTable.htmlUrl],
                            lastSyncedAt = repoRow[GitHubRepositoriesTable.lastSyncedAt],
                            isSyncing = GitHubSyncManager.isSyncing(repoId),
                            totalCommits = totalCommits,
                            openPullRequests = openPrs,
                            mergedPullRequests = mergedPrs,
                            topContributors = topContributors,
                            commitActivity = commitActivity,
                            recentCommits = recentCommits,
                            recentPullRequests = recentPullRequests,
                            notifyChannelId = repoRow[GitHubRepositoriesTable.notifyChannelId],
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

                    call.respond(HttpStatusCode.OK, analytics)
                }
            }
        }
    }
}
