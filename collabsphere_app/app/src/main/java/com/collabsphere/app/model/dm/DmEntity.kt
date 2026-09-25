package com.collabsphere.app.model.dm

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "DM",
    indices = [
        // Composite covering index matches the getDmHistory() WHERE clause exactly:
        // workspaceId AND ((senderId=A AND receiverId=B) OR (senderId=B AND receiverId=A))
        // Room will use this index for the full predicate instead of scanning the table.
        Index(value = ["workspaceId", "senderId", "receiverId"]),
        // Separate reverse index for the other half of the OR predicate
        Index(value = ["workspaceId", "receiverId", "senderId"]),
        // Speeds up deleteDmByContentAndTimestamp lookup
        Index(value = ["timestamp", "senderId"])
    ]
)
data class DmEntity(
    @PrimaryKey val id: Int,
    val workspaceId: Int,
    val senderId: Int,
    val receiverId: Int,
    val dm_content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val mediaUrl: String? = null,
    val isRead: Boolean = false,
    val replyToId: Int? = null
)