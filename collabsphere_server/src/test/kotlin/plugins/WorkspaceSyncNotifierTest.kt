package plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceSyncNotifierTest {

    @Test
    fun `parses database change payloads`() {
        assertEquals(SyncSignal(12, "tasks"), SyncSignal.parse("12:tasks"))
        assertEquals(SyncSignal(3, "workspace", 41), SyncSignal.parse("3:workspace:41"))
    }

    @Test
    fun `rejects malformed or unexpected payloads`() {
        assertNull(SyncSignal.parse(""))
        assertNull(SyncSignal.parse("12"))
        assertNull(SyncSignal.parse("abc:tasks"))
        assertNull(SyncSignal.parse("0:tasks"))
        assertNull(SyncSignal.parse("12:messages"))
        assertNull(SyncSignal.parse("12:tasks:x"))
        assertNull(SyncSignal.parse("12:tasks:1:2"))
    }

    @Test
    fun `bursts collapse into one frame per workspace`() {
        val grouped = WorkspaceSyncNotifier.group(
            listOf(
                SyncSignal(5, "tasks"),
                SyncSignal(5, "votes"),
                SyncSignal(5, "tasks"),
                SyncSignal(2, "workspace", 9),
                SyncSignal(2, "workspace", 10)
            )
        )
        assertEquals(listOf(2, 5), grouped.map { it.workspaceId })
        assertEquals(listOf("tasks", "votes"), grouped[1].scopes)
        assertEquals(setOf(9, 10), grouped[0].extraRecipients)
        assertTrue(grouped[1].extraRecipients.isEmpty())
    }

    @Test
    fun `ready frame announces the capability`() {
        assertEquals("""{"action":"SYNC_READY"}""", WorkspaceSyncNotifier.readyFrame)
    }
}
