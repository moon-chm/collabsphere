package com.collabsphere.app.view.components

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.IconButton
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.collabsphere.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Time formatting shared by channel and DM chats ──

/** "Today", "Yesterday", "March 4", or "Mar 4, 2025" for other years. */
fun chatDayLabel(timestamp: Long): String {
    val message = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        sameDay(message, today) -> "Today"
        sameDay(message, yesterday) -> "Yesterday"
        message.get(Calendar.YEAR) == today.get(Calendar.YEAR) ->
            SimpleDateFormat("MMMM d", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

/** "3:42 PM" (or the device's 24-hour equivalent pattern). */
fun chatTime(timestamp: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))

fun isSameChatDay(t1: Long, t2: Long): Boolean = sameDay(
    Calendar.getInstance().apply { timeInMillis = t1 },
    Calendar.getInstance().apply { timeInMillis = t2 }
)

private fun sameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

// ── Date separator chip ──

/** Floating day chip between messages from different days. */
@Composable
fun ChatDateSeparator(timestamp: Long, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .drawWithCache {
                    onDrawBehind {
                        drawRoundRect(
                            color = ShadowDark.copy(alpha = 0.12f),
                            topLeft = Offset(0f, 1.5.dp.toPx()),
                            size = Size(size.width, size.height),
                            cornerRadius = CornerRadius(12.dp.toPx())
                        )
                        drawRoundRect(
                            color = SurfaceRaised.copy(alpha = 0.94f),
                            cornerRadius = CornerRadius(12.dp.toPx())
                        )
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.85f),
                            cornerRadius = CornerRadius(12.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text(
                text = chatDayLabel(timestamp),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                color = Muted
            )
        }
    }
}

// ── Jump to latest ──

/**
 * Tracks messages that arrive while the reader is scrolled up, and whether to offer a jump back down.
 * [lastIndex] is the index of the newest message row in the list.
 */
@Stable
class JumpToLatestState {
    var unseenCount by mutableIntStateOf(0)
        internal set

    fun onNewMessages(count: Int) { unseenCount += count }
    fun reset() { unseenCount = 0 }
}

@Composable
fun rememberJumpToLatestState(key: Any?): JumpToLatestState = remember(key) { JumpToLatestState() }

/** True once the newest message row is no longer (nearly) on screen. */
fun LazyListState.isAwayFromLatest(lastIndex: Int): Boolean {
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return false
    return lastIndex >= 0 && lastVisible < lastIndex - 1
}

/** Round coral button with an optional count of messages that arrived while scrolled up. */
@Composable
fun JumpToLatestButton(
    visible: Boolean,
    unseenCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.TopCenter) {
            Box(
                modifier = Modifier
                    .padding(top = if (unseenCount > 0) 8.dp else 0.dp)
                    .size(44.dp)
                    .drawWithCache {
                        onDrawBehind {
                            drawCircle(
                                color = ShadowDark.copy(alpha = 0.30f),
                                center = Offset(center.x, center.y + 3.dp.toPx())
                            )
                            drawCircle(brush = Brush.linearGradient(listOf(SurfaceRaised, Surface)))
                            drawCircle(color = Color.White.copy(alpha = 0.9f), style = Stroke(1.dp.toPx()))
                        }
                    }
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (unseenCount > 0) "$unseenCount new messages, jump to latest" else "Jump to latest",
                    tint = CoralStart,
                    modifier = Modifier.size(26.dp)
                )
            }
            if (unseenCount > 0) {
                UnreadCountBadge(count = unseenCount)
            }
        }
    }
}

// ── Unread badge ──

/** Coral pill with the count ("99+" above 99); draws nothing for zero. */
@Composable
fun UnreadCountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .heightIn(min = 20.dp)
            .widthIn(min = 20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(CoralStart, CoralEnd)))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
            color = Color.White,
            maxLines = 1
        )
    }
}

fun lastSeenLabel(lastSeen: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - lastSeen) / 60_000L
    return when {
        minutes < 1 -> "Last seen just now"
        minutes < 60 -> "Last seen $minutes min ago"
        isSameChatDay(lastSeen, now) -> "Last seen today at ${chatTime(lastSeen)}"
        isSameChatDay(lastSeen, now - 86_400_000L) -> "Last seen yesterday at ${chatTime(lastSeen)}"
        else -> "Last seen ${chatDayLabel(lastSeen)}"
    }
}

class ChatSearchState {
    var isOpen by mutableStateOf(false)
    var query by mutableStateOf("")
    var position by mutableIntStateOf(0)
    var highlightedId by mutableStateOf<Int?>(null)

    fun close() {
        isOpen = false
        query = ""
        position = 0
        highlightedId = null
    }
}

@Composable
fun rememberChatSearchState(key: Any?): ChatSearchState = remember(key) { ChatSearchState() }

fun <T> chatSearchMatches(items: List<T>, query: String, text: (T) -> String): List<Int> {
    val q = query.trim()
    if (q.length < 2) return emptyList()
    return items.indices.filter { text(items[it]).contains(q, ignoreCase = true) }
}

@Composable
fun ChatSearchBar(
    state: ChatSearchState,
    matchCount: Int,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(50.dp)
            .skeuoInset(cornerRadius = 25.dp, depth = 3.dp, symmetric = true, shadowStrength = FieldInsetShadow)
            .background(SurfaceRaised.copy(alpha = 0.6f), RoundedCornerShape(25.dp))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = CoralStart.copy(alpha = 0.75f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = state.query,
            onValueChange = {
                state.query = it
                state.position = 0
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
            cursorBrush = SolidColor(CoralStart),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (state.query.isEmpty()) {
                        Text("Search in chat", style = MaterialTheme.typography.bodyLarge, color = Ink.copy(alpha = 0.40f))
                    }
                    inner()
                }
            }
        )
        if (state.query.trim().length >= 2) {
            Text(
                text = if (matchCount == 0) "No results" else "${state.position + 1} of $matchCount",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
                maxLines = 1
            )
        }
        IconButton(onClick = { if (matchCount > 0) state.position = (state.position + 1) % matchCount }, enabled = matchCount > 1) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Older match", tint = if (matchCount > 1) Ink else Muted.copy(alpha = 0.4f))
        }
        IconButton(onClick = { if (matchCount > 0) state.position = (state.position - 1 + matchCount) % matchCount }, enabled = matchCount > 1) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Newer match", tint = if (matchCount > 1) Ink else Muted.copy(alpha = 0.4f))
        }
        IconButton(onClick = { state.close() }) {
            Icon(Icons.Default.Close, contentDescription = "Close search", tint = Muted)
        }
    }
}
