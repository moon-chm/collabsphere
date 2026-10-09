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
        "workspace_membership_state",
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

        tx.exec(
            """
            CREATE OR REPLACE FUNCTION collab_track_workspace_membership() RETURNS trigger AS ${'$'}${'$'}
            DECLARE
                membership_workspace_id integer;
                membership_user_id integer;
                membership_is_member boolean;
            BEGIN
                IF TG_OP = 'DELETE' THEN
                    membership_workspace_id := OLD.workspace_id;
                    membership_user_id := OLD.user_id;
                    membership_is_member := false;
                ELSE
                    membership_workspace_id := NEW.workspace_id;
                    membership_user_id := NEW.user_id;
                    membership_is_member := true;
                END IF;

                INSERT INTO workspace_membership_state (workspace_id, user_id, is_member, updated_at)
                VALUES (
                    membership_workspace_id,
                    membership_user_id,
                    membership_is_member,
                    (extract(epoch from clock_timestamp()) * 1000)::bigint
                )
                ON CONFLICT (workspace_id, user_id) DO UPDATE SET
                    is_member = EXCLUDED.is_member,
                    updated_at = EXCLUDED.updated_at;

                IF TG_OP = 'DELETE' THEN
                    RETURN OLD;
                END IF;
                RETURN NEW;
            END;
            ${'$'}${'$'} LANGUAGE plpgsql
            """.trimIndent()
        )
        tx.exec(
            "CREATE OR REPLACE TRIGGER workspace_membership_state_sync " +
                "AFTER INSERT OR UPDATE OR DELETE ON workspace_members " +
                "FOR EACH ROW EXECUTE FUNCTION collab_track_workspace_membership()"
        )
        // Populate active state for memberships that predate this migration. Do not overwrite
        // existing inactive rows: they carry removal state for clients resuming with an old cursor.
        tx.exec(
            "INSERT INTO workspace_membership_state (workspace_id, user_id, is_member, updated_at) " +
                "SELECT workspace_id, user_id, true, (extract(epoch from clock_timestamp()) * 1000)::bigint " +
                "FROM workspace_members ON CONFLICT (workspace_id, user_id) DO NOTHING"
        )

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
