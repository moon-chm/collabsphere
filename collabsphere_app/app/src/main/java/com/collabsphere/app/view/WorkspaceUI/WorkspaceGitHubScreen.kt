package com.collabsphere.app.view.WorkspaceUI

import com.collabsphere.app.view.openInBrowser
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.ui.text.style.TextOverflow
import com.collabsphere.app.viewmodel.GitHubChannelOption
import com.collabsphere.app.viewmodel.GitHubDailyCount
import com.collabsphere.app.viewmodel.GitHubLinkedRepo
import androidx.compose.foundation.horizontalScroll
import com.collabsphere.app.viewmodel.GitHubContributorStats
import com.collabsphere.app.view.UserUI.resolveAvatarUrl
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import com.collabsphere.app.viewmodel.GitHubPullRequestItem
import com.collabsphere.app.viewmodel.AvailableRepo
import com.collabsphere.app.viewmodel.GitHubAuthEvents
import com.collabsphere.app.viewmodel.GitHubViewModel
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.entry.entryModelOf

@Composable
fun WorkspaceGitHubScreen(
    workspaceId: Int,
    viewModel: GitHubViewModel
) {
    val context = LocalContext.current
    val analytics by viewModel.analytics.collectAsState()
    val availableRepos by viewModel.availableRepos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLinking by viewModel.isLinking.collectAsState()
    val isLoadingRepos by viewModel.isLoadingRepos.collectAsState()
    val isPickingRepo by viewModel.isPickingRepo.collectAsState()
    val error by viewModel.error.collectAsState()
    val authResult by GitHubAuthEvents.result.collectAsState()
    val pullRequestList by viewModel.pullRequestList.collectAsState()
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showRemoveRepoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(workspaceId) {
        viewModel.resetForWorkspace()
        viewModel.loadAnalytics(workspaceId)
    }

    LaunchedEffect(authResult) {
        val result = authResult ?: return@LaunchedEffect
        if (result.workspaceId != null && result.workspaceId != workspaceId) return@LaunchedEffect
        GitHubAuthEvents.consume()
        result.error?.let { viewModel.reportAuthError(it) }
        viewModel.loadAnalytics(workspaceId)
    }

    LaunchedEffect(error) {
        val message = error ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.clearError()
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect GitHub?") },
            text = { Text("The linked repository and its synced commits and pull requests will be removed from this workspace.") },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectDialog = false
                    viewModel.disconnectGitHub(workspaceId)
                }) {
                    Text("Disconnect", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRemoveRepoDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveRepoDialog = false },
            title = { Text("Remove ${analytics?.repositoryName ?: "repository"}?") },
            text = { Text("Its synced commits, pull requests and issues will be removed from this workspace. Other linked repositories stay connected.") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveRepoDialog = false
                    viewModel.removeSelectedRepo(workspaceId)
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveRepoDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F6F0))
            .padding(16.dp)
    ) {
        val canManage = analytics?.canManage == true
        val isConnected = analytics?.isConnected == true
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (canManage && (isPickingRepo || (!isConnected && analytics?.hasConnection == true))) {
            val linkedNames = analytics?.repositories.orEmpty().map { it.fullName }.toSet()
            RepoPickerView(
                repos = availableRepos.filter { it.fullName !in linkedNames },
                isLinking = isLinking,
                isLoadingRepos = isLoadingRepos,
                onRepoSelected = { repo -> viewModel.linkRepo(workspaceId, repo) },
                onRefresh = { viewModel.loadAvailableRepos(workspaceId) },
                onCancel = if (isConnected) ({ viewModel.cancelAddRepo() }) else null,
                onDisconnect = { showDisconnectDialog = true }
            )
        } else if (isConnected && pullRequestList != null) {
            PullRequestListView(
                state = pullRequestList!!,
                onBack = { viewModel.closePullRequests() },
                onFilterSelected = { viewModel.setPullRequestFilter(workspaceId, it) },
                onLoadMore = { viewModel.loadMorePullRequests(workspaceId) }
            )
        } else if (isConnected) {
            ConnectedView(
                analytics = analytics!!,
                canManage = canManage,
                onAddRepo = { viewModel.startAddRepo(workspaceId) },
                onRemoveRepo = { showRemoveRepoDialog = true },
                onRepoSelected = { viewModel.selectRepo(workspaceId, it) },
                onSync = { viewModel.syncNow(workspaceId) },
                onNotifyChannelSelected = { viewModel.setNotifyChannel(workspaceId, it) },
                onViewAllPullRequests = { viewModel.openPullRequests(workspaceId) },
                onDisconnect = { showDisconnectDialog = true }
            )
        } else if (analytics != null && !canManage) {
            OwnerOnlyView()
        } else {
            NotConnectedView(
                onConnect = {
                    viewModel.startInstall(workspaceId) { url ->
                        openInBrowser(context, url)
                    }
                }
            )
        }
    }
}

