package com.example.rohit_project_challlange.model.notes

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
    val description: String
)