package com.collabsphere.app.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.collabsphere.app.model.channels.ChannelRepo
import com.collabsphere.app.model.dm.DmRepo
import com.collabsphere.app.model.message.MessageEntity
import com.collabsphere.app.model.message.MessageRepo
import com.collabsphere.app.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

data class ForwardPerson(val userId: Int, val name: String)

@Composable
fun ForwardDialog(
    workspaceId: Int,
    currentUserId: Int,
    currentUserName: String,
    people: List<ForwardPerson>,
    content: String,
    mediaUrl: String?,
    onDismiss: () -> Unit
) {
    val channelRepo = koinInject<ChannelRepo>()
    val messageRepo = koinInject<MessageRepo>()
    val dmRepo = koinInject<DmRepo>()
    val scope = rememberCoroutineScope()
    val channels by remember(workspaceId) { channelRepo.getallchannelbyuser(workspaceId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var sending by remember { mutableStateOf(false) }

    fun forward(label: String, send: suspend () -> Unit) {
        if (sending) return
        sending = true
        scope.launch {
            runCatching { send() }
                .onSuccess { AppToast.show("Forwarded to $label") }
                .onFailure { AppToast.error("Couldn't forward the message") }
            sending = false
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .skeuoFloatingCard(cornerRadius = 24.dp, surfaceColor = SurfaceRaised)
                .padding(vertical = 18.dp)
        ) {
            Text(
                text = "Forward to",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Ink,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Text(
                text = content.ifBlank { "Attachment" },
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp)
            )
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                if (channels.isNotEmpty()) {
                    item { ForwardSectionLabel("Channels") }
                    items(channels, key = { "c${it.id}" }) { channel ->
                        ForwardRow(
                            title = "#${channel.channelName}",
                            leading = { Icon(Icons.Default.Tag, contentDescription = null, tint = CoralStart, modifier = Modifier.size(18.dp)) },
                            enabled = !sending
                        ) {
                            forward("#${channel.channelName}") {
                                messageRepo.sendMessageToUser(
                                    MessageEntity(
                                        userId = currentUserId,
                                        workspaceId = workspaceId,
                                        channelId = channel.id,
                                        userName = currentUserName,
                                        content = content,
                                        mediaUrl = mediaUrl
                                    )
                                )
                            }
                        }
                    }
                }
                val others = people.filter { it.userId != currentUserId }
                if (others.isNotEmpty()) {
                    item { ForwardSectionLabel("People") }
                    items(others, key = { "p${it.userId}" }) { person ->
                        ForwardRow(
                            title = person.name,
                            leading = {
                                Text(
                                    text = person.name.take(1).uppercase(),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = IndigoStart
                                )
                            },
                            enabled = !sending
                        ) {
                            forward(person.name) {
                                dmRepo.sendRealtimeDm(
                                    workspaceId = workspaceId,
                                    senderId = currentUserId,
                                    receiverId = person.userId,
                                    content = content,
                                    mediaUrl = mediaUrl
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForwardSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = Muted,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp)
    )
}

@Composable
private fun ForwardRow(title: String, leading: @Composable () -> Unit, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Background),
            contentAlignment = Alignment.Center
        ) { leading() }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
        )
    }
}
