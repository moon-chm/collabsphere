package com.collabsphere.app.view.WorkspaceUI

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.collabsphere.app.ui.theme.SurfaceRaised
import com.collabsphere.app.viewmodel.GitHubViewModel
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.entry.entryModelOf

@Composable
fun WorkspaceGitHubScreen(
    workspaceId: Int,
    viewModel: GitHubViewModel
) {
    val context = LocalContext.current
    val analytics by viewModel.analytics.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(workspaceId) {
        viewModel.loadAnalytics(workspaceId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F6F0)) // Matching the skeuomorphic background color
            .padding(16.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (analytics?.isConnected != true) {
            // Not connected UI
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        val url = "https://collabsphere-server-qtke.onrender.com/auth/github/install?workspaceId=$workspaceId"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2A28))
                ) {
                    Icon(imageVector = Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect Repository")
                }
            }
        } else {
            // Connected UI - Analytics & Branches
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = analytics?.repositoryName ?: "GitHub Activity",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C2A28),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Vico Chart for Commits (Mocked data shape for visual purposes, bound to totalCommits)
                val chartEntryModel = entryModelOf(0, 1, 3, 2, analytics?.totalCommits ?: 5)
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(16.dp)
                ) {
                    Column {
                        Text("Commit Activity", fontWeight = FontWeight.Medium, color = Color(0xFF70625E))
                        Spacer(modifier = Modifier.height(8.dp))
                        ProvideChartStyle(m3ChartStyle()) {
                            Chart(
                                chart = lineChart(),
                                model = chartEntryModel,
                                startAxis = rememberStartAxis(),
                                bottomAxis = rememberBottomAxis(),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatCard("Total Commits", analytics?.totalCommits?.toString() ?: "0", Modifier.weight(1f))
                    StatCard("Open PRs", analytics?.openPullRequests?.toString() ?: "0", Modifier.weight(1f))
                    StatCard("Merged", analytics?.mergedPullRequests?.toString() ?: "0", Modifier.weight(1f))
                }
            }
        }
    }
}

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
