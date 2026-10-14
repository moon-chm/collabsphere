package com.collabsphere.app.view.ProfileUI

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.collabsphere.app.dto.progression.BadgeEvidenceResponse
import com.collabsphere.app.dto.progression.CareerLevelDto
import com.collabsphere.app.dto.progression.LedgerEntryResponse
import com.collabsphere.app.ui.theme.AmberWarn
import com.collabsphere.app.ui.theme.CoralEnd
import com.collabsphere.app.ui.theme.CoralStart
import com.collabsphere.app.ui.theme.Destructive
import com.collabsphere.app.ui.theme.Ink
import com.collabsphere.app.ui.theme.MintGreen
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.ui.theme.SurfaceRaised
import com.collabsphere.app.ui.theme.skeuoFloatingCard
import com.collabsphere.app.viewmodel.progression.BadgeTierUi
import com.collabsphere.app.viewmodel.progression.BadgeUi
import com.collabsphere.app.viewmodel.progression.CareerUiState
import com.collabsphere.app.viewmodel.progression.EvidenceState
import com.collabsphere.app.viewmodel.progression.GitHubIdentityState
import com.collabsphere.app.viewmodel.progression.GitHubLinkEffect
import com.collabsphere.app.model.progression.GitHubIdentityEvents
import com.collabsphere.app.view.openInBrowser
import com.collabsphere.app.viewmodel.progression.HistoryState
import com.collabsphere.app.viewmodel.progression.ProgressionViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.text.NumberFormat
import java.text.DateFormat
import java.util.Date

// Tier colours come from the existing palette: coral-brown bronze, muted silver, amber gold.
private val BronzeColor = CoralEnd
private val SilverColor = Muted
private val GoldColor = AmberWarn

private fun tierColor(tier: BadgeTierUi?): Color = when (tier) {
    BadgeTierUi.BRONZE -> BronzeColor
    BadgeTierUi.SILVER -> SilverColor
    BadgeTierUi.GOLD -> GoldColor
    null -> Muted
}

private fun tierName(tier: BadgeTierUi): String = tier.name.lowercase().replaceFirstChar { it.uppercase() }

private fun formatSp(value: Long): String = NumberFormat.getIntegerInstance().format(value)

// java.text rather than java.time: minSdk is 24 and core-library desugaring is not enabled.
private fun formatDate(epochMs: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))

/** Families whose counter is calculated rather than built from listed activities. */
private val DERIVED_COUNTERS = setOf("STREAK_DAYS", "DISTINCT_WORKSPACES", "DISTINCT_BADGE_FAMILIES")

private fun badgeIcon(familyKey: String): ImageVector = when (familyKey) {
    "CODECRAFT" -> Icons.Default.Code
    "DEADLINE_ACE" -> Icons.Default.Timer
    "SYNERGY_STAR" -> Icons.Default.Groups
    "TASK_TITAN" -> Icons.Default.TaskAlt
    "MOMENTUM_KEEPER" -> Icons.Default.LocalFireDepartment
    "BUG_BUSTER" -> Icons.Default.BugReport
    "KNOWLEDGE_ARCHITECT" -> Icons.AutoMirrored.Filled.MenuBook
    "MILESTONE_MAVERICK" -> Icons.Default.Flag
    "GUARDIAN" -> Icons.Default.Shield
    "MERGE_MASTER" -> Icons.AutoMirrored.Filled.MergeType
    "BRIDGE_BUILDER" -> Icons.Default.Forum
    "MENTOR_SPARK" -> Icons.Default.VolunteerActivism
    "LAUNCH_COMMANDER" -> Icons.Default.RocketLaunch
    "SPHERE_EXPLORER" -> Icons.Default.Explore
    "SPHERE_LEGEND" -> Icons.Default.EmojiEvents
    else -> Icons.Default.MilitaryTech
}

