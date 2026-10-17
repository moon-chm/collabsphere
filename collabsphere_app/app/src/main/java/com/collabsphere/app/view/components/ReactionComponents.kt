package com.collabsphere.app.view.components

import com.collabsphere.app.ui.theme.*
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.collabsphere.app.dto.message.ChannelReadState
import com.collabsphere.app.model.message.MessageEntity
import com.collabsphere.app.ui.theme.CoralStart
import com.collabsphere.app.ui.theme.Ink
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.ui.theme.SurfaceRaised

val REACTION_EMOJI_CHOICES = listOf("👍", "❤️", "😂", "🚀", "👀")

@Composable
fun ReactionPickerDialog(
    selectedEmojis: Set<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("reaction_recents", android.content.Context.MODE_PRIVATE) }
    val quickRow = remember {
        val recent = prefs.getString("recent", null)?.split("\u0000")?.filter { it.isNotBlank() }.orEmpty()
        (recent + REACTION_EMOJI_CHOICES).distinct().take(6)
    }
    var expanded by remember { mutableStateOf(false) }

    fun pick(emoji: String) {
        val recent = prefs.getString("recent", null)?.split("\u0000")?.filter { it.isNotBlank() }.orEmpty()
        prefs.edit().putString("recent", (listOf(emoji) + recent).distinct().take(12).joinToString("\u0000")).apply()
        onPick(emoji)
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceRaised)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "React to message",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Muted
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                quickRow.forEach { emoji ->
                    val isSelected = emoji in selectedEmojis
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = 44.dp)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(if (isSelected) PrintCoral else PrintWhite)
                            .border(if (isSelected) 2.dp else 1.5.dp, PrintInk, CircleShape)
                            .clickable { pick(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 20.sp)
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 44.dp)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(if (expanded) PrintCoral else PrintWhite)
                        .border(1.5.dp, PrintInk, CircleShape)
                        .clickable { expanded = !expanded },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (expanded) "–" else "+", fontSize = 22.sp, color = Muted)
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        androidx.emoji2.emojipicker.EmojiPickerView(ctx).apply {
                            emojiGridColumns = 8
                            setOnEmojiPickedListener { item -> pick(item.emoji) }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }
        }
    }
}

@Composable
fun ReactionChipsRow(
    reactions: Map<String, Set<Int>>,
    currentUserId: Int,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (reactions.isEmpty()) return
    Row(
        modifier = modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        reactions.entries
            .sortedWith(compareByDescending<Map.Entry<String, Set<Int>>> { it.value.size }.thenBy { it.key })
            .forEach { (emoji, users) ->
                val mine = currentUserId in users
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (mine) PrintCoral.copy(alpha = 0.55f) else PrintWhite)
                        .border(if (mine) 2.dp else 1.5.dp, PrintInk, RoundedCornerShape(14.dp))
                        .clickable { onToggle(emoji) }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(text = emoji, fontSize = 13.sp)
                    Text(
                        text = users.size.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                        color = PrintInk
                    )
                }
            }
    }
}

fun seenByLabel(
    states: Collection<ChannelReadState>,
    message: MessageEntity,
    currentUserId: Int
): String? {
    val readers = states
        .filter { it.userId != currentUserId && it.userId != message.userId && it.lastReadMessageId >= message.id }
        .map { it.userName }
        .sorted()
    return when {
        readers.isEmpty() -> null
        readers.size <= 3 -> "Seen by ${readers.joinToString(", ")}"
        else -> "Seen by ${readers.take(2).joinToString(", ")} +${readers.size - 2}"
    }
}

fun typingLabel(names: List<String>): String? = when (names.size) {
    0 -> null
    1 -> "${names[0]} is typing…"
    2 -> "${names[0]} and ${names[1]} are typing…"
    else -> "Several people are typing…"
}
