package com.collabsphere.app.model

import androidx.room.Database
import androidx.room.TypeConverters
import com.collabsphere.app.model.task.TaskListConverters
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
import com.collabsphere.app.model.vote.TaskVoteDao
import com.collabsphere.app.model.vote.TaskVoteProposalEntity

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
        DmReactionEntity::class,
        TaskVoteProposalEntity::class,
        com.collabsphere.app.model.progression.ProgressionSummaryEntity::class,
        com.collabsphere.app.model.progression.ProgressionCatalogEntity::class,
        com.collabsphere.app.model.progression.BadgeProgressEntity::class
    ],
    version = 46,
    exportSchema = false
)
@TypeConverters(TaskListConverters::class)
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
    abstract fun taskVoteDao(): TaskVoteDao
    abstract fun progressionDao(): com.collabsphere.app.model.progression.ProgressionDao

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

        val MIGRATION_41_42 = object : Migration(41, 42) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE task ADD COLUMN checklist TEXT NOT NULL DEFAULT '[]'")
                database.execSQL("ALTER TABLE task ADD COLUMN labels TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_42_43 = object : Migration(42, 43) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE message ADD COLUMN clientMutationId TEXT")
            }
        }

        val MIGRATION_43_44 = object : Migration(43, 44) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE message ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'")
            }
        }

        val MIGRATION_44_45 = object : Migration(44, 45) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `task_vote_proposal` (
                        `id` INTEGER NOT NULL,
                        `workspaceId` INTEGER NOT NULL,
                        `proposerId` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `taskId` INTEGER,
                        `taskName` TEXT,
                        `taskDescription` TEXT,
                        `assignedToUserId` INTEGER,
                        `priority` TEXT,
                        `dueDate` INTEGER,
                        `checklist` TEXT NOT NULL DEFAULT '[]',
                        `labels` TEXT NOT NULL DEFAULT '[]',
                        `deleteReason` TEXT,
                        `eligibleCount` INTEGER NOT NULL,
                        `approvalThreshold` INTEGER NOT NULL,
                        `approveCount` INTEGER NOT NULL,
                        `rejectCount` INTEGER NOT NULL,
                        `resolvedAt` INTEGER,
                        `createdTaskId` INTEGER,
                        `deadline` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL DEFAULT 0,
                        `myVote` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_task_vote_proposal_workspaceId` ON `task_vote_proposal` (`workspaceId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_task_vote_proposal_status` ON `task_vote_proposal` (`status`)")
            }
        }

        /**
         * Migration 45 → 46: offline cache for career progression (summary, catalog, badge progress).
         * Server-authoritative data only — nothing here can grant SP or badges locally.
         */
        val MIGRATION_45_46 = object : Migration(45, 46) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `progression_summary` (
                        `userId` INTEGER NOT NULL,
                        `totalSp` INTEGER NOT NULL,
                        `level` INTEGER NOT NULL,
                        `maxLevel` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `levelRequiredSp` INTEGER NOT NULL,
                        `nextLevel` INTEGER,
                        `nextTitle` TEXT,
                        `nextLevelRequiredSp` INTEGER,
                        `spIntoLevel` INTEGER NOT NULL,
                        `spSpanToNext` INTEGER NOT NULL,
                        `isMaxLevel` INTEGER NOT NULL,
                        `currentStreakDays` INTEGER NOT NULL,
                        `bestStreakDays` INTEGER NOT NULL,
                        `lastLedgerId` INTEGER NOT NULL,
                        `fetchedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`)
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `progression_catalog` (
                        `id` INTEGER NOT NULL,
                        `version` INTEGER NOT NULL,
                        `etag` TEXT,
                        `json` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `badge_progress` (
                        `userId` INTEGER NOT NULL,
                        `familyKey` TEXT NOT NULL,
                        `available` INTEGER NOT NULL,
                        `counterValue` INTEGER NOT NULL,
                        `bronzeUnlockedAt` INTEGER,
                        `silverUnlockedAt` INTEGER,
                        `goldUnlockedAt` INTEGER,
                        `nextTier` TEXT,
                        `nextThreshold` INTEGER,
                        `sortOrder` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`, `familyKey`)
                    )
                """.trimIndent())
            }
        }
    }
}