package plugins.mcp

import com.collabsphere.model.McpTokensTable
import com.collabsphere.model.UsersTable
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.util.AttributeKey
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import plugins.dbQuery
import plugins.dbReadQuery
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

internal data class McpPrincipal(
    val userId: Int,
    val userName: String,
    val tokenId: Int,
    val tokenName: String,
    val scopes: Set<String>
) {
    val canWrite: Boolean get() = McpScopes.WRITE in scopes
}

internal object McpScopes {
    const val READ = "read"
    const val WRITE = "write"
    val all = listOf(READ, WRITE)

    fun normalize(requested: List<String>): List<String>? {
        val cleaned = requested.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        if (cleaned.isEmpty() || cleaned.any { it !in all }) return null
        return all.filter { it in cleaned || it == READ }
    }

    fun parse(stored: String): Set<String> = stored.split(',').map { it.trim() }.filter { it in all }.toSet()
}

internal object McpTokenFormat {
    const val PREFIX = "csmcp_"
    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }

    fun displayPrefix(token: String): String = token.take(PREFIX.length + 6)

    fun looksValid(token: String): Boolean = token.startsWith(PREFIX) && token.length in 40..80
}

internal class McpRateLimiter(
    private val maxRequests: Int = 120,
    private val windowMs: Long = 60_000L,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private class Window(var startedAt: Long, var count: Int)

    private val windows = ConcurrentHashMap<Int, Window>()

    fun tryAcquire(tokenId: Int): Boolean {
        val now = clock()
        val window = windows.computeIfAbsent(tokenId) { Window(now, 0) }
        synchronized(window) {
            if (now - window.startedAt >= windowMs) {
                window.startedAt = now
                window.count = 0
            }
            if (window.count >= maxRequests) return false
            window.count++
            return true
        }
    }
}

internal object McpTokenStore {
    private const val LAST_USED_WRITE_INTERVAL_MS = 5 * 60_000L

    suspend fun authenticate(rawToken: String, now: Long = System.currentTimeMillis()): McpPrincipal? {
        if (!McpTokenFormat.looksValid(rawToken)) return null
        val hash = McpTokenFormat.hash(rawToken)
        val match = dbReadQuery {
            (McpTokensTable innerJoin UsersTable)
                .selectAll()
                .where { McpTokensTable.tokenHash eq hash }
                .singleOrNull()
        } ?: return null
        if (match[McpTokensTable.revokedAt] != null) return null
        val expiresAt = match[McpTokensTable.expiresAt]
        if (expiresAt != null && expiresAt <= now) return null
        if (match[McpTokensTable.userTokenVersion] != match[UsersTable.tokenVersion]) return null

        val tokenId = match[McpTokensTable.id]
        val lastUsed = match[McpTokensTable.lastUsedAt]
        if (lastUsed == null || now - lastUsed >= LAST_USED_WRITE_INTERVAL_MS) {
            runCatching {
                dbQuery { McpTokensTable.update({ McpTokensTable.id eq tokenId }) { it[lastUsedAt] = now } }
            }
        }
        return McpPrincipal(
            userId = match[McpTokensTable.userId],
            userName = match[UsersTable.username],
            tokenId = tokenId,
            tokenName = match[McpTokensTable.name],
            scopes = McpScopes.parse(match[McpTokensTable.scopes])
        )
    }
}

internal val McpPrincipalKey = AttributeKey<McpPrincipal>("McpPrincipal")

internal fun bearerToken(authorization: String?): String? {
    val value = authorization?.trim() ?: return null
    if (!value.startsWith("Bearer ", ignoreCase = true)) return null
    return value.substring(7).trim().takeIf { it.isNotEmpty() }
}

internal typealias McpAuthenticator = suspend (String) -> McpPrincipal?

internal fun mcpAuthPlugin(
    rateLimiter: McpRateLimiter,
    authenticator: McpAuthenticator = { McpTokenStore.authenticate(it) }
) = createApplicationPlugin("McpAuth") {
    onCall { call ->
        if (call.request.path() != CollabSphereMcp.PATH) return@onCall
        val token = bearerToken(call.request.headers[HttpHeaders.Authorization])
        val principal = token?.let { authenticator(it) }
        if (principal == null) {
            val challenge = if (token == null) {
                "Bearer realm=\"collabsphere\""
            } else {
                "Bearer realm=\"collabsphere\", error=\"invalid_token\", error_description=\"Token is invalid, expired or revoked\""
            }
            call.response.header(HttpHeaders.WWWAuthenticate, challenge)
            call.respondText(
                "Create an access token in the CollabSphere app under Profile, then AI tools.",
                status = HttpStatusCode.Unauthorized
            )
            return@onCall
        }
        if (!rateLimiter.tryAcquire(principal.tokenId)) {
            call.response.header(HttpHeaders.RetryAfter, "60")
            call.respondText("Too many requests for this token. Try again in a minute.", status = HttpStatusCode.TooManyRequests)
            return@onCall
        }
        call.attributes.put(McpPrincipalKey, principal)
    }
}