@Composable
private fun OwnerOnlyView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Code,
            contentDescription = "GitHub",
            modifier = Modifier.size(64.dp),
            tint = Color(0xFF4F423F)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No repository linked",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2C2A28)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Only the workspace owner can connect GitHub and link a repository.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF70625E),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun NotConnectedView(onConnect: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Code,
            contentDescription = "GitHub",
            modifier = Modifier.size(64.dp),
            tint = Color(0xFF4F423F)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Connect GitHub",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2C2A28)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Link a repository to this workspace to track commits, PRs, and branch activity directly from CollabSphere.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF70625E),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onConnect,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2A28))
        ) {
            Icon(imageVector = Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Connect to GitHub")
        }
    }
}

@Composable
private fun RepoPickerView(
    repos: List<AvailableRepo>,
    isLinking: Boolean,
    isLoadingRepos: Boolean,
    onRepoSelected: (AvailableRepo) -> Unit,
    onRefresh: () -> Unit,
    onCancel: (() -> Unit)?,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Select a Repository",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2C2A28)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "GitHub is connected! Choose which repository to link to this workspace.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF70625E)
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (isLinking || isLoadingRepos) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (repos.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No repositories found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF70625E)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Make sure you gave the GitHub App access to at least one repository.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9E8E89),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onRefresh) {
                        Text("Refresh")
                    }
                }
            }
        } else {
            repos.forEach { repo ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable(enabled = !isLinking) { onRepoSelected(repo) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (repo.isPrivate) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = Color(0xFF70625E),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = repo.fullName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = Color(0xFF2C2A28)
                            )
                            Text(
                                text = "Branch: ${repo.defaultBranch}",
                                fontSize = 12.sp,
                                color = Color(0xFF9E8E89)
                            )
                        }
                        Text(
                            text = "Link",
                            color = Color(0xFF2C2A28),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        if (onCancel != null) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !isLinking,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", fontSize = 13.sp, color = Color(0xFF2C2A28))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        TextButton(
            onClick = onDisconnect,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Disconnect GitHub Account", color = Color(0xFF9E8E89), fontSize = 13.sp)
        }
    }
}

