package com.example.rohit_project_challlange.view.dmUI

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
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
import com.example.rohit_project_challlange.model.UserEntity
import com.example.rohit_project_challlange.model.dm.DmEntity
import com.example.rohit_project_challlange.ui.theme.*
import com.example.rohit_project_challlange.viewmodel.dm.DmViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DMScreen(
    viewModel: DmViewModel,
    workspaceId: Int,
    currentUserId: Long,
    baseUrl: String = com.example.rohit_project_challlange.AppConfig.BASE_URL,
    onExitModule: () -> Unit
) {
    val workspaceMembers by viewModel.workspaceMembers.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()

    var activeChatPartner by remember { mutableStateOf<UserEntity?>(null) }
    var typedText by remember { mutableStateOf("") }
    var selectedMessage by remember { mutableStateOf<DmEntity?>(null) }
    var showActionMenu by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()

    val currentPartnerId = activeChatPartner?.id
    val currentMembersList = workspaceMembers

    LaunchedEffect(currentMembersList, currentPartnerId) {
        if (currentPartnerId != null) {
            val updatedPartner = currentMembersList.find { it.id == currentPartnerId }
            if (updatedPartner != null) {
                activeChatPartner = updatedPartner
            }
        }
    }

    DisposableEffect(workspaceId, currentUserId, baseUrl) {
        viewModel.loadWorkspaceMembers(workspaceId)
        viewModel.initWebSocketConnection(baseUrl, currentUserId)
        onDispose {}
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            lazyListState.animateScrollToItem(messages.size - 1)
        }
    }

    if (activeChatPartner != null) {
        BackHandler {
            activeChatPartner = null
            viewModel.closeChatSessionUi()
        }
    } else {
        BackHandler {
            onExitModule()
        }
    }

    AnimatedContent(
        targetState = activeChatPartner,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    initialOffsetX = { width -> width }
                ) + fadeIn(animationSpec = tween(200)))
                    .togetherWith(
                        slideOutHorizontally(
                            animationSpec = tween(280, easing = FastOutSlowInEasing),
                            targetOffsetX = { width -> -width / 4 }
                        ) + fadeOut(animationSpec = tween(200))
                    )
            } else {
                (slideInHorizontally(
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    initialOffsetX = { width -> -width / 4 }
                ) + fadeIn(animationSpec = tween(200)))
                    .togetherWith(
                        slideOutHorizontally(
                            animationSpec = tween(280, easing = FastOutSlowInEasing),
                            targetOffsetX = { width -> width }
                        ) + fadeOut(animationSpec = tween(200))
                    )
            }
        },
        label = "dmConversationTransition"
    ) { partner ->
        if (partner == null) {
            // Direct Messages Member Directory View
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Direct messages",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    val otherMembersCount = workspaceMembers.filter { it.id != currentUserId.toInt() }.size
                    Box(
                        modifier = Modifier
                            .drawBehind {
                                drawRoundRect(
                                    color = ShadowDark.copy(alpha = 0.12f),
                                    topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                                drawRoundRect(
                                    color = SurfaceRaised,
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$otherMembersCount members",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Muted
                        )
                    }
                }

                val otherMembers = workspaceMembers.filter { it.id != currentUserId.toInt() }

                if (otherMembers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
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
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                    .background(Surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Forum,
                                        contentDescription = null,
                                        tint = CoralStart,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Text(
                                    text = "No team members yet",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Ink
                                )

                                Text(
                                    text = "Invite members to this workspace using the '+' button in the top bar to begin chatting.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Muted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(
                            start = 18.dp,
                            end = 18.dp,
                            top = 6.dp,
                            bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(otherMembers, key = { it.id }) { member ->
                            SkeuoMemberRow(
                                member = member,
                                modifier = Modifier.animateItem(),
                                onClick = {
                                    activeChatPartner = member
                                    viewModel.loadChatHistory(workspaceId, currentUserId.toInt(), member.id, baseUrl)
                                }
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Active 1:1 Conversation View
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Tactile Conversation Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(64.dp)
                        .drawBehind {
                            drawRect(
                                color = ShadowDark.copy(alpha = 0.14f),
                                topLeft = Offset(0f, size.height),
                                size = Size(size.width, 3.dp.toPx())
                            )
                            drawRect(color = SurfaceRaised)
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                activeChatPartner = null
                                viewModel.closeChatSessionUi()
                            },
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

                        Spacer(modifier = Modifier.width(12.dp))

                        // Partner Avatar
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .drawBehind {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(IndigoStart, IndigoEnd),
                                            center = Offset(center.x - 4.dp.toPx(), center.y - 4.dp.toPx()),
                                            radius = size.minDimension / 2f
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val initial = partner.userName.trim().take(1).uppercase().ifEmpty { "U" }
                            Text(
                                text = initial,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = partner.userName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Mint)
                                )
                                Text(
                                    text = "Active session",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = Muted
                                )
                            }
                        }
                    }
                }

                // Chat Messages
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        val isOwnMessage = message.senderId.toLong() == currentUserId

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
                                Box(
                                    modifier = Modifier
                                        .drawBehind {
                                            if (isOwnMessage) {
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
                                                // Top hairline specular hugging rounded contour
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
                                        text = message.dm_content,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            lineHeight = 20.sp,
                                            fontSize = 15.sp
                                        ),
                                        color = if (isOwnMessage) Color.White else Ink
                                    )

                                    if (showActionMenu && selectedMessage?.id == message.id) {
                                        DropdownMenu(
                                            expanded = showActionMenu,
                                            onDismissRequest = {
                                                showActionMenu = false
                                                selectedMessage = null
                                            },
                                            modifier = Modifier.background(SurfaceRaised)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Delete message", color = Destructive) },
                                                onClick = {
                                                    viewModel.deleteMessage(message.id, workspaceId)
                                                    showActionMenu = false
                                                    selectedMessage = null
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteOutline,
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

                // Skeuomorphic Message Input Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .drawBehind {
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Debossed message field
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
                                value = typedText,
                                onValueChange = { typedText = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (typedText.trim().isNotEmpty()) {
                                            viewModel.sendMessage(
                                                id = 0,
                                                workspaceId = workspaceId,
                                                senderId = currentUserId.toInt(),
                                                receiverId = partner.id,
                                                content = typedText.trim()
                                            )
                                            typedText = ""
                                        }
                                    }
                                ),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (typedText.isEmpty()) {
                                            Text(
                                                text = "Message ${partner.userName}...",
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
                        val canSend = typedText.trim().isNotEmpty()

                        val sendScale by animateFloatAsState(
                            targetValue = if (isSendPressed) 0.90f else if (canSend) 1f else 0.88f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "dmSendScale"
                        )

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
                                    if (typedText.trim().isNotEmpty()) {
                                        viewModel.sendMessage(
                                            id = 0,
                                            workspaceId = workspaceId,
                                            senderId = currentUserId.toInt(),
                                            receiverId = partner.id,
                                            content = typedText.trim()
                                        )
                                        typedText = ""
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Message",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun SkeuoMemberRow(
    member: UserEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "memberScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .skeuoFloatingCard(cornerRadius = 18.dp, isPressed = isPressed)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Member Initial in debossed squircle well
                val initial = member.userName.trim().take(1).uppercase().ifEmpty { "U" }
                SkeuoDebossedIconWell(wellSize = 46.dp, cornerRadius = 14.dp) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = Color(0xFF2C221E)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = member.userName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.5.sp
                        ),
                        color = Color(0xFF1F1A17),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tap to open conversation",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                        color = Color(0xFF6E635C),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFFA89E97),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}