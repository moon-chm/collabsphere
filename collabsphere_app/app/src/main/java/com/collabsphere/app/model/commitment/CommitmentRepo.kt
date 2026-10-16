package com.collabsphere.app.model.commitment

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.collabsphere.app.model.task.TaskEntity
import com.collabsphere.app.model.task.TaskPriority
import com.collabsphere.app.model.task.TaskRepo
import com.collabsphere.app.model.task.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class CommitmentSource(
    val key: String,
    val workspaceId: Int,
    val assigneeId: Int,
    val assigneeName: String,
    val placeLabel: String,
    val quote: String
)

class CommitmentRepo(
    private val dataStore: DataStore<Preferences>,
    private val taskRepo: TaskRepo
) {
    val trackedKeys: Flow<Set<String>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[TRACKED] ?: emptySet() }

    suspend fun createTask(
        source: CommitmentSource,
        createdByUserId: Int,
        title: String,
        dueDate: Long?
    ): Result<Long> {
        val task = TaskEntity(
            id = 0,
            createdByUserId = createdByUserId,
            assignedToUserId = source.assigneeId,
            workspaceId = source.workspaceId,
            taskName = title.trim(),
            taskDescription = describe(source),
            status = TaskStatus.TO_DO,
            dueDate = dueDate,
            priority = TaskPriority.MEDIUM,
            labels = listOf(LABEL)
        )
        val result = taskRepo.addTask(task)
        if (result.isSuccess) {
            dataStore.edit { prefs -> prefs[TRACKED] = (prefs[TRACKED] ?: emptySet()) + source.key }
        }
        return result
    }

    companion object {
        const val LABEL = "from-chat"
        private val TRACKED = stringSetPreferencesKey("tracked_commitments")

        fun channelKey(messageId: Int) = "c:$messageId"
        fun dmKey(dmId: Int) = "d:$dmId"

        fun describe(source: CommitmentSource): String =
            "Promised by ${source.assigneeName} in ${source.placeLabel}:\n“${source.quote.trim()}”"
    }
}
