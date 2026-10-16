package com.collabsphere.dto

import kotlinx.serialization.Serializable

@Serializable
data class McpTokenInfo(
    val id: Int,
    val name: String,
    val prefix: String,
    val scopes: List<String>,
    val createdAt: Long,
    val expiresAt: Long? = null,
    val lastUsedAt: Long? = null
)

@Serializable
data class CreateMcpTokenRequest(
    val name: String,
    val scopes: List<String> = listOf("read"),
    val expiresInDays: Int? = 90
)

@Serializable
data class CreatedMcpToken(val token: String, val info: McpTokenInfo)

@Serializable
data class McpActivityItem(
    val id: Long,
    val tokenName: String,
    val tool: String,
    val workspaceId: Int? = null,
    val outcome: String,
    val createdAt: Long
)
