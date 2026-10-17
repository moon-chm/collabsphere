package com.collabsphere.app.view.components

import com.collabsphere.app.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import com.collabsphere.app.ui.theme.CoralLight

private fun MessageEntity.pinnedPreview(): String =
    content.ifBlank { if (mediaUrl != null) "Photo" else "" }

@Composable
fun PinnedMessagesBanner(
    latest: MessageEntity,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = PinnedMessagesBanner(
    preview = "${latest.userName}: ${latest.pinnedPreview()}",
    count = count,
    onClick = onClick,
    modifier = modifier
)

@Composable
fun PinnedMessagesBanner(
    preview: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .skeuoFloatingCard(cornerRadius = 14.dp, surfaceColor = SurfaceRaised)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.5.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(PrintCoral)
        )
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(PrintCoral.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PushPin, contentDescription = null, tint = PrintInk, modifier = Modifier.size(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (count > 1) "Pinned · $count messages" else "Pinned message",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = PrintInk
            )
            Text(
                text = preview,
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
                            .background(PrintSky.copy(alpha = 0.35f))
                            .clickable { onSelect(message) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = message.userName,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = PrintInk
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
                Text("Close", color = PrintInk, fontWeight = FontWeight.Bold)
            }
        }
    }
}
