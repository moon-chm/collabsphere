package com.collabsphere.app.view.WorkspaceUI

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.collabsphere.app.dto.workspace.ContributionCounts
import com.collabsphere.app.dto.workspace.MemberContribution
import com.collabsphere.app.dto.workspace.UnlinkedGitHubContributor
import com.collabsphere.app.dto.workspace.WorkspaceAnalyticsResponse
import com.collabsphere.app.ui.theme.*
import com.collabsphere.app.view.UserUI.resolveAvatarUrl
import com.collabsphere.app.view.components.CollabSpinner
import com.collabsphere.app.view.components.SkeuoEmptyState
import com.collabsphere.app.viewmodel.AnalyticsRange
import com.collabsphere.app.viewmodel.AnalyticsUiState
import com.collabsphere.app.viewmodel.WorkspaceAnalyticsViewModel

private val TaskColor = PrintCoral
private val NoteColor = PrintSky
private val FileColor = PrintLeaf
private val GitHubColor = PrintInk

private fun onFill(color: Color): Color = if (color == PrintInk) PrintPaper else PrintInk

@Composable
fun WorkspaceInsightsScreen(
    viewModel: WorkspaceAnalyticsViewModel,
    workspaceName: String,
    currentUserId: Int,
    onBack: () -> Unit
) {
    val range by viewModel.range.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrintPaper)
            .paperGrain()
            .statusBarsPadding()
    ) {
        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkeuoActionIconButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                size = 40.dp,
                iconSize = 20.dp,
                tint = Ink
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                MisregisteredTitle(text = "Insights", style = PrintType.H2)
                Text(
                    text = workspaceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val refreshing = (state as? AnalyticsUiState.Loaded)?.isRefreshing == true
            if (refreshing) {
                CollabSpinner(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            } else {
                SkeuoActionIconButton(
                    onClick = viewModel::refresh,
                    icon = Icons.Outlined.Refresh,
                    contentDescription = "Refresh",
                    size = 36.dp,
                    iconSize = 18.dp,
                    tint = CoralStart
                )
            }
        }

        RangeSelector(
            selected = range,
            onSelect = viewModel::onRangeChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        when (val s = state) {
            AnalyticsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CollabSpinner(modifier = Modifier.size(36.dp))
            }
            is AnalyticsUiState.Error -> SkeuoEmptyState(
                icon = Icons.Outlined.Insights,
                title = "Insights unavailable",
                description = s.message,
                actionLabel = "Try again",
                onAction = viewModel::refresh
            )
            is AnalyticsUiState.Loaded -> InsightsContent(
                data = s.data,
                range = range,
                currentUserId = currentUserId
            )
        }
    }
}

