package plugins

import com.collabsphere.model.GitHubCommitsTable
import com.collabsphere.model.GitHubPrApprovalsTable
import com.collabsphere.model.GitHubPullRequestsTable
import com.collabsphere.model.GitHubRepositoriesTable
import com.collabsphere.model.TasksTable
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Standup")

private const val MAX_LOOKBACK_MS = 7L * 24 * 60 * 60 * 1000
private const val TASK_LIMIT = 300
private const val COMMIT_LIMIT = 1000
private const val PR_LIMIT = 300

internal fun Route.standupRoutes() {
    get("/api/standup/draft") {
        val userId = call.authenticatedUserId()
        val workspaceId = call.request.queryParameters["workspaceId"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "workspaceId is required")
        val now = System.currentTimeMillis()
        val since = (call.request.queryParameters["since"]?.toLongOrNull() ?: (now - StandupComposer.DAY_MS))
            .coerceIn(now - MAX_LOOKBACK_MS, now)
        val today = call.request.queryParameters["today"]?.toLongOrNull()
            ?.takeIf { kotlin.math.abs(it - now) <= 2 * StandupComposer.DAY_MS }
            ?: (now - now % StandupComposer.DAY_MS)
        try {
            if (!isMember(userId, workspaceId)) return@get call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
            val draft = dbReadQuery {
                val tasks = TasksTable.selectAll()
                    .where {
                        (TasksTable.workspaceId eq workspaceId) and
                            (TasksTable.assignedToUserId eq userId) and
                            (TasksTable.isDeleted eq false) and
                            ((TasksTable.status neq "DONE") or (TasksTable.completedAt greaterEq since))
                    }
                    .limit(TASK_LIMIT)
                    .map {
                        StandupTask(
                            name = it[TasksTable.taskName],
                            status = it[TasksTable.status],
                            dueDate = it[TasksTable.dueDate],
                            completedAt = it[TasksTable.completedAt]
                        )
                    }

                val repos = GitHubRepositoriesTable.selectAll()
                    .where { GitHubRepositoriesTable.workspaceId eq workspaceId }
                    .associate { it[GitHubRepositoriesTable.id] to (it[GitHubRepositoriesTable.fullName] to it[GitHubRepositoriesTable.githubRepoId]) }
                val identities = WorkspaceGitHubIdentities.forWorkspace(workspaceId)
                val githubLinked = identities.loginOf(userId) != null

                val commits = if (repos.isEmpty()) emptyList() else GitHubCommitsTable.selectAll()
                    .where { (GitHubCommitsTable.repositoryId inList repos.keys) and (GitHubCommitsTable.commitDate greaterEq since) }
                    .limit(COMMIT_LIMIT)
                    .filter {
                        it[GitHubCommitsTable.commitDate] < now &&
                            identities.resolve(
                                githubId = it[GitHubCommitsTable.authorGithubId],
                                login = it[GitHubCommitsTable.authorLogin],
                                email = it[GitHubCommitsTable.authorEmail]
                            )?.userId == userId
                    }
                    .map { StandupCommit(repo = repos.getValue(it[GitHubCommitsTable.repositoryId]).first, message = it[GitHubCommitsTable.message]) }

                val prRows = if (repos.isEmpty()) emptyList() else GitHubPullRequestsTable.selectAll()
                    .where {
                        (GitHubPullRequestsTable.repositoryId inList repos.keys) and (
                            (GitHubPullRequestsTable.state eq "open") or
                                (GitHubPullRequestsTable.createdAt greaterEq since) or
                                (GitHubPullRequestsTable.mergedAt greaterEq since)
                            )
                    }
                    .limit(PR_LIMIT)
                    .filter {
                        identities.resolve(
                            githubId = it[GitHubPullRequestsTable.authorGithubId],
                            login = it[GitHubPullRequestsTable.authorUsername]
                        )?.userId == userId
                    }

                val approved = if (prRows.isEmpty()) emptySet() else {
                    val repoGithubIds = prRows.map { repos.getValue(it[GitHubPullRequestsTable.repositoryId]).second }.distinct()
                    GitHubPrApprovalsTable.selectAll()
                        .where { GitHubPrApprovalsTable.githubRepoId inList repoGithubIds }
                        .map { it[GitHubPrApprovalsTable.githubRepoId] to it[GitHubPrApprovalsTable.prNumber] }
                        .toSet()
                }

                val prs = prRows.map {
                    val (fullName, githubRepoId) = repos.getValue(it[GitHubPullRequestsTable.repositoryId])
                    StandupPr(
                        number = it[GitHubPullRequestsTable.number],
                        title = it[GitHubPullRequestsTable.title],
                        repo = fullName,
                        state = it[GitHubPullRequestsTable.state],
                        createdAt = it[GitHubPullRequestsTable.createdAt],
                        mergedAt = it[GitHubPullRequestsTable.mergedAt],
                        approved = (githubRepoId to it[GitHubPullRequestsTable.number]) in approved
                    )
                }

                StandupComposer.compose(tasks, prs, commits, since, now, today, githubLinked)
            }
            call.respond(HttpStatusCode.OK, draft)
        } catch (e: Exception) {
            val failure = classifyFailure(e, logger, "Standup draft")
            call.respond(failure.status, failure.message)
        }
    }
}
