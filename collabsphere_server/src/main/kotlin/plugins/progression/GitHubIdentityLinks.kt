package plugins.progression

import com.collabsphere.model.GitHubConnectionsTable
import com.collabsphere.model.UserGitHubIdentitiesTable
import dto.GitHubIdentityResponse
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

enum class IdentityLinkResult { LINKED, ACCOUNT_IN_USE }

/**
 * Members' verified GitHub identities. Linking happens only in the OAuth callback, after GitHub has
 * authenticated the account, so a user can never claim someone else's GitHub activity. Must run
 * inside a transaction.
 */
object GitHubIdentityLinks {

    fun status(userId: Int): GitHubIdentityResponse {
        UserGitHubIdentitiesTable.selectAll().where { UserGitHubIdentitiesTable.userId eq userId }.singleOrNull()?.let {
            return GitHubIdentityResponse(linked = true, login = it[UserGitHubIdentitiesTable.githubLogin])
        }
        // A workspace owner's GitHub App connection is already OAuth-verified.
        GitHubConnectionsTable.selectAll().where { GitHubConnectionsTable.userId eq userId }.singleOrNull()?.let {
            return GitHubIdentityResponse(linked = true, login = it[GitHubConnectionsTable.githubUsername])
        }
        return GitHubIdentityResponse(linked = false)
    }

    fun link(userId: Int, githubUserId: Long, githubLogin: String, now: Long = System.currentTimeMillis()): IdentityLinkResult {
        val claimedByOther =
            UserGitHubIdentitiesTable.selectAll().where {
                (UserGitHubIdentitiesTable.githubUserId eq githubUserId) and (UserGitHubIdentitiesTable.userId neq userId)
            }.count() > 0 ||
                GitHubConnectionsTable.selectAll().where {
                    (GitHubConnectionsTable.githubUserId eq githubUserId) and (GitHubConnectionsTable.userId neq userId)
                }.count() > 0
        if (claimedByOther) return IdentityLinkResult.ACCOUNT_IN_USE

        UserGitHubIdentitiesTable.deleteWhere { UserGitHubIdentitiesTable.userId eq userId }
        UserGitHubIdentitiesTable.insert {
            it[UserGitHubIdentitiesTable.userId] = userId
            it[UserGitHubIdentitiesTable.githubUserId] = githubUserId
            it[UserGitHubIdentitiesTable.githubLogin] = githubLogin.take(255)
            it[verifiedAt] = now
        }
        return IdentityLinkResult.LINKED
    }

    /** Stops future attribution; SP and badges already earned stay. */
    fun unlink(userId: Int) {
        UserGitHubIdentitiesTable.deleteWhere { UserGitHubIdentitiesTable.userId eq userId }
    }
}
