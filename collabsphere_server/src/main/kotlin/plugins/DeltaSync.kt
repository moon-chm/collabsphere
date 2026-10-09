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
internal data class SyncRequest(
    val cursor: Long?,
    val since: Long,
    val pageSize: Int? = null,
    val pageToken: String? = null
)

internal data class SyncPage<T>(
    val rows: List<T>,
    /** Cursor the client should send next time. */
    val nextCursor: Long,
    /**
     * The client's cursor came from a different database (e.g. after a restore onto a new cluster)
     * and can't be compared — the client must drop its sync state and resync from scratch.
     */
    val reset: Boolean = false,
    val nextPageToken: String? = null
)

internal data class SyncSnapshot(val xmin: Long, val xmax: Long)

internal const val MAX_DELTA_PAGE_SIZE = 250
// Continuations carry a stable ordered key so requests fetch the next bounded page without OFFSET.
internal data class SyncPosition(val syncXid: Long, val id: Long) : Comparable<SyncPosition> {
    override fun compareTo(other: SyncPosition): Int =
        compareValuesBy(this, other, SyncPosition::syncXid, SyncPosition::id)
}

internal data class DeltaPageToken(val snapshotCursor: Long, val position: SyncPosition)

internal data class DeltaSyncRow<T>(val value: T, val position: SyncPosition)

internal fun encodePageToken(token: DeltaPageToken): String =
    java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString("${token.snapshotCursor}:${token.position.syncXid}:${token.position.id}".toByteArray(Charsets.UTF_8))

internal fun decodePageToken(value: String): DeltaPageToken? = runCatching {
    val decoded = String(java.util.Base64.getUrlDecoder().decode(value), Charsets.UTF_8).split(':')
    require(decoded.size == 3)
    val cursor = decoded[0].toLong().also { require(it >= 0) }
    val syncXid = decoded[1].toLong().also { require(it >= 0) }
    val id = decoded[2].toLong().also { require(it >= 0) }
    DeltaPageToken(cursor, SyncPosition(syncXid, id))
}.getOrNull()

internal fun <T> createDeltaSyncPage(
    fetched: List<DeltaSyncRow<T>>,
    pageSize: Int?,
    snapshotCursor: Long
): SyncPage<T> {
    val hasMore = pageSize != null && fetched.size > pageSize
    val returnedRows = if (hasMore) fetched.take(pageSize!!) else fetched
    return SyncPage(
        rows = returnedRows.map { it.value },
        nextCursor = snapshotCursor,
        nextPageToken = if (hasMore) {
            encodePageToken(DeltaPageToken(snapshotCursor, fetched[pageSize!! - 1].position))
        } else null
    )
}

internal fun ApplicationCall.syncRequest(sinceParam: String = "since"): SyncRequest = SyncRequest(
    cursor = request.queryParameters["cursor"]?.toLongOrNull()?.takeIf { it >= 0 },
    since = request.queryParameters[sinceParam]?.toLongOrNull() ?: 0L,
    pageSize = request.queryParameters["pageSize"]?.toIntOrNull()?.coerceIn(1, MAX_DELTA_PAGE_SIZE),
    pageToken = request.queryParameters["pageToken"]
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
    useCursorForFirstPage: Boolean = true,
    fetch: (filter: Op<Boolean>, limit: Int?, after: SyncPosition?) -> List<DeltaSyncRow<T>>
): SyncPage<T> {
    val snapshot = currentSyncSnapshot()
    val cursor = request.cursor
    val pageToken = request.pageToken?.let(::decodePageToken)
    if (request.pageToken != null && (pageToken == null || request.pageSize == null)) return SyncPage(emptyList(), snapshot.xmin, reset = true)
    // A cursor ahead of every transaction id this database has handed out can't have come from it.
    if ((cursor != null && cursor > snapshot.xmax) ||
        (pageToken != null && (pageToken.snapshotCursor > snapshot.xmax || pageToken.position.syncXid > snapshot.xmax))
    ) {
        return SyncPage(emptyList(), snapshot.xmin, reset = true)
    }
    // Page-capable clients start their initial bounded scan from the durable sync key. Legacy clients
    // without pageSize retain the timestamp contract; DM sync opts out because it has a separate
    // knownUpToId bootstrap rule.
    val queryCursor = cursor ?: if (request.pageSize != null && useCursorForFirstPage) 0L else null
    val filter = if (queryCursor != null) changedSince(queryCursor) else legacyChangedSince(request.since)
    val snapshotCursor = pageToken?.snapshotCursor ?: snapshot.xmin
    val after = pageToken?.position
    val pageSize = request.pageSize
    val fetched = fetch(filter, pageSize?.plus(1), after)
    return createDeltaSyncPage(fetched, pageSize, snapshotCursor)
}

internal fun ApplicationCall.appendSyncHeaders(nextCursor: Long, reset: Boolean = false, nextPageToken: String? = null) {
    if (nextPageToken == null) response.headers.append(SYNC_CURSOR_HEADER, nextCursor.toString())
    else response.headers.append("X-Sync-Page-Token", nextPageToken)
    if (reset) response.headers.append(SYNC_RESET_HEADER, "true")
}

/** Low-cardinality sync metrics for response volume, grouped by entity type. */
internal fun recordSyncPage(entity: String, rowCount: Int) {
    val registry = io.micrometer.core.instrument.Metrics.globalRegistry
    io.micrometer.core.instrument.Counter.builder("collabsphere.sync.responses")
        .tag("entity", entity)
        .register(registry)
        .increment()
    io.micrometer.core.instrument.Counter.builder("collabsphere.sync.rows")
        .tag("entity", entity)
        .register(registry)
        .increment(rowCount.toDouble())
}
