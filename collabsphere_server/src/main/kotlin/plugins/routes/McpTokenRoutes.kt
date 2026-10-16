package plugins

import com.collabsphere.dto.CreateMcpTokenRequest
import com.collabsphere.dto.CreatedMcpToken
import com.collabsphere.dto.McpActivityItem
import com.collabsphere.dto.McpTokenInfo
import com.collabsphere.model.McpAuditTable
import com.collabsphere.model.McpTokensTable
import com.collabsphere.model.UsersTable
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import plugins.mcp.McpScopes
import plugins.mcp.McpTokenFormat

private val logger = LoggerFactory.getLogger("McpTokens")

internal object McpTokenRules {
    const val MAX_ACTIVE_TOKENS = 10
    const val MAX_NAME_LENGTH = 60
    val allowedExpiryDays = setOf(7, 30, 90, 365)

    fun nameRejection(name: String): String? = when {
        name.isBlank() -> "Give the token a name, like \"Claude Code on my laptop\""
        name.length > MAX_NAME_LENGTH -> "Keep the name under $MAX_NAME_LENGTH characters"
        else -> null
    }

    fun expiryRejection(days: Int?): String? =
        if (days == null || days in allowedExpiryDays) null else "Expiry must be 7, 30, 90 or 365 days, or never"
}

private fun ResultRow.toTokenInfo() = McpTokenInfo(
    id = this[McpTokensTable.id],
    name = this[McpTokensTable.name],
    prefix = this[McpTokensTable.prefix],
    scopes = McpScopes.parse(this[McpTokensTable.scopes]).sortedBy { McpScopes.all.indexOf(it) },
    createdAt = this[McpTokensTable.createdAt],
    expiresAt = this[McpTokensTable.expiresAt],
    lastUsedAt = this[McpTokensTable.lastUsedAt]
)

internal fun Route.mcpTokenRoutes() {
    route("/api/mcp") {
        get("/tokens") {
            val userId = call.authenticatedUserId()
            try {
                val now = System.currentTimeMillis()
                val tokens = dbReadQuery {
                    McpTokensTable.selectAll()
                        .where { (McpTokensTable.userId eq userId) and McpTokensTable.revokedAt.isNull() }
                        .orderBy(McpTokensTable.createdAt, SortOrder.DESC)
                        .map { it.toTokenInfo() }
                        .filter { it.expiresAt == null || it.expiresAt > now }
                }
                call.respond(HttpStatusCode.OK, tokens)
            } catch (e: Exception) {
                val failure = classifyFailure(e, logger, "MCP token list")
                call.respond(failure.status, failure.message)
            }
        }

        post("/tokens") {
            val userId = call.authenticatedUserId()
            try {
                val request = call.receive<CreateMcpTokenRequest>()
                val name = request.name.trim()
                McpTokenRules.nameRejection(name)?.let { return@post call.respond(HttpStatusCode.BadRequest, it) }
                McpTokenRules.expiryRejection(request.expiresInDays)?.let { return@post call.respond(HttpStatusCode.BadRequest, it) }
                val scopes = McpScopes.normalize(request.scopes)
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Access must be read, or read and write")
                val now = System.currentTimeMillis()
                val token = McpTokenFormat.generate()
                val created = dbQuery {
                    val active = McpTokensTable.selectAll()
                        .where { (McpTokensTable.userId eq userId) and McpTokensTable.revokedAt.isNull() }
                        .count { it[McpTokensTable.expiresAt]?.let { exp -> exp > now } ?: true }
                    if (active >= McpTokenRules.MAX_ACTIVE_TOKENS) return@dbQuery null
                    val tokenVersion = UsersTable.select(UsersTable.tokenVersion).where { UsersTable.id eq userId }
                        .single()[UsersTable.tokenVersion]
                    val id = McpTokensTable.insert {
                        it[McpTokensTable.userId] = userId
                        it[McpTokensTable.name] = name
                        it[tokenHash] = McpTokenFormat.hash(token)
                        it[prefix] = McpTokenFormat.displayPrefix(token)
                        it[McpTokensTable.scopes] = scopes.joinToString(",")
                        it[userTokenVersion] = tokenVersion
                        it[createdAt] = now
                        it[expiresAt] = request.expiresInDays?.let { days -> now + days * 24L * 60 * 60 * 1000 }
                    }[McpTokensTable.id]
                    McpTokensTable.selectAll().where { McpTokensTable.id eq id }.single().toTokenInfo()
                } ?: return@post call.respond(
                    HttpStatusCode.Conflict,
                    "You already have ${McpTokenRules.MAX_ACTIVE_TOKENS} active tokens. Revoke one first."
                )
                call.respond(HttpStatusCode.Created, CreatedMcpToken(token = token, info = created))
            } catch (e: Exception) {
                val failure = classifyFailure(e, logger, "MCP token create")
                call.respond(failure.status, failure.message)
            }
        }

        delete("/tokens/{id}") {
            val userId = call.authenticatedUserId()
            val id = call.parameters["id"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest, false)
            try {
                val updated = dbQuery {
                    McpTokensTable.update({
                        (McpTokensTable.id eq id) and (McpTokensTable.userId eq userId) and McpTokensTable.revokedAt.isNull()
                    }) { it[revokedAt] = System.currentTimeMillis() }
                }
                if (updated > 0) call.respond(HttpStatusCode.OK, true) else call.respond(HttpStatusCode.NotFound, false)
            } catch (e: Exception) {
                val failure = classifyFailure(e, logger, "MCP token revoke")
                call.respond(failure.status, failure.message)
            }
        }

        get("/activity") {
            val userId = call.authenticatedUserId()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
            try {
                val items = dbReadQuery {
                    (McpAuditTable innerJoin McpTokensTable)
                        .selectAll()
                        .where { McpAuditTable.userId eq userId }
                        .orderBy(McpAuditTable.createdAt, SortOrder.DESC)
                        .limit(limit)
                        .map {
                            McpActivityItem(
                                id = it[McpAuditTable.id],
                                tokenName = it[McpTokensTable.name],
                                tool = it[McpAuditTable.tool],
                                workspaceId = it[McpAuditTable.workspaceId],
                                outcome = it[McpAuditTable.outcome],
                                createdAt = it[McpAuditTable.createdAt]
                            )
                        }
                }
                call.respond(HttpStatusCode.OK, items)
            } catch (e: Exception) {
                val failure = classifyFailure(e, logger, "MCP activity")
                call.respond(failure.status, failure.message)
            }
        }
    }
}
