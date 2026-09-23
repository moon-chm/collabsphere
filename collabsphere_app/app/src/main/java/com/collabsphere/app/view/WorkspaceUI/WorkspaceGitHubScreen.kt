package com.collabsphere.app.view.WorkspaceUI

import android.content.Intent
import android.net.Uri
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
    var showDisconnectDialog by remember { mutableStateOf(false) }

    LaunchedEffect(workspaceId) {
        viewModel.cancelChangeRepo()
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
            RepoPickerView(
                repos = availableRepos,
                isLinking = isLinking,
                isLoadingRepos = isLoadingRepos,
                onRepoSelected = { repo -> viewModel.linkRepo(workspaceId, repo) },
                onRefresh = { viewModel.loadAvailableRepos(workspaceId) },
                onCancel = if (isConnected) ({ viewModel.cancelChangeRepo() }) else null,
                onDisconnect = { showDisconnectDialog = true }
            )
        } else if (isConnected) {
            ConnectedView(
                analytics = analytics!!,
                canManage = canManage,
                onChangeRepo = { viewModel.startChangeRepo(workspaceId) },
                onSync = { viewModel.syncNow(workspaceId) },
                onNotifyChannelSelected = { viewModel.setNotifyChannel(workspaceId, it) },
                onDisconnect = { showDisconnectDialog = true }
            )
        } else if (analytics != null && !canManage) {
            OwnerOnlyView()
        } else {
            NotConnectedView(
                onConnect = {
                    viewModel.startInstall(workspaceId) { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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
    onChangeRepo: () -> Unit,
    onSync: () -> Unit,
    onNotifyChannelSelected: (Int?) -> Unit,
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
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                    Text("Open", color = Color(0xFF2C2A28))
                }
            }
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
                        Text(
                            text = contributor.username,
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp,
                            color = Color(0xFF2C2A28),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
            SectionCard(title = "Recent pull requests") {
                analytics.recentPullRequests.forEach { pr ->
                    val (statusLabel, statusColor) = when {
                        pr.mergedAt != null -> "Merged" to Color(0xFF6F42C1)
                        pr.state == "open" -> "Open" to Color(0xFF2DA44E)
                        else -> "Closed" to Color(0xFFCF222E)
                    }
                    ActivityRow(
                        title = "#${pr.number} ${pr.title}",
                        subtitle = "${pr.authorUsername.ifBlank { "Unknown" }} · ${relativeTime(pr.createdAt)}",
                        badge = statusLabel,
                        badgeColor = statusColor
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
                        badgeColor = Color(0xFF70625E)
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
                    onClick = onChangeRepo,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Change Repo", fontSize = 13.sp, color = Color(0xFF2C2A28))
                }
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Disconnect", fontSize = 13.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
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
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(16.dp)
    ) {
        Text(title, fontWeight = FontWeight.Medium, color = Color(0xFF70625E))
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ActivityRow(title: String, subtitle: String, badge: String, badgeColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
        Text(
            text = badge,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = badgeColor
        )
    }
}

private fun relativeTime(epochMillis: Long): String =
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
