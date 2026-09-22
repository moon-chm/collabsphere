package com.collabsphere.app.model.dm

import androidx.room.Entity

/**
 * Stores emoji reactions on DM messages locally.
 * Composite primary key: (messageId, userId, emoji) — one user can react
 * multiple different emojis to the same message, but not the same emoji twice.
 */
@Entity(
    tableName = "DmReactions",
    primaryKeys = ["messageId", "userId", "emoji"]
)
data class DmReactionEntity(
    val messageId: Int,
    val userId: Int,
    val emoji: String
)
