package plugins

import dto.WorkspaceRoles
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceRolesTest {

    @Test
    fun `only owners and admins can moderate`() {
        assertTrue(WorkspaceRoles.canModerate(WorkspaceRoles.OWNER))
        assertTrue(WorkspaceRoles.canModerate(WorkspaceRoles.ADMIN))
        assertFalse(WorkspaceRoles.canModerate(WorkspaceRoles.MEMBER))
        assertFalse(WorkspaceRoles.canModerate(null))
    }

    @Test
    fun `anyone but the owner can leave`() {
        assertTrue(WorkspaceRoles.canRemove(WorkspaceRoles.MEMBER, WorkspaceRoles.MEMBER, isSelf = true))
        assertTrue(WorkspaceRoles.canRemove(WorkspaceRoles.ADMIN, WorkspaceRoles.ADMIN, isSelf = true))
        assertFalse(WorkspaceRoles.canRemove(WorkspaceRoles.OWNER, WorkspaceRoles.OWNER, isSelf = true))
    }

    @Test
    fun `admins remove members but only the owner removes admins`() {
        assertTrue(WorkspaceRoles.canRemove(WorkspaceRoles.ADMIN, WorkspaceRoles.MEMBER, isSelf = false))
        assertFalse(WorkspaceRoles.canRemove(WorkspaceRoles.ADMIN, WorkspaceRoles.ADMIN, isSelf = false))
        assertTrue(WorkspaceRoles.canRemove(WorkspaceRoles.OWNER, WorkspaceRoles.ADMIN, isSelf = false))
        assertFalse(WorkspaceRoles.canRemove(WorkspaceRoles.MEMBER, WorkspaceRoles.MEMBER, isSelf = false))
        assertFalse(WorkspaceRoles.canRemove(WorkspaceRoles.ADMIN, WorkspaceRoles.OWNER, isSelf = false))
    }
}
