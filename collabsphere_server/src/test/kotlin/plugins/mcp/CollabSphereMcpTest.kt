package plugins.mcp

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CollabSphereMcpTest {

    private val enabled = mapOf("MCP_ENABLED" to "YES")

    private fun ApplicationTestBuilder.appLikeProduction(environment: Map<String, String> = enabled) {
        application {
            install(WebSockets)
            install(ContentNegotiation) { json() }
            install(StatusPages)
            configureMcp(environment)
        }
    }

    private suspend fun ApplicationTestBuilder.rpc(body: String, host: String = "localhost"): HttpResponse =
        client.post(CollabSphereMcp.PATH) {
            header(HttpHeaders.Host, host)
            header(HttpHeaders.Accept, "application/json, text/event-stream")
            header("MCP-Protocol-Version", "2025-11-25")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private fun payload(response: String): JsonObject {
        val text = response.trim()
        val json = if (text.startsWith("{")) text else text.lines().first { it.startsWith("data:") }.removePrefix("data:").trim()
        return Json.parseToJsonElement(json).jsonObject
    }

    private val initialize = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"m0-test","version":"1"}}}"""

    @Test
    fun `initialize reports the CollabSphere server and a supported protocol version`() = testApplication {
        appLikeProduction()
        val response = rpc(initialize)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val result = payload(response.bodyAsText())["result"]!!.jsonObject
        assertEquals(CollabSphereMcp.SERVER_NAME, result["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("2025-11-25", result["protocolVersion"]!!.jsonPrimitive.content)
    }

    @Test
    fun `tools list and tool call work without a session`() = testApplication {
        appLikeProduction()
        val list = rpc("""{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}""")
        assertEquals(HttpStatusCode.OK, list.status, list.bodyAsText())
        val tools = payload(list.bodyAsText())["result"]!!.jsonObject["tools"]!!.jsonArray
        assertEquals(listOf(CollabSphereMcp.STATUS_TOOL), tools.map { it.jsonObject["name"]!!.jsonPrimitive.content })

        val call = rpc("""{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"${CollabSphereMcp.STATUS_TOOL}","arguments":{}}}""")
        assertEquals(HttpStatusCode.OK, call.status, call.bodyAsText())
        val content = payload(call.bodyAsText())["result"]!!.jsonObject["content"]!!.jsonArray
        assertTrue(content.first().jsonObject["text"]!!.jsonPrimitive.content.contains("reachable"))
    }

    @Test
    fun `endpoint does not exist unless explicitly enabled`() = testApplication {
        appLikeProduction(environment = emptyMap())
        assertEquals(HttpStatusCode.NotFound, rpc(initialize).status)
    }

    @Test
    fun `requests for an unknown host are refused`() = testApplication {
        appLikeProduction()
        val response = rpc(initialize, host = "attacker.example")
        assertNotEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `allowed hosts include production and configurable extras`() {
        val hosts = CollabSphereMcp.allowedHosts(mapOf("MCP_ALLOWED_HOSTS" to " MCP.Example.com , ,staging.example.com"))
        assertTrue("collabsphere-server-qtke.onrender.com" in hosts)
        assertTrue("mcp.example.com" in hosts)
        assertTrue("staging.example.com" in hosts)
        assertTrue("localhost" in hosts)
    }

    @Test
    fun `the official MCP client connects, lists and calls tools`() = testApplication {
        appLikeProduction()
        val http = createClient {
            install(io.ktor.client.plugins.sse.SSE)
            install(io.ktor.client.plugins.DefaultRequest) { header(HttpHeaders.Host, "localhost") }
        }
        val mcp = http.mcpStreamableHttp("http://localhost${CollabSphereMcp.PATH}")
        try {
            assertEquals(CollabSphereMcp.SERVER_NAME, mcp.serverVersion?.name)
            val tools = mcp.listTools().tools.map { it.name }
            assertEquals(listOf(CollabSphereMcp.STATUS_TOOL), tools)
            val result = mcp.callTool(CollabSphereMcp.STATUS_TOOL, emptyMap())
            val text = (result.content.first() as io.modelcontextprotocol.kotlin.sdk.types.TextContent).text
            assertTrue(text.contains("reachable"))
        } finally {
            mcp.close()
        }
    }

    @Test
    fun `a host header with a port is accepted for allowed hosts`() = testApplication {
        appLikeProduction()
        val response = rpc(initialize, host = "localhost:8080")
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
    }
}

