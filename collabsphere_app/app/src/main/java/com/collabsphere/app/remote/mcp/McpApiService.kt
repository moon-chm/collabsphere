package com.collabsphere.app.remote.mcp

import com.collabsphere.app.AppConfig
import com.collabsphere.app.dto.mcp.CreateMcpTokenRequest
import com.collabsphere.app.dto.mcp.CreatedMcpToken
import com.collabsphere.app.dto.mcp.McpActivityItem
import com.collabsphere.app.dto.mcp.McpTokenInfo
import com.collabsphere.app.remote.requireSuccess
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class McpApiService(private val client: HttpClient) {

    private val baseUrl: String get() = "${AppConfig.BASE_URL}/api/mcp"

    suspend fun tokens(): List<McpTokenInfo> =
        client.get("$baseUrl/tokens").requireSuccess().body()

    suspend fun createToken(request: CreateMcpTokenRequest): CreatedMcpToken =
        client.post("$baseUrl/tokens") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireSuccess().body()

    suspend fun revokeToken(id: Int) {
        client.delete("$baseUrl/tokens/$id").requireSuccess()
    }

    suspend fun activity(limit: Int = 20): List<McpActivityItem> =
        client.get("$baseUrl/activity") { parameter("limit", limit) }.requireSuccess().body()

    companion object {
        val serverUrl: String get() = "${AppConfig.BASE_URL}/mcp"

        fun claudeCodeCommand(token: String): String =
            "claude mcp add --transport http collabsphere $serverUrl --header \"Authorization: Bearer $token\""
    }
}
