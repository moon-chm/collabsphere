package com.collabsphere.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHubStateTest {

    @Test
    fun `github state round trips user and workspace`() {
        val state = JwtConfig.generateGitHubState(userId = 42, workspaceId = 7)
        assertEquals(42 to 7, JwtConfig.verifyGitHubState(state))
    }

    @Test
    fun `login token is not accepted as github state`() {
        assertNull(JwtConfig.verifyGitHubState(JwtConfig.generateToken(42)))
    }

    @Test
    fun `tampered github state is rejected`() {
        val state = JwtConfig.generateGitHubState(userId = 42, workspaceId = 7)
        assertNull(JwtConfig.verifyGitHubState(state.dropLast(2) + "xx"))
    }

    @Test
    fun `parseGitHubTime handles utc and offset timestamps`() {
        assertEquals(1_700_000_000_000L, parseGitHubTime("2023-11-14T22:13:20Z"))
        assertEquals(1_700_000_000_000L, parseGitHubTime("2023-11-14T17:13:20-05:00"))
        assertNull(parseGitHubTime("not a date"))
    }
}
