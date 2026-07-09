package com.example.rohit_project_challlange.model.task

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.rohit_project_challlange.model.UserEntity
import com.example.rohit_project_challlange.model.workspace.WorkspaceEntity

enum class TaskStatus {
    TO_DO, IN_PROGRESS, DONE
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
    val status: TaskStatus = TaskStatus.TO_DO
)