@Composable
private fun RangeSelector(
    selected: AnalyticsRange,
    onSelect: (AnalyticsRange) -> Unit,
    modifier: Modifier = Modifier
) {
    // Same carved-in track as the text fields, with the chosen range raised out of it.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .skeuoInset(cornerRadius = 24.dp)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnalyticsRange.entries.forEach { option ->
            val isSelected = option == selected
            val textColor by animateColorAsState(
                targetValue = if (isSelected) PrintPaper else PrintInk,
                animationSpec = tween(200),
                label = "rangeText"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(19.dp))
                    .then(
                        if (isSelected) Modifier.background(PrintInk)
                        else Modifier
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = androidx.compose.ui.semantics.Role.Tab
                    ) { onSelect(option) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = textColor,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun InsightsContent(
    data: WorkspaceAnalyticsResponse,
    range: AnalyticsRange,
    currentUserId: Int
) {
    val me = data.members.firstOrNull { it.userId == currentUserId }
    // Scales every member's bar against the busiest member, so bars compare across the team.
    val entranceRegistry = rememberEntranceRegistry()
    val maxTotal = data.members.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "totals") { TotalsCard(data.totals, data.hasGitHub, range) }

        if (data.hasGitHub && me != null && me.githubLogin == null) {
            item(key = "link-github") { LinkGitHubHint() }
        }

        item(key = "contributors-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Contributors",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Ink,
                    modifier = Modifier.weight(1f)
                )
                Legend(showGitHub = data.hasGitHub)
            }
        }

        itemsIndexed(data.members, key = { _, it -> "member-${it.userId}" }) { index, member ->
            MemberCard(
                modifier = Modifier.printListEntrance(member.userId, index, entranceRegistry),
                member = member,
                rank = index + 1,
                isMe = member.userId == currentUserId,
                maxTotal = maxTotal,
                showGitHub = data.hasGitHub
            )
        }

        if (data.unlinkedGitHub.isNotEmpty()) {
            item(key = "unlinked") { UnlinkedCard(data.unlinkedGitHub) }
        }

        item(key = "footnote") {
            Text(
                text = "Counts tasks, notes, files and GitHub work. Chat and direct messages are not included.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

// ── Totals ──

@Composable
private fun TotalsCard(totals: ContributionCounts, hasGitHub: Boolean, range: AnalyticsRange) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .skeuoFloatingCard(cornerRadius = 24.dp)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = (if (range == AnalyticsRange.ALL) "Workspace so far" else "Last ${range.label}").uppercase(),
            style = PrintType.Tiny,
            color = PrintInk
        )
        val tiles = buildList {
            add(StatTile(Icons.Outlined.CheckCircle, TaskColor, totals.tasksCompleted, "Tasks done"))
            add(StatTile(Icons.Outlined.CheckCircle, TaskColor, totals.tasksCreated, "Tasks created"))
            add(StatTile(Icons.Outlined.Description, NoteColor, totals.notesWritten, "Notes"))
            add(StatTile(Icons.Outlined.Folder, FileColor, totals.filesUploaded, "Files"))
            if (hasGitHub) {
                add(StatTile(Icons.Outlined.AccountTree, GitHubColor, totals.commits, "Commits"))
                add(StatTile(Icons.Outlined.AccountTree, GitHubColor, totals.pullRequestsMerged, "PRs merged"))
            }
        }
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile -> StatTileView(tile, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private data class StatTile(val icon: ImageVector, val color: Color, val value: Int, val label: String)

@Composable
private fun StatTileView(tile: StatTile, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PrintPaper)
            .border(1.5.dp, PrintInk, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .printSurface(shape = CircleShape, fill = tile.color, shadow = null, outline = 1.5.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(tile.icon, contentDescription = null, tint = onFill(tile.color), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = tile.value.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Ink
            )
            Text(
                text = tile.label,
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Members ──

@Composable
private fun Legend(showGitHub: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(TaskColor, "Tasks")
        LegendDot(NoteColor, "Notes")
        LegendDot(FileColor, "Files")
        if (showGitHub) LegendDot(GitHubColor, "GitHub")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color).border(1.dp, PrintInk, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

@Composable
private fun MemberCard(
    modifier: Modifier = Modifier,
    member: MemberContribution,
    rank: Int,
    isMe: Boolean,
    maxTotal: Int,
    showGitHub: Boolean
) {
    var expanded by rememberSaveable(member.userId) { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, tween(200), label = "memberArrow")
    val c = member.counts

    Column(
        modifier = modifier
            .fillMaxWidth()
            .skeuoFloatingCard(cornerRadius = 22.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { expanded = !expanded }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(member)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.username,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isMe) {
                        Spacer(Modifier.width(6.dp))
                        PrintChip(text = "You", fill = PrintCoral)
                    }
                }
                Spacer(Modifier.height(2.dp))
                if (showGitHub) {
                    Text(
                        text = member.githubLogin?.let { "@$it" } ?: "GitHub not linked",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (member.githubLogin != null) GitHubColor else Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = if (rank == 1 && member.total > 0) "Most active" else "#$rank",
                        style = MaterialTheme.typography.labelMedium,
                        color = Muted
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = member.total.toString(),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Ink
                )
                Text("contributions", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) "Hide details" else "Show details",
                tint = PrintInk,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp)
                    .rotate(arrowRotation)
            )
        }

        Spacer(Modifier.height(12.dp))
        ShareBar(counts = c, total = member.total, maxTotal = maxTotal, showGitHub = showGitHub)

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(180)) + expandVertically(),
            exit = fadeOut(tween(120)) + shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BreakdownRow(TaskColor, "Tasks completed", c.tasksCompleted)
                BreakdownRow(TaskColor, "Tasks created", c.tasksCreated)
                BreakdownRow(TaskColor, "Open tasks assigned", c.tasksOpen, isSnapshot = true)
                BreakdownRow(NoteColor, "Notes written", c.notesWritten)
                BreakdownRow(FileColor, "Files uploaded", c.filesUploaded, suffix = c.fileBytes.takeIf { it > 0 }?.let { formatBytes(it) })
                if (showGitHub) {
                    BreakdownRow(GitHubColor, "Commits", c.commits)
                    BreakdownRow(GitHubColor, "Pull requests opened", c.pullRequestsOpened)
                    BreakdownRow(GitHubColor, "Pull requests merged", c.pullRequestsMerged)
                    BreakdownRow(GitHubColor, "Issues opened", c.issuesOpened)
                }
            }
        }
    }
}

