package plugins

import dto.DmDto
import kotlin.test.Test
import kotlin.test.assertEquals

class DmHistoryTest {

    private fun dm(id: Int, sender: Int, receiver: Int, workspace: Int = 1) =
        DmDto(id = id, action = "HISTORY", workspaceId = workspace, senderId = sender, receiverId = receiver)

    @Test
    fun `keeps only the newest messages per conversation and returns them oldest first`() {
        val newestFirst = listOf(dm(6, 1, 2), dm(5, 2, 1), dm(4, 1, 3), dm(3, 1, 2), dm(2, 3, 1), dm(1, 2, 1))
        val result = selectInitialDmHistory(newestFirst, userId = 1, perConversation = 2)
        assertEquals(listOf(2, 4, 5, 6), result.map { it.id })
    }

    @Test
    fun `treats the same partner in different workspaces as separate conversations`() {
        val newestFirst = listOf(dm(4, 1, 2, workspace = 2), dm(3, 1, 2, workspace = 1), dm(2, 2, 1, workspace = 2), dm(1, 2, 1, workspace = 1))
        val result = selectInitialDmHistory(newestFirst, userId = 1, perConversation = 1)
        assertEquals(listOf(3, 4), result.map { it.id })
    }
}
