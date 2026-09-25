package com.collabsphere.app.model.message

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.collabsphere.app.model.UserEntity
import com.collabsphere.app.model.workspace.WorkspaceEntity
import com.collabsphere.app.model.channels.ChannelEntity

enum class MessageStatus {
    Delivered, Seen
}

@Entity(
    tableName = "message",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["workspaceId"]),
        Index(value = ["channelId"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: Int,
    val workspaceId: Int,
    val channelId: Int,
    val userName: String,
    val content: String,
    val status: MessageStatus = MessageStatus.Delivered,
    val replyToId: Int? = null,
    val mediaUrl: String? = null,
    val pinnedAt: Long? = null
)