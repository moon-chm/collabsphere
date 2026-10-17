package com.collabsphere.app.view.TaskUI

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.collabsphere.app.view.components.SkeuoTextField
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
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 14.dp),
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
            com.collabsphere.app.view.components.SkeuoEmptyState(
                icon = if (selectedTab == 0) Icons.Default.HowToVote else Icons.Default.History,
                title = if (selectedTab == 0) "No pending votes" else "No vote history yet",
                description = if (selectedTab == 0) "Tasks proposed for creation or deletion show up here for the team to vote on." else "Finished votes are kept here.",
                actionLabel = null,
                onAction = {},
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp)
            )
        } else {
            val entranceRegistry = rememberEntranceRegistry()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 2.dp, end = 4.dp, bottom = 96.dp)
            ) {
                itemsIndexed(displayedList, key = { _, it -> it.id }) { index, proposal ->
                    ProposalCard(
                        modifier = Modifier
                            .animateItem()
                            .printListEntrance(proposal.id, index, entranceRegistry),
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
    PrintChip(
        text = if (count > 0) "$title · $count" else title,
        fill = if (isSelected) PrintSky else PrintWhite,
        onClick = onClick
    )
}

@Composable
fun ProposalCard(
    proposal: TaskVoteProposalEntity,
    isCurrentUsersProposal: Boolean,
    onVote: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isPending = proposal.status == "PENDING"
    val isDelete = proposal.type == "DELETE"
    val hasVoted = proposal.myVote != null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .printSurface(shape = RoundedCornerShape(PrintTokens.RadiusCard))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PrintChip(
                        text = if (isDelete) "Delete" else "New task",
                        fill = if (isDelete) PrintCoral else PrintLeaf
                    )
                    if (isCurrentUsersProposal) {
                        Text(
                            text = "Yours",
                            style = PrintType.Caption.copy(fontWeight = FontWeight.Bold),
                            color = Muted
                        )
                    }
                }

                StatusPill(status = proposal.status)
            }

            Text(
                text = if (isDelete) "Task #${proposal.taskId}" else (proposal.taskName ?: "Untitled Task"),
                style = PrintType.H3,
                color = PrintInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val detailText = if (isDelete) {
                proposal.deleteReason ?: "No deletion reason provided"
            } else {
                proposal.taskDescription ?: "No description"
            }

            Text(
                text = detailText,
                style = PrintType.Caption.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = Muted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${proposal.approveCount} of ${proposal.approvalThreshold} approvals · ${proposal.eligibleCount} members",
                        style = PrintType.Caption.copy(fontWeight = FontWeight.Bold),
                        color = PrintInk,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (proposal.rejectCount > 0) {
                        Text(
                            text = "${proposal.rejectCount} against",
                            style = PrintType.Caption.copy(fontWeight = FontWeight.Bold),
                            color = PrintRedDark
                        )
                    }
                }

                val progress = (proposal.approveCount.toFloat() / proposal.approvalThreshold.toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(PrintPaper)
                        .border(1.5.dp, PrintInk, RoundedCornerShape(percent = 50))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (progress >= 1f) PrintLeaf else PrintSky)
                            .border(1.5.dp, PrintInk, RoundedCornerShape(percent = 50))
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
                Text(
                    text = "Proposed " + dateFormat.format(Date(proposal.createdAt)),
                    style = PrintType.Caption,
                    color = Muted
                )

                if (isPending) {
                    val remainingHours = ((proposal.deadline - System.currentTimeMillis()) / (1000 * 3600)).coerceAtLeast(0)
                    Text(
                        text = "${remainingHours}h left",
                        style = PrintType.Caption.copy(fontWeight = FontWeight.Bold),
                        color = if (remainingHours < 6) PrintRedDark else PrintInk
                    )
                }
            }

            if (isPending) {
                if (hasVoted) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(PrintTokens.RadiusSmall))
                            .background(if (proposal.myVote == "APPROVE") PrintLeaf.copy(alpha = 0.35f) else PrintCoral.copy(alpha = 0.35f))
                            .border(1.5.dp, PrintInk, RoundedCornerShape(PrintTokens.RadiusSmall))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (proposal.myVote == "APPROVE") Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = PrintInk,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "You voted to ${proposal.myVote?.lowercase()}",
                            style = PrintType.Caption.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                            color = PrintInk
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        VoteButton(
                            text = "Approve",
                            icon = Icons.Default.ThumbUp,
                            fill = PrintLeaf,
                            onClick = { onVote("APPROVE") },
                            modifier = Modifier.weight(1f)
                        )
                        VoteButton(
                            text = "Reject",
                            icon = Icons.Default.ThumbDown,
                            fill = PrintCoral,
                            onClick = { onVote("REJECT") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoteButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    fill: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val press = rememberPrintPress(interactionSource)
    Row(
        modifier = modifier
            .height(PrintTokens.TouchTarget)
            .printSurface(shape = RoundedCornerShape(percent = 50), fill = fill, shadowOffset = 3.dp, pressProgress = press)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = PrintInk, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = PrintType.Label.copy(fontSize = 15.sp), color = PrintInk)
    }
}

@Composable
fun StatusPill(status: String) {
    val (fill, label) = when (status) {
        "APPROVED" -> PrintLeaf to "Approved"
        "REJECTED" -> PrintCoral to "Rejected"
        "EXPIRED" -> PrintPaper to "Expired"
        else -> PrintSky to "Voting"
    }
    PrintChip(text = label, fill = fill)
}

@Composable
fun RequestDeleteProposalDialog(
    taskName: String,
    onConfirm: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        com.collabsphere.app.view.components.WorkspaceDialogCard(
            icon = Icons.Default.HowToVote,
            accent = PrintCoral,
            title = "Ask to delete",
            subtitle = "Deleting “$taskName” needs a team vote. Tell everyone why.",
            onClose = onDismiss
        ) {
            SkeuoTextField(
                value = reason,
                onValueChange = { reason = it },
                label = "Reason",
                placeholder = "e.g. Duplicate task, plans changed",
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 3,
                maxLines = 5
            )
            Spacer(Modifier.height(18.dp))
            val canSubmit = reason.trim().isNotEmpty()
            com.collabsphere.app.view.components.WorkspaceDialogButton(
                text = "Start vote",
                colors = listOf(PrintInk),
                enabled = canSubmit,
                onClick = { if (canSubmit) onConfirm(reason.trim()) }
            )
            Spacer(Modifier.height(12.dp))
            PrintButton(
                text = "Cancel",
                onClick = onDismiss,
                primary = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
