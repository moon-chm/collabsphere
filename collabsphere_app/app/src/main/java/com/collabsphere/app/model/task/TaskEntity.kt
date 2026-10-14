package com.collabsphere.app.model.task

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.collabsphere.app.dto.task.ChecklistItem
import com.collabsphere.app.model.UserEntity
import com.collabsphere.app.model.workspace.WorkspaceEntity

/** [remote] is the server's spelling; Room keeps storing the enum name, so local data is unchanged. */
enum class TaskStatus(val remote: String) {
    TO_DO("TODO"), IN_PROGRESS("IN_PROGRESS"), DONE("DONE");

    companion object {
        /** Accepts the server value or a legacy enum name (older queued sync work stored names). */
        fun fromRemote(value: String?): TaskStatus =
            entries.firstOrNull { it.remote == value || it.name == value } ?: TO_DO
    }
}

enum class TaskPriority {
    LOW, MEDIUM, HIGH;

    companion object {
        fun fromRemote(value: String?): TaskPriority =
            entries.firstOrNull { it.name == value?.uppercase() } ?: MEDIUM
    }
}

/** Whether a mutation reached the server, or only got queued locally for a background retry. */
enum class TaskSyncOutcome {
    CONFIRMED, QUEUED
}

@Entity(
    tableName = "task",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["createdByUserId"],
            onDelete = ForeignKey.NO_ACTION,
            deferred = true
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["assignedToUserId"],
            onDelete = ForeignKey.SET_NULL,
            deferred = true
        ),
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true
        )
    ],
    indices = [
        Index(value = ["createdByUserId"]),
        Index(value = ["assignedToUserId"]),
        Index(value = ["workspaceId"])
    ]
)
data class TaskEntity(
    @PrimaryKey val id: Int,
    val createdByUserId: Int,
    val assignedToUserId: Int?,
    val workspaceId: Int,
    val taskName: String,
    val taskDescription: String,
    val status: TaskStatus = TaskStatus.TO_DO,
    val dueDate: Long? = null,
    @ColumnInfo(defaultValue = "MEDIUM")
    val priority: TaskPriority = TaskPriority.MEDIUM,
    @ColumnInfo(defaultValue = "[]")
    val checklist: List<ChecklistItem> = emptyList(),
    @ColumnInfo(defaultValue = "[]")
    val labels: List<String> = emptyList()
)