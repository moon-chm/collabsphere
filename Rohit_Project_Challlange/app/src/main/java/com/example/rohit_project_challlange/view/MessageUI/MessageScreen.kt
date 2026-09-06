package com.example.rohit_project_challlange.view.MessageUI

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rohit_project_challlange.model.message.MessageEntity
import com.example.rohit_project_challlange.ui.theme.*
import com.example.rohit_project_challlange.viewmodel.message.MessageViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MessageScreen(
    viewModel: MessageViewModel,
    channelName: String,
    onBack: () -> Unit
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val messageContent by viewModel.messageContent.collectAsStateWithLifecycle()
    val currentUserId = viewModel.currentUserId

    var selectedMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var showActionMenu by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Tactile Skeuomorphic Top Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .drawBehind {
                        // Ambient shadow beneath header
                        drawRect(
                            color = ShadowDark.copy(alpha = 0.14f),
                            topLeft = Offset(0f, size.height),
                            size = Size(size.width, 3.dp.toPx())
                        )
                        drawRect(color = SurfaceRaised)
                        // Top hairline highlight
                        drawRect(
                            color = Color.White.copy(alpha = 0.85f),
                            topLeft = Offset(0f, 0f),
                            size = Size(size.width, 1.dp.toPx())
                        )
                    }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back button
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .drawBehind {
                                drawCircle(
                                    color = ShadowDark.copy(alpha = 0.22f),
                                    radius = size.minDimension / 2f,
                                    center = Offset(center.x + 1.5.dp.toPx(), center.y + 2.dp.toPx())
                                )
                                drawCircle(
                                    color = ShadowLight.copy(alpha = 0.90f),
                                    radius = size.minDimension / 2f,
                                    center = Offset(center.x - 1.5.dp.toPx(), center.y - 1.5.dp.toPx())
                                )
                                drawCircle(
                                    color = Surface,
                                    radius = size.minDimension / 2f
                                )
                            }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Ink,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Title with Channel Tag
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tag,
                            contentDescription = null,
                            tint = CoralStart,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = channelName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Channel indicator
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Mint)
                    )
                }
            }
        },
        bottomBar = {
            // Skeuomorphic Message Input Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .drawBehind {
                        // Dual shadow on top of bottom bar
                        drawRect(
                            color = ShadowDark.copy(alpha = 0.16f),
                            topLeft = Offset(0f, -3.dp.toPx()),
                            size = Size(size.width, 3.dp.toPx())
                        )
                        drawRect(
                            color = ShadowLight.copy(alpha = 0.85f),
                            topLeft = Offset(0f, 0f),
                            size = Size(size.width, 1.dp.toPx())
                        )
                        drawRect(color = SurfaceRaised)
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // Editing banner if active
                if (isEditing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Editing message...",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = IndigoStart
                        )
                        IconButton(
                            onClick = {
                                isEditing = false
                                selectedMessage = null
                                viewModel.onMessageContentChange("")
                            },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel edit",
                                tint = Muted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Debossed text input field
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp, max = 120.dp)
                            .drawBehind {
                                drawRoundRect(
                                    color = ShadowDark.copy(alpha = 0.22f),
                                    topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                                    size = Size(size.width - 1.5.dp.toPx(), size.height - 1.5.dp.toPx()),
                                    cornerRadius = CornerRadius(16.dp.toPx())
                                )
                                drawRoundRect(
                                    color = ShadowLight.copy(alpha = 0.85f),
                                    topLeft = Offset(-1.dp.toPx(), -1.dp.toPx()),
                                    size = Size(size.width + 1.dp.toPx(), size.height + 1.dp.toPx()),
                                    cornerRadius = CornerRadius(16.dp.toPx())
                                )
                                drawRoundRect(
                                    color = Background.copy(alpha = 0.85f),
                                    cornerRadius = CornerRadius(16.dp.toPx())
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = messageContent,
                            onValueChange = { viewModel.onMessageContentChange(it) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (messageContent.trim().isNotEmpty()) {
                                        if (isEditing) {
                                            val currentMsg = selectedMessage
                                            if (currentMsg != null) {
                                                viewModel.changeStatus(
                                                    currentMsg.copy(content = messageContent.trim()),
                                                    currentMsg.status
                                                )
                                            }
                                            isEditing = false
                                            selectedMessage = null
                                            viewModel.onMessageContentChange("")
                                        } else {
                                            viewModel.onSendMessageUser()
                                        }
                                    }
                                }
                            ),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (messageContent.isEmpty()) {
                                        Text(
                                            text = if (isEditing) "Edit message..." else "Message #$channelName...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Muted.copy(alpha = 0.6f)
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }

                    // Tactile Send Button
                    val sendInteractionSource = remember { MutableInteractionSource() }
                    val isSendPressed by sendInteractionSource.collectIsPressedAsState()
                    val sendScale by animateFloatAsState(
                        targetValue = if (isSendPressed) 0.90f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "sendScale"
                    )

                    val canSend = messageContent.trim().isNotEmpty()
                    val sendColor = if (canSend) CoralStart else Muted.copy(alpha = 0.45f)
                    val sendColorEnd = if (canSend) CoralEnd else Muted.copy(alpha = 0.35f)

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer { scaleX = sendScale; scaleY = sendScale }
                            .drawBehind {
                                val shadowOffset = if (isSendPressed) 1.5.dp else 4.dp
                                val shadowAlpha = if (isSendPressed) 0.12f else 0.32f

                                drawCircle(
                                    color = sendColor.copy(alpha = shadowAlpha),
                                    radius = size.minDimension / 2f,
                                    center = Offset(center.x, center.y + shadowOffset.toPx())
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.30f),
                                    radius = size.minDimension / 2f,
                                    center = Offset(center.x - 1.dp.toPx(), center.y - 1.dp.toPx())
                                )
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(sendColor, sendColorEnd),
                                        center = Offset(center.x - 4.dp.toPx(), center.y - 4.dp.toPx()),
                                        radius = size.minDimension / 2f
                                    )
                                )
                                // Specular spot
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.35f),
                                    radius = 7.dp.toPx(),
                                    center = Offset(center.x - 7.dp.toPx(), center.y - 7.dp.toPx())
                                )
                            }
                            .clip(CircleShape)
                            .clickable(
                                enabled = canSend,
                                interactionSource = sendInteractionSource,
                                indication = null
                            ) {
                                if (isEditing) {
                                    val currentMsg = selectedMessage
                                    if (currentMsg != null) {
                                        viewModel.changeStatus(
                                            currentMsg.copy(content = messageContent.trim()),
                                            currentMsg.status
                                        )
                                    }
                                    isEditing = false
                                    selectedMessage = null
                                    viewModel.onMessageContentChange("")
                                } else {
                                    viewModel.onSendMessageUser()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Tactile empty card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                drawRoundRect(
                                    color = ShadowDark.copy(alpha = 0.20f),
                                    topLeft = Offset(4.dp.toPx(), 6.dp.toPx()),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(24.dp.toPx())
                                )
                                drawRoundRect(
                                    color = ShadowLight.copy(alpha = 0.85f),
                                    topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(24.dp.toPx())
                                )
                                drawRoundRect(
                                    color = SurfaceRaised,
                                    cornerRadius = CornerRadius(24.dp.toPx())
                                )
                            }
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tag,
                                    contentDescription = null,
                                    tint = CoralStart,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Text(
                                text = "Welcome to #$channelName",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Ink
                            )

                            Text(
                                text = "This is the start of the #$channelName channel. Send a message to start collaborating!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Muted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id ?: it.hashCode() }) { message ->
                        val isOwnMessage = message.userId == currentUserId

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                            contentAlignment = if (isOwnMessage) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Column(
                                horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start,
                                modifier = Modifier.widthIn(max = 300.dp)
                            ) {
                                // Author tag
                                Text(
                                    text = if (isOwnMessage) "You" else message.userName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = if (isOwnMessage) CoralStart else Muted,
                                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 3.dp)
                                )

                                // Tactile Chat Bubble
                                Box(
                                    modifier = Modifier
                                        .drawBehind {
                                            if (isOwnMessage) {
                                                // Own message: Coral tactile raised bubble
                                                drawRoundRect(
                                                    color = CoralStart.copy(alpha = 0.25f),
                                                    topLeft = Offset(0f, 3.dp.toPx()),
                                                    size = Size(size.width, size.height),
                                                    cornerRadius = CornerRadius(16.dp.toPx())
                                                )
                                                drawRoundRect(
                                                    brush = Brush.linearGradient(
                                                        colors = listOf(CoralLight, CoralStart),
                                                        start = Offset(0f, 0f),
                                                        end = Offset(size.width, size.height)
                                                    ),
                                                    cornerRadius = CornerRadius(16.dp.toPx())
                                                )
                                                // Top hairline specular hugging rounded contour
                                                drawRoundRect(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = 0.35f),
                                                            Color.White.copy(alpha = 0.10f),
                                                            Color.Transparent
                                                        ),
                                                        startY = 0f,
                                                        endY = 16.dp.toPx()
                                                    ),
                                                    topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                                                    size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                                                    cornerRadius = CornerRadius(16.dp.toPx()),
                                                    style = Stroke(width = 1.dp.toPx())
                                                )
                                            } else {
                                                // Incoming message: Surface parchment raised bubble
                                                drawRoundRect(
                                                    color = ShadowDark.copy(alpha = 0.18f),
                                                    topLeft = Offset(2.dp.toPx(), 3.dp.toPx()),
                                                    size = Size(size.width, size.height),
                                                    cornerRadius = CornerRadius(16.dp.toPx())
                                                )
                                                drawRoundRect(
                                                    color = ShadowLight.copy(alpha = 0.85f),
                                                    topLeft = Offset(-1.5.dp.toPx(), -1.5.dp.toPx()),
                                                    size = Size(size.width, size.height),
                                                    cornerRadius = CornerRadius(16.dp.toPx())
                                                )
                                                drawRoundRect(
                                                    color = SurfaceRaised,
                                                    cornerRadius = CornerRadius(16.dp.toPx())
                                                )
                                                // Top hairline hugging rounded contour
                                                drawRoundRect(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = 0.70f),
                                                            Color.White.copy(alpha = 0.15f),
                                                            Color.Transparent
                                                        ),
                                                        startY = 0f,
                                                        endY = 16.dp.toPx()
                                                    ),
                                                    topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                                                    size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                                                    cornerRadius = CornerRadius(16.dp.toPx()),
                                                    style = Stroke(width = 1.dp.toPx())
                                                )
                                            }
                                        }
                                        .clip(RoundedCornerShape(16.dp))
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = {
                                                if (isOwnMessage) {
                                                    selectedMessage = message
                                                    showActionMenu = true
                                                }
                                            }
                                        )
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = message.content,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            lineHeight = 20.sp,
                                            fontSize = 15.sp
                                        ),
                                        color = if (isOwnMessage) Color.White else Ink
                                    )

                                    if (selectedMessage == message && showActionMenu) {
                                        DropdownMenu(
                                            expanded = showActionMenu,
                                            onDismissRequest = { showActionMenu = false },
                                            modifier = Modifier.background(SurfaceRaised)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Edit", color = Ink) },
                                                onClick = {
                                                    showActionMenu = false
                                                    isEditing = true
                                                    viewModel.onMessageContentChange(message.content)
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.Edit,
                                                        contentDescription = null,
                                                        tint = IndigoStart
                                                    )
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete", color = Destructive) },
                                                onClick = {
                                                    showActionMenu = false
                                                    message.id?.let { viewModel.onDeleteMessage(it) }
                                                    selectedMessage = null
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = null,
                                                        tint = Destructive
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}