/**
 * The member's work split by kind. Its length is relative to the busiest member, so the bars rank
 * the team at a glance; the empty remainder stays a carved-in track.
 */
@Composable
private fun ShareBar(counts: ContributionCounts, total: Int, maxTotal: Int, showGitHub: Boolean) {
    val fill by animateFloatAsState(
        targetValue = if (total == 0) 0f else total.toFloat() / maxTotal,
        animationSpec = tween(700, easing = PrintTokens.SpringEasing),
        label = "shareFill"
    )
    val segments = listOfNotNull(
        TaskColor to counts.taskWork,
        NoteColor to counts.notesWritten,
        FileColor to counts.filesUploaded,
        if (showGitHub) GitHubColor to counts.gitHubWork else null
    ).filter { it.second > 0 }
    val segmentTotal = segments.sumOf { it.second }.coerceAtLeast(1)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(PrintPaper)
            .border(1.5.dp, PrintInk, RoundedCornerShape(7.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fill.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(7.dp))
        ) {
            segments.forEach { (color, value) ->
                Box(
                    Modifier
                        .weight(value.toFloat() / segmentTotal)
                        .fillMaxHeight()
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun BreakdownRow(color: Color, label: String, value: Int, isSnapshot: Boolean = false, suffix: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color).border(1.dp, PrintInk, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(
            text = label + if (isSnapshot) " (now)" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
            modifier = Modifier.weight(1f)
        )
        if (suffix != null) {
            Text(suffix, style = MaterialTheme.typography.labelMedium, color = Muted)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = if (value > 0) Ink else Muted
        )
    }
}

@Composable
private fun MemberAvatar(member: MemberContribution) {
    val initial: @Composable () -> Unit = {
        Text(
            text = member.username.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = PrintInk
        )
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .printSurface(shape = CircleShape, fill = PrintSky, shadow = null, outline = 1.5.dp),
        contentAlignment = Alignment.Center
    ) {
        val url = member.avatarUrl
        if (url != null) {
            SubcomposeAsyncImage(
                model = resolveAvatarUrl(url),
                contentDescription = member.username,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(CircleShape),
                loading = { initial() },
                error = { initial() }
            )
        } else {
            initial()
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

// ── GitHub linking ──

@Composable
private fun LinkGitHubHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PrintPaper)
            .border(1.5.dp, PrintInk, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.AccountTree, contentDescription = null, tint = GitHubColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Link your GitHub account from your Profile so your commits and pull requests count here.",
            style = MaterialTheme.typography.bodySmall,
            color = Ink
        )
    }
}

@Composable
private fun UnlinkedCard(people: List<UnlinkedGitHubContributor>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .skeuoFloatingCard(cornerRadius = 22.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LinkOff, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Unlinked GitHub activity",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Ink
            )
        }
        Text(
            text = "These GitHub accounts aren't linked to anyone here. If one is yours, link it from your Profile.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted
        )
        people.forEach { person ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = unlinkedSummary(person),
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted
                )
            }
        }
    }
}

private fun unlinkedSummary(p: UnlinkedGitHubContributor): String = listOfNotNull(
    p.commits.takeIf { it > 0 }?.let { "$it commit${if (it == 1) "" else "s"}" },
    (p.pullRequestsOpened + p.pullRequestsMerged).takeIf { it > 0 }?.let { "$it PR${if (it == 1) "" else "s"}" },
    p.issuesOpened.takeIf { it > 0 }?.let { "$it issue${if (it == 1) "" else "s"}" }
).joinToString(" · ")

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format("%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format("%.1f MB", bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> String.format("%.0f KB", bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}
