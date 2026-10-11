package com.collabsphere.app.model.vote

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskVoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProposal(proposal: TaskVoteProposalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProposals(proposals: List<TaskVoteProposalEntity>)

    @Query("SELECT * FROM task_vote_proposal WHERE workspaceId = :workspaceId AND status = 'PENDING' ORDER BY createdAt DESC")
    fun getPendingProposals(workspaceId: Int): Flow<List<TaskVoteProposalEntity>>

    @Query("SELECT * FROM task_vote_proposal WHERE workspaceId = :workspaceId AND status != 'PENDING' ORDER BY resolvedAt DESC, createdAt DESC")
    fun getProposalHistory(workspaceId: Int): Flow<List<TaskVoteProposalEntity>>

    @Query("SELECT * FROM task_vote_proposal WHERE workspaceId = :workspaceId ORDER BY createdAt DESC")
    fun getAllProposals(workspaceId: Int): Flow<List<TaskVoteProposalEntity>>

    @Query("SELECT * FROM task_vote_proposal WHERE id = :id LIMIT 1")
    fun getProposalById(id: Int): Flow<TaskVoteProposalEntity?>

    @Query("SELECT * FROM task_vote_proposal WHERE id = :id LIMIT 1")
    suspend fun getProposalByIdDirect(id: Int): TaskVoteProposalEntity?

    @Update
    suspend fun updateProposal(proposal: TaskVoteProposalEntity)

    @Query("UPDATE task_vote_proposal SET myVote = :vote WHERE id = :proposalId")
    suspend fun updateMyVote(proposalId: Int, vote: String)

    @Query("DELETE FROM task_vote_proposal WHERE id = :id")
    suspend fun deleteProposal(id: Int)

    @Query("DELETE FROM task_vote_proposal WHERE workspaceId = :workspaceId")
    suspend fun clearProposalsForWorkspace(workspaceId: Int)
}
