package com.collabsphere.app.model.channels

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.collabsphere.app.model.UserEntity
import com.collabsphere.app.model.workspace.WorkspaceEntity

@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["workspaceId"])
    ]
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: Int?,
    val channelName: String,
    val workspaceId: Int,
    val description: String
)