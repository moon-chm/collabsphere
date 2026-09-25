package com.collabsphere.app.view.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.collabsphere.app.ui.theme.DestructiveStart
import com.collabsphere.app.ui.theme.Muted

const val PENDING_GRACE_MS = 10_000L

fun isStalePending(id: Int, sentAt: Long?, now: Long): Boolean =
    id < 0 && (sentAt == null || now - sentAt >= PENDING_GRACE_MS)

@Composable
fun PendingMessageLabel(stale: Boolean, onDarkBubble: Boolean, onRetry: () -> Unit) {
    val color = when {
        onDarkBubble -> Color.White
        stale -> DestructiveStart
        else -> Muted
    }
    Row(
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .then(if (stale) Modifier.clickable(onClick = onRetry) else Modifier)
            .padding(horizontal = 2.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (stale) Icons.Default.ErrorOutline else Icons.Default.Schedule,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = if (stale) "Not sent · Tap to retry" else "Sending…",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = color
        )
    }
}