private fun describeActivity(eventType: String?, referenceType: String?, referenceId: String?): String = when (eventType) {
    "TASK_COMPLETED" -> "Completed task #$referenceId"
    "NOTE_APPROVED" -> "Note #$referenceId pinned by a teammate"
    // GitHub references are "<repo id>#<number>"; the number is what people recognise.
    "GITHUB_PR_AUTHORED" -> "Merged pull request #${referenceId?.substringAfter('#')}"
    "GITHUB_PR_MERGED_BY" -> "Merged a teammate's pull request #${referenceId?.substringAfter('#')}"
    "GITHUB_REVIEW_APPROVED" -> "Approved pull request #${referenceId?.substringAfter('#')}"
    "GITHUB_ISSUE_RESOLVED" -> "Resolved issue #${referenceId?.substringAfter('#')}"
    "GITHUB_RELEASE_PUBLISHED" -> "Released $referenceId"
    "GITHUB_MILESTONE_COMPLETED" -> "Completed milestone $referenceId"
    "MESSAGE_MARKED_HELPFUL" -> "Message #$referenceId marked helpful"
    "PEER_RECOGNITION" -> "Recognised by a new teammate (message #$referenceId)"
    null -> "Adjustment"
    else -> "${referenceType?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Activity"} #$referenceId"
}

@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Career progression and achievements for the signed-in user. Everything shown is server-confirmed
 * (cached in Room for offline use); celebrations appear once per device and never block the screen.
 */
@Composable
fun CareerSection(userId: Int, snackbarHostState: SnackbarHostState) {
    if (userId <= 0) return
    val viewModel: ProgressionViewModel = koinViewModel(key = "progression-$userId") { parametersOf(userId) }
    val state by viewModel.uiState.collectAsState()
    val pending by viewModel.pendingFeedback.collectAsState()
    val history by viewModel.history.collectAsState()
    val evidence by viewModel.evidence.collectAsState()

    var showRoadmap by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var selectedBadgeKey by rememberSaveable { mutableStateOf<String?>(null) }

    val gitHubIdentity by viewModel.gitHubIdentity.collectAsState()
    val identityResult by GitHubIdentityEvents.result.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(userId) {
        viewModel.refresh(force = true)
        viewModel.loadGitHubIdentity()
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is GitHubLinkEffect.OpenUrl -> openInBrowser(context, effect.url)
                is GitHubLinkEffect.Message -> snackbarHostState.showSnackbar(effect.text)
            }
        }
    }
    // The OAuth redirect lands in MainActivity; report its outcome here once.
    LaunchedEffect(identityResult) {
        val result = identityResult ?: return@LaunchedEffect
        GitHubIdentityEvents.consume()
        viewModel.loadGitHubIdentity()
        snackbarHostState.showSnackbar(GitHubIdentityEvents.message(result))
    }

    // One celebration at a time; acknowledged only after it was actually shown, so leaving the screen
    // mid-snackbar shows it again later instead of losing it, and replays never repeat it.
    val nextFeedback = pending.firstOrNull()
    LaunchedEffect(nextFeedback) {
        if (nextFeedback != null) {
            snackbarHostState.showSnackbar(nextFeedback.message, duration = SnackbarDuration.Short)
            viewModel.acknowledge(nextFeedback)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CareerProgressCard(
            state = state,
            onRetry = { viewModel.refresh(force = true) },
            onOpenRoadmap = { showRoadmap = true },
            onOpenHistory = {
                showHistory = true
                if (!history.loadedOnce) viewModel.loadMoreHistory()
            }
        )
        AchievementsCard(
            state = state,
            onBadgeClick = { badge ->
                selectedBadgeKey = badge.family.key
                if (badge.family.counter !in DERIVED_COUNTERS) viewModel.loadEvidence(badge.family.key)
            }
        )
        GitHubLinkCard(
            state = gitHubIdentity,
            onLink = viewModel::startGitHubLink,
            onUnlink = viewModel::unlinkGitHub,
            onRetry = viewModel::loadGitHubIdentity
        )
    }

    if (showRoadmap) {
        CareerRoadmapSheet(state = state, onDismiss = { showRoadmap = false })
    }
    if (showHistory) {
        SpHistorySheet(
            history = history,
            ruleDescriptions = state.ruleDescriptions,
            onLoadMore = viewModel::loadMoreHistory,
            onDismiss = { showHistory = false }
        )
    }
    val selectedBadge = state.badges.firstOrNull { it.family.key == selectedBadgeKey }
    if (selectedBadge != null) {
        BadgeDetailSheet(
            badge = selectedBadge,
            evidence = evidence,
            onDismiss = {
                selectedBadgeKey = null
                viewModel.clearEvidence()
            }
        )
    }
}

