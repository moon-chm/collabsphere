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
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.and
import java.time.Instant

fun Application.configureGitHubRoutes() {
    routing {
        
        // Unauthenticated routes for OAuth and Webhooks
        route("/auth/github") {
            
            // Redirect to GitHub for App Installation & Authorization
            get("/install") {
                val workspaceId = call.request.queryParameters["workspaceId"]
                val clientId = System.getenv("GITHUB_CLIENT_ID")
                println("[GitHub] /install hit. workspaceId=$workspaceId, clientId=$clientId")
                
                // Use OAuth authorize with redirect_uri pointing back to our callback
                val callbackUrl = "https://collabsphere-server-qtke.onrender.com/auth/github/callback"
                val url = "https://github.com/login/oauth/authorize?client_id=$clientId&redirect_uri=$callbackUrl&state=$workspaceId"
                println("[GitHub] Redirecting to: $url")
                call.respondRedirect(url)
            }

            // The callback when a user authorizes and installs the app
            get("/callback") {
                val code = call.request.queryParameters["code"]
                val installationIdStr = call.request.queryParameters["installation_id"]
                val setupAction = call.request.queryParameters["setup_action"]
                val workspaceId = call.request.queryParameters["state"]?.toIntOrNull()
                
                println("[GitHub] /callback hit!")
                println("[GitHub]   code=${code?.take(10)}...")
                println("[GitHub]   installation_id=$installationIdStr")
                println("[GitHub]   setup_action=$setupAction")
                println("[GitHub]   state/workspaceId=$workspaceId")

                if (code == null) {
                    println("[GitHub] ERROR: No code parameter!")
                    call.respond(HttpStatusCode.BadRequest, "Missing authorization code")
                    return@get
                }

                // Exchange code for User Token
                println("[GitHub] Exchanging code for user token...")
                val tokenResponse = GitHubAuthService.exchangeCodeForUserToken(code)
                if (tokenResponse == null) {
                    println("[GitHub] ERROR: Token exchange failed!")
                    call.respond(HttpStatusCode.InternalServerError, "Failed to exchange token")
                    return@get
                }
                println("[GitHub] Token exchange SUCCESS. access_token starts with: ${tokenResponse.access_token.take(10)}...")

                // Try to get installationId from the callback params first
                var installationId = installationIdStr?.toLongOrNull()
                
                // If no installationId in callback (pure OAuth flow), query user's installations
                if (installationId == null) {
                    println("[GitHub] No installation_id in callback, querying user installations...")
                    val installations = GitHubService.getUserInstallations(tokenResponse.access_token)
                    println("[GitHub] Found ${installations?.size ?: 0} installations")
                    installationId = installations?.firstOrNull()?.id
                    println("[GitHub] Using installationId=$installationId")
                }
                
                if (installationId != null && workspaceId != null) {
                    println("[GitHub] Fetching repos for installationId=$installationId...")
                    val repos = GitHubService.getInstallationRepositories(tokenResponse.access_token, installationId)
                    println("[GitHub] Found ${repos?.size ?: 0} repos")
                    
                    if (repos != null && repos.isNotEmpty()) {
                        val repo = repos.first()
                        println("[GitHub] Linking repo: ${repo.full_name} to workspace $workspaceId")
                        
                        try {
                            dbQuery {
                                val workspaceRow = WorkspacesTable.select { WorkspacesTable.id eq workspaceId }.singleOrNull()
                                if (workspaceRow != null) {
                                    val userId = workspaceRow[WorkspacesTable.userId]
                                    println("[GitHub] Found workspace owner userId=$userId")
                                    
                                    var connectionRow = GitHubConnectionsTable.select { GitHubConnectionsTable.userId eq userId }.singleOrNull()
                                    val connectionId = if (connectionRow == null) {
                                        println("[GitHub] Creating new GitHubConnection...")
                                        GitHubConnectionsTable.insert {
                                            it[GitHubConnectionsTable.userId] = userId
                                            it[GitHubConnectionsTable.githubUserId] = 0L
                                            it[GitHubConnectionsTable.githubUsername] = "connected_user"
                                            it[GitHubConnectionsTable.installationId] = installationId
                                            it[GitHubConnectionsTable.accessTokenEncrypted] = tokenResponse.access_token
                                        }[GitHubConnectionsTable.id]
                                    } else {
                                        println("[GitHub] Reusing existing GitHubConnection")
                                        connectionRow[GitHubConnectionsTable.id]
                                    }

                                    val existingRepo = GitHubRepositoriesTable.select { GitHubRepositoriesTable.workspaceId eq workspaceId }.singleOrNull()
                                    if (existingRepo == null) {
                                        println("[GitHub] Inserting repo into GitHubRepositoriesTable...")
                                        GitHubRepositoriesTable.insert {
                                            it[GitHubRepositoriesTable.connectionId] = connectionId
                                            it[GitHubRepositoriesTable.workspaceId] = workspaceId
                                            it[GitHubRepositoriesTable.githubRepoId] = repo.id
                                            it[GitHubRepositoriesTable.fullName] = repo.full_name
                                            it[GitHubRepositoriesTable.owner] = repo.owner.login
                                            it[GitHubRepositoriesTable.name] = repo.name
                                            it[GitHubRepositoriesTable.isPrivate] = repo.private
                                            it[GitHubRepositoriesTable.htmlUrl] = repo.html_url
                                            it[GitHubRepositoriesTable.defaultBranch] = repo.default_branch
                                        }
                                        println("[GitHub] SUCCESS: Repo linked!")
                                    } else {
                                        println("[GitHub] Repo already linked for this workspace")
                                    }
                                } else {
                                    println("[GitHub] ERROR: Workspace $workspaceId not found!")
                                }
                            }
                        } catch (e: Exception) {
                            println("[GitHub] DB ERROR: ${e.message}")
                            e.printStackTrace()
                        }
                    } else {
                        println("[GitHub] WARNING: No repos found for this installation")
                    }
                } else {
                    println("[GitHub] WARNING: installationId=$installationId, workspaceId=$workspaceId — skipping DB insert")
                }

                // Redirect back to Android App via deep link
                println("[GitHub] Redirecting to deep link: collabsphere://github-auth-success")
                call.respondRedirect("collabsphere://github-auth-success?installation_id=$installationId&workspace_id=$workspaceId")
            }
        }

        post("/webhook/github") {
            val eventType = call.request.header("X-GitHub-Event")
            val signature = call.request.header("X-Hub-Signature-256")
            
            val payload = call.receiveText()
            
            // Validate webhook signature
            val isValid = com.collabsphere.util.GitHubWebhookService.verifySignature(payload, signature)
            if (!isValid) {
                // If you haven't set a GITHUB_WEBHOOK_SECRET locally, this will fail.
                // call.respond(HttpStatusCode.Unauthorized, "Invalid signature")
                // return@post
                
                // For development, we'll just log and proceed.
                println("Warning: Webhook signature verification failed or secret not set.")
            }

            // Parse and insert into DB
            com.collabsphere.util.GitHubWebhookService.handleWebhookEvent(eventType, payload)

            call.respond(HttpStatusCode.OK)
        }

        // Authenticated routes for the Android App
        authenticate("auth-jwt") {
            route("/api/workspace/{workspaceId}/github") {
                
                get("/analytics") {
                    val workspaceId = call.parameters["workspaceId"]?.toIntOrNull()
                    if (workspaceId == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid workspace ID")
                        return@get
                    }
                    
                    val analytics = dbQuery {
                        // Find if workspace has a linked repo
                        val repoRow = GitHubRepositoriesTable
                            .select { GitHubRepositoriesTable.workspaceId eq workspaceId }
                            .singleOrNull()

                        if (repoRow == null) {
                            return@dbQuery com.collabsphere.dto.GitHubAnalyticsResponse(
                                isConnected = false,
                                repositoryName = null,
                                totalCommits = 0,
                                openPullRequests = 0,
                                mergedPullRequests = 0,
                                topContributors = emptyList()
                            )
                        }

                        val repoId = repoRow[GitHubRepositoriesTable.id]
                        val repoName = repoRow[GitHubRepositoriesTable.fullName]

                        // Get Commit Count
                        val totalCommits = com.collabsphere.model.GitHubCommitsTable
                            .select { com.collabsphere.model.GitHubCommitsTable.repositoryId eq repoId }
                            .count().toInt()

                        // Get PR Stats
                        val openPrs = com.collabsphere.model.GitHubPullRequestsTable
                            .select { (com.collabsphere.model.GitHubPullRequestsTable.repositoryId eq repoId) and (com.collabsphere.model.GitHubPullRequestsTable.state eq "open") }
                            .count().toInt()

                        val mergedPrs = com.collabsphere.model.GitHubPullRequestsTable
                            .select { (com.collabsphere.model.GitHubPullRequestsTable.repositoryId eq repoId) and (com.collabsphere.model.GitHubPullRequestsTable.mergedAt neq null) }
                            .count().toInt()

                        com.collabsphere.dto.GitHubAnalyticsResponse(
                            isConnected = true,
                            repositoryName = repoName,
                            totalCommits = totalCommits,
                            openPullRequests = openPrs,
                            mergedPullRequests = mergedPrs,
                            topContributors = emptyList() // TODO: Group by query for contributors
                        )
                    }
                    
                    call.respond(HttpStatusCode.OK, analytics)
                }
            }
        }
    }
}
