package com.collabsphere.app.remote

import com.collabsphere.app.model.SyncDecision
import com.collabsphere.app.model.SyncPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiStatusExceptionTest {

    @Test
    fun `short plain-text server reasons are shown to the user`() {
        assertEquals("Invalid or expired invite code", readableErrorMessage("Invalid or expired invite code"))
        assertEquals("User is already a member", readableErrorMessage("  \"User is already a member\"  "))
    }

    @Test
    fun `bodies that are not a human-readable reason are ignored`() {
        assertNull(readableErrorMessage(null))
        assertNull(readableErrorMessage("   "))
        assertNull(readableErrorMessage("false"))
        assertNull(readableErrorMessage("""{"success":false,"message":"x"}"""))
        assertNull(readableErrorMessage("[]"))
        assertNull(readableErrorMessage("x".repeat(201)))
    }

    @Test
    fun `the message falls back to the status when the server gave no reason`() {
        assertEquals("Invalid or expired invite code", ApiStatusException(404, "Invalid or expired invite code").message)
        assertEquals("Server responded with HTTP 503", ApiStatusException(503).message)
    }

    @Test
    fun `the sync decision depends on the status, not on the server's wording`() {
        assertEquals(SyncDecision.DROP, SyncPolicy.forFailure(ApiStatusException(400, "Malformed request body"), isDelete = false))
        assertEquals(SyncDecision.RETRY, SyncPolicy.forFailure(ApiStatusException(500, "Message create failed"), isDelete = false))
    }
}
