package com.collabsphere.app.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.collabsphere.app.model.commitment.Commitment
import com.collabsphere.app.model.commitment.CommitmentRepo
import com.collabsphere.app.model.commitment.CommitmentSource
import com.collabsphere.app.ui.theme.Ink
import com.collabsphere.app.ui.theme.Mint
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.view.TaskUI.todayUtcMidnight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val DAY_MS = 24 * 60 * 60 * 1000L

data class CommitmentTarget(val commitment: Commitment, val source: CommitmentSource)

@Composable
fun CommitmentTaskDialog(
    target: CommitmentTarget,
    currentUserId: Int,
    onDismiss: () -> Unit
) {
    val repo = koinInject<CommitmentRepo>()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var title by remember(target) { mutableStateOf(target.commitment.title) }
    var dueInDays by remember(target) { mutableStateOf(target.commitment.dueInDays) }
    var saving by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(1000)
            onDismiss()
        }
    }

    val assigneeLabel = if (target.source.assigneeId == currentUserId) "you" else target.source.assigneeName
    val canSave = title.isNotBlank() && !saving

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        WorkspaceDialogCard(
            icon = Icons.Outlined.TaskAlt,
            accent = Mint,
            title = "Track as task",
            subtitle = "Assigned to $assigneeLabel · from ${target.source.placeLabel}",
            onClose = onDismiss,
            closeEnabled = !saving,
            successMessage = successMessage
        ) {
            SkeuoTextField(
                value = title,
                onValueChange = { title = it.take(120) },
                placeholder = "Task title",
                enabled = !saving,
                imeAction = ImeAction.Done,
                leadingIcon = {
                    Icon(Icons.Outlined.Edit, contentDescription = null, tint = Mint, modifier = Modifier.size(18.dp))
                }
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Event, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
                dueOptions(target.commitment.dueInDays).forEach { option ->
                    DueChip(
                        label = option.second,
                        selected = dueInDays == option.first,
                        enabled = !saving,
                        onClick = { dueInDays = option.first }
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            WorkspaceDialogButton(
                text = "Create task",
                loadingText = "Creating…",
                colors = listOf(Mint, Mint.copy(red = Mint.red * 0.8f, green = Mint.green * 0.8f, blue = Mint.blue * 0.8f)),
                enabled = canSave,
                loading = saving,
                onClick = {
                    if (!canSave) return@WorkspaceDialogButton
                    saving = true
                    scope.launch {
                        val dueDate = dueInDays?.let { todayUtcMidnight() + it * DAY_MS }
                        val result = repo.createTask(target.source, currentUserId, title, dueDate)
                        saving = false
                        if (result.isSuccess) {
                            successMessage = "Task created"
                        } else {
                            AppToast.error(result.exceptionOrNull()?.localizedMessage ?: "Couldn't create the task")
                        }
                    }
                }
            )
        }
    }
}

private fun dueOptions(detected: Int?): List<Pair<Int?, String>> {
    val options = mutableListOf<Pair<Int?, String>>(null to "No date", 0 to "Today", 1 to "Tomorrow")
    if (detected != null && detected > 1) {
        val day = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, detected) }
        options += detected to SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(day.time)
    }
    return options
}

@Composable
private fun DueChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Mint.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (selected) Mint else Muted.copy(alpha = 0.25f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) Mint else Ink
        )
    }
}

@Composable
fun TrackedCommitmentLabel(onDarkBubble: Boolean, modifier: Modifier = Modifier) {
    val tint = if (onDarkBubble) Color.White.copy(alpha = 0.75f) else Mint
    Row(
        modifier = modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
        Text(
            text = "Tracked as task",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = tint
        )
    }
}
