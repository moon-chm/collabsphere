package com.collabsphere.app.view.MessageUI

import androidx.compose.foundation.border
import com.collabsphere.app.view.components.chatSendButton
import com.collabsphere.app.view.components.chatComposerField
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.collabsphere.app.model.message.MessageEntity
import com.collabsphere.app.ui.theme.*
import com.collabsphere.app.view.components.FieldInsetShadow
import com.collabsphere.app.view.components.MarkdownText
import com.collabsphere.app.view.components.chatDayLabel
import com.collabsphere.app.view.components.chatTime
import com.collabsphere.app.view.components.isSameChatDay
import com.collabsphere.app.viewmodel.message.MessageViewModel

private fun sentLabel(at: Long?): String {
    if (at == null) return ""
    return if (isSameChatDay(at, System.currentTimeMillis())) chatTime(at) else "${chatDayLabel(at)} ${chatTime(at)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadSheet(
    root: MessageEntity,
    viewModel: MessageViewModel,
    onDismiss: () -> Unit
) {
    val replies by remember(root.id) { viewModel.threadReplies(root.id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f

    LaunchedEffect(replies.size) {
        if (replies.isNotEmpty()) listState.animateScrollToItem(replies.size)
    }

    fun send() {
        if (draft.isBlank()) return
        viewModel.sendThreadReply(root.id, draft)
        draft = ""
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PrintWhite,
        scrimColor = PrintInk.copy(alpha = 0.45f),
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
        dragHandle = { PrintSheetHandle() },
        shape = PrintSheetShape,
        modifier = Modifier.printSheetOutline()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .imePadding()
        ) {
            Text(
                text = "Thread",
                style = PrintType.H2.copy(fontSize = 24.sp, lineHeight = 28.sp),
                color = PrintInk,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "root") {
                    ThreadMessageRow(root, isRoot = true, isOwn = root.userId == viewModel.currentUserId)
                }
                item(key = "count") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = when (replies.size) {
                                0 -> "No replies yet"
                                1 -> "1 reply"
                                else -> "${replies.size} replies"
                            },
                            style = PrintType.Tiny,
                            color = PrintInk
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f).height(1.5.dp).background(PrintInk.copy(alpha = 0.2f)))
                    }
                }
                items(replies, key = { it.id }) { reply ->
                    ThreadMessageRow(reply, isRoot = false, isOwn = reply.userId == viewModel.currentUserId)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 120.dp)
                        .chatComposerField(false)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = PrintInk),
                        cursorBrush = SolidColor(PrintInk),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (draft.isEmpty()) Text("Reply in thread…", style = MaterialTheme.typography.bodyLarge, color = Muted)
                                inner()
                            }
                        }
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .chatSendButton(enabled = draft.isNotBlank(), pressProgress = { 0f })
                        .clickable(enabled = draft.isNotBlank()) { send() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send reply", tint = if (draft.isNotBlank()) PrintInk else Muted, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun ThreadMessageRow(message: MessageEntity, isRoot: Boolean, isOwn: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isRoot) PrintPaper else Color.Transparent)
            .border(if (isRoot) 1.5.dp else 0.dp, if (isRoot) PrintInk else Color.Transparent, RoundedCornerShape(16.dp))
            .padding(horizontal = if (isRoot) 14.dp else 4.dp, vertical = if (isRoot) 12.dp else 2.dp)
    ) {
        Text(
            text = listOfNotNull(
                if (isOwn) "You" else message.userName,
                sentLabel(message.createdAt).ifBlank { null },
                if (message.editedAt != null) "edited" else null
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (isOwn) PrintInk else Muted
        )
        Spacer(Modifier.height(3.dp))
        if (message.content.isNotBlank()) {
            MarkdownText(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = PrintInk,
                linkColor = PrintInk
            )
        } else if (message.mediaUrl != null) {
            Text("Attachment", style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        if (message.id < 0) {
            Text("Sending…", style = PrintType.Caption, color = Muted)
        }
    }
}
