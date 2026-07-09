package com.example.rohit_project_challlange.model.file

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_files",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["workspaceId"])
    ]
)
data class FileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId : Int,
    val workspaceId: Int,
    val userName: String,
    val url : String,
    val mimeType: String,
    val localpath : String?,
    val fileName: String,
    val sizebytes: Long,
    val fileLocation: String
)