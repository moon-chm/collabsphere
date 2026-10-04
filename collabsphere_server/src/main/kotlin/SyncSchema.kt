package com.collabsphere

import org.jetbrains.exposed.sql.Transaction
import org.slf4j.LoggerFactory

/**
 * Database objects Exposed can't declare: the trigger that stamps `sync_xid` (the delta-sync cursor,
 * see plugins/DeltaSync.kt) and one-off backfills. Every statement is idempotent — this runs on each
 * boot, after SchemaUtils has added any missing columns.
 */
internal object SyncSchema {
    private val logger = LoggerFactory.getLogger(SyncSchema::class.java)

    /** Tables whose rows are delivered to clients through a cursor-based delta sync. */
    val SYNCED_TABLES = listOf(
        "workspace",
        "workspace_members",
        "channels",
        "local_files",
        "message",
        "notes",
        "task",
        "direct_messages"
    )

    fun install(tx: Transaction) {
        // Before the triggers exist, so the backfill doesn't mark every file row as changed.
        backfillFileStorageKeys(tx)

        tx.exec(
            """
            CREATE OR REPLACE FUNCTION collab_stamp_sync_xid() RETURNS trigger AS ${'$'}${'$'}
            BEGIN
                NEW.sync_xid := pg_current_xact_id()::text::bigint;
                RETURN NEW;
            END;
            ${'$'}${'$'} LANGUAGE plpgsql
            """.trimIndent()
        )
        for (table in SYNCED_TABLES) {
            tx.exec(
                "CREATE OR REPLACE TRIGGER ${table}_stamp_sync_xid " +
                    "BEFORE INSERT OR UPDATE ON $table " +
                    "FOR EACH ROW EXECUTE FUNCTION collab_stamp_sync_xid()"
            )
        }

        // After a dump/restore onto another cluster, rows can carry transaction ids from the old
        // cluster that are ahead of this one's counter — they'd match every future cursor and be
        // re-sent on every poll. Touching them lets the trigger re-stamp them with a current id.
        for (table in SYNCED_TABLES) {
            tx.exec(
                "UPDATE $table SET sync_xid = 0 " +
                    "WHERE sync_xid > pg_snapshot_xmax(pg_current_snapshot())::text::bigint"
            )
        }
    }

    private fun backfillFileStorageKeys(tx: Transaction) {
        tx.exec(
            "UPDATE local_files SET storage_key = substring(url from '/download/([^/?#]+)$') " +
                "WHERE storage_key IS NULL AND url ~ '/download/[^/?#]+$'"
        )
        val missing = tx.exec("SELECT count(*) FROM local_files WHERE storage_key IS NULL") { rs ->
            rs.next()
            rs.getLong(1)
        } ?: 0L
        if (missing > 0) {
            logger.warn("[SyncSchema] $missing local_files rows have no storage_key (url not in /download/<name> form) — they can't be downloaded by name")
        }
    }
}
