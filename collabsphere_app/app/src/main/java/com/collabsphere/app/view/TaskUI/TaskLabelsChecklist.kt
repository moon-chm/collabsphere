package com.collabsphere.app.view.TaskUI

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.collabsphere.app.dto.task.ChecklistItem
import com.collabsphere.app.model.task.TaskEntity
import com.collabsphere.app.ui.theme.Amber
import com.collabsphere.app.ui.theme.CoralStart
import com.collabsphere.app.ui.theme.IndigoStart
import com.collabsphere.app.ui.theme.Ink
import com.collabsphere.app.ui.theme.Mint
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.ui.theme.Surface

const val MAX_TASK_LABELS = 5
const val MAX_TASK_LABEL_LENGTH = 20
const val MAX_CHECKLIST_ITEMS = 30
const val MAX_CHECKLIST_TEXT = 200

fun addLabel(labels: List<String>, raw: String): List<String> {
    val label = raw.trim().take(MAX_TASK_LABEL_LENGTH)
    if (label.isEmpty() || labels.size >= MAX_TASK_LABELS) return labels
    if (labels.any { it.equals(label, ignoreCase = true) }) return labels
    return labels + label
}

private val labelPalette = listOf(IndigoStart, Mint, Amber, CoralStart, Color(0xFF8E5BC4), Color(0xFF2E8FA8))

fun labelColor(label: String): Color =
    labelPalette[(label.lowercase().hashCode() and Int.MAX_VALUE) % labelPalette.size]

@Composable
fun LabelPill(label: String, onRemove: (() -> Unit)? = null) {
    val color = labelColor(label)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1
        )
        if (onRemove != null) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove label $label",
                tint = color,
                modifier = Modifier
                    .size(12.dp)
                    .clickable(onClick = onRemove)
            )
        }
    }
}

@Composable
fun TaskLabelsAndProgress(task: TaskEntity) {
    val total = task.checklist.size
    if (task.labels.isEmpty() && total == 0) return
    Row(
        modifier = Modifier
            .padding(top = 6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (total > 0) {
            val done = task.checklist.count { it.done }
            PlanningPill(
                text = "$done/$total",
                color = if (done == total) Mint else Muted,
                icon = { Icon(Icons.Default.Checklist, null, tint = it, modifier = Modifier.size(12.dp)) }
            )
        }
        task.labels.forEach { LabelPill(it) }
    }
}

@Composable
private fun AddItemField(
    placeholder: String,
    icon: ImageVector,
    maxLength: Int,
    onSubmit: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val submit = {
        val text = input.trim()
        if (text.isNotEmpty()) onSubmit(text)
        input = ""
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, Muted.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = input,
            onValueChange = { input = it.take(maxLength) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
            cursorBrush = SolidColor(CoralStart),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (input.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = Muted)
                    inner()
                }
            }
        )
        if (input.isNotBlank()) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = placeholder,
                tint = CoralStart,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { submit() }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelEditor(
    labels: List<String>,
    onLabelsChange: (List<String>) -> Unit,
    enabled: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (labels.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                labels.forEach { label ->
                    LabelPill(label, onRemove = if (enabled) ({ onLabelsChange(labels - label) }) else null)
                }
            }
        }
        if (enabled && labels.size < MAX_TASK_LABELS) {
            AddItemField("Add label", Icons.Default.Sell, MAX_TASK_LABEL_LENGTH) { text ->
                val updated = addLabel(labels, text)
                if (updated != labels) onLabelsChange(updated)
            }
        }
    }
}

@Composable
fun ChecklistEditor(
    items: List<ChecklistItem>,
    onItemsChange: (List<ChecklistItem>) -> Unit,
    enabled: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        if (items.isNotEmpty()) {
            Text(
                text = "Checklist · ${items.count { it.done }}/${items.size}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Muted
            )
        }
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (item.done) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = if (item.done) "Mark not done" else "Mark done",
                    tint = if (item.done) Mint else Muted,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable(enabled = enabled) {
                            onItemsChange(items.toMutableList().apply { this[index] = item.copy(done = !item.done) })
                        }
                )
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (item.done) Muted else Ink,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (enabled) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove item",
                        tint = Muted,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onItemsChange(items.filterIndexed { i, _ -> i != index }) }
                    )
                }
            }
        }
        if (enabled && items.size < MAX_CHECKLIST_ITEMS) {
            AddItemField("Add checklist item", Icons.Default.Checklist, MAX_CHECKLIST_TEXT) { text ->
                onItemsChange(items + ChecklistItem(text))
            }
        }
    }
}

@Composable
fun LabelFilterRow(
    labels: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (labels.isEmpty()) return
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterPill("All labels", selected == null, Muted) { onSelect(null) }
        labels.forEach { label ->
            val isSelected = selected.equals(label, ignoreCase = true)
            FilterPill(label, isSelected, labelColor(label)) { onSelect(if (isSelected) null else label) }
        }
    }
}

@Composable
private fun FilterPill(text: String, isSelected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) color.copy(alpha = 0.18f) else Surface)
            .border(1.dp, if (isSelected) color else Muted.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
            color = if (isSelected) color else Ink
        )
    }
}
