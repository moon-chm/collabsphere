package com.collabsphere.app.widget

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.room.Room
import com.collabsphere.app.model.AppDatabase
import com.collabsphere.app.model.notes.NotesEntity
import com.collabsphere.app.model.task.TaskEntity
import com.collabsphere.app.model.task.TaskStatus
import com.collabsphere.app.model.workspace.WorkspaceEntity
import kotlinx.coroutines.flow.firstOrNull
import com.collabsphere.app.dataStore

// ─────────────────────────────────────────────────────────────────
// WidgetDataProvider — opens Room DB and DataStore directly.
// Widgets run in a process that may not have Koin initialized,
// so we build a lightweight DB reference here rather than injecting.
//
// All public methods are suspend functions called from the Glance
// coroutine scope; they are safe to call from any background thread.
// ─────────────────────────────────────────────────────────────────

object WidgetDataProvider {

    // Room instances are thread-safe and meant to be long-lived — building a fresh one on every
    // widget refresh (and never closing it) leaked a SQLite connection on every tick.
    @Volatile
    private var db: AppDatabase? = null

    private fun getDatabase(context: Context): AppDatabase =
        db ?: synchronized(this) {
            db ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "app_database"
            )
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .addMigrations(AppDatabase.MIGRATION_35_36, AppDatabase.MIGRATION_36_37, AppDatabase.MIGRATION_37_38, AppDatabase.MIGRATION_38_39, AppDatabase.MIGRATION_39_40, AppDatabase.MIGRATION_40_41)
                .build().also { db = it }
        }

    /**
     * Returns the saved user ID from DataStore, or -1 if not signed in.
     */
    suspend fun getSavedUserId(context: Context): Int {
        val prefs = context.dataStore.data.firstOrNull() ?: return -1
        return prefs[intPreferencesKey("saved_user_id")] ?: -1
    }

    /**
     * Returns up to [limit] active (TO_DO or IN_PROGRESS) tasks for any workspace
     * where [userId] is the creator. Falls back to an empty list on error.
     */
    suspend fun getActiveTasks(context: Context, userId: Int, limit: Int = 5): List<TaskEntity> {
        if (userId == -1) return emptyList()
        return try {
            val db = getDatabase(context)
            db.taskDao()
                .getTasksByUserAllWorkspaces(userId)
                .firstOrNull()
                ?.filter { it.status != TaskStatus.DONE }
                ?.take(limit)
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Returns up to [limit] most-recent notes for any workspace where [userId] is owner.
     */
    suspend fun getRecentNotes(context: Context, userId: Int, limit: Int = 5): List<NotesEntity> {
        if (userId == -1) return emptyList()
        return try {
            val db = getDatabase(context)
            db.notesDao()
                .getNotesByUser(userId)
                .firstOrNull()
                ?.take(limit)
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Returns up to [limit] workspaces that [userId] owns or is a member of.
     * Used by [CollabWorkspacesWidget] to display active workspaces on the home screen.
     */
    suspend fun getActiveWorkspaces(
        context: Context,
        userId : Int,
        limit  : Int = 3
    ): List<WorkspaceEntity> {
        if (userId == -1) return emptyList()
        return try {
            val db = getDatabase(context)
            db.workspaceDao()
                .getAllWorkspacesForUser(userId)
                .firstOrNull()
                ?.take(limit)
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
