package com.example.rohit_project_challlange.model.workspace

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "workspace_members",
    primaryKeys = ["workspaceId", "userId"],
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workspaceId"]), Index(value = ["userId"])]
)
data class WorkspaceMemberEntity(
    val workspaceId: Int,
    val userId: Int
)