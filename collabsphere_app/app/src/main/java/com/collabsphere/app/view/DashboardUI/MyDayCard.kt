package com.collabsphere.app.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.collabsphere.app.model.task.TaskEntity
import com.collabsphere.app.model.task.TaskPriority
import com.collabsphere.app.model.task.TaskRepo
import com.collabsphere.app.model.workspace.WorkspaceEntity
import com.collabsphere.app.ui.theme.BlobShape
import com.collabsphere.app.ui.theme.PrintChip
import com.collabsphere.app.ui.theme.PrintCoral
import com.collabsphere.app.ui.theme.PrintInk
import com.collabsphere.app.ui.theme.PrintInkSoft
import com.collabsphere.app.ui.theme.PrintLeaf
import com.collabsphere.app.ui.theme.PrintPaper
import com.collabsphere.app.ui.theme.PrintRedDark
import com.collabsphere.app.ui.theme.PrintRisoRed
import com.collabsphere.app.ui.theme.PrintSky
import com.collabsphere.app.ui.theme.PrintTokens
import com.collabsphere.app.ui.theme.PrintType
import com.collabsphere.app.ui.theme.printSurface
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

@OptIn(ExperimentalLayoutApi::class)
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
    val sunShape = remember { BlobShape(seed = 41, wobble = 0.14f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .printSurface(shape = RoundedCornerShape(PrintTokens.RadiusCard))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .printSurface(shape = sunShape, fill = PrintCoral, shadow = null),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = PrintInk, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(text = "My day", style = PrintType.H2.copy(fontSize = 24.sp, lineHeight = 28.sp), color = PrintInk)
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrintChip(
                text = "${summary.overdue.size} overdue",
                fill = if (summary.overdue.isEmpty()) PrintPaper else PrintCoral
            )
            PrintChip(
                text = "${summary.dueToday.size} due today",
                fill = if (summary.dueToday.isEmpty()) PrintPaper else PrintSky
            )
            PrintChip(
                text = "$unreadCount unread",
                fill = if (unreadCount == 0) PrintPaper else PrintLeaf,
                onClick = onNotificationsClick
            )
        }

        val focus = summary.focus.take(3)
        if (focus.isEmpty()) {
            Text(
                text = "Nothing urgent for you today. Enjoy it.",
                style = PrintType.Body,
                color = PrintInkSoft
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                focus.forEach { task ->
                    val workspace = workspacesById[task.workspaceId]
                    val due = task.dueDate
                    val state = due?.let { dueStateOf(it, today) }
                    val rowShape = RoundedCornerShape(PrintTokens.RadiusSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(rowShape)
                            .background(PrintPaper)
                            .border(1.5.dp, PrintInk, rowShape)
                            .clickable(enabled = workspace != null) { workspace?.let(onOpenTasks) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    when (state) {
                                        DueState.OVERDUE -> PrintRisoRed
                                        DueState.TODAY -> PrintSky
                                        else -> PrintCoral
                                    }
                                )
                                .border(1.5.dp, PrintInk, CircleShape)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.taskName,
                                style = PrintType.Body.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                                color = PrintInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = workspace?.workspaceName ?: "Workspace",
                                style = PrintType.Caption,
                                color = PrintInkSoft,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when {
                                due != null -> dueLabel(due)
                                task.priority == TaskPriority.HIGH -> "High priority"
                                else -> ""
                            },
                            style = PrintType.Caption.copy(fontWeight = FontWeight.Bold),
                            color = if (state == DueState.OVERDUE) PrintRedDark else PrintInk
                        )
                    }
                }
            }
        }
    }
}
