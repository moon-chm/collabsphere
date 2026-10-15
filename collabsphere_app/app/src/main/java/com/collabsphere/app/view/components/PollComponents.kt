package com.collabsphere.app.view.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.collabsphere.app.dto.message.PollView
import com.collabsphere.app.ui.theme.*

@Composable
fun PollCard(
    poll: PollView,
    onDarkBubble: Boolean,
    isAuthor: Boolean,
    onVote: (Int) -> Unit,
    onClose: () -> Unit
) {
    val text = if (onDarkBubble) Color.White else Ink
    val sub = if (onDarkBubble) Color.White.copy(alpha = 0.8f) else Muted
    val accent = if (onDarkBubble) Color.White else CoralStart
    val totalVotes = poll.options.sumOf { it.votes }.coerceAtLeast(1)

    Column(modifier = Modifier.widthIn(min = 220.dp, max = 280.dp)) {
        Text(
            text = poll.question,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = text
        )
        Text(
            text = when {
                poll.closed -> "Poll closed"
                poll.allowsMultiple -> "Choose one or more"
                else -> "Choose one"
            },
            style = MaterialTheme.typography.labelSmall,
            color = sub
        )
        Spacer(Modifier.height(8.dp))
        poll.options.forEachIndexed { index, option ->
            val mine = index in poll.myVotes
            val share by animateFloatAsState(option.votes.toFloat() / totalVotes, tween(400), label = "pollShare")
            Box(
                modifier = Modifier
                    .padding(vertical = 3.dp)
                    .fillMaxWidth()
                    .heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.10f))
                    .border(1.dp, if (mine) accent else accent.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .clickable(enabled = !poll.closed) { onVote(index) }
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .fillMaxWidth(share)
                        .background(accent.copy(alpha = if (mine) 0.30f else 0.16f))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (mine) {
                        Icon(Icons.Default.Check, contentDescription = "Your vote", tint = accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = option.text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal),
                        color = text,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(option.votes * 100f / totalVotes).toInt()}%",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = sub
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (poll.totalVoters == 1) "1 vote" else "${poll.totalVoters} votes",
                style = MaterialTheme.typography.labelSmall,
                color = sub,
                modifier = Modifier.weight(1f)
            )
            if (isAuthor && !poll.closed) {
                Text(
                    text = "Close poll",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun CreatePollDialog(
    onCreate: (question: String, options: List<String>, allowsMultiple: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var allowsMultiple by remember { mutableStateOf(false) }
    val filled = options.map { it.trim() }.filter { it.isNotEmpty() }
    val canCreate = question.isNotBlank() && filled.size >= 2 && filled.distinct().size == filled.size

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .skeuoFloatingCard(cornerRadius = 24.dp, surfaceColor = SurfaceRaised)
                .padding(20.dp)
        ) {
            Text(
                text = "Create poll",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Ink
            )
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SkeuoTextField(
                    value = question,
                    onValueChange = { question = it.take(200) },
                    label = "Question",
                    placeholder = "Ask something…"
                )
                options.forEachIndexed { index, value ->
                    SkeuoTextField(
                        value = value,
                        onValueChange = { options[index] = it.take(200) },
                        label = "Option ${index + 1}",
                        placeholder = "Answer",
                        trailingIcon = if (options.size > 2) {
                            {
                                IconButton(onClick = { options.removeAt(index) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove option", tint = Muted, modifier = Modifier.size(18.dp))
                                }
                            }
                        } else null
                    )
                }
                if (options.size < 10) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { options.add("") }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = CoralStart, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add option", style = MaterialTheme.typography.labelLarge, color = CoralStart)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Allow multiple answers",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = allowsMultiple,
                        onCheckedChange = { allowsMultiple = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = CoralStart)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge, color = Muted)
                }
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (canCreate) CoralStart else CoralStart.copy(alpha = 0.5f))
                        .clickable(enabled = canCreate) { onCreate(question, filled, allowsMultiple) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Create poll", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                }
            }
        }
    }
}
