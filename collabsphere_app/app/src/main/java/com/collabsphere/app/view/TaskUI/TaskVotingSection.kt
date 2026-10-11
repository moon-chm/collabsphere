package com.collabsphere.app.view.TaskUI

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.collabsphere.app.model.vote.TaskVoteProposalEntity
import com.collabsphere.app.ui.theme.*
import com.collabsphere.app.viewmodel.vote.TaskVoteViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TaskVotingSection(
    viewModel: TaskVoteViewModel,
    modifier: Modifier = Modifier
) {
    val pendingProposals by viewModel.pendingProposals.collectAsState()
    val proposalHistory by viewModel.proposalHistory.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Active, 1 = History

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Tab Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VoteTabPill(
                title = "Pending votes",
                count = pendingProposals.size,
                isSelected = selectedTab == 0,
                onClick = { selectedTab = 0 }
            )
            VoteTabPill(
                title = "Vote history",
                count = proposalHistory.size,
                isSelected = selectedTab == 1,
                onClick = { selectedTab = 1 }
            )
        }

        val displayedList = if (selectedTab == 0) pendingProposals else proposalHistory

        if (displayedList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (selectedTab == 0) Icons.Default.HowToVote else Icons.Default.History,
                        contentDescription = null,
                        tint = Muted.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (selectedTab == 0) "No pending votes" else "No vote history yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Ink
                    )
                    Text(
                        text = if (selectedTab == 0) "Tasks proposed for creation or deletion will appear here." else "Resolved team votes will be archived here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(displayedList, key = { it.id }) { proposal ->
                    ProposalCard(
                        proposal = proposal,
                        isCurrentUsersProposal = (proposal.proposerId == currentUserId),
                        onVote = { vote -> viewModel.castVote(proposal.id, vote) }
                    )
                }
            }
        }
    }
}

@Composable
fun VoteTabPill(
    title: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else SurfaceRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isSelected) Color.White else Ink
            )
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color.White.copy(alpha = 0.25f) else Amber.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) Color.White else Amber
                    )
                }
            }
        }
    }
}

@Composable
fun ProposalCard(
    proposal: TaskVoteProposalEntity,
    isCurrentUsersProposal: Boolean,
    onVote: (String) -> Unit
) {
    val isPending = proposal.status == "PENDING"
    val isDelete = proposal.type == "DELETE"
    val hasVoted = proposal.myVote != null

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Type badge & Status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDelete) Destructive.copy(alpha = 0.15f) else IndigoStart.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isDelete) "DELETE REQUEST" else "CREATE TASK",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isDelete) Destructive else IndigoStart
                        )
                    }
                    if (isCurrentUsersProposal) {
                        Text(
                            text = "(Your proposal)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted
                        )
                    }
                }

                // Status Badge
                StatusPill(status = proposal.status)
            }

            // Task Name or Info
            Text(
                text = if (isDelete) "Task #${proposal.taskId}" else (proposal.taskName ?: "Untitled Task"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Description or Reason
            val detailText = if (isDelete) {
                proposal.deleteReason ?: "No deletion reason provided"
            } else {
                proposal.taskDescription ?: "No description"
            }

            Text(
                text = detailText,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Vote Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Approval: ${proposal.approveCount}/${proposal.approvalThreshold} needed (${proposal.eligibleCount} members)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Ink
                    )
                    if (proposal.rejectCount > 0) {
                        Text(
                            text = "${proposal.rejectCount} rejected",
                            style = MaterialTheme.typography.labelSmall,
                            color = Destructive
                        )
                    }
                }

                val progress = (proposal.approveCount.toFloat() / proposal.approvalThreshold.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (progress >= 1f) Mint else IndigoStart,
                    trackColor = Surface
                )
            }

            // Time / Deadline info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
                Text(
                    text = "Proposed " + dateFormat.format(Date(proposal.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted
                )

                if (isPending) {
                    val remainingHours = ((proposal.deadline - System.currentTimeMillis()) / (1000 * 3600)).coerceAtLeast(0)
                    Text(
                        text = "$remainingHours hrs remaining",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (remainingHours < 6) Destructive else Muted
                    )
                }
            }

            // Action Buttons for Voting
            if (isPending) {
                if (hasVoted) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Surface)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (proposal.myVote == "APPROVE") Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (proposal.myVote == "APPROVE") Mint else Destructive,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "You voted to ${proposal.myVote?.lowercase()}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = Ink
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Approve Button
                        Button(
                            onClick = { onVote("APPROVE") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Mint)
                        ) {
                            Icon(imageVector = Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Approve", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }

                        // Reject Button
                        Button(
                            onClick = { onVote("REJECT") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Destructive)
                        ) {
                            Icon(imageVector = Icons.Default.ThumbDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Reject", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusPill(status: String) {
    val (bgColor, textColor, label) = when (status) {
        "APPROVED" -> Triple(Mint.copy(alpha = 0.15f), Mint, "Approved")
        "REJECTED" -> Triple(Destructive.copy(alpha = 0.15f), Destructive, "Rejected")
        "EXPIRED" -> Triple(Muted.copy(alpha = 0.15f), Muted, "Expired")
        else -> Triple(Amber.copy(alpha = 0.15f), Amber, "Voting active")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = textColor
        )
    }
}

@Composable
fun RequestDeleteProposalDialog(
    taskName: String,
    onConfirm: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Amber.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.HowToVote,
                        contentDescription = null,
                        tint = Amber,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "Request task deletion",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Ink
                )

                Text(
                    text = "In CollabSphere, deleting '$taskName' requires team approval. Please provide a reason for the vote.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for deletion") },
                    placeholder = { Text("e.g. Duplicate task, requirements changed...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 3,
                    maxLines = 5
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Surface)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Cancel", style = MaterialTheme.typography.labelLarge, color = Muted)
                    }

                    val canSubmit = reason.trim().isNotEmpty()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (canSubmit) Amber else Amber.copy(alpha = 0.5f))
                            .clickable(enabled = canSubmit) {
                                onConfirm(reason.trim())
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Start vote",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
