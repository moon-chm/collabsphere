package com.example.rohit_project_challlange.widget

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.room.Room
import com.example.rohit_project_challlange.model.AppDatabase
import com.example.rohit_project_challlange.model.notes.NotesEntity
import com.example.rohit_project_challlange.model.task.TaskEntity
import com.example.rohit_project_challlange.model.task.TaskStatus
import kotlinx.coroutines.flow.firstOrNull
import com.example.rohit_project_challlange.dataStore

// ─────────────────────────────────────────────────────────────────
// WidgetDataProvider — opens Room DB and DataStore directly.
// Widgets run in a process that may not have Koin initialized,
// so we build a lightweight DB reference here rather than injecting.
// ─────────────────────────────────────────────────────────────────

object WidgetDataProvider {

    private fun getDatabase(context: Context): AppDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "app_database"
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

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
}
