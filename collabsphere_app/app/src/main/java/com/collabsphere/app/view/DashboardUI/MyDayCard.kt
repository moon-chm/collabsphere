package com.collabsphere.app.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.collabsphere.app.model.task.TaskEntity
import com.collabsphere.app.model.task.TaskPriority
import com.collabsphere.app.model.task.TaskRepo
import com.collabsphere.app.model.workspace.WorkspaceEntity
import com.collabsphere.app.ui.theme.Amber
import com.collabsphere.app.ui.theme.CoralStart
import com.collabsphere.app.ui.theme.DestructiveStart
import com.collabsphere.app.ui.theme.IndigoStart
import com.collabsphere.app.ui.theme.Ink
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.ui.theme.skeuoFloatingCard
import com.collabsphere.app.view.TaskUI.DueState
import com.collabsphere.app.view.TaskUI.dueLabel
import com.collabsphere.app.view.TaskUI.dueStateOf
import com.collabsphere.app.view.TaskUI.todayUtcMidnight
import org.koin.compose.koinInject

data class MyDaySummary(
    val overdue: List<TaskEntity>,
    val dueToday: List<TaskEntity>,
    val highPriority: List<TaskEntity>
) {
    val focus: List<TaskEntity>
        get() = (overdue + dueToday + highPriority).distinctBy { it.id }
}

fun summarizeMyDay(tasks: List<TaskEntity>, today: Long): MyDaySummary {
    val overdue = tasks.filter { task -> task.dueDate?.let { dueStateOf(it, today) == DueState.OVERDUE } == true }
        .sortedBy { it.dueDate }
    val dueToday = tasks.filter { task -> task.dueDate?.let { dueStateOf(it, today) == DueState.TODAY } == true }
    val highPriority = tasks.filter { it.priority == TaskPriority.HIGH }
        .sortedBy { it.dueDate ?: Long.MAX_VALUE }
    return MyDaySummary(overdue, dueToday, highPriority)
}

@Composable
fun MyDayCard(
    userId: Int,
    workspaces: List<WorkspaceEntity>,
    unreadCount: Int,
    onOpenTasks: (WorkspaceEntity) -> Unit,
    onNotificationsClick: () -> Unit
) {
    if (userId <= 0) return
    val taskRepo = koinInject<TaskRepo>()
    val tasks by remember(userId) { taskRepo.getMyOpenTasks(userId) }.collectAsState(initial = emptyList())
    val today = remember { todayUtcMidnight() }
    val summary = remember(tasks, today) { summarizeMyDay(tasks, today) }
    val workspacesById = remember(workspaces) { workspaces.associateBy { it.id } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .skeuoFloatingCard(cornerRadius = 20.dp)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.WbSunny, contentDescription = null, tint = Amber, modifier = Modifier.size(20.dp))
            Text(
                text = "My day",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Ink
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPill("${summary.overdue.size} overdue", if (summary.overdue.isEmpty()) Muted else DestructiveStart)
            StatPill("${summary.dueToday.size} due today", if (summary.dueToday.isEmpty()) Muted else Amber)
            StatPill(
                text = "$unreadCount unread",
                color = if (unreadCount == 0) Muted else IndigoStart,
                onClick = onNotificationsClick
            )
        }

        val focus = summary.focus.take(3)
        if (focus.isEmpty()) {
            Text(
                text = "Nothing urgent assigned to you. Enjoy your day!",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
        } else {
            focus.forEach { task ->
                val workspace = workspacesById[task.workspaceId]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = workspace != null) { workspace?.let(onOpenTasks) }
                        .background(Color.Black.copy(alpha = 0.03f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.taskName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = workspace?.workspaceName ?: "Workspace",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                            maxLines = 1
                        )
                    }
                    val due = task.dueDate
                    Text(
                        text = when {
                            due != null -> dueLabel(due)
                            task.priority == TaskPriority.HIGH -> "High priority"
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = when {
                            due != null && dueStateOf(due, today) == DueState.OVERDUE -> DestructiveStart
                            due != null && dueStateOf(due, today) == DueState.TODAY -> Amber
                            else -> CoralStart
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatPill(text: String, color: Color, onClick: (() -> Unit)? = null) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.10f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}
