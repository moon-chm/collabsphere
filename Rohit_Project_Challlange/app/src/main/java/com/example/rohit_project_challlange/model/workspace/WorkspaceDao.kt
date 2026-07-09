package com.example.rohit_project_challlange.model.workspace

import androidx.room.*
import com.example.rohit_project_challlange.model.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {

    @Query("""
        SELECT DISTINCT w.* FROM workspace w
        INNER JOIN workspace_members wm ON w.id = wm.workspaceId
        WHERE wm.userId = :userId OR w.userId = :userId
    """)
    fun getAllWorkspacesForUser(userId: Int): Flow<List<WorkspaceEntity>>

    @Upsert
    suspend fun upsertWorkspace(workspace: WorkspaceEntity)

    @Query("DELETE FROM workspace WHERE id = :id")
    suspend fun deleteWorkspaceById(id: Int)

    @Upsert
    suspend fun upsertMember(member: WorkspaceMemberEntity)

    @Upsert
    suspend fun upsertUser(user: UserEntity)

    @Query("SELECT id FROM users WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getUserIdByEmail(email: String): Int?

    @Query("SELECT * FROM users INNER JOIN workspace_members ON users.id = workspace_members.userId WHERE workspace_members.workspaceId = :workspaceId")
    fun getWorkspaceMembers(workspaceId: Int): Flow<List<UserEntity>>

    @Query("DELETE FROM workspace WHERE workspaceName = :workspaceName AND userId = :userId AND workspacePassword = :workspacePassword")
    suspend fun deleteWorkspaceFromScreenRaw(workspaceName: String, userId: Int, workspacePassword: String): Int

    @Query("SELECT COUNT(*) FROM workspace_members WHERE workspaceId = :workspaceId AND userId = :userId")
    suspend fun isUserMember(workspaceId: Int, userId: Int): Int

    @Transaction
    suspend fun deleteWorkspaceFromScreen(workspaceName: String, userId: Int, workspacePassword: String): Int {
        return deleteWorkspaceFromScreenRaw(workspaceName, userId, workspacePassword)
    }

    @Query("UPDATE workspace SET id = :newId WHERE id = :oldId")
    suspend fun updateWorkspaceId(oldId: Int, newId: Int)

    @Query("UPDATE workspace_members SET workspaceId = :newId WHERE workspaceId = :oldId")
    suspend fun updateWorkspaceMemberId(oldId: Int, newId: Int)

    @Query("UPDATE channels SET workspaceId = :newId WHERE workspaceId = :oldId")
    suspend fun updateChannelWorkspaceId(oldId: Int, newId: Int)

    @Query("UPDATE task SET workspaceId = :newId WHERE workspaceId = :oldId")
    suspend fun updateTaskWorkspaceId(oldId: Int, newId: Int)

    @Query("UPDATE notes SET workspaceId = :newId WHERE workspaceId = :oldId")
    suspend fun updateNotesWorkspaceId(oldId: Int, newId: Int)

    @Query("UPDATE local_files SET workspaceId = :newId WHERE workspaceId = :oldId")
    suspend fun updateFilesWorkspaceId(oldId: Int, newId: Int)

    @Query("UPDATE message SET workspaceId = :newId WHERE workspaceId = :oldId")
    suspend fun updateMessageWorkspaceId(oldId: Int, newId: Int)

    @Query("UPDATE DM SET workspaceId = :newId WHERE workspaceId = :oldId")
    suspend fun updateDmWorkspaceId(oldId: Int, newId: Int)

    @Transaction
    suspend fun swapWorkspaceId(oldId: Int, newId: Int) {
        updateWorkspaceId(oldId, newId)
        updateWorkspaceMemberId(oldId, newId)
        updateChannelWorkspaceId(oldId, newId)
        updateTaskWorkspaceId(oldId, newId)
        updateNotesWorkspaceId(oldId, newId)
        updateFilesWorkspaceId(oldId, newId)
        updateMessageWorkspaceId(oldId, newId)
        updateDmWorkspaceId(oldId, newId)
    }
}