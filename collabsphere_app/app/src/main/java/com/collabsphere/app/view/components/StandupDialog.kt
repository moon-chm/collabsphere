package com.collabsphere.app.view.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.collabsphere.app.model.standup.StandupFormat
import com.collabsphere.app.remote.standup.StandupApiService
import com.collabsphere.app.ui.theme.CoralEnd
import com.collabsphere.app.ui.theme.CoralStart
import com.collabsphere.app.ui.theme.Ink
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.view.TaskUI.todayUtcMidnight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StandupDialog(
    workspaceId: Int,
    channelName: String,
    onPost: (text: String, onDone: (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val api = koinInject<StandupApiService>()
    var loading by remember { mutableStateOf(true) }
    var posting by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var githubLinked by remember { mutableStateOf(true) }
    var yesterday by remember { mutableStateOf("") }
    var today by remember { mutableStateOf("") }
    var blockers by remember { mutableStateOf("") }
    val dateLabel = remember { SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date()) }

    LaunchedEffect(workspaceId) {
        try {
            val draft = api.draft(workspaceId, StandupFormat.previousWorkdayStart(), todayUtcMidnight())
            yesterday = StandupFormat.toEditable(draft.yesterday)
            today = StandupFormat.toEditable(draft.today)
            blockers = StandupFormat.toEditable(draft.blockers)
            githubLinked = draft.githubLinked
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppToast.show("Couldn't load your activity, so write it in yourself")
        }
        loading = false
    }

    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            delay(1000)
            onDismiss()
        }
    }

    val composed = StandupFormat.compose(dateLabel, yesterday, today, blockers)

    Dialog(
        onDismissRequest = { if (!posting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        WorkspaceDialogCard(
            icon = Icons.Outlined.WbSunny,
            accent = CoralStart,
            title = "Your standup",
            subtitle = "Drafted from your tasks${if (githubLinked) " and GitHub" else ""}. Edit anything, then post to #$channelName.",
            onClose = onDismiss,
            closeEnabled = !posting,
            successMessage = successMessage
        ) {
            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                    CollabSpinner(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp, color = CoralStart)
                }
                return@WorkspaceDialogCard
            }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                StandupSection("Yesterday", yesterday, "• What did you get done?", !posting) { yesterday = it }
                StandupSection("Today", today, "• What are you working on?", !posting) { today = it }
                StandupSection("Blockers", blockers, "• Anything in your way? Leave empty for none", !posting) { blockers = it }
            }

            if (!githubLinked) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
                    Text(
                        text = "Link GitHub in your profile to include commits and PRs.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            WorkspaceDialogButton(
                text = "Post to #$channelName",
                loadingText = "Posting…",
                colors = listOf(CoralStart, CoralEnd),
                enabled = composed != null,
                loading = posting,
                onClick = {
                    val text = composed
                    if (text == null) {
                        AppToast.warning("Add at least one thing you did or plan to do")
                        return@WorkspaceDialogButton
                    }
                    posting = true
                    onPost(text) { ok ->
                        posting = false
                        if (ok) successMessage = "Standup posted" else AppToast.error("Couldn't post your standup")
                    }
                }
            )
        }
    }
}

@Composable
private fun StandupSection(
    title: String,
    value: String,
    placeholder: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = Ink,
            modifier = Modifier.padding(start = 6.dp)
        )
        SkeuoTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            enabled = enabled,
            singleLine = false,
            minLines = 2,
            maxLines = 7,
            cornerRadius = 18.dp
        )
    }
}
