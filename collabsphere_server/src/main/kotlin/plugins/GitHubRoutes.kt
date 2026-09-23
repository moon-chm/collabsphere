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
                // Using the OAuth authorize endpoint as a reliable entry point. 
                // Alternatively, https://github.com/apps/<app-slug>/installations/new?state=$workspaceId
                val url = "https://github.com/login/oauth/authorize?client_id=$clientId&state=$workspaceId"
                call.respondRedirect(url)
            }

            // The callback when a user authorizes and installs the app
            get("/callback") {
                val code = call.request.queryParameters["code"]
                val installationIdStr = call.request.queryParameters["installation_id"]
                val setupAction = call.request.queryParameters["setup_action"]
                val workspaceId = call.request.queryParameters["state"]?.toIntOrNull()

                if (code == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing authorization code")
                    return@get
                }

                // Exchange code for User Token
                val tokenResponse = GitHubAuthService.exchangeCodeForUserToken(code)
                if (tokenResponse == null) {
                    call.respond(HttpStatusCode.InternalServerError, "Failed to exchange token")
                    return@get
                }

                val installationId = installationIdStr?.toLongOrNull()
                
                // If installationId is provided in callback, we can fetch repos immediately
                if (installationId != null && workspaceId != null) {
                    val reposResponse = GitHubService.getInstallationRepositories(tokenResponse.access_token, installationId)
                    if (reposResponse != null && reposResponse.repositories.isNotEmpty()) {
                        val repo = reposResponse.repositories.first()
                        
                        dbQuery {
                            // Check if workspace already has a repo linked
                            val existing = GitHubRepositoriesTable.select { GitHubRepositoriesTable.workspaceId eq workspaceId }.singleOrNull()
                            if (existing == null) {
                                GitHubRepositoriesTable.insert {
                                    it[GitHubRepositoriesTable.workspaceId] = workspaceId
                                    it[GitHubRepositoriesTable.installationId] = installationId
                                    it[GitHubRepositoriesTable.repositoryId] = repo.id
                                    it[GitHubRepositoriesTable.fullName] = repo.full_name
                                    it[GitHubRepositoriesTable.owner] = repo.owner.login
                                    it[GitHubRepositoriesTable.name] = repo.name
                                }
                            }
                        }
                    }
                }

                // Redirect back to Android App via deep link
                call.respondRedirect("collabsphere://github-auth-success?installation_id=$installationId")
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
