package plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeltaSyncPagingTest {
    @Test
    fun `page tokens round trip ordered keys and reject negative values`() {
        val token = DeltaPageToken(snapshotCursor = 42, position = SyncPosition(syncXid = 99, id = 12))
        assertEquals(token, decodePageToken(encodePageToken(token)))
        assertNull(decodePageToken(java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("42:-1:12".toByteArray())))
        assertNull(decodePageToken(java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("42:99:-1".toByteArray())))
        assertNull(decodePageToken("not-a-page-token"))
    }

    @Test
    fun `pagination preserves snapshot cursor until final page`() {
        val snapshot = SyncSnapshot(xmin = 100, xmax = 200)
        val rows = (1..5).map { DeltaSyncRow(it, SyncPosition(10, it.toLong())) }
        val first = createDeltaSyncPage(rows.take(3), pageSize = 2, snapshotCursor = snapshot.xmin)
        assertEquals(listOf(1, 2), first.rows)
        assertEquals(100L, first.nextCursor)
        val afterFirst = decodePageToken(first.nextPageToken!!)?.position
        assertEquals(SyncPosition(10, 2), afterFirst)

        val secondRows = rows.filter { it.position > afterFirst!! }.take(3)
        val second = createDeltaSyncPage(secondRows, pageSize = 2, snapshotCursor = snapshot.xmin)
        assertEquals(listOf(3, 4), second.rows)
        assertEquals(100L, second.nextCursor)
        assertTrue(second.nextPageToken != null)

        val afterSecond = decodePageToken(second.nextPageToken!!)?.position
        val finalRows = rows.filter { it.position > afterSecond!! }.take(3)
        val final = createDeltaSyncPage(finalRows, pageSize = 2, snapshotCursor = snapshot.xmin)
        assertEquals(listOf(5), final.rows)
        assertEquals(100L, final.nextCursor)
        assertNull(final.nextPageToken)
    }
}
