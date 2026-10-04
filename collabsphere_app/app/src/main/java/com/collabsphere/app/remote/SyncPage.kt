package com.collabsphere.app.remote

import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse

/**
 * One delta-sync response: the changed rows plus where to resume next time.
 *
 * [cursor] is the server's resume point (absent from a server that predates cursors — the client then
 * keeps using its updatedAt watermark). [reset] means the server can't interpret the cursor we sent
 * (e.g. its database was restored elsewhere) and local sync state must be dropped and rebuilt.
 */
data class SyncPage<T>(val items: List<T>, val cursor: Long?, val reset: Boolean)

const val SYNC_CURSOR_HEADER = "X-Sync-Cursor"
const val SYNC_RESET_HEADER = "X-Sync-Reset"

suspend inline fun <reified T> HttpResponse.toSyncPage(): SyncPage<T> {
    requireSuccess()
    return SyncPage(
        items = body(),
        cursor = headers[SYNC_CURSOR_HEADER]?.toLongOrNull(),
        reset = headers[SYNC_RESET_HEADER] == "true"
    )
}

/**
 * Sends both resume points: a cursor-aware server uses `cursor`, an older one ignores it and falls
 * back to the timestamp — so a server rollback degrades to the old behaviour instead of a full resync.
 */
fun HttpRequestBuilder.syncParameters(since: Long, cursor: Long?, sinceParam: String = "since") {
    parameter(sinceParam, since)
    cursor?.let { parameter("cursor", it) }
}
