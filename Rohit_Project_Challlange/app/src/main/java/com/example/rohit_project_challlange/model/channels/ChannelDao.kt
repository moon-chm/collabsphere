package com.example.rohit_project_challlange.model.channels

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createChannels(channelEntity: ChannelEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllChannels(channels: List<ChannelEntity>)
    @Query("SELECT * FROM channels WHERE workspaceId = :workspaceId")
    fun getchannels(workspaceId: Int): Flow<List<ChannelEntity>>

    @Query("DELETE FROM channels WHERE channelName = :channelName AND workspaceId = :workspaceId AND (userId = :userId OR (userId IS NULL AND :userId = 0))")
    suspend fun deletechannel(channelName: String, workspaceId: Int, userId: Int): Int

    @Query("SELECT id FROM channels WHERE channelName = :channelName AND workspaceId = :workspaceId LIMIT 1")
    suspend fun getChannelIdByName(channelName: String, workspaceId: Int): Int?

    @Query("UPDATE channels SET id = :newId WHERE id = :oldId")
    suspend fun updateChannelIdRaw(oldId: Int, newId: Int)

    @Query("UPDATE message SET channelId = :newId WHERE channelId = :oldId")
    suspend fun updateMessageChannelIdRaw(oldId: Int, newId: Int)

    @androidx.room.Transaction
    suspend fun swapChannelId(oldId: Int, newId: Int) {
        updateChannelIdRaw(oldId, newId)
        updateMessageChannelIdRaw(oldId, newId)
    }
}