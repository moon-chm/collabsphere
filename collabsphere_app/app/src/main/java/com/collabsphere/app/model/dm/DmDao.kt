package com.collabsphere.app.model.dm

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("SELECT MAX(id) FROM DM WHERE id > 0")
    suspend fun newestSyncedDmId(): Int?

    @Query("""
        SELECT MIN(id) FROM DM
        WHERE id > 0 AND workspaceId = :workspaceId
        AND ((senderId = :currentUserId AND receiverId = :chatPartnerId)
        OR (senderId = :chatPartnerId AND receiverId = :currentUserId))
    """)
    suspend fun oldestSyncedDmId(workspaceId: Int, currentUserId: Int, chatPartnerId: Int): Int?

    @Query("UPDATE DM SET dm_content = :newContent WHERE id = :dmId")
    suspend fun updateDmContent(dmId: Int, newContent: String)

    @Query("UPDATE DM SET isRead = :isRead WHERE id = :dmId")
    suspend fun updateIsRead(dmId: Int, isRead: Boolean)

    @Query("UPDATE DM SET isRead = 1 WHERE receiverId = :currentUserId AND senderId = :partnerId AND workspaceId = :workspaceId AND isRead = 0")
    suspend fun markAllReadFrom(currentUserId: Int, partnerId: Int, workspaceId: Int)

    @Query("UPDATE DM SET mediaUrl = :mediaUrl WHERE id = :dmId")
    suspend fun updateMediaUrl(dmId: Int, mediaUrl: String?)

    @Query("DELETE FROM DM WHERE id = :dmId AND workspaceId = :workspaceId")
    suspend fun deleteDm(dmId: Int, workspaceId: Int)

    @Query("DELETE FROM DM WHERE id = :dmId")
    suspend fun deleteDmById(dmId: Int)

    @Query("DELETE FROM DM WHERE dm_content = :content AND timestamp = :timestamp")
    suspend fun deleteDmByContentAndTimestamp(content: String, timestamp: Long)
}