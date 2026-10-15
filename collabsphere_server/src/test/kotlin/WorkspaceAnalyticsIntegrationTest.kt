import com.collabsphere.dto.WorkspaceAnalyticsResponse
import com.collabsphere.model.GitHubCommitsTable
import com.collabsphere.model.GitHubConnectionsTable
import com.collabsphere.model.GitHubIssuesTable
import com.collabsphere.model.GitHubPullRequestsTable
import com.collabsphere.model.GitHubRepositoriesTable
import com.collabsphere.model.LocalFilesTable
import com.collabsphere.model.NotesTable
import com.collabsphere.model.TasksTable
import com.collabsphere.model.UserGitHubIdentitiesTable
import com.collabsphere.model.UsersTable
import com.collabsphere.model.WorkspaceMembersTable
import com.collabsphere.model.WorkspacesTable
import com.collabsphere.module
import dto.LoginResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID
import kotlin.test.*

/**
 * Workspace analytics through auth and real Postgres: per-member counts, GitHub attribution through
 * verified accounts, the date range, and who may read it.
 */
class WorkspaceAnalyticsIntegrationTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val createdUserIds = mutableSetOf<Int>()
    private val createdWorkspaceIds = mutableSetOf<Int>()
    private val createdEmails = mutableSetOf<String>()
    private val githubIdBase = 900_000_000L + (System.nanoTime() % 1_000_000L) * 10

    private data class Account(val id: Int, val token: String, val email: String)

    private suspend fun HttpClient.signUp(userName: String): Account {
        val email = "analytics-${UUID.randomUUID()}@example.com"
        createdEmails += email
        post("/api/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123","userName":"$userName"}""")
        }
        transaction { UsersTable.update({ UsersTable.email eq email }) { it[isEmailVerified] = true } }
        val login = post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123"}""")
        }
        val response = json.decodeFromString<LoginResponse>(login.bodyAsText())
        createdUserIds += response.id
        return Account(response.id, response.token!!, email)
    }

    @AfterTest
    fun cleanUpFixtureRows() {
        transaction {
            if (createdWorkspaceIds.isNotEmpty()) WorkspacesTable.deleteWhere { WorkspacesTable.id inList createdWorkspaceIds }
            if (createdEmails.isNotEmpty()) UsersTable.deleteWhere { UsersTable.email inList createdEmails }
            if (createdUserIds.isNotEmpty()) UsersTable.deleteWhere { UsersTable.id inList createdUserIds }
        }
    }

    private suspend fun HttpClient.analytics(token: String, workspaceId: Int, range: String): HttpResponse =
        get("/api/workspace/$workspaceId/analytics?range=$range") { bearerAuth(token) }

    @Test
    fun `counts each member's work and attributes GitHub activity through verified accounts`() = testApplication {
        application { module() }
        val owner = client.signUp("ana-owner")
        val member = client.signUp("ana-member")
        val now = System.currentTimeMillis()
        val old = now - 100L * 24 * 60 * 60 * 1000
        val ownerGitHubId = githubIdBase + 1
        val memberGitHubId = githubIdBase + 2

        val workspaceId = transaction {
            val ws = WorkspacesTable.insert {
                it[userId] = owner.id
                it[workspaceName] = "analytics"
                it[workspaceOwner] = "analytics"
                it[workspacePassword] = ""
            }[WorkspacesTable.id]
            listOf(owner.id, member.id).forEach { m -> WorkspaceMembersTable.insert { it[workspaceId] = ws; it[userId] = m } }

            fun task(createdBy: Int, assignee: Int?, status: String, createdAt: Long?, completedAt: Long? = null, deleted: Boolean = false) =
                TasksTable.insert {
                    it[createdByUserId] = createdBy
                    it[assignedToUserId] = assignee
                    it[workspaceId] = ws
                    it[taskName] = "t"
                    it[taskDescription] = ""
                    it[TasksTable.status] = status
                    it[TasksTable.completedAt] = completedAt
                    it[isDeleted] = deleted
                    it[TasksTable.createdAt] = createdAt
                    it[updatedAt] = createdAt ?: old
                }
            task(owner.id, member.id, "DONE", now, completedAt = now)
            task(owner.id, member.id, "TODO", now)
            task(owner.id, null, "TODO", old)                       // only in "all"
            task(owner.id, null, "TODO", now, deleted = true)       // never counted

            fun note(author: Int, createdAt: Long?, updatedAt: Long, deleted: Boolean = false) = NotesTable.insert {
                it[userIdNotes] = author
                it[workspaceId] = ws
                it[notesName] = "n"
                it[notesDescription] = ""
                it[isDeleted] = deleted
                it[NotesTable.createdAt] = createdAt
                it[NotesTable.updatedAt] = updatedAt
            }
            note(member.id, now, now)
            note(member.id, null, old)                              // legacy row: falls back to updated_at
            note(member.id, now, now, deleted = true)

            LocalFilesTable.insert {
                it[userId] = owner.id
                it[workspaceId] = ws
                it[userName] = "ana-owner"
                it[url] = "https://example.com/f"
                it[mimeType] = "text/plain"
                it[fileName] = "f.txt"
                it[sizeBytes] = 1000
                it[fileLocation] = "x"
            }

            // Owner connected GitHub (App connection); the member linked theirs through OAuth.
            val connection = GitHubConnectionsTable.insert {
                it[userId] = owner.id
                it[githubUserId] = ownerGitHubId
                it[githubUsername] = "Owner-GH"
                it[installationId] = 1
            }[GitHubConnectionsTable.id]
            UserGitHubIdentitiesTable.insert {
                it[userId] = member.id
                it[githubUserId] = memberGitHubId
                it[githubLogin] = "member-gh"
                it[verifiedAt] = now
            }
            val repo = GitHubRepositoriesTable.insert {
                it[connectionId] = connection
                it[workspaceId] = ws
                it[githubRepoId] = githubIdBase
                it[GitHubRepositoriesTable.owner] = "o"
                it[name] = "r"
                it[fullName] = "o/r"
                it[isPrivate] = false
                it[htmlUrl] = "https://github.com/o/r"
                it[defaultBranch] = "main"
            }[GitHubRepositoriesTable.id]

            fun commit(sha: String, githubId: Long?, login: String?, email: String?, date: Long) = GitHubCommitsTable.insert {
                it[repositoryId] = repo
                it[GitHubCommitsTable.sha] = sha
                it[message] = "m"
                it[authorName] = login ?: "someone"
                it[authorEmail] = email
                it[authorLogin] = login
                it[authorGithubId] = githubId
                it[commitDate] = date
            }
            commit("a1", memberGitHubId, "member-gh", "laptop@example.com", now)      // by numeric id
            commit("a2", null, "MEMBER-GH", null, now)                                // by login, any case
            commit("a3", null, null, owner.email, now)                               // legacy row, by email
            commit("a4", null, "stranger", null, now)                                 // unlinked
            commit("a5", memberGitHubId, "member-gh", null, old)                      // only in "all"

            GitHubPullRequestsTable.insert {
                it[repositoryId] = repo
                it[githubPrId] = 1
                it[number] = 1
                it[title] = "p"
                it[state] = "closed"
                it[authorUsername] = "owner-gh"
                it[createdAt] = now
                it[mergedAt] = now
            }
            GitHubIssuesTable.insert {
                it[repositoryId] = repo
                it[githubIssueId] = 1
                it[number] = 2
                it[title] = "i"
                it[state] = "open"
                it[authorUsername] = "stranger"
                it[url] = "https://github.com/o/r/issues/2"
                it[createdAt] = now
            }
            ws
        }
        createdWorkspaceIds += workspaceId

        val month = client.analytics(member.token, workspaceId, "30d")
        assertEquals(HttpStatusCode.OK, month.status, month.bodyAsText())
        val body = json.decodeFromString<WorkspaceAnalyticsResponse>(month.bodyAsText())
        assertTrue(body.hasGitHub)
        val o = body.members.single { it.userId == owner.id }
        val m = body.members.single { it.userId == member.id }

        assertEquals(2, o.counts.tasksCreated)
        assertEquals(1, o.counts.filesUploaded)
        assertEquals(1000L, o.counts.fileBytes)
        assertEquals(1, o.counts.commits, "matched by git email")
        assertEquals(1, o.counts.pullRequestsOpened, "matched by the App connection's login")
        assertEquals(1, o.counts.pullRequestsMerged)
        assertEquals("Owner-GH", o.githubLogin)

        assertEquals(1, m.counts.tasksCompleted)
        assertEquals(1, m.counts.tasksOpen)
        assertEquals(1, m.counts.notesWritten, "deleted and legacy-old notes excluded")
        assertEquals(2, m.counts.commits, "matched by GitHub id and by login")
        assertEquals("member-gh", m.githubLogin)

        val stranger = body.unlinkedGitHub.single()
        assertEquals("stranger", stranger.name)
        assertEquals(1, stranger.commits)
        assertEquals(1, stranger.issuesOpened)

        assertEquals(o.counts.tasksCreated + m.counts.tasksCreated, body.totals.tasksCreated)
        assertEquals(3, body.totals.commits, "unlinked commits are not in member totals")
        assertEquals(body.members.map { it.total }.sortedDescending(), body.members.map { it.total })

        val all = json.decodeFromString<WorkspaceAnalyticsResponse>(client.analytics(owner.token, workspaceId, "all").bodyAsText())
        assertEquals(3, all.members.single { it.userId == owner.id }.counts.tasksCreated)
        assertEquals(2, all.members.single { it.userId == member.id }.counts.notesWritten)
        assertEquals(3, all.members.single { it.userId == member.id }.counts.commits)
        assertNull(all.since)
    }

    @Test
    fun `outsiders are refused and unknown ranges are rejected`() = testApplication {
        application { module() }
        val owner = client.signUp("ana-owner2")
        val outsider = client.signUp("ana-outsider")
        val workspaceId = transaction {
            val ws = WorkspacesTable.insert {
                it[userId] = owner.id
                it[workspaceName] = "analytics"
                it[workspaceOwner] = "analytics"
                it[workspacePassword] = ""
            }[WorkspacesTable.id]
            WorkspaceMembersTable.insert { it[WorkspaceMembersTable.workspaceId] = ws; it[userId] = owner.id }
            ws
        }
        createdWorkspaceIds += workspaceId

        assertEquals(HttpStatusCode.Forbidden, client.analytics(outsider.token, workspaceId, "30d").status)
        assertEquals(HttpStatusCode.BadRequest, client.analytics(owner.token, workspaceId, "1y").status)

        val empty = client.analytics(owner.token, workspaceId, "7d")
        assertEquals(HttpStatusCode.OK, empty.status)
        val body = json.decodeFromString<WorkspaceAnalyticsResponse>(empty.bodyAsText())
        assertFalse(body.hasGitHub)
        assertEquals(0, body.members.single().total, "members with no activity are still listed")
    }
}
