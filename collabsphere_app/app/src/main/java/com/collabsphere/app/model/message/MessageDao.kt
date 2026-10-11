package com.collabsphere.app.model.message

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.collabsphere.app.model.OutboxSyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun sendMessage(message: MessageEntity): Long

    @Query("SELECT * FROM message WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: Int): MessageEntity?

    @Query("SELECT * FROM message WHERE workspaceId = :workspaceId AND channelId = :channelId ORDER BY id ASC")
    fun getMessageForChannels(workspaceId: Int, channelId: Int): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message WHERE workspaceId = :workspaceId AND channelId = :channelId AND syncStatus = 'FAILED_PERMANENT'")
    fun getFailedMessages(workspaceId: Int, channelId: Int): Flow<List<MessageEntity>>

    @Query("UPDATE message SET syncStatus = :syncStatus WHERE id = :id")
    suspend fun updateSyncStatus(id: Int, syncStatus: OutboxSyncStatus)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("DELETE FROM message WHERE id = :messageId AND userId = :userId AND workspaceId = :workspaceId AND channelId = :channelId")
    suspend fun deleteMessage(messageId: Int, userId: Int, workspaceId: Int, channelId: Int): Int

    @Query("UPDATE message SET id = :newId, syncStatus = 'SYNCED' WHERE id = :oldId")
    suspend fun updateMessageId(oldId: Int, newId: Int)

    @Query("SELECT MIN(id) FROM message WHERE workspaceId = :workspaceId AND channelId = :channelId AND id > 0")
    suspend fun oldestSyncedMessageId(workspaceId: Int, channelId: Int): Int?

    @Query("UPDATE message SET pinnedAt = :pinnedAt WHERE id = :id")
    suspend fun updatePinnedAt(id: Int, pinnedAt: Long?)

    @Query("SELECT COUNT(*) FROM message WHERE id = :id")
    suspend fun countById(id: Int): Int

    @Transaction
    suspend fun replaceTempId(oldId: Int, newId: Int) {
        if (countById(newId) > 0) {
            deleteByIds(listOf(oldId))
        } else {
            updateMessageId(oldId, newId)
        }
    }

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