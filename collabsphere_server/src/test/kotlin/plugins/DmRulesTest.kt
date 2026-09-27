package plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DmRulesTest {

    private fun rejection(
        senderIsMember: Boolean = true,
        receiverIsMember: Boolean = true,
        blockedEitherWay: Boolean = false,
        content: String = "hi",
        hasMedia: Boolean = false
    ) = DmRules.sendRejection(senderIsMember, receiverIsMember, blockedEitherWay, content, hasMedia)

    @Test
    fun `allows a normal message between two workspace members`() {
        assertNull(rejection())
    }

    @Test
    fun `refuses when either party is not a member of the workspace`() {
        assertNotNull(rejection(senderIsMember = false))
        assertNotNull(rejection(receiverIsMember = false))
    }

    @Test
    fun `refuses when either user has blocked the other`() {
        assertNotNull(rejection(blockedEitherWay = true))
    }

    @Test
    fun `refuses an empty message unless it carries media`() {
        assertNotNull(rejection(content = "   "))
        assertNull(rejection(content = "", hasMedia = true))
    }

    @Test
    fun `refuses content over the length cap`() {
        assertNull(rejection(content = "a".repeat(DmRules.MAX_CONTENT_LENGTH)))
        assertNotNull(rejection(content = "a".repeat(DmRules.MAX_CONTENT_LENGTH + 1)))
    }

    @Test
    fun `partner is the other party only when the caller took part in the message`() {
        assertEquals(2, DmRules.partnerOf(senderId = 1, receiverId = 2, actingUserId = 1))
        assertEquals(1, DmRules.partnerOf(senderId = 1, receiverId = 2, actingUserId = 2))
        assertNull(DmRules.partnerOf(senderId = 1, receiverId = 2, actingUserId = 3))
    }
}
