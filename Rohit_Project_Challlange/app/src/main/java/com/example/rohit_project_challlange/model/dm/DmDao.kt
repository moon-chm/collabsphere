package com.example.rohit_project_challlange.model.dm

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DmDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun sendDm(dm: DmEntity): Long

    @Query("""
        SELECT * FROM DM 
        WHERE workspaceId = :workspaceId 
        AND ((senderId = :currentUserId AND receiverId = :chatPartnerId) 
        OR (senderId = :chatPartnerId AND receiverId = :currentUserId))
        ORDER BY timestamp ASC
    """)
    fun getDmHistory(workspaceId: Int, currentUserId: Int, chatPartnerId: Int): Flow<List<DmEntity>>

    @Update
    suspend fun updateDm(dm: DmEntity)

    @Query("DELETE FROM DM WHERE id = :dmId AND workspaceId = :workspaceId")
    suspend fun deleteDm(dmId: Int, workspaceId: Int)

    @Query("DELETE FROM DM WHERE dm_content = :content AND timestamp = :timestamp")
    suspend fun deleteDmByContentAndTimestamp(content: String, timestamp: Long)
}