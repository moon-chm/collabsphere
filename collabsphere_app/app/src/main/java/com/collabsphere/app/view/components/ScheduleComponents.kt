package com.collabsphere.app.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.collabsphere.app.dto.message.ScheduleMessageRequest
import com.collabsphere.app.dto.message.ScheduledMessageItem
import com.collabsphere.app.remote.message.MessageApiService
import com.collabsphere.app.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.util.Calendar
import java.util.TimeZone

class ScheduledMessagesState(
    private val api: MessageApiService,
    private val kind: String,
    private val workspaceId: Int,
    private val channelId: Int?,
    private val receiverId: Int?
) {
    var items by mutableStateOf<List<ScheduledMessageItem>>(emptyList())
        private set

    suspend fun refresh() {
        runCatching { api.getScheduled(workspaceId, channelId, receiverId) }.onSuccess { items = it }
    }

    suspend fun schedule(content: String, sendAt: Long): Boolean = runCatching {
        api.scheduleMessage(ScheduleMessageRequest(kind, workspaceId, channelId, receiverId, content, sendAt))
    }.onSuccess { refresh() }.isSuccess

    suspend fun cancel(id: Int): Boolean = runCatching { api.cancelScheduled(id) }
        .onSuccess { items = items.filterNot { it.id == id } }
        .isSuccess
}

@Composable
fun rememberScheduledMessages(kind: String, workspaceId: Int, channelId: Int?, receiverId: Int?): ScheduledMessagesState {
    val api = koinInject<MessageApiService>()
    val state = remember(kind, workspaceId, channelId, receiverId) {
        ScheduledMessagesState(api, kind, workspaceId, channelId, receiverId)
    }
    LaunchedEffect(state) { state.refresh() }
    return state
}

private fun nextHour(): Long = System.currentTimeMillis() + 60 * 60 * 1000L

private fun tomorrowAt9(): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, 1)
    set(Calendar.HOUR_OF_DAY, 9)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun scheduleLabel(at: Long): String = "${chatDayLabel(at)} at ${chatTime(at)}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSendDialog(onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    val now = Calendar.getInstance()
    val timeState = rememberTimePickerState(initialHour = (now.get(Calendar.HOUR_OF_DAY) + 1) % 24, initialMinute = 0)

    when (step) {
        0 -> Dialog(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .skeuoFloatingCard(cornerRadius = 24.dp, surfaceColor = SurfaceRaised)
                    .padding(vertical = 18.dp)
            ) {
                Text(
                    text = "Schedule message",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Ink,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(10.dp))
                listOf(
                    "In 1 hour" to nextHour(),
                    "Tomorrow, 9:00 AM" to tomorrowAt9()
                ).forEach { (label, at) ->
                    ScheduleOption(label, scheduleLabel(at)) { onPick(at) }
                }
                ScheduleOption("Pick date & time", "Choose any time") { step = 1 }
            }
        }
        1 -> DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = { step = 2 }) { Text("Next", color = CoralStart) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
        ) {
            DatePicker(state = dateState)
        }
        else -> Dialog(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .skeuoFloatingCard(cornerRadius = 24.dp, surfaceColor = SurfaceRaised)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Pick a time", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = Ink)
                Spacer(Modifier.height(12.dp))
                TimePicker(state = timeState)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) }
                    TextButton(onClick = {
                        val utcDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = dateState.selectedDateMillis ?: System.currentTimeMillis()
                        }
                        val local = Calendar.getInstance().apply {
                            set(utcDate.get(Calendar.YEAR), utcDate.get(Calendar.MONTH), utcDate.get(Calendar.DAY_OF_MONTH), timeState.hour, timeState.minute, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onPick(local.timeInMillis)
                    }) { Text("Schedule", color = CoralStart, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun ScheduleOption(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = CoralStart, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
fun ScheduledMessagesChip(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(IndigoStart.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = IndigoStart, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (count == 1) "1 scheduled message" else "$count scheduled messages",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = IndigoStart
        )
    }
}

@Composable
fun ScheduledMessagesDialog(state: ScheduledMessagesState, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .skeuoFloatingCard(cornerRadius = 24.dp, surfaceColor = SurfaceRaised)
                .padding(vertical = 18.dp)
        ) {
            Text(
                text = "Scheduled",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Ink,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(8.dp))
            if (state.items.isEmpty()) {
                Text("Nothing scheduled", style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.padding(20.dp))
            }
            LazyColumn {
                items(state.items, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = scheduleLabel(item.sendAt),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = IndigoStart
                            )
                            Text(
                                text = item.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                if (state.cancel(item.id)) AppToast.show("Scheduled message cancelled")
                                else AppToast.error("Couldn't cancel it. Check your connection.")
                            }
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel scheduled message", tint = Muted)
                        }
                    }
                }
            }
        }
    }
}
