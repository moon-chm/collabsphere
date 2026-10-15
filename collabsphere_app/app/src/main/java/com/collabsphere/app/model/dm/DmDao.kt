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

    @Query("""
        SELECT d.id AS id, 
               CASE WHEN d.senderId = :currentUserId THEN d.receiverId ELSE d.senderId END AS partnerId,
               COALESCE(u.userName, '') AS partnerName,
               d.dm_content AS content,
               d.timestamp AS timestamp
        FROM DM d
        LEFT JOIN users u ON u.id = CASE WHEN d.senderId = :currentUserId THEN d.receiverId ELSE d.senderId END
        WHERE d.workspaceId = :workspaceId 
          AND (d.senderId = :currentUserId OR d.receiverId = :currentUserId)
          AND d.dm_content LIKE '%' || :query || '%'
        ORDER BY d.timestamp DESC LIMIT 50
    """)
    suspend fun searchDmsOffline(workspaceId: Int, currentUserId: Int, query: String): List<com.collabsphere.app.dto.search.SearchDmHit>

    @Query("UPDATE DM SET dm_content = :newContent WHERE id = :dmId")
    suspend fun updateDmContent(dmId: Int, newContent: String)

    /** A text edit: also stamps editedAt, unless the text is unchanged (then it is not an edit). */
    @Query("UPDATE DM SET editedAt = CASE WHEN dm_content = :newContent THEN editedAt ELSE :editedAt END, dm_content = :newContent WHERE id = :dmId")
    suspend fun updateDmEdited(dmId: Int, newContent: String, editedAt: Long)

    /** Unread DMs sent to [currentUserId] in a workspace, per sender — the conversation-list badges. */
    @Query("""
        SELECT senderId AS partnerId, COUNT(*) AS count FROM DM
        WHERE workspaceId = :workspaceId AND receiverId = :currentUserId AND isRead = 0 AND id > 0
        GROUP BY senderId
    """)
    fun unreadCountsByPartner(workspaceId: Int, currentUserId: Int): kotlinx.coroutines.flow.Flow<List<DmUnreadCount>>

    @Query("UPDATE DM SET pinnedAt = :pinnedAt WHERE id = :dmId")
    suspend fun updatePinned(dmId: Int, pinnedAt: Long?)

    @Query("UPDATE DM SET isRead = :isRead WHERE id = :dmId")
    suspend fun updateIsRead(dmId: Int, isRead: Boolean)

    @Query("SELECT MIN(id) FROM DM WHERE workspaceId = :workspaceId AND senderId = :partnerId AND receiverId = :currentUserId AND isRead = 0 AND id > 0")
    suspend fun firstUnreadId(workspaceId: Int, currentUserId: Int, partnerId: Int): Int?

    @Query("UPDATE DM SET isRead = 1 WHERE receiverId = :receiverId AND senderId = :senderId AND workspaceId = :workspaceId AND isRead = 0")
    suspend fun markMessagesAsRead(senderId: Int, receiverId: Int, workspaceId: Int)

    @Query("UPDATE DM SET mediaUrl = :mediaUrl WHERE id = :dmId")
    suspend fun updateMediaUrl(dmId: Int, mediaUrl: String?)

    @Query("DELETE FROM DM WHERE id = :dmId AND workspaceId = :workspaceId")
    suspend fun deleteDm(dmId: Int, workspaceId: Int)

    @Query("DELETE FROM DM WHERE id = :dmId")
    suspend fun deleteDmById(dmId: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM DM WHERE id = :dmId)")
    suspend fun exists(dmId: Int): Boolean

    @Query("DELETE FROM DM WHERE dm_content = :content AND timestamp = :timestamp AND senderId = :senderId")
    suspend fun deleteDmByContentAndTimestamp(content: String, timestamp: Long, senderId: Int)
}

data class DmUnreadCount(val partnerId: Int, val count: Int)
