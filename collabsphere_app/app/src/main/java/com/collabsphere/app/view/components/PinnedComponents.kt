package com.collabsphere.app.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.collabsphere.app.model.message.MessageEntity
import com.collabsphere.app.ui.theme.CoralStart
import com.collabsphere.app.ui.theme.IndigoStart
import com.collabsphere.app.ui.theme.Ink
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.ui.theme.SurfaceRaised
import com.collabsphere.app.ui.theme.skeuoFloatingCard

private fun MessageEntity.pinnedPreview(): String =
    content.ifBlank { if (mediaUrl != null) "📷 Photo" else "" }

@Composable
fun PinnedMessagesBanner(
    latest: MessageEntity,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .skeuoFloatingCard(cornerRadius = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.PushPin, contentDescription = null, tint = CoralStart, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (count > 1) "Pinned · $count messages" else "Pinned message",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = CoralStart
            )
            Text(
                text = "${latest.userName}: ${latest.pinnedPreview()}",
                style = MaterialTheme.typography.bodySmall,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PinnedMessagesDialog(
    pinned: List<MessageEntity>,
    onSelect: (MessageEntity) -> Unit,
    onUnpin: (MessageEntity) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceRaised)
                .padding(16.dp)
        ) {
            Text(
                text = "Pinned messages",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Ink
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pinned, key = { it.id }) { message ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(IndigoStart.copy(alpha = 0.06f))
                            .clickable { onSelect(message) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = message.userName,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = IndigoStart
                            )
                            Text(
                                text = message.pinnedPreview(),
                                style = MaterialTheme.typography.bodySmall,
                                color = Ink,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = { onUnpin(message) }) {
                            Text("Unpin", color = Muted)
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Close", color = CoralStart, fontWeight = FontWeight.Bold)
            }
        }
    }
}
