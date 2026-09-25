package com.collabsphere.app.model.notes

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["workspaceId"])
    ]
)
data class NotesEntity(
    @PrimaryKey val id: Int,
    val userId: Int,
    val workspaceId: Int,
    val notesName: String,
    val description: String,
    @ColumnInfo(defaultValue = "0")
    val isPinned: Boolean = false
)