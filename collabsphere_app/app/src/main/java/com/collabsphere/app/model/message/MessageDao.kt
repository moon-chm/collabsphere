package com.collabsphere.app.model.message

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun sendMessage(message: MessageEntity): Long

    @Query("SELECT * FROM message WHERE workspaceId = :workspaceId AND channelId = :channelId ORDER BY id ASC")
    fun getMessageForChannels(workspaceId: Int, channelId: Int): Flow<List<MessageEntity>>

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("DELETE FROM message WHERE id = :messageId AND userId = :userId AND workspaceId = :workspaceId AND channelId = :channelId")
    suspend fun deleteMessage(messageId: Int, userId: Int, workspaceId: Int, channelId: Int): Int

    @Query("UPDATE message SET id = :newId WHERE id = :oldId")
    suspend fun updateMessageId(oldId: Int, newId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllMessage(message: List<MessageEntity>)

    @Upsert
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("DELETE FROM message WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)

    @Transaction
    suspend fun applyDelta(upserts: List<MessageEntity>, deletes: List<Int>) {
        if (deletes.isNotEmpty()) deleteByIds(deletes)
        if (upserts.isNotEmpty()) upsertAll(upserts)
    }
}