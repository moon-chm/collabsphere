package com.collabsphere.app.model.progression

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubIdentityEventsTest {
    @Test
    fun `link outcomes map to clear messages`() {
        assertEquals(
            "GitHub linked as @octo. Your merged work now counts toward your career.",
            GitHubIdentityEvents.message(GitHubIdentityResult(login = "octo", error = null))
        )
        assertEquals(
            "That GitHub account is already linked to another CollabSphere user.",
            GitHubIdentityEvents.message(GitHubIdentityResult(login = null, error = "github_account_in_use"))
        )
        assertEquals("GitHub linking was cancelled.", GitHubIdentityEvents.message(GitHubIdentityResult(null, "access_denied")))
        assertEquals("Couldn't link GitHub. Please try again.", GitHubIdentityEvents.message(GitHubIdentityResult(null, "token_exchange_failed")))
    }
}
