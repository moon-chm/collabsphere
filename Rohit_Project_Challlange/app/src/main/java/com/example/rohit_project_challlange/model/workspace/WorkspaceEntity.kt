package com.example.rohit_project_challlange.model.workspace

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "workspace",
    indices = [Index(value = ["userId"])]
)
data class WorkspaceEntity(
    @PrimaryKey val id: Int,
    val userId: Int,
    val workspaceName: String,
    val workspaceOwner: String,
    val workspacePassword: String
)