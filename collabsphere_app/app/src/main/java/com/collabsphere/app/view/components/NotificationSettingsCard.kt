package com.collabsphere.app.view.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.collabsphere.app.model.QuietHours
import com.collabsphere.app.model.QuietHoursStore
import com.collabsphere.app.ui.theme.CoralStart
import com.collabsphere.app.ui.theme.IndigoStart
import com.collabsphere.app.ui.theme.Ink
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.ui.theme.Surface
import com.collabsphere.app.ui.theme.skeuoFloatingCard
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

fun formatMinuteOfDay(minuteOfDay: Int, is24Hour: Boolean): String {
    val hour = minuteOfDay / 60
    val minute = minuteOfDay % 60
    if (is24Hour) return "%02d:%02d".format(hour, minute)
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "%d:%02d %s".format(displayHour, minute, if (hour < 12) "AM" else "PM")
}

@Composable
fun NotificationSettingsCard() {
    val store = koinInject<QuietHoursStore>()
    val settings by store.settings.collectAsState(initial = QuietHours())
    val scope = rememberCoroutineScope()
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    var editing by remember { mutableStateOf<Boolean?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .skeuoFloatingCard(cornerRadius = 20.dp)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Bedtime, contentDescription = null, tint = IndigoStart, modifier = Modifier.size(20.dp))
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Ink
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Quiet hours", style = MaterialTheme.typography.bodyMedium, color = Ink)
                Text(
                    "Silence alerts on this device during these hours. They still appear in your notification list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }
            Switch(
                checked = settings.enabled,
                onCheckedChange = { enabled -> scope.launch { store.update(settings.copy(enabled = enabled)) } },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = CoralStart,
                    uncheckedThumbColor = Muted,
                    uncheckedTrackColor = Surface
                )
            )
        }
        if (settings.enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeChip("From", formatMinuteOfDay(settings.startMinute, is24Hour)) { editing = true }
                TimeChip("Until", formatMinuteOfDay(settings.endMinute, is24Hour)) { editing = false }
            }
        }
    }

    editing?.let { editingStart ->
        QuietHoursTimeDialog(
            initialMinute = if (editingStart) settings.startMinute else settings.endMinute,
            is24Hour = is24Hour,
            onConfirm = { minute ->
                scope.launch {
                    store.update(if (editingStart) settings.copy(startMinute = minute) else settings.copy(endMinute = minute))
                }
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun TimeChip(label: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = CoralStart)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietHoursTimeDialog(
    initialMinute: Int,
    is24Hour: Boolean,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = is24Hour
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text("Set", color = CoralStart, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) }
        },
        text = { TimePicker(state = state) }
    )
}
