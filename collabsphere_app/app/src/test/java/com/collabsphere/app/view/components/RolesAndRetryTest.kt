package com.collabsphere.app.view.components

import com.collabsphere.app.dto.workspace.WorkspaceRoles
import com.collabsphere.app.model.RetryOutcome
import com.collabsphere.app.model.retryOutcomeMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RolesAndRetryTest {

    @Test
    fun clientRoleRulesMatchTheServer() {
        assertTrue(WorkspaceRoles.canRemove(WorkspaceRoles.ADMIN, WorkspaceRoles.MEMBER, isSelf = false))
        assertFalse(WorkspaceRoles.canRemove(WorkspaceRoles.ADMIN, WorkspaceRoles.ADMIN, isSelf = false))
        assertTrue(WorkspaceRoles.canRemove(WorkspaceRoles.OWNER, WorkspaceRoles.ADMIN, isSelf = false))
        assertFalse(WorkspaceRoles.canRemove(WorkspaceRoles.MEMBER, WorkspaceRoles.MEMBER, isSelf = false))
        assertFalse(WorkspaceRoles.canRemove(null, WorkspaceRoles.MEMBER, isSelf = false))
        assertFalse(WorkspaceRoles.canRemove(WorkspaceRoles.OWNER, WorkspaceRoles.OWNER, isSelf = true))
    }

    @Test
    fun pendingMessagesOnlyOfferRetryAfterTheGracePeriod() {
        val sentAt = 1_000_000L
        assertFalse(isStalePending(-5, sentAt, sentAt + PENDING_GRACE_MS - 1))
        assertTrue(isStalePending(-5, sentAt, sentAt + PENDING_GRACE_MS))
        assertTrue(isStalePending(-5, null, sentAt))
        assertFalse(isStalePending(12, sentAt, sentAt + 60_000))
    }

    @Test
    fun retryOutcomesMapToFeedback() {
        assertNull(retryOutcomeMessage(RetryOutcome.SENT))
        assertEquals("Already sending — hang tight.", retryOutcomeMessage(RetryOutcome.ALREADY_SENDING))
        assertTrue(retryOutcomeMessage(RetryOutcome.STILL_OFFLINE)!!.startsWith("Still can't reach"))
    }
}
