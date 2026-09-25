package com.collabsphere.app.view.TaskUI

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.collabsphere.app.model.task.TaskEntity
import com.collabsphere.app.model.task.TaskPriority
import com.collabsphere.app.model.task.TaskStatus
import com.collabsphere.app.ui.theme.Amber
import com.collabsphere.app.ui.theme.CoralStart
import com.collabsphere.app.ui.theme.DestructiveStart
import com.collabsphere.app.ui.theme.IndigoStart
import com.collabsphere.app.ui.theme.Ink
import com.collabsphere.app.ui.theme.Mint
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.ui.theme.Surface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private const val DAY_MS = 24 * 60 * 60 * 1000L

enum class DueState { OVERDUE, TODAY, TOMORROW, LATER }

fun todayUtcMidnight(): Long {
    val local = Calendar.getInstance()
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

fun dueStateOf(dueDate: Long, today: Long = todayUtcMidnight()): DueState = when {
    dueDate < today -> DueState.OVERDUE
    dueDate < today + DAY_MS -> DueState.TODAY
    dueDate < today + 2 * DAY_MS -> DueState.TOMORROW
    else -> DueState.LATER
}

fun formatDueDate(dueDate: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(dueDate)

fun dueLabel(dueDate: Long): String = when (dueStateOf(dueDate)) {
    DueState.OVERDUE -> "Overdue · ${formatDueDate(dueDate)}"
    DueState.TODAY -> "Due today"
    DueState.TOMORROW -> "Due tomorrow"
    DueState.LATER -> "Due ${formatDueDate(dueDate)}"
}

fun priorityColor(priority: TaskPriority): Color = when (priority) {
    TaskPriority.HIGH -> DestructiveStart
    TaskPriority.MEDIUM -> Amber
    TaskPriority.LOW -> Mint
}

fun priorityLabel(priority: TaskPriority): String = when (priority) {
    TaskPriority.HIGH -> "High"
    TaskPriority.MEDIUM -> "Medium"
    TaskPriority.LOW -> "Low"
}

@Composable
fun PrioritySelector(
    selected: TaskPriority,
    onSelect: (TaskPriority) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TaskPriority.entries.forEach { option ->
            val isSelected = option == selected
            val color = priorityColor(option)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) color.copy(alpha = 0.16f) else Surface)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) color else Muted.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable(enabled = enabled) { onSelect(option) },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = priorityLabel(option),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) Ink else Muted
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueDateSelector(
    dueDate: Long?,
    onDueDateChange: (Long?) -> Unit,
    enabled: Boolean = true
) {
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, Muted.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { showPicker = true }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Event,
            contentDescription = null,
            tint = if (dueDate != null) IndigoStart else Muted,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = dueDate?.let { "Due ${formatDueDate(it)}" } ?: "Add due date",
            style = MaterialTheme.typography.bodyMedium,
            color = if (dueDate != null) Ink else Muted,
            modifier = Modifier.weight(1f)
        )
        if (dueDate != null && enabled) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear due date",
                tint = Muted,
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { onDueDateChange(null) }
            )
        }
    }

    if (showPicker) {
        val today = remember { todayUtcMidnight() }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dueDate ?: today,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= today
            }
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let(onDueDateChange)
                    showPicker = false
                }) {
                    Text("Set", color = CoralStart, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Cancel", color = Muted)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
fun TaskPlanningBadges(task: TaskEntity) {
    val showPriority = task.priority != TaskPriority.MEDIUM
    val dueDate = task.dueDate
    if (!showPriority && dueDate == null) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 6.dp)
    ) {
        if (showPriority) {
            PlanningPill(
                text = priorityLabel(task.priority),
                color = priorityColor(task.priority),
                icon = { Icon(Icons.Default.Flag, null, tint = it, modifier = Modifier.size(12.dp)) }
            )
        }
        if (dueDate != null) {
            val isDone = task.status == TaskStatus.DONE
            val color = when {
                isDone -> Muted
                dueStateOf(dueDate) == DueState.OVERDUE -> DestructiveStart
                dueStateOf(dueDate) == DueState.TODAY -> Amber
                else -> IndigoStart
            }
            PlanningPill(
                text = if (isDone) "Due ${formatDueDate(dueDate)}" else dueLabel(dueDate),
                color = color,
                icon = { Icon(Icons.Default.Event, null, tint = it, modifier = Modifier.size(12.dp)) }
            )
        }
    }
}

@Composable
internal fun PlanningPill(text: String, color: Color, icon: @Composable (Color) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        icon(color)
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = color
        )
    }
}
