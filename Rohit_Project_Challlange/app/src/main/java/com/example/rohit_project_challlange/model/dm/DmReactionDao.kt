package com.example.rohit_project_challlange.model.dm

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DmReactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReaction(reaction: DmReactionEntity)

    @Query("DELETE FROM DmReactions WHERE messageId = :messageId AND userId = :userId AND emoji = :emoji")
    suspend fun deleteReaction(messageId: Int, userId: Int, emoji: String)

    @Query("DELETE FROM DmReactions WHERE messageId = :messageId")
    suspend fun deleteAllReactionsForMessage(messageId: Int)

    /**
     * Returns grouped emoji reactions as a map of emoji → count for the given message.
     * Used by the UI to render reaction pills.
     */
    @Query("SELECT emoji, COUNT(*) as cnt FROM DmReactions WHERE messageId = :messageId GROUP BY emoji")
    fun getReactionCountsForMessage(messageId: Int): Flow<List<EmojiCount>>

    /**
     * Returns all emoji reactions for a given user in a conversation, for toggling state.
     */
    @Query("SELECT emoji FROM DmReactions WHERE messageId = :messageId AND userId = :userId")
    suspend fun getUserReactionsForMessage(messageId: Int, userId: Int): List<String>
}

/** Lightweight projection for grouped reaction counts. */
data class EmojiCount(val emoji: String, val cnt: Int)
