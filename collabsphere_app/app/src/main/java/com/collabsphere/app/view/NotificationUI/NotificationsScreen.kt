package com.collabsphere.app.view.NotificationUI

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.collabsphere.app.dto.notification.NotificationResponse
import com.collabsphere.app.ui.theme.*
import com.collabsphere.app.viewmodel.NotificationsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    onBack: () -> Unit,
    onNotificationClick: (NotificationResponse) -> Unit
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.loadNotifications() }

    LaunchedEffect(statusMessage) {
        if (statusMessage.isNotEmpty()) {
            snackbarHostState.showSnackbar(statusMessage)
            viewModel.clearStatus()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .drawWithCache {
                        onDrawBehind {
                        drawRect(color = SurfaceRaised)
                        drawRect(color = Color.White.copy(alpha = 0.85f), topLeft = Offset(0f, 0f), size = Size(size.width, 1.dp.toPx()))
                        }
                    }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Notifications",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Ink
                        )
                    }
                    if (notifications.isNotEmpty()) {
                        Row {
                            Text(
                                text = "Mark all read",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = CoralStart,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { viewModel.onMarkAllRead() }
                                    .padding(8.dp)
                            )
                            Text(
                                text = "Clear all",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Destructive,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { viewModel.onClearAll() }
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading && notifications.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = CoralStart)
                }
                notifications.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = null, tint = Muted, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("You're all caught up", style = MaterialTheme.typography.titleMedium, color = Muted)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(notifications, key = { it.id }) { notification ->
                            NotificationRow(
                                notification = notification,
                                onClick = { onNotificationClick(notification) },
                                onDelete = { viewModel.onDelete(notification.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun iconFor(type: String): ImageVector = when (type) {
    "MENTION" -> Icons.Default.AlternateEmail
    "TASK_ASSIGNED", "TASK_UPDATED" -> Icons.AutoMirrored.Filled.Assignment
    else -> Icons.AutoMirrored.Filled.Chat
}

@Composable
private fun NotificationRow(
    notification: NotificationResponse,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .skeuoFloatingCard(cornerRadius = 18.dp, surfaceColor = if (notification.isRead) Surface else SurfaceRaised)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (notification.isRead) Surface else CoralStart.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconFor(notification.type),
                    contentDescription = null,
                    tint = if (notification.isRead) Muted else CoralStart,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (notification.isRead) FontWeight.Medium else FontWeight.Bold
                    ),
                    color = Ink,
                    maxLines = 1
                )
                Text(
                    text = notification.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = 2
                )
                Text(
                    text = formatRelativeTime(notification.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted
                )
            }

            if (!notification.isRead) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(CoralStart))
                Spacer(Modifier.width(8.dp))
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Muted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun formatRelativeTime(timestampMillis: Long): String {
    val diffMs = (System.currentTimeMillis() - timestampMillis).coerceAtLeast(0)
    val minutes = diffMs / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}