@Composable
internal fun CareerProgressCard(
    state: CareerUiState,
    onRetry: () -> Unit,
    onOpenRoadmap: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val reduceMotion = rememberReduceMotion()
    val progress by animateFloatAsState(
        targetValue = state.levelProgress,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 600),
        label = "careerProgress"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .skeuoFloatingCard(cornerRadius = 20.dp)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = CoralStart, modifier = Modifier.size(20.dp))
            Text(
                text = "Career",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Ink,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            if (state.isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp).semantics { contentDescription = "Updating career progress" },
                    strokeWidth = 2.dp,
                    color = CoralStart
                )
            }
        }

        val summary = state.summary
        when {
            summary == null && state.refreshFailed -> {
                Text(
                    text = "Your career progress hasn't loaded yet. Check your connection and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
                TextButton(onClick = onRetry) { Text("Retry", color = CoralStart, fontWeight = FontWeight.Bold) }
            }
            summary == null -> Text(
                text = "Loading your career…",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
            else -> {
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Ink
                )
                Text(
                    text = "Level ${summary.level} of ${summary.maxLevel} · ${formatSp(summary.totalSp)} SP",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
                val progressDescription = if (summary.isMaxLevel) {
                    "Maximum level reached"
                } else {
                    "${formatSp(summary.spIntoLevel)} of ${formatSp(summary.spSpanToNext)} SP toward ${summary.nextTitle}"
                }
                LinearProgressIndicator(
                    progress = { progress },
                    color = CoralStart,
                    trackColor = Muted.copy(alpha = 0.15f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .semantics { contentDescription = progressDescription }
                )
                Text(
                    text = if (summary.isMaxLevel) {
                        "Top designation reached — every SP still adds to your total."
                    } else {
                        "${formatSp(summary.spSpanToNext - summary.spIntoLevel)} SP to ${summary.nextTitle} " +
                            "(${formatSp(summary.nextLevelRequiredSp ?: 0)} SP)"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CareerPill(
                        text = "${summary.currentStreakDays}-day streak",
                        color = if (summary.currentStreakDays > 0) CoralStart else Muted
                    )
                    CareerPill(text = "Best ${summary.bestStreakDays} days", color = Muted)
                }
                if (state.refreshFailed) {
                    Text(
                        text = "Showing saved progress — couldn't reach the server.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onOpenRoadmap, enabled = state.levels.isNotEmpty()) {
                Text("Career roadmap", color = CoralStart, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onOpenHistory, enabled = state.hasData) {
                Text("SP history", color = CoralStart, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun AchievementsCard(state: CareerUiState, onBadgeClick: (BadgeUi) -> Unit) {
    val earned = state.badges.count { !it.isLocked }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .skeuoFloatingCard(cornerRadius = 20.dp)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = AmberWarn, modifier = Modifier.size(20.dp))
            Text(
                text = "Achievements",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Ink,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            if (state.badges.isNotEmpty()) {
                Text(
                    text = "$earned / ${state.badges.size} earned",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Muted
                )
            }
        }
        if (state.badges.isEmpty()) {
            Text(
                text = if (state.refreshFailed) "Achievements will appear once your career loads." else "Loading achievements…",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
        } else {
            state.badges.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { badge -> BadgeTile(badge, onClick = { onBadgeClick(badge) }, modifier = Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun BadgeTile(badge: BadgeUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tier = badge.highestTier
    val accent = tierColor(tier)
    val status = when {
        !badge.family.available -> "Coming soon"
        tier == BadgeTierUi.GOLD -> "Gold"
        badge.nextThreshold != null -> "${formatSp(badge.counterValue)} / ${formatSp(badge.nextThreshold)}"
        else -> tier?.let(::tierName) ?: "Locked"
    }
    val description = buildString {
        append(badge.family.name)
        append(", ")
        append(tier?.let { "${tierName(it)} tier earned" } ?: "locked")
        if (!badge.family.available) append(", coming soon")
        else if (badge.nextThreshold != null && badge.nextTier != null) {
            append(", ${badge.counterValue} of ${badge.nextThreshold} toward ${tierName(badge.nextTier)}")
        }
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (tier != null) accent.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.03f))
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (tier != null) 0.18f else 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (badge.family.available) badgeIcon(badge.family.key) else Icons.Default.Lock,
                contentDescription = null,
                tint = if (tier != null) accent else Muted.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = badge.family.name,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (badge.family.available) Ink else Muted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(text = status, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            BadgeTierUi.entries.forEach { t ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (t in badge.unlockedAt) tierColor(t) else Muted.copy(alpha = 0.2f))
                )
            }
        }
    }
}

@Composable
private fun CareerPill(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CareerRoadmapSheet(state: CareerUiState, onDismiss: () -> Unit) {
    val currentLevel = state.summary?.level ?: 1
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceRaised
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Career roadmap",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Ink,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Designations are CollabSphere achievements. They don't change your permissions in any workspace.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            }
            items(state.levels, key = { it.level }) { level -> RoadmapRow(level, currentLevel) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun RoadmapRow(level: CareerLevelDto, currentLevel: Int) {
    val reached = level.level <= currentLevel
    val current = level.level == currentLevel
    val stateLabel = when {
        current -> "current"
        reached -> "reached"
        else -> "locked"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (current) CoralStart.copy(alpha = 0.10f) else Color.Transparent)
            .clearAndSetSemantics {
                contentDescription = "Level ${level.level}, ${level.title}, ${formatSp(level.requiredSp)} SP, $stateLabel"
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (reached) CoralStart.copy(alpha = 0.18f) else Muted.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = level.level.toString(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (reached) CoralStart else Muted
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = level.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (current) FontWeight.Bold else FontWeight.Medium),
                color = if (reached) Ink else Muted
            )
            Text(text = "${formatSp(level.requiredSp)} SP", style = MaterialTheme.typography.labelSmall, color = Muted)
        }
        if (reached && !current) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MintGreen, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpHistorySheet(
    history: HistoryState,
    ruleDescriptions: Map<String, String>,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceRaised
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text(
                    text = "Sphere Points history",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Ink,
                    modifier = Modifier.padding(bottom = 8.dp).semantics { heading() }
                )
            }
            if (history.loadedOnce && history.entries.isEmpty()) {
                item {
                    Text(
                        text = "No Sphere Points yet. Complete a task a teammate assigned to you to earn your first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted
                    )
                }
            }
            items(history.entries, key = { it.id }) { entry -> HistoryRow(entry, ruleDescriptions) }
            item {
                when {
                    history.isLoading -> CircularProgressIndicator(
                        modifier = Modifier.padding(12.dp).size(20.dp),
                        strokeWidth = 2.dp,
                        color = CoralStart
                    )
                    history.loadFailed -> TextButton(onClick = onLoadMore) {
                        Text("Couldn't load history — retry", color = CoralStart)
                    }
                    history.canLoadMore -> TextButton(onClick = onLoadMore) {
                        Text("Load more", color = CoralStart, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: LedgerEntryResponse, ruleDescriptions: Map<String, String>) {
    val label = when {
        entry.kind == "CORRECTION" -> entry.reason ?: "Adjustment"
        else -> describeActivity(entry.eventType, entry.referenceType, entry.referenceId)
    }
    val detail = entry.ruleKey?.let { ruleDescriptions[it] }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.03f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOfNotNull(detail, formatDate(entry.createdAt)).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = Muted
            )
        }
        Text(
            text = (if (entry.amount >= 0) "+" else "") + "${entry.amount} SP",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = if (entry.amount >= 0) MintGreen else Destructive
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BadgeDetailSheet(badge: BadgeUi, evidence: EvidenceState, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceRaised
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(tierColor(badge.highestTier).copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(badgeIcon(badge.family.key), contentDescription = null, tint = tierColor(badge.highestTier))
                    }
                    Column {
                        Text(
                            text = badge.family.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Ink,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(text = badge.family.theme, style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
                Text(
                    text = badge.family.requirement,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (!badge.family.available) {
                    Text(
                        text = "This badge becomes earnable in a later update.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted
                    )
                }
            }
            items(BadgeTierUi.entries) { tier -> TierRow(badge, tier) }
            if (badge.family.counter !in DERIVED_COUNTERS) {
                item {
                    Text(
                        text = "Recent qualifying activity",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Ink,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                when (evidence) {
                    EvidenceState.Idle, EvidenceState.Loading -> item {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = CoralStart)
                    }
                    EvidenceState.Failed -> item {
                        Text("Couldn't load activity right now.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    is EvidenceState.Loaded -> if (evidence.events.isEmpty()) {
                        item { Text("Nothing yet.", style = MaterialTheme.typography.bodySmall, color = Muted) }
                    } else {
                        items(evidence.events) { event -> EvidenceRow(event) }
                    }
                }
            } else {
                item {
                    Text(
                        text = "Calculated from your overall activity rather than individual items.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun TierRow(badge: BadgeUi, tier: BadgeTierUi) {
    val unlockedAt = badge.unlockedAt[tier]
    val threshold = badge.threshold(tier)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (unlockedAt != null) tierColor(tier).copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.03f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(tierColor(tier)))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${tierName(tier)} · ${formatSp(threshold)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Ink
            )
            Text(
                text = when {
                    unlockedAt != null -> "Unlocked ${formatDate(unlockedAt)}"
                    !badge.family.available -> "Not yet earnable"
                    else -> "${formatSp(badge.counterValue.coerceAtMost(threshold))} / ${formatSp(threshold)}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Muted
            )
        }
        if (unlockedAt != null) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Unlocked", tint = MintGreen, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EvidenceRow(event: BadgeEvidenceResponse) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = describeActivity(event.eventType, event.referenceType, event.referenceId),
            style = MaterialTheme.typography.bodySmall,
            color = Ink,
            modifier = Modifier.weight(1f)
        )
        Text(text = formatDate(event.occurredAt), style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

/**
 * Identity-only GitHub link: lets merged pull requests, reviews, issues, releases and milestones count
 * toward the user's career. GitHub verifies the account; no repository access is requested here.
 */
@Composable
internal fun GitHubLinkCard(
    state: GitHubIdentityState,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .skeuoFloatingCard(cornerRadius = 20.dp)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Code, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
            Text(
                text = "GitHub contributions",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Ink,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
        }
        when (state) {
            GitHubIdentityState.Unknown, GitHubIdentityState.Loading -> Text(
                text = "Checking your GitHub link…",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
            GitHubIdentityState.Failed -> {
                Text("Couldn't check your GitHub link.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                TextButton(onClick = onRetry) { Text("Retry", color = CoralStart, fontWeight = FontWeight.Bold) }
            }
            is GitHubIdentityState.Linked -> {
                Text(
                    text = "Linked as @${state.login ?: "GitHub"}. Merged pull requests, approved reviews, resolved issues, releases and milestones in your workspaces' repositories earn Sphere Points.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
                TextButton(onClick = onUnlink) { Text("Unlink GitHub", color = Destructive, fontWeight = FontWeight.SemiBold) }
            }
            GitHubIdentityState.NotLinked -> {
                Text(
                    text = "Link your GitHub account so your merged work in your workspaces' repositories counts toward your career.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
                TextButton(onClick = onLink) { Text("Link GitHub", color = CoralStart, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
