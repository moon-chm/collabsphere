package com.collabsphere.app.model.vote

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.collabsphere.app.dto.task.ChecklistItem

@Entity(
    tableName = "task_vote_proposal",
    indices = [
        Index(value = ["workspaceId"]),
        Index(value = ["status"])
    ]
)
data class TaskVoteProposalEntity(
    @PrimaryKey val id: Int,
    val workspaceId: Int,
    val proposerId: Int,
    val type: String, // "CREATE" or "DELETE"
    val status: String, // "PENDING", "APPROVED", "REJECTED", "EXPIRED"
    val taskId: Int? = null,
    val taskName: String? = null,
    val taskDescription: String? = null,
    val assignedToUserId: Int? = null,
    val priority: String? = null,
    val dueDate: Long? = null,
    val checklist: List<ChecklistItem> = emptyList(),
    val labels: List<String> = emptyList(),
    val deleteReason: String? = null,
    val eligibleCount: Int,
    val approvalThreshold: Int,
    val approveCount: Int,
    val rejectCount: Int,
    val resolvedAt: Long? = null,
    val createdTaskId: Int? = null,
    val deadline: Long,
    val createdAt: Long,
    val updatedAt: Long = 0L,
    val myVote: String? = null // "APPROVE", "REJECT", or null
)
