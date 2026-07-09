package com.example.rohit_project_challlange.model.dm

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "DM",
    indices = [
        Index(value = ["senderId"]),
        Index(value = ["receiverId"]),
        Index(value = ["workspaceId"])
    ]
)
data class DmEntity(
    @PrimaryKey val id: Int,
    val workspaceId: Int,
    val senderId: Int,
    val receiverId: Int,
    val dm_content: String,
    val timestamp: Long = System.currentTimeMillis()
)