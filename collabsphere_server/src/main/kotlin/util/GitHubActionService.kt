package com.collabsphere.util

import com.collabsphere.dto.GitHubActionRequest
import com.collabsphere.dto.GitHubActionResponse
import com.collabsphere.dto.GitHubActionResultStatus
import com.collabsphere.model.GitHubActionIdempotencyTable
import com.collabsphere.model.GitHubConnectionsTable
import com.collabsphere.model.GitHubRepositoriesTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

object GitHubActionService {

    suspend fun processAction(
        workspaceId: Int,
        userId: Int,
        idempotencyKey: String,
        request: GitHubActionRequest
    ): GitHubActionResponse {
        // 1. Resolve Resource Identity (URL -> Owner/Repo -> Linked Repo)
        val parsed = GitHubUnfurlService.parse(request.url) 
            ?: return GitHubActionResponse(GitHubActionResultStatus.ValidationFailed, "Invalid GitHub URL")

        return newSuspendedTransaction {
            // 2. Validate workspace connection and repository link
            val repoRow = GitHubRepositoriesTable.selectAll()
                .where { (GitHubRepositoriesTable.workspaceId eq workspaceId) and 
                         (GitHubRepositoriesTable.fullName.lowerCase() eq parsed.repoFullName.lowercase()) }
                .singleOrNull()
            
            if (repoRow == null) {
                return@newSuspendedTransaction GitHubActionResponse(
                    GitHubActionResultStatus.ResourceNotFound, 
                    "Repository is not linked to this workspace"
                )
            }

            // 3. Token lookup
            val connRow = GitHubConnectionsTable.selectAll()
                .where { GitHubConnectionsTable.userId eq userId }
                .singleOrNull()

            if (connRow == null || connRow[GitHubConnectionsTable.accessTokenEncrypted] == null) {
                return@newSuspendedTransaction GitHubActionResponse(
                    GitHubActionResultStatus.AuthenticationRequired,
                    "Please connect your GitHub account to perform this action"
                )
            }

            val encryptedToken = connRow[GitHubConnectionsTable.accessTokenEncrypted]
            val token = CryptoService.decrypt(encryptedToken)

            if (token.isNullOrBlank()) {
                return@newSuspendedTransaction GitHubActionResponse(
                    GitHubActionResultStatus.AuthenticationRequired,
                    "GitHub access token is invalid or expired. Please reconnect."
                )
            }
            // 4. Idempotency State Machine
            val existing = GitHubActionIdempotencyTable.selectAll()
                .where { GitHubActionIdempotencyTable.idempotencyKey eq idempotencyKey }
                .singleOrNull()
            
            val now = System.currentTimeMillis()

            if (existing != null) {
                val status = existing[GitHubActionIdempotencyTable.status]
                if (status == "SUCCEEDED" || status == "FAILED_FINAL") {
                    val resultJson = existing[GitHubActionIdempotencyTable.resultBody] ?: "{}"
                    // Normally parse JSON, but for simplicity let's just construct it
                    // The resultBody can be stored as JSON string of GitHubActionResponse
                    return@newSuspendedTransaction kotlinx.serialization.json.Json.decodeFromString<GitHubActionResponse>(resultJson)
                }
                
                if (status == "IN_PROGRESS") {
                    return@newSuspendedTransaction GitHubActionResponse(
                        GitHubActionResultStatus.RateLimited, 
                        "Action already in progress"
                    )
                }
                
                if (status == "FAILED_RETRYABLE") {
                    // Update to IN_PROGRESS and proceed
                    GitHubActionIdempotencyTable.update({ GitHubActionIdempotencyTable.idempotencyKey eq idempotencyKey }) {
                        it[this.status] = "IN_PROGRESS"
                    }
                }
            } else {
                try {
                    GitHubActionIdempotencyTable.insert {
                        it[this.idempotencyKey] = idempotencyKey
                        it[this.userId] = userId
                        it[this.workspaceId] = workspaceId
                        it[this.resourceIdentity] = request.url
                        it[this.action] = request.action
                        it[this.status] = "IN_PROGRESS"
                        it[this.createdAt] = now
                    }
                } catch (e: Exception) {
                    // Unique constraint violation means another thread just inserted it
                    return@newSuspendedTransaction GitHubActionResponse(
                        GitHubActionResultStatus.RateLimited, 
                        "Action already in progress"
                    )
                }
            }

            // 5. Execute action using GitHub API
            val result = try {
                executeGitHubMutation(token, parsed, request.action, repoRow[GitHubRepositoriesTable.id])
            } catch (e: Exception) {
                GitHubActionResponse(
                    GitHubActionResultStatus.UnknownFailure, 
                    "Network error occurred"
                )
            }

            // 6. Update Idempotency Table
            val finalStatus = if (result.status == GitHubActionResultStatus.UnknownFailure || result.status == GitHubActionResultStatus.GitHubUnavailable || result.status == GitHubActionResultStatus.RateLimited) {
                "FAILED_RETRYABLE"
            } else if (result.status == GitHubActionResultStatus.ActionSucceeded || result.status == GitHubActionResultStatus.ActionAlreadyApplied) {
                "SUCCEEDED"
            } else {
                "FAILED_FINAL"
            }

            val resultJson = kotlinx.serialization.json.Json.encodeToString(GitHubActionResponse.serializer(), result)

            GitHubActionIdempotencyTable.update({ GitHubActionIdempotencyTable.idempotencyKey eq idempotencyKey }) {
                it[this.status] = finalStatus
                it[this.resultBody] = resultJson
                it[this.completedAt] = System.currentTimeMillis()
            }

            result
        }
    }

    private suspend fun executeGitHubMutation(
        token: String, 
        parsed: GitHubUnfurlService.ParsedUrl, 
        action: String,
        repositoryId: Int
    ): GitHubActionResponse {
        return when (action) {
            "CLOSE_ISSUE", "REOPEN_ISSUE" -> {
                if (parsed !is GitHubUnfurlService.ParsedUrl.Issue) {
                    return GitHubActionResponse(GitHubActionResultStatus.ValidationFailed, "URL is not an issue")
                }
                
                val newState = if (action == "CLOSE_ISSUE") "closed" else "open"
                val stateReason = if (action == "CLOSE_ISSUE") "completed" else "reopened"
                
                val (updatedIssue, statusCode) = GitHubService.updateIssueState(token, parsed.repoFullName, parsed.number, newState, stateReason)
                
                when (statusCode) {
                    io.ktor.http.HttpStatusCode.OK -> {
                        if (updatedIssue != null) {
                            newSuspendedTransaction {
                                GitHubDataStore.saveIssue(repositoryId, updatedIssue.toRecord())
                            }
                            GitHubActionResponse(GitHubActionResultStatus.ActionSucceeded, "Issue successfully $newState", newState)
                        } else {
                            GitHubActionResponse(GitHubActionResultStatus.UnknownFailure, "Failed to parse GitHub response")
                        }
                    }
                    io.ktor.http.HttpStatusCode.Forbidden, io.ktor.http.HttpStatusCode.Unauthorized -> {
                        GitHubActionResponse(GitHubActionResultStatus.PermissionDenied, "You do not have permission to modify this issue on GitHub")
                    }
                    io.ktor.http.HttpStatusCode.NotFound -> {
                        GitHubActionResponse(GitHubActionResultStatus.ResourceNotFound, "Issue not found on GitHub")
                    }
                    io.ktor.http.HttpStatusCode.TooManyRequests -> {
                        GitHubActionResponse(GitHubActionResultStatus.RateLimited, "GitHub API rate limit exceeded")
                    }
                    else -> {
                        GitHubActionResponse(GitHubActionResultStatus.UnknownFailure, "GitHub API returned status ${statusCode.value}")
                    }
                }
            }
            "CLOSE_PR", "REOPEN_PR" -> {
                if (parsed !is GitHubUnfurlService.ParsedUrl.PullRequest) {
                    return GitHubActionResponse(GitHubActionResultStatus.ValidationFailed, "URL is not a pull request")
                }
                val newState = if (action == "CLOSE_PR") "closed" else "open"
                val (updatedPr, statusCode) = GitHubService.updatePullRequestState(token, parsed.repoFullName, parsed.number, newState)
                
                when (statusCode) {
                    io.ktor.http.HttpStatusCode.OK -> {
                        if (updatedPr != null) {
                            val now = System.currentTimeMillis()
                            val prRecord = com.collabsphere.util.PullRequestRecord(
                                githubPrId = updatedPr.id,
                                number = updatedPr.number,
                                title = updatedPr.title,
                                state = updatedPr.state,
                                authorUsername = updatedPr.user?.login ?: "",
                                createdAt = com.collabsphere.util.parseGitHubTime(updatedPr.created_at) ?: now,
                                closedAt = com.collabsphere.util.parseGitHubTime(updatedPr.closed_at),
                                mergedAt = com.collabsphere.util.parseGitHubTime(updatedPr.merged_at),
                                body = updatedPr.body.orEmpty(),
                                url = updatedPr.html_url,
                                headSha = updatedPr.head?.sha
                            )
                            newSuspendedTransaction {
                                GitHubDataStore.savePullRequest(repositoryId, prRecord)
                            }
                            GitHubActionResponse(GitHubActionResultStatus.ActionSucceeded, "Pull request successfully $newState", newState)
                        } else {
                            GitHubActionResponse(GitHubActionResultStatus.UnknownFailure, "Failed to parse GitHub response")
                        }
                    }
                    io.ktor.http.HttpStatusCode.Forbidden, io.ktor.http.HttpStatusCode.Unauthorized -> {
                        GitHubActionResponse(GitHubActionResultStatus.PermissionDenied, "You do not have permission to modify this pull request")
                    }
                    io.ktor.http.HttpStatusCode.NotFound -> {
                        GitHubActionResponse(GitHubActionResultStatus.ResourceNotFound, "Pull request not found on GitHub")
                    }
                    io.ktor.http.HttpStatusCode.UnprocessableEntity -> {
                        GitHubActionResponse(GitHubActionResultStatus.ValidationFailed, "Cannot reopen a merged pull request")
                    }
                    io.ktor.http.HttpStatusCode.TooManyRequests -> {
                        GitHubActionResponse(GitHubActionResultStatus.RateLimited, "GitHub API rate limit exceeded")
                    }
                    else -> GitHubActionResponse(GitHubActionResultStatus.UnknownFailure, "GitHub API returned status ${statusCode.value}")
                }
            }
            "APPROVE_PR" -> {
                if (parsed !is GitHubUnfurlService.ParsedUrl.PullRequest) {
                    return GitHubActionResponse(GitHubActionResultStatus.ValidationFailed, "URL is not a pull request")
                }
                val statusCode = GitHubService.approvePullRequest(token, parsed.repoFullName, parsed.number)
                when (statusCode) {
                    io.ktor.http.HttpStatusCode.OK -> GitHubActionResponse(GitHubActionResultStatus.ActionSucceeded, "Pull request approved")
                    io.ktor.http.HttpStatusCode.Forbidden, io.ktor.http.HttpStatusCode.Unauthorized -> GitHubActionResponse(GitHubActionResultStatus.PermissionDenied, "You do not have permission to approve this pull request")
                    io.ktor.http.HttpStatusCode.NotFound -> GitHubActionResponse(GitHubActionResultStatus.ResourceNotFound, "Pull request not found")
                    io.ktor.http.HttpStatusCode.UnprocessableEntity -> GitHubActionResponse(GitHubActionResultStatus.ValidationFailed, "Cannot approve your own pull request or PR is closed")
                    io.ktor.http.HttpStatusCode.TooManyRequests -> GitHubActionResponse(GitHubActionResultStatus.RateLimited, "GitHub API rate limit exceeded")
                    else -> GitHubActionResponse(GitHubActionResultStatus.UnknownFailure, "GitHub API returned status ${statusCode.value}")
                }
            }
            "MERGE_PR" -> {
                if (parsed !is GitHubUnfurlService.ParsedUrl.PullRequest) {
                    return GitHubActionResponse(GitHubActionResultStatus.ValidationFailed, "URL is not a pull request")
                }
                val (result, statusCode) = GitHubService.mergePullRequest(token, parsed.repoFullName, parsed.number)
                when (statusCode) {
                    io.ktor.http.HttpStatusCode.OK -> {
                        // Merged successfully.
                        // We must fetch the updated PR state to save it accurately with the correct timestamp.
                        val updatedPr = GitHubService.getPullRequest(token, parsed.repoFullName, parsed.number)
                        if (updatedPr != null) {
                            val now = System.currentTimeMillis()
                            val prRecord = com.collabsphere.util.PullRequestRecord(
                                githubPrId = updatedPr.id,
                                number = updatedPr.number,
                                title = updatedPr.title,
                                state = updatedPr.state,
                                authorUsername = updatedPr.user?.login ?: "",
                                createdAt = com.collabsphere.util.parseGitHubTime(updatedPr.created_at) ?: now,
                                closedAt = com.collabsphere.util.parseGitHubTime(updatedPr.closed_at),
                                mergedAt = com.collabsphere.util.parseGitHubTime(updatedPr.merged_at),
                                body = updatedPr.body.orEmpty(),
                                url = updatedPr.html_url,
                                headSha = updatedPr.head?.sha
                            )
                            newSuspendedTransaction {
                                GitHubDataStore.savePullRequest(repositoryId, prRecord)
                            }
                        }
                        GitHubActionResponse(GitHubActionResultStatus.ActionSucceeded, result?.message ?: "Pull request successfully merged", "closed")
                    }
                    io.ktor.http.HttpStatusCode.MethodNotAllowed -> {
                        GitHubActionResponse(GitHubActionResultStatus.NotMergeable, result?.message ?: "Pull request is not mergeable (required checks failed or review required)")
                    }
                    io.ktor.http.HttpStatusCode.Conflict -> {
                        GitHubActionResponse(GitHubActionResultStatus.Conflict, result?.message ?: "Merge conflict or head SHA changed")
                    }
                    io.ktor.http.HttpStatusCode.Forbidden, io.ktor.http.HttpStatusCode.Unauthorized -> {
                        GitHubActionResponse(GitHubActionResultStatus.PermissionDenied, "You do not have permission to merge this pull request")
                    }
                    io.ktor.http.HttpStatusCode.NotFound -> GitHubActionResponse(GitHubActionResultStatus.ResourceNotFound, "Pull request not found")
                    io.ktor.http.HttpStatusCode.TooManyRequests -> GitHubActionResponse(GitHubActionResultStatus.RateLimited, "GitHub API rate limit exceeded")
                    else -> GitHubActionResponse(GitHubActionResultStatus.UnknownFailure, "GitHub API returned status ${statusCode.value}")
                }
            }
            else -> GitHubActionResponse(GitHubActionResultStatus.ValidationFailed, "Unsupported action: $action")
        }
    }
}
