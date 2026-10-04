package plugins

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.transactions.TransactionManager

/*
 * Delta-sync cursors.
 *
 * The old protocol asked for rows with `updated_at > lastSeenUpdatedAt`. `updated_at` is stamped
 * *before* the writing transaction commits, so a slow transaction can commit a row whose timestamp
 * is older than rows another client already synced past — that row is then skipped forever.
 *
 * The cursor protocol instead uses transaction ids:
 *  - every synced row carries `sync_xid`, set by a trigger to the id of the transaction that last
 *    wrote it (see SyncSchema.kt);
 *  - each sync response returns `xmin` of the snapshot the rows were read under as the next cursor;
 *  - the next request asks for rows with `sync_xid >= cursor`.
 *
 * Why nothing is missed: a row not visible in snapshot S was written by a transaction that was still
 * running at S (or started after it), and every such transaction id is >= S.xmin. So any row the
 * previous response could not see is guaranteed to match the next request once it commits.
 * The price is a little overlap (rows written by transactions >= xmin come back again), which is
 * harmless because clients apply sync rows as idempotent upserts/deletes.
 */

internal const val SYNC_CURSOR_HEADER = "X-Sync-Cursor"
internal const val SYNC_RESET_HEADER = "X-Sync-Reset"

/** A sync request: [cursor] when the client has one, otherwise the legacy `updated_at` watermark. */
internal data class SyncRequest(val cursor: Long?, val since: Long)

internal data class SyncPage<T>(
    val rows: List<T>,
    /** Cursor the client should send next time. */
    val nextCursor: Long,
    /**
     * The client's cursor came from a different database (e.g. after a restore onto a new cluster)
     * and can't be compared — the client must drop its sync state and resync from scratch.
     */
    val reset: Boolean = false
)

internal data class SyncSnapshot(val xmin: Long, val xmax: Long)

internal fun ApplicationCall.syncRequest(sinceParam: String = "since"): SyncRequest = SyncRequest(
    cursor = request.queryParameters["cursor"]?.toLongOrNull()?.takeIf { it >= 0 },
    since = request.queryParameters[sinceParam]?.toLongOrNull() ?: 0L
)

/**
 * Bounds of the snapshot the current transaction reads under. Must run inside the transaction that
 * also reads the rows, and *before* the row query — under REPEATABLE READ both see the same snapshot,
 * and under READ COMMITTED an earlier xmin only widens the overlap, never opens a gap.
 */
internal fun currentSyncSnapshot(): SyncSnapshot =
    TransactionManager.current().exec(
        "SELECT pg_snapshot_xmin(pg_current_snapshot())::text::bigint, " +
            "pg_snapshot_xmax(pg_current_snapshot())::text::bigint"
    ) { rs ->
        rs.next()
        SyncSnapshot(xmin = rs.getLong(1), xmax = rs.getLong(2))
    } ?: error("Could not read the current transaction snapshot")

/**
 * Runs one delta-sync query. [changedSince] builds the filter for the cursor protocol,
 * [legacyChangedSince] the `updated_at` filter for clients that haven't received a cursor yet,
 * and [fetch] runs the actual query with whichever filter applies.
 */
internal fun <T> deltaSync(
    request: SyncRequest,
    changedSince: (cursor: Long) -> Op<Boolean>,
    legacyChangedSince: (since: Long) -> Op<Boolean>,
    fetch: (filter: Op<Boolean>) -> List<T>
): SyncPage<T> {
    val snapshot = currentSyncSnapshot()
    val cursor = request.cursor
    // A cursor ahead of every transaction id this database has handed out can't have come from it.
    if (cursor != null && cursor > snapshot.xmax) {
        return SyncPage(emptyList(), snapshot.xmin, reset = true)
    }
    val filter = if (cursor != null) changedSince(cursor) else legacyChangedSince(request.since)
    return SyncPage(fetch(filter), snapshot.xmin)
}

internal fun ApplicationCall.appendSyncHeaders(nextCursor: Long, reset: Boolean = false) {
    response.headers.append(SYNC_CURSOR_HEADER, nextCursor.toString())
    if (reset) response.headers.append(SYNC_RESET_HEADER, "true")
}
