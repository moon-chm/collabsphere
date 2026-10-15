package plugins

import com.collabsphere.model.GitHubConnectionsTable
import com.collabsphere.model.UserGitHubIdentitiesTable
import com.collabsphere.model.UsersTable
import com.collabsphere.model.WorkspaceMembersTable
import com.collabsphere.util.AvatarGenerator
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.selectAll

/** A workspace member as shown wherever their GitHub activity appears. */
internal data class WorkspaceMemberRef(
    val userId: Int,
    val username: String,
    val avatarUrl: String
)

/**
 * Resolves GitHub authors (numeric id, login, or git email) to members of one workspace.
 *
 * Accounts come from verified sources only: a member's OAuth-linked identity, or the GitHub App
 * connection of a member who connected the workspace. A typed-in username is never trusted, so
 * nobody can claim someone else's commits. The git email is a last resort for commits that GitHub
 * itself could not tie to an account (or that were stored before the author columns existed).
 */
internal class WorkspaceGitHubIdentities(
    private val members: List<WorkspaceMemberRef>,
    private val byGitHubId: Map<Long, WorkspaceMemberRef>,
    private val byLogin: Map<String, WorkspaceMemberRef>,
    private val byEmail: Map<String, WorkspaceMemberRef>,
    private val loginByUserId: Map<Int, String>
) {
    fun resolve(githubId: Long? = null, login: String? = null, email: String? = null): WorkspaceMemberRef? {
        githubId?.let { id -> byGitHubId[id]?.let { return it } }
        login?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { l -> byLogin[l]?.let { return it } }
        val normalized = email?.trim()?.lowercase() ?: return null
        byEmail[normalized]?.let { return it }
        // GitHub's private-email form encodes the login: "12345+login@users.noreply.github.com".
        val noreplyLogin = GITHUB_NOREPLY_EMAIL.matchEntire(normalized)?.groupValues?.get(1) ?: return null
        return byLogin[noreplyLogin]
    }

    /** Current members of the workspace. */
    fun members(): List<WorkspaceMemberRef> = members

    /** The member's linked GitHub login, if they have one. */
    fun loginOf(userId: Int): String? = loginByUserId[userId]

    companion object {
        private val GITHUB_NOREPLY_EMAIL = Regex("(?:\\d+\\+)?([a-z0-9-]+)@users\\.noreply\\.github\\.com")

        /** Must run inside a transaction. */
        fun forWorkspace(workspaceId: Int): WorkspaceGitHubIdentities {
            val members = (WorkspaceMembersTable innerJoin UsersTable)
                .selectAll()
                .where { WorkspaceMembersTable.workspaceId eq workspaceId }
                .map {
                    WorkspaceMemberRef(
                        userId = it[UsersTable.id],
                        username = it[UsersTable.username],
                        avatarUrl = AvatarGenerator.avatarUrlFor(it[UsersTable.id], it[UsersTable.avatarUrl])
                    ) to it[UsersTable.email].trim().lowercase()
                }
            val byUserId = members.associate { (member, _) -> member.userId to member }
            val memberIds = byUserId.keys.toList()

            // Both sources map to the member; for the displayed login the OAuth-linked identity is listed
            // last so it wins over an App connection.
            val accounts = ArrayList<Triple<Int, Long, String>>()
            if (memberIds.isNotEmpty()) {
                GitHubConnectionsTable.selectAll()
                    .where { GitHubConnectionsTable.userId inList memberIds }
                    .forEach {
                        accounts += Triple(
                            it[GitHubConnectionsTable.userId],
                            it[GitHubConnectionsTable.githubUserId],
                            it[GitHubConnectionsTable.githubUsername]
                        )
                    }
                UserGitHubIdentitiesTable.selectAll()
                    .where { UserGitHubIdentitiesTable.userId inList memberIds }
                    .forEach {
                        accounts += Triple(
                            it[UserGitHubIdentitiesTable.userId],
                            it[UserGitHubIdentitiesTable.githubUserId],
                            it[UserGitHubIdentitiesTable.githubLogin]
                        )
                    }
            }

            val byGitHubId = HashMap<Long, WorkspaceMemberRef>()
            val byLogin = HashMap<String, WorkspaceMemberRef>()
            val loginByUserId = HashMap<Int, String>()
            accounts.forEach { (userId, githubId, login) ->
                val member = byUserId[userId] ?: return@forEach
                byGitHubId[githubId] = member
                byLogin[login.lowercase()] = member
                loginByUserId[userId] = login
            }
            return WorkspaceGitHubIdentities(
                members = members.map { it.first },
                byGitHubId = byGitHubId,
                byLogin = byLogin,
                byEmail = members.associate { (member, email) -> email to member },
                loginByUserId = loginByUserId
            )
        }
    }
}
