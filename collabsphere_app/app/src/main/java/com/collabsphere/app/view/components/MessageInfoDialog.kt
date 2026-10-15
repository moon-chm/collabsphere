package com.collabsphere.app.view.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.collabsphere.app.ui.theme.*

data class MessageInfoSection(val title: String, val names: List<String>)

fun messageInfoTime(at: Long): String = "${chatDayLabel(at)}, ${chatTime(at)}"

@Composable
fun MessageInfoDialog(
    preview: String,
    sentAt: Long?,
    editedAt: Long?,
    statusLine: Pair<Boolean, String>?,
    sections: List<MessageInfoSection>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .skeuoFloatingCard(cornerRadius = 24.dp, surfaceColor = SurfaceRaised)
                .padding(20.dp)
        ) {
            Text(
                text = "Message info",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Ink
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = preview.ifBlank { "Attachment" },
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(14.dp))
            sentAt?.let { InfoRow(Icons.Default.Schedule, "Sent", messageInfoTime(it)) }
            editedAt?.let { InfoRow(Icons.Default.Edit, "Edited", messageInfoTime(it)) }
            statusLine?.let { (read, label) ->
                InfoRow(if (read) Icons.Default.DoneAll else Icons.Default.Done, "Status", label, tint = if (read) Mint else Muted)
            }
            if (sections.isNotEmpty()) {
                LazyColumn(modifier = Modifier.padding(top = 6.dp)) {
                    sections.forEach { section ->
                        item(key = "h-${section.title}") {
                            Text(
                                text = "${section.title.uppercase()} · ${section.names.size}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Muted,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        if (section.names.isEmpty()) {
                            item(key = "e-${section.title}") {
                                Text("Nobody yet", style = MaterialTheme.typography.bodyMedium, color = Muted.copy(alpha = 0.7f))
                            }
                        }
                        items(section.names, key = { "${section.title}-$it" }) { name ->
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Ink,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, tint: Color = CoralStart) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Muted, modifier = Modifier.width(64.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Ink)
    }
}
