package com.collabsphere.app.model

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.collabsphere.app.model.channels.ChannelDao
import com.collabsphere.app.model.channels.ChannelEntity
import com.collabsphere.app.model.dm.DmDao
import com.collabsphere.app.model.dm.DmEntity
import com.collabsphere.app.model.dm.DmReactionDao
import com.collabsphere.app.model.dm.DmReactionEntity
import com.collabsphere.app.model.notes.NotesDao
import com.collabsphere.app.model.notes.NotesEntity
import com.collabsphere.app.model.task.TaskDao
import com.collabsphere.app.model.task.TaskEntity
import com.collabsphere.app.model.workspace.WorkspaceDao
import com.collabsphere.app.model.workspace.WorkspaceEntity
import com.collabsphere.app.model.workspace.WorkspaceMemberEntity
import com.collabsphere.app.model.message.MessageDao
import com.collabsphere.app.model.file.FileEntity
import com.collabsphere.app.model.file.FileDao
import com.collabsphere.app.model.message.MessageEntity

@Database(
    entities = [
        UserEntity::class,
        WorkspaceEntity::class,
        WorkspaceMemberEntity::class,
        ChannelEntity::class,
        TaskEntity::class,
        NotesEntity::class,
        MessageEntity::class,
        FileEntity::class,
        DmEntity::class,
        DmReactionEntity::class
    ],
    version = 41,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun channelDao(): ChannelDao
    abstract fun taskDao(): TaskDao
    abstract fun notesDao(): NotesDao
    abstract fun messageDao(): MessageDao
    abstract fun fileDao(): FileDao
    abstract fun dmDao(): DmDao
    abstract fun dmReactionDao(): DmReactionDao

    companion object {
        /**
         * Migration 35 → 36:
         * - Adds mediaUrl (TEXT, nullable) to DM table for media/file sharing
         * - Adds isRead (INTEGER, default 0) to DM table for read receipts
         * - Creates DmReactions table for emoji reactions
         */
        val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE DM ADD COLUMN mediaUrl TEXT")
                database.execSQL("ALTER TABLE DM ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS DmReactions (
                        messageId INTEGER NOT NULL,
                        userId INTEGER NOT NULL,
                        emoji TEXT NOT NULL,
                        PRIMARY KEY(messageId, userId, emoji)
                    )
                """.trimIndent())
            }
        }

        /**
         * Migration 36 → 37:
         * Replaces the three single-column DM indexes with two composite covering indexes
         * that match the getDmHistory() query predicate exactly, eliminating full table scans.
         * Also adds a (timestamp, senderId) index for deleteDmByContentAndTimestamp.
         */
        val MIGRATION_36_37 = object : Migration(36, 37) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Drop old single-column indexes (may not exist on fresh installs — IF EXISTS guard)
                database.execSQL("DROP INDEX IF EXISTS `index_DM_senderId`")
                database.execSQL("DROP INDEX IF EXISTS `index_DM_receiverId`")
                database.execSQL("DROP INDEX IF EXISTS `index_DM_workspaceId`")
                // Create composite covering indexes
                database.execSQL("CREATE INDEX IF NOT EXISTS `idx_dm_ws_sender_receiver` ON `DM` (`workspaceId`, `senderId`, `receiverId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `idx_dm_ws_receiver_sender` ON `DM` (`workspaceId`, `receiverId`, `senderId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `idx_dm_timestamp_sender` ON `DM` (`timestamp`, `senderId`)")
            }
        }

        val MIGRATION_37_38 = object : Migration(37, 38) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE task ADD COLUMN dueDate INTEGER")
                database.execSQL("ALTER TABLE task ADD COLUMN priority TEXT NOT NULL DEFAULT 'MEDIUM'")
            }
        }

        val MIGRATION_38_39 = object : Migration(38, 39) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE message ADD COLUMN replyToId INTEGER")
                database.execSQL("ALTER TABLE DM ADD COLUMN replyToId INTEGER")
            }
        }

        val MIGRATION_39_40 = object : Migration(39, 40) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE message ADD COLUMN mediaUrl TEXT")
            }
        }

        val MIGRATION_40_41 = object : Migration(40, 41) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE message ADD COLUMN pinnedAt INTEGER")
                database.execSQL("ALTER TABLE notes ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}