@Composable
private fun ConnectedView(
    analytics: com.collabsphere.app.viewmodel.GitHubAnalyticsResponse,
    canManage: Boolean,
    onAddRepo: () -> Unit,
    onRemoveRepo: () -> Unit,
    onRepoSelected: (Int) -> Unit,
    onSync: () -> Unit,
    onNotifyChannelSelected: (Int?) -> Unit,
    onViewAllPullRequests: () -> Unit,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = analytics.repositoryName ?: "GitHub Activity",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C2A28)
                )
                if (analytics.isSyncing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Syncing with GitHub…", fontSize = 12.sp, color = Color(0xFF9E8E89))
                    }
                } else {
                    analytics.lastSyncedAt?.let {
                        Text(
                            text = "Updated ${relativeTime(it)}",
                            fontSize = 12.sp,
                            color = Color(0xFF9E8E89)
                        )
                    }
                }
            }
            TextButton(onClick = onSync, enabled = !analytics.isSyncing) {
                Text("Sync", color = Color(0xFF2C2A28))
            }
            analytics.repositoryUrl?.let { url ->
                TextButton(onClick = { openInBrowser(context, url) }) {
                    Text("Open", color = Color(0xFF2C2A28))
                }
            }
        }

        if (analytics.repositories.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            RepoSwitcher(
                repositories = analytics.repositories,
                selectedId = analytics.repositoryId,
                onSelected = onRepoSelected
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (analytics.commitActivity.isNotEmpty()) {
            val counts = analytics.commitActivity
            val chartEntryModel = remember(counts) {
                entryModelOf(*counts.map<GitHubDailyCount, Number> { it.count }.toTypedArray())
            }
            val dayLabels = remember(counts) { counts.map { it.date.takeLast(2).trimStart('0') } }
            val bottomAxisFormatter = remember(dayLabels) {
                AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
                    dayLabels.getOrNull(value.toInt()) ?: ""
                }
            }

            SectionCard(title = "Commits, last ${counts.size} days") {
                ProvideChartStyle(m3ChartStyle()) {
                    Chart(
                        chart = columnChart(),
                        model = chartEntryModel,
                        startAxis = rememberStartAxis(),
                        bottomAxis = rememberBottomAxis(valueFormatter = bottomAxisFormatter),
                        modifier = Modifier.height(180.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard("Commits", analytics.totalCommits.toString(), Modifier.weight(1f))
            StatCard("Open PRs", analytics.openPullRequests.toString(), Modifier.weight(1f))
            StatCard("Merged", analytics.mergedPullRequests.toString(), Modifier.weight(1f))
        }

        if (analytics.topContributors.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            SectionCard(title = "Top contributors") {
                analytics.topContributors.forEach { contributor ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ContributorAvatar(contributor)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = contributor.username,
                                fontSize = 14.sp,
                                color = Color(0xFF2C2A28),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val githubName = contributor.githubName
                            if (contributor.memberUserId != null && githubName != null && githubName != contributor.username) {
                                Text(
                                    text = githubName,
                                    fontSize = 12.sp,
                                    color = Color(0xFF9E8E89),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = "${contributor.commits} commits",
                            fontSize = 13.sp,
                            color = Color(0xFF70625E)
                        )
                    }
                }
            }
        }

        if (analytics.recentPullRequests.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            SectionCard(
                title = "Recent pull requests",
                action = {
                    TextButton(onClick = onViewAllPullRequests) {
                        Text("View all", color = Color(0xFF2C2A28), fontSize = 13.sp)
                    }
                }
            ) {
                analytics.recentPullRequests.forEach { pr ->
                    val (statusLabel, statusColor) = pullRequestStatus(pr)
                    ActivityRow(
                        title = "#${pr.number} ${pr.title}",
                        subtitle = "${pr.authorUsername.ifBlank { "Unknown" }} · ${relativeTime(pr.createdAt)}",
                        badge = statusLabel,
                        badgeColor = statusColor,
                        url = pr.url,
                        ci = pr.ciStatus
                    )
                }
            }
        }

        if (analytics.recentIssues.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            SectionCard(title = "Issues (${analytics.openIssues} open)") {
                analytics.recentIssues.forEach { issue ->
                    ActivityRow(
                        title = "#${issue.number} ${issue.title}",
                        subtitle = "${issue.authorUsername.ifBlank { "Unknown" }} · ${relativeTime(issue.createdAt)}",
                        badge = if (issue.state == "open") "Open" else "Closed",
                        badgeColor = if (issue.state == "open") Color(0xFF2DA44E) else Color(0xFF6F42C1),
                        url = issue.url
                    )
                }
            }
        }

        if (analytics.recentCommits.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            SectionCard(title = "Recent commits") {
                analytics.recentCommits.forEach { commit ->
                    ActivityRow(
                        title = commit.message.ifBlank { commit.sha.take(7) },
                        subtitle = "${commit.authorName ?: "Unknown"} · ${relativeTime(commit.commitDate)}",
                        badge = commit.sha.take(7),
                        badgeColor = Color(0xFF70625E),
                        url = commit.url,
                        ci = commit.ciStatus
                    )
                }
            }
        }

        if (canManage) {
            Spacer(modifier = Modifier.height(16.dp))
            NotifyChannelPicker(
                channels = analytics.channels,
                selectedId = analytics.notifyChannelId,
                onSelected = onNotifyChannelSelected
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onAddRepo,
                    enabled = analytics.repositories.size < MAX_LINKED_REPOS,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Add Repo", fontSize = 13.sp, color = Color(0xFF2C2A28))
                }
                OutlinedButton(
                    onClick = onRemoveRepo,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Remove Repo", fontSize = 13.sp, color = Color(0xFF2C2A28))
                }
            }
            TextButton(
                onClick = onDisconnect,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Disconnect GitHub", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private const val MAX_LINKED_REPOS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoSwitcher(
    repositories: List<GitHubLinkedRepo>,
    selectedId: Int?,
    onSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repositories.forEach { repo ->
            FilterChip(
                selected = repo.id == selectedId,
                onClick = { onSelected(repo.id) },
                label = { Text(repo.fullName.substringAfter('/')) }
            )
        }
    }
}

@Composable
private fun ContributorAvatar(contributor: GitHubContributorStats) {
    val avatarUrl = contributor.avatarUrl
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(0xFFEDE8DF)),
        contentAlignment = Alignment.Center
    ) {
        val initial: @Composable () -> Unit = {
            Text(
                text = contributor.username.take(1).uppercase(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF70625E)
            )
        }
        if (avatarUrl != null) {
            SubcomposeAsyncImage(
                model = resolveAvatarUrl(avatarUrl),
                contentDescription = contributor.username,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                loading = { initial() },
                error = { initial() }
            )
        } else {
            initial()
        }
    }
}

@Composable
private fun NotifyChannelPicker(
    channels: List<GitHubChannelOption>,
    selectedId: Int?,
    onSelected: (Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = channels.firstOrNull { it.id == selectedId }?.name

    SectionCard(title = "Post GitHub updates to") {
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = channels.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = when {
                        channels.isEmpty() -> "Create a channel first"
                        selectedName != null -> "#$selectedName"
                        else -> "Off"
                    },
                    color = Color(0xFF2C2A28)
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Off") },
                    onClick = {
                        expanded = false
                        onSelected(null)
                    }
                )
                channels.forEach { channel ->
                    DropdownMenuItem(
                        text = { Text("#${channel.name}") },
                        onClick = {
                            expanded = false
                            onSelected(channel.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Medium, color = Color(0xFF70625E), modifier = Modifier.weight(1f))
            action?.invoke()
        }
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

private fun ciIndicator(ci: String?): Pair<String, Color>? = when (ci) {
    "success" -> "✓" to Color(0xFF2DA44E)
    "failure" -> "✗" to Color(0xFFCF222E)
    "pending" -> "●" to Color(0xFFBF8700)
    else -> null
}

internal fun pullRequestStatus(pr: GitHubPullRequestItem): Pair<String, Color> = when {
    pr.mergedAt != null -> "Merged" to Color(0xFF6F42C1)
    pr.state == "open" -> "Open" to Color(0xFF2DA44E)
    else -> "Closed" to Color(0xFFCF222E)
}

@Composable
internal fun ActivityRow(
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    url: String? = null,
    ci: String? = null
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = url != null) { url?.let { openInBrowser(context, it) } }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = Color(0xFF2C2A28),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color(0xFF9E8E89),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        ciIndicator(ci)?.let { (symbol, color) ->
            Text(text = symbol, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = badge,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = badgeColor
        )
    }
}

internal fun relativeTime(epochMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(epochMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = title, fontSize = 12.sp, color = Color(0xFF70625E), fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 24.sp, color = Color(0xFF2C2A28), fontWeight = FontWeight.Bold)
        }
    }
}
