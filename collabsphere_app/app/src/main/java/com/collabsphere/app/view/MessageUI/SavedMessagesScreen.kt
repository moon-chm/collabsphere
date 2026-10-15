package com.collabsphere.app.view.MessageUI

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.collabsphere.app.dto.message.SavedMessageItem
import com.collabsphere.app.model.message.SavedMessagesStore
import com.collabsphere.app.ui.theme.*
import com.collabsphere.app.view.components.AppToast
import com.collabsphere.app.view.components.CollabSpinner
import com.collabsphere.app.view.components.SkeuoEmptyState
import com.collabsphere.app.view.components.chatDayLabel
import com.collabsphere.app.view.components.chatTime
import com.collabsphere.app.view.components.isSameChatDay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SavedMessagesScreen(
    onBack: () -> Unit,
    onOpen: (SavedMessageItem) -> Unit
) {
    val store = koinInject<SavedMessagesStore>()
    val items by store.items.collectAsStateWithLifecycle()
    val loaded by store.loaded.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { failed = store.refresh().isFailure }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
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
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Saved messages",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = Ink
                )
                Text(
                    text = if (items.isEmpty()) "Only you can see these" else "${items.size} saved · only you can see these",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }
        }

        when {
            !loaded && !failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CollabSpinner(modifier = Modifier.size(36.dp))
            }
            items.isEmpty() -> SkeuoEmptyState(
                icon = Icons.Default.Bookmark,
                title = if (failed) "Couldn't load saved messages" else "Nothing saved yet",
                description = if (failed) "Check your connection and try again."
                else "Long-press any message in a channel or DM and choose Save to keep it here.",
                actionLabel = if (failed) "Try again" else null,
                onAction = { scope.launch { failed = store.refresh().isFailure } }
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { "${it.kind}:${it.messageId}" }) { item ->
                    SavedMessageCard(
                        item = item,
                        onOpen = { onOpen(item) },
                        onUnsave = {
                            scope.launch {
                                store.toggle(item.kind, item.messageId)
                                    .onSuccess { AppToast.show("Removed from saved") }
                                    .onFailure { AppToast.error("Couldn't remove it. Check your connection.") }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedMessageCard(item: SavedMessageItem, onOpen: () -> Unit, onUnsave: () -> Unit) {
    val place = if (item.kind == SavedMessagesStore.CHANNEL) "#${item.channelName ?: "channel"}" else "DM with ${item.partnerName ?: "member"}"
    val sent = if (isSameChatDay(item.sentAt, System.currentTimeMillis())) chatTime(item.sentAt) else chatDayLabel(item.sentAt)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .skeuoFloatingCard(cornerRadius = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, top = 12.dp, bottom = 14.dp, end = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(CoralStart.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Tag, contentDescription = null, tint = CoralStart, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$place · ${item.workspaceName}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onUnsave) {
                Icon(Icons.Outlined.BookmarkRemove, contentDescription = "Remove from saved", tint = Muted, modifier = Modifier.size(20.dp))
            }
        }
        Text(
            text = "${item.authorName} · $sent",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = Ink,
            modifier = Modifier.padding(end = 12.dp)
        )
        Spacer(Modifier.height(4.dp))
        if (item.content.isNotBlank()) {
            Text(
                text = item.content,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 12.dp)
            )
        } else if (item.mediaUrl != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Image, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Attachment", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
    }
}
