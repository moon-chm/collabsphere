package com.collabsphere.app.view.WorkspaceUI

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.collabsphere.app.ui.theme.fadingScrollEdges
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.collabsphere.app.view.components.CollabSpinner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.collabsphere.app.viewmodel.PullRequestFilter
import com.collabsphere.app.viewmodel.PullRequestListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PullRequestListView(
    state: PullRequestListState,
    onBack: () -> Unit,
    onFilterSelected: (PullRequestFilter) -> Unit,
    onLoadMore: () -> Unit
) {
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.collabsphere.app.ui.theme.SkeuoActionIconButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                size = 40.dp,
                iconSize = 20.dp,
                tint = com.collabsphere.app.ui.theme.PrintInk
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Pull requests",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = com.collabsphere.app.ui.theme.PrintInk
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PullRequestFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { if (state.filter != filter) onFilterSelected(filter) },
                    label = { Text(filter.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.isLoading) {
                    CollabSpinner()
                } else {
                    Text("No pull requests", color = com.collabsphere.app.ui.theme.Muted)
                }
            }
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .fadingScrollEdges(listState = listState, top = 20.dp, bottom = 48.dp)
            ) {
                items(state.items, key = { it.number }) { pr ->
                    val (statusLabel, statusColor) = pullRequestStatus(pr)
                    ActivityRow(
                        title = "#${pr.number} ${pr.title}",
                        subtitle = "${pr.displayAuthor} · ${relativeTime(pr.createdAt)}",
                        badge = statusLabel,
                        badgeColor = statusColor,
                        url = pr.url,
                        ci = pr.ciStatus
                    )
                }
                if (state.hasMore || state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.isLoading) {
                                CollabSpinner(modifier = Modifier.size(24.dp))
                            } else {
                                OutlinedButton(onClick = onLoadMore) {
                                    Text("Load more", color = com.collabsphere.app.ui.theme.PrintInk)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
