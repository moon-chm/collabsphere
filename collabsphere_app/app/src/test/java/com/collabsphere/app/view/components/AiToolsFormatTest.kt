package com.collabsphere.app.view.components

import com.collabsphere.app.remote.mcp.McpApiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiToolsFormatTest {

    private val now = 1_000_000_000_000L
    private val minute = 60_000L
    private val day = 24 * 60 * minute

    @Test
    fun lastUsedReadsNaturally() {
        assertEquals("Never used", AiToolsFormat.ago(null, now))
        assertEquals("Used just now", AiToolsFormat.ago(now - 10_000, now))
        assertEquals("Used 5m ago", AiToolsFormat.ago(now - 5 * minute, now))
        assertEquals("Used 3h ago", AiToolsFormat.ago(now - 180 * minute, now))
        assertEquals("Used 2d ago", AiToolsFormat.ago(now - 2 * day, now))
    }

    @Test
    fun expiryReadsNaturally() {
        assertEquals("Never expires", AiToolsFormat.expiry(null, now))
        assertEquals("Expires today", AiToolsFormat.expiry(now + 60 * minute, now))
        assertEquals("Expires tomorrow", AiToolsFormat.expiry(now + day + 60 * minute, now))
        assertEquals("Expires in 30 days", AiToolsFormat.expiry(now + 30 * day + minute, now))
        assertEquals("Expires today", AiToolsFormat.expiry(now - day, now))
    }

    @Test
    fun toolNamesBecomePlainEnglish() {
        assertEquals("Listed tasks", AiToolsFormat.toolLabel("list_tasks"))
        assertEquals("Read a task", AiToolsFormat.toolLabel("get_task_context"))
        assertEquals("Some new tool", AiToolsFormat.toolLabel("some_new_tool"))
    }

    @Test
    fun setupCommandTargetsTheMcpEndpointWithTheToken() {
        val command = McpApiService.claudeCodeCommand("csmcp_abc")
        assertTrue(command.startsWith("claude mcp add --transport http collabsphere "))
        assertTrue(command.contains("/mcp "))
        assertTrue(command.endsWith("--header \"Authorization: Bearer csmcp_abc\""))
    }

    @Test
    fun expiryChoicesMatchWhatTheServerAccepts() {
        assertEquals(listOf(30, 90, 365, null), AiToolsFormat.expiryChoices.map { it.second })
    }
}
