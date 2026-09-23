package plugins

import com.collabsphere.model.GitHubConnectionsTable
import com.collabsphere.model.GitHubRepositoriesTable
import com.collabsphere.model.WorkspacesTable
import com.collabsphere.model.WorkspaceMembersTable
import com.collabsphere.util.GitHubAuthService
import com.collabsphere.util.GitHubService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import kotlinx.serialization.Serializable
import java.time.Instant

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

fun Application.configureGitHubRoutes() {
    routing {
        
        // Unauthenticated routes for OAuth and Webhooks
        route("/auth/github") {
            
            // Redirect to GitHub for App Installation & Authorization
            get("/install") {
                val workspaceId = call.request.queryParameters["workspaceId"]
                println("[GitHub] /install hit. workspaceId=$workspaceId")
                val url = "https://github.com/apps/collabspheregithubfeat/installations/new?state=$workspaceId"
                println("[GitHub] Redirecting to App install page: $url")
                call.respondRedirect(url)
            }

            // Callback: save the GitHub connection only. User picks repo in-app.
            get("/callback") {
                val code = call.request.queryParameters["code"]
                val installationIdStr = call.request.queryParameters["installation_id"]
                val workspaceId = call.request.queryParameters["state"]?.toIntOrNull()
                
                println("[GitHub] /callback hit! code=${code?.take(10)}..., installation_id=$installationIdStr, workspaceId=$workspaceId")

                if (code == null) {
                    println("[GitHub] ERROR: No code parameter!")
                    call.respond(HttpStatusCode.BadRequest, "Missing authorization code")
                    return@get
                }

                println("[GitHub] Exchanging code for user token...")
                val tokenResponse = GitHubAuthService.exchangeCodeForUserToken(code)
                if (tokenResponse == null) {
                    println("[GitHub] ERROR: Token exchange failed!")
                    call.respond(HttpStatusCode.InternalServerError, "Failed to exchange token")
                    return@get
                }
                println("[GitHub] Token exchange SUCCESS")

                var installationId = installationIdStr?.toLongOrNull()
                if (installationId == null) {
                    println("[GitHub] No installation_id in callback, querying user installations...")
                    val installations = GitHubService.getUserInstallations(tokenResponse.access_token)
                    installationId = installations?.firstOrNull()?.id
                    println("[GitHub] Using installationId=$installationId")
                }
                
                if (installationId != null && workspaceId != null) {
                    try {
                        dbQuery {
                            val workspaceRow = WorkspacesTable.select { WorkspacesTable.id eq workspaceId }.singleOrNull()
                            if (workspaceRow != null) {
                                val userId = workspaceRow[WorkspacesTable.userId]
                                
                                val existingConn = GitHubConnectionsTable.select { GitHubConnectionsTable.userId eq userId }.singleOrNull()
                                if (existingConn == null) {
                                    println("[GitHub] Creating new GitHubConnection for userId=$userId")
                                    GitHubConnectionsTable.insert {
                                        it[GitHubConnectionsTable.userId] = userId
                                        it[GitHubConnectionsTable.githubUserId] = 0L
                                        it[GitHubConnectionsTable.githubUsername] = "connected_user"
                                        it[GitHubConnectionsTable.installationId] = installationId
                                        it[GitHubConnectionsTable.accessTokenEncrypted] = tokenResponse.access_token
                                    }
                                } else {
                                    println("[GitHub] Updating existing GitHubConnection for userId=$userId")
                                    GitHubConnectionsTable.update({ GitHubConnectionsTable.userId eq userId }) {
                                        it[GitHubConnectionsTable.installationId] = installationId
                                        it[GitHubConnectionsTable.accessTokenEncrypted] = tokenResponse.access_token
                                        it[GitHubConnectionsTable.updatedAt] = System.currentTimeMillis()
                                    }
                                }
                                println("[GitHub] Connection saved! User will pick repo in-app.")
                            }
                        }
                    } catch (e: Exception) {
                        println("[GitHub] DB ERROR: ${e.message}")
                        e.printStackTrace()
                    }
                }

                call.respondRedirect("collabsphere://github-auth-success?installation_id=$installationId&workspace_id=$workspaceId")
            }

            // Quick reset endpoints for testing/debugging via browser
            get("/unlink/{workspaceId}") {
                val workspaceId = call.parameters["workspaceId"]?.toIntOrNull()
                if (workspaceId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid workspace ID")
                    return@get
                }
                val count = dbQuery {
                    GitHubRepositoriesTable.deleteWhere { GitHubRepositoriesTable.workspaceId eq workspaceId }
                }
                call.respondText("Successfully unlinked repository from workspace $workspaceId (rows removed: $count). Now open the app and pick a repo!")
            }

            get("/reset/{workspaceId}") {
                val workspaceId = call.parameters["workspaceId"]?.toIntOrNull()
                if (workspaceId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid workspace ID")
                    return@get
                }
                val info = dbQuery {
                    val workspaceRow = WorkspacesTable.select { WorkspacesTable.id eq workspaceId }.singleOrNull()
                    if (workspaceRow != null) {
                        val userId = workspaceRow[WorkspacesTable.userId]
                        val connDeleted = GitHubConnectionsTable.deleteWhere { GitHubConnectionsTable.userId eq userId }
                        val repoDeleted = GitHubRepositoriesTable.deleteWhere { GitHubRepositoriesTable.workspaceId eq workspaceId }
                        "Deleted connection ($connDeleted) and repo ($repoDeleted) for user $userId, workspace $workspaceId"
                    } else {
                        "Workspace $workspaceId not found"
                    }
                }
                call.respondText("Reset complete: $info")
            }

            get("/debug") {
                val info = dbQuery {
                    val conns = GitHubConnectionsTable.selectAll().map {
                        "Conn(id=${it[GitHubConnectionsTable.id]}, userId=${it[GitHubConnectionsTable.userId]}, instId=${it[GitHubConnectionsTable.installationId]}, user=${it[GitHubConnectionsTable.githubUsername]})"
                    }
                    val repos = GitHubRepositoriesTable.selectAll().map {
                        "Repo(id=${it[GitHubRepositoriesTable.id]}, connId=${it[GitHubRepositoriesTable.connectionId]}, wsId=${it[GitHubRepositoriesTable.workspaceId]}, fullName=${it[GitHubRepositoriesTable.fullName]})"
                    }
                    val workspaces = WorkspacesTable.selectAll().map {
                        "WS(id=${it[WorkspacesTable.id]}, userId=${it[WorkspacesTable.userId]}, name=${it[WorkspacesTable.workspaceName]})"
                    }
                    "=== WORKSPACES ===\n${workspaces.joinToString("\n")}\n\n=== CONNECTIONS ===\n${conns.joinToString("\n")}\n\n=== REPOSITORIES ===\n${repos.joinToString("\n")}"
                }
                call.respondText(info)
            }
        }

        post("/webhook/github") {
            val eventType = call.request.header("X-GitHub-Event")
            val signature = call.request.header("X-Hub-Signature-256")
            val payload = call.receiveText()
            
            val isValid = com.collabsphere.util.GitHubWebhookService.verifySignature(payload, signature)
            if (!isValid) {
                println("Warning: Webhook signature verification failed or secret not set.")
            }
            com.collabsphere.util.GitHubWebhookService.handleWebhookEvent(eventType, payload)
            call.respond(HttpStatusCode.OK)
        }

        // Authenticated routes for the Android App
        authenticate("auth-jwt") {
            route("/api/workspace/{workspaceId}/github") {
                
                // List repos the user can link to this workspace
                get("/available-repos") {
                    val workspaceId = call.parameters["workspaceId"]?.toIntOrNull()
                    if (workspaceId == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid workspace ID")
                        return@get
                    }
                    
                    try {
                        val connectionInfo = dbQuery {
                            val workspaceRow = WorkspacesTable.select { WorkspacesTable.id eq workspaceId }.singleOrNull()
                            val wsOwnerId = workspaceRow?.get(WorkspacesTable.userId)
                            val callerUserId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asInt()
                            
                            val connRow = (if (wsOwnerId != null) GitHubConnectionsTable.select { GitHubConnectionsTable.userId eq wsOwnerId }.firstOrNull() else null)
                                ?: (if (callerUserId != null) GitHubConnectionsTable.select { GitHubConnectionsTable.userId eq callerUserId }.firstOrNull() else null)
                                ?: GitHubConnectionsTable.selectAll().firstOrNull()
                                ?: return@dbQuery null
                                
                            Pair(
                                connRow[GitHubConnectionsTable.installationId],
                                connRow[GitHubConnectionsTable.accessTokenEncrypted]
                            )
                        }
                        
                        if (connectionInfo == null) {
                            call.respond(HttpStatusCode.OK, emptyList<AvailableRepo>())
                            return@get
                        }
                        
                        val (installationId, accessToken) = connectionInfo
                        val repos = GitHubService.getInstallationRepositories(accessToken ?: "", installationId)
                        
                        val available = repos?.map { repo ->
                            AvailableRepo(
                                id = repo.id,
                                fullName = repo.full_name,
                                owner = repo.owner.login,
                                name = repo.name,
                                isPrivate = repo.private,
                                htmlUrl = repo.html_url,
                                defaultBranch = repo.default_branch
                            )
                        } ?: emptyList()
                        
                        call.respond(HttpStatusCode.OK, available)
                    } catch (e: Exception) {
                        println("[GitHub] Error fetching available repos: ${e.message}")
                        call.respond(HttpStatusCode.InternalServerError, "Failed to fetch repos")
                    }
                }
                
                // Link a specific repo to this workspace
                post("/link-repo") {
                    val workspaceId = call.parameters["workspaceId"]?.toIntOrNull()
                    if (workspaceId == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid workspace ID")
                        return@post
                    }
                    
                    val request = call.receive<LinkRepoRequest>()
                    
                    try {
                        var insertedRepoId: Int? = null
                        var userToken: String? = null
                        
                        val linkSuccess = dbQuery {
                            val workspaceRow = WorkspacesTable.select { WorkspacesTable.id eq workspaceId }.singleOrNull()
                                ?: return@dbQuery false
                            val wsOwnerId = workspaceRow[WorkspacesTable.userId]
                            val callerUserId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asInt()
                            
                            val connRow = (if (wsOwnerId != null) GitHubConnectionsTable.select { GitHubConnectionsTable.userId eq wsOwnerId }.firstOrNull() else null)
                                ?: (if (callerUserId != null) GitHubConnectionsTable.select { GitHubConnectionsTable.userId eq callerUserId }.firstOrNull() else null)
                                ?: GitHubConnectionsTable.selectAll().firstOrNull()
                                ?: return@dbQuery false
                                
                            val connectionId = connRow[GitHubConnectionsTable.id]
                            userToken = connRow[GitHubConnectionsTable.accessTokenEncrypted]
                            
                            // Remove any existing link for this workspace
                            GitHubRepositoriesTable.deleteWhere { GitHubRepositoriesTable.workspaceId eq workspaceId }
                            
                            val repoId = GitHubRepositoriesTable.insert {
                                it[GitHubRepositoriesTable.connectionId] = connectionId
                                it[GitHubRepositoriesTable.workspaceId] = workspaceId
                                it[GitHubRepositoriesTable.githubRepoId] = request.repoId
                                it[GitHubRepositoriesTable.fullName] = request.fullName
                                it[GitHubRepositoriesTable.owner] = request.owner
                                it[GitHubRepositoriesTable.name] = request.name
                                it[GitHubRepositoriesTable.isPrivate] = request.isPrivate
                                it[GitHubRepositoriesTable.htmlUrl] = request.htmlUrl
                                it[GitHubRepositoriesTable.defaultBranch] = request.defaultBranch
                            } get GitHubRepositoriesTable.id
                            
                            insertedRepoId = repoId
                            println("[GitHub] Linked repo ${request.fullName} (id=$repoId) to workspace $workspaceId")
                            true
                        }
                        
                        if (!linkSuccess) {
                            println("[GitHub] Failed to link repo: No connection or workspace found for ws=$workspaceId")
                            call.respond(HttpStatusCode.NotFound, "No GitHub connection or workspace found. Please connect to GitHub first.")
                            return@post
                        }
                        
                        // Initial sync of recent commits and PRs so analytics immediately shows data
                        if (insertedRepoId != null && !userToken.isNullOrBlank()) {
                            try {
                                val recentCommits = GitHubService.getRecentCommits(userToken!!, request.fullName)
                                if (recentCommits != null && recentCommits.isNotEmpty()) {
                                    dbQuery {
                                        for (c in recentCommits) {
                                            com.collabsphere.model.GitHubCommitsTable.insert {
                                                it[com.collabsphere.model.GitHubCommitsTable.repositoryId] = insertedRepoId!!
                                                it[com.collabsphere.model.GitHubCommitsTable.sha] = c.sha
                                                it[com.collabsphere.model.GitHubCommitsTable.message] = c.commit?.message ?: ""
                                                it[com.collabsphere.model.GitHubCommitsTable.authorName] = c.commit?.author?.name
                                                it[com.collabsphere.model.GitHubCommitsTable.authorEmail] = c.commit?.author?.email
                                                it[com.collabsphere.model.GitHubCommitsTable.commitDate] = System.currentTimeMillis()
                                            }
                                        }
                                    }
                                    println("[GitHub] Synced ${recentCommits.size} initial commits for ${request.fullName}")
                                }
                                
                                val recentPrs = GitHubService.getRecentPullRequests(userToken!!, request.fullName)
                                if (recentPrs != null && recentPrs.isNotEmpty()) {
                                    dbQuery {
                                        for (pr in recentPrs) {
                                            com.collabsphere.model.GitHubPullRequestsTable.insert {
                                                it[com.collabsphere.model.GitHubPullRequestsTable.repositoryId] = insertedRepoId!!
                                                it[com.collabsphere.model.GitHubPullRequestsTable.githubPrId] = pr.id
                                                it[com.collabsphere.model.GitHubPullRequestsTable.number] = pr.number
                                                it[com.collabsphere.model.GitHubPullRequestsTable.title] = pr.title
                                                it[com.collabsphere.model.GitHubPullRequestsTable.state] = pr.state
                                                it[com.collabsphere.model.GitHubPullRequestsTable.authorUsername] = "github_user"
                                                it[com.collabsphere.model.GitHubPullRequestsTable.createdAt] = System.currentTimeMillis()
                                                it[com.collabsphere.model.GitHubPullRequestsTable.mergedAt] = if (pr.merged_at != null) System.currentTimeMillis() else null
                                            }
                                        }
                                    }
                                    println("[GitHub] Synced ${recentPrs.size} initial PRs for ${request.fullName}")
                                }
                            } catch (e: Exception) {
                                println("[GitHub] Warning: Initial sync of commits/PRs failed: ${e.message}")
                            }
                        }
                        
                        call.respond(HttpStatusCode.OK, mapOf("status" to "linked", "repo" to request.fullName))
                    } catch (e: Exception) {
                        println("[GitHub] Error linking repo: ${e.message}")
                        e.printStackTrace()
                        call.respond(HttpStatusCode.InternalServerError, "Failed to link repo: ${e.message}")
                    }
                }
                
                // Unlink repo from this workspace
                post("/unlink-repo") {
                    val workspaceId = call.parameters["workspaceId"]?.toIntOrNull()
                    if (workspaceId == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid workspace ID")
                        return@post
                    }
                    try {
                        dbQuery {
                            GitHubRepositoriesTable.deleteWhere { GitHubRepositoriesTable.workspaceId eq workspaceId }
                        }
                        println("[GitHub] Unlinked repo from workspace $workspaceId")
                        call.respond(HttpStatusCode.OK, mapOf("status" to "unlinked"))
                    } catch (e: Exception) {
                        println("[GitHub] Error unlinking repo: ${e.message}")
                        call.respond(HttpStatusCode.InternalServerError, "Failed to unlink repo")
                    }
                }

                // Disconnect GitHub completely for this workspace / user
                post("/disconnect") {
                    val workspaceId = call.parameters["workspaceId"]?.toIntOrNull()
                    if (workspaceId == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid workspace ID")
                        return@post
                    }
                    try {
                        dbQuery {
                            val workspaceRow = WorkspacesTable.select { WorkspacesTable.id eq workspaceId }.singleOrNull()
                            val wsOwnerId = workspaceRow?.get(WorkspacesTable.userId)
                            val callerUserId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asInt()
                            if (wsOwnerId != null) {
                                GitHubConnectionsTable.deleteWhere { GitHubConnectionsTable.userId eq wsOwnerId }
                            }
                            if (callerUserId != null) {
                                GitHubConnectionsTable.deleteWhere { GitHubConnectionsTable.userId eq callerUserId }
                            }
                            GitHubRepositoriesTable.deleteWhere { GitHubRepositoriesTable.workspaceId eq workspaceId }
                        }
                        println("[GitHub] Disconnected GitHub for workspace $workspaceId")
                        call.respond(HttpStatusCode.OK, mapOf("status" to "disconnected"))
                    } catch (e: Exception) {
                        println("[GitHub] Error disconnecting GitHub: ${e.message}")
                        call.respond(HttpStatusCode.InternalServerError, "Failed to disconnect GitHub")
                    }
                }
                
                get("/analytics") {
                    val workspaceId = call.parameters["workspaceId"]?.toIntOrNull()
                    if (workspaceId == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid workspace ID")
                        return@get
                    }
                    
                    val analytics = dbQuery {
                        val workspaceRow = WorkspacesTable.select { WorkspacesTable.id eq workspaceId }.singleOrNull()
                        val wsOwnerId = workspaceRow?.get(WorkspacesTable.userId)
                        val callerUserId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asInt()
                        
                        val hasConnection = (if (wsOwnerId != null) GitHubConnectionsTable.select { GitHubConnectionsTable.userId eq wsOwnerId }.count() > 0 else false)
                            || (if (callerUserId != null) GitHubConnectionsTable.select { GitHubConnectionsTable.userId eq callerUserId }.count() > 0 else false)
                            || (GitHubConnectionsTable.selectAll().count() > 0)
                        
                        val repoRow = GitHubRepositoriesTable
                            .select { GitHubRepositoriesTable.workspaceId eq workspaceId }
                            .singleOrNull()

                        if (repoRow == null) {
                            return@dbQuery com.collabsphere.dto.GitHubAnalyticsResponse(
                                isConnected = false,
                                hasConnection = hasConnection,
                                repositoryName = null,
                                totalCommits = 0,
                                openPullRequests = 0,
                                mergedPullRequests = 0,
                                topContributors = emptyList()
                            )
                        }

                        val repoId = repoRow[GitHubRepositoriesTable.id]
                        val repoName = repoRow[GitHubRepositoriesTable.fullName]

                        val totalCommits = com.collabsphere.model.GitHubCommitsTable
                            .select { com.collabsphere.model.GitHubCommitsTable.repositoryId eq repoId }
                            .count().toInt()

                        val openPrs = com.collabsphere.model.GitHubPullRequestsTable
                            .select { (com.collabsphere.model.GitHubPullRequestsTable.repositoryId eq repoId) and (com.collabsphere.model.GitHubPullRequestsTable.state eq "open") }
                            .count().toInt()

                        val mergedPrs = com.collabsphere.model.GitHubPullRequestsTable
                            .select { (com.collabsphere.model.GitHubPullRequestsTable.repositoryId eq repoId) and (com.collabsphere.model.GitHubPullRequestsTable.mergedAt neq null) }
                            .count().toInt()

                        com.collabsphere.dto.GitHubAnalyticsResponse(
                            isConnected = true,
                            hasConnection = true,
                            repositoryName = repoName,
                            totalCommits = totalCommits,
                            openPullRequests = openPrs,
                            mergedPullRequests = mergedPrs,
                            topContributors = emptyList()
                        )
                    }
                    
                    call.respond(HttpStatusCode.OK, analytics)
                }
            }
        }
    }
}
