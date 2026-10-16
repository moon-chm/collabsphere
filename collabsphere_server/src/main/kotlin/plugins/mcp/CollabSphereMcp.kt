package plugins.mcp

import io.ktor.http.ContentType
import io.ktor.http.content.TextContent as HttpTextContent
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.request.path
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStatelessStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

internal object CollabSphereMcp {
    const val PATH = "/mcp"
    const val SERVER_NAME = "collabsphere"
    const val SERVER_VERSION = "0.1.0"
    const val STATUS_TOOL = "collabsphere_status"

    private val defaultHosts = listOf("localhost", "127.0.0.1", "collabsphere-server-qtke.onrender.com")

    fun isEnabled(environment: Map<String, String> = System.getenv()): Boolean =
        environment["MCP_ENABLED"].equals("YES", ignoreCase = true)

    fun allowedHosts(environment: Map<String, String> = System.getenv()): List<String> {
        val extra = environment["MCP_ALLOWED_HOSTS"].orEmpty()
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        return (defaultHosts + extra).distinct()
    }

    fun buildServer(principal: McpPrincipal): Server = Server(
        serverInfo = Implementation(name = SERVER_NAME, version = SERVER_VERSION),
        options = ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))
        )
    ).apply {
        addTool(
            name = STATUS_TOOL,
            description = "Shows which CollabSphere account and access token this connection uses. Returns no workspace data.",
            inputSchema = ToolSchema(properties = buildJsonObject { }),
            toolAnnotations = io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false)
        ) {
            val access = if (principal.canWrite) "read and write" else "read only"
            CallToolResult(
                content = listOf(TextContent("Connected to CollabSphere as ${principal.userName} using the token \"${principal.tokenName}\" ($access)."))
            )
        }
        McpTools.register(this, principal)
    }
}

internal object McpJsonCleanup {
    fun stripNulls(text: String): String {
        val parsed = runCatching { Json.parseToJsonElement(text) }.getOrNull() ?: return text
        return clean(parsed, topLevel = true).toString()
    }

    private fun clean(element: JsonElement, topLevel: Boolean = false): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.entries
                .filter { (key, value) -> value !is JsonNull || (topLevel && key == "id") }
                .associate { (key, value) -> key to clean(value) }
        )
        is JsonArray -> JsonArray(element.map { clean(it) })
        else -> element
    }
}

private val McpNullStripping = createApplicationPlugin("McpNullStripping") {
    onCallRespond { call ->
        if (call.request.path() != CollabSphereMcp.PATH) return@onCallRespond
        transformBody { body ->
            when {
                body is HttpTextContent && body.contentType.match(ContentType.Application.Json) ->
                    HttpTextContent(McpJsonCleanup.stripNulls(body.text), body.contentType, body.status)
                body is String -> McpJsonCleanup.stripNulls(body)
                else -> body
            }
        }
    }
}

internal fun Application.configureMcp(
    environment: Map<String, String> = System.getenv(),
    rateLimiter: McpRateLimiter = McpRateLimiter(),
    authenticator: McpAuthenticator = { McpTokenStore.authenticate(it) }
) {
    if (!CollabSphereMcp.isEnabled(environment)) return
    install(McpNullStripping)
    install(mcpAuthPlugin(rateLimiter, authenticator))
    mcpStatelessStreamableHttp(
        path = CollabSphereMcp.PATH,
        enableDnsRebindingProtection = true,
        allowedHosts = CollabSphereMcp.allowedHosts(environment)
    ) {
        CollabSphereMcp.buildServer(call.attributes[McpPrincipalKey])
    }
}
