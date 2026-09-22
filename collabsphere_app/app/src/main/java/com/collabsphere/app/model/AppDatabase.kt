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
    version = 36,
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
    }
}