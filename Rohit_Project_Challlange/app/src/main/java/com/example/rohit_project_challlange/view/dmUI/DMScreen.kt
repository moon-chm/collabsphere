package com.example.rohit_project_challlange.view.dmUI

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.rohit_project_challlange.AppConfig
import com.example.rohit_project_challlange.model.UserEntity
import com.example.rohit_project_challlange.model.dm.DmEntity
import com.example.rohit_project_challlange.ui.theme.*
import com.example.rohit_project_challlange.viewmodel.dm.DmViewModel

private val REACTION_EMOJIS = listOf("👍", "❤️", "😂", "🚀", "👀")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DMScreen(
    viewModel: DmViewModel,
    workspaceId: Int,
    currentUserId: Long,
    initialPartnerId: Int? = null,
    baseUrl: String = AppConfig.BASE_URL,
    onConversationActiveChange: (Boolean) -> Unit = {},
    onExitModule: () -> Unit
) {
    val workspaceMembers by viewModel.workspaceMembers.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val onlineUserIds by viewModel.onlineUserIds.collectAsStateWithLifecycle()
    val typingPartnerIds by viewModel.typingPartnerIds.collectAsStateWithLifecycle()
    val reactions by viewModel.reactions.collectAsStateWithLifecycle()
    val isUploadingMedia by viewModel.isUploadingMedia.collectAsStateWithLifecycle()

    var activeChatPartner by remember { mutableStateOf<UserEntity?>(null) }

    LaunchedEffect(activeChatPartner) {
        onConversationActiveChange(activeChatPartner != null)
    }
    var typedText by remember { mutableStateOf("") }
    var selectedMessage by remember { mutableStateOf<DmEntity?>(null) }
    var editingMessage by remember { mutableStateOf<DmEntity?>(null) }
    var showActionMenu by remember { mutableStateOf(false) }
    var showReactionPickerFor by remember { mutableStateOf<DmEntity?>(null) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }

    val lazyListState = rememberLazyListState()
    val context = LocalContext.current

    val currentPartnerId = activeChatPartner?.id
    val currentMembersList = workspaceMembers
    val isPartnerTyping = activeChatPartner?.let { it.id in typingPartnerIds } ?: false
    val isPartnerOnline = activeChatPartner?.let { it.id in onlineUserIds } ?: false

    // Image/file picker
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val partner = activeChatPartner ?: return@let
            val bytes = context.contentResolver.openInputStream(it)?.readBytes() ?: return@let
            val mimeType = context.contentResolver.getType(it) ?: "image/jpeg"
            val fileName = it.lastPathSegment ?: "dm_media_${System.currentTimeMillis()}"
            viewModel.sendMediaMessage(
                baseUrl = baseUrl,
                workspaceId = workspaceId,
                senderId = currentUserId.toInt(),
                receiverId = partner.id,
                fileBytes = bytes,
                mimeType = mimeType,
                fileName = fileName
            )
        }
    }

    LaunchedEffect(initialPartnerId, currentMembersList) {
        if (initialPartnerId != null && activeChatPartner == null) {
            val partner = currentMembersList.find { it.id == initialPartnerId }
            if (partner != null) {
                activeChatPartner = partner
                viewModel.loadChatHistory(workspaceId, currentUserId.toInt(), partner.id, baseUrl)
            }
        }
    }

    LaunchedEffect(currentMembersList, currentPartnerId) {
        if (currentPartnerId != null) {
            val updatedPartner = currentMembersList.find { it.id == currentPartnerId }
            if (updatedPartner != null) {
                activeChatPartner = updatedPartner
            }
        }
    }

    DisposableEffect(workspaceId, currentUserId, baseUrl) {
        viewModel.loadWorkspaceMembers(workspaceId, baseUrl)
        viewModel.initWebSocketConnection(baseUrl, currentUserId)
        onDispose {}
    }

    LaunchedEffect(messages.size, isPartnerTyping) {
        val totalCount = messages.size + if (isPartnerTyping) 1 else 0
        if (totalCount > 0) {
            lazyListState.animateScrollToItem(totalCount - 1)
        }
    }

    if (activeChatPartner != null) {
        BackHandler {
            activeChatPartner = null
            viewModel.closeChatSessionUi()
        }
    } else {
        BackHandler { onExitModule() }
    }

    // Full-screen image viewer
    fullscreenImageUrl?.let { imgUrl ->
        Dialog(onDismissRequest = { fullscreenImageUrl = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { fullscreenImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context).data(imgUrl).crossfade(true).build(),
                    contentDescription = "Full screen image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    contentScale = ContentScale.Fit,
                    loading = { CircularProgressIndicator(color = CoralStart, modifier = Modifier.size(40.dp)) }
                )
            }
        }
    }

    // Emoji reaction picker popup
    showReactionPickerFor?.let { targetMessage ->
        Dialog(onDismissRequest = { showReactionPickerFor = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .drawBehind {
                        drawRoundRect(
                            color = ShadowDark.copy(alpha = 0.22f),
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
                        drawRoundRect(color = SurfaceRaised, cornerRadius = CornerRadius(24.dp.toPx()))
                    }
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "React to message",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Muted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val partner = activeChatPartner
                        REACTION_EMOJIS.forEach { emoji ->
                            val myReactionMap = reactions[targetMessage.id]
                            val isReacted = myReactionMap?.containsKey(emoji) == true
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .drawBehind {
                                        if (isReacted) {
                                            drawCircle(
                                                color = CoralStart.copy(alpha = 0.25f),
                                                radius = size.minDimension / 2f
                                            )
                                        }
                                        drawCircle(
                                            color = ShadowDark.copy(alpha = 0.14f),
                                            radius = size.minDimension / 2f,
                                            center = Offset(center.x + 1.dp.toPx(), center.y + 2.dp.toPx())
                                        )
                                        drawCircle(
                                            color = if (isReacted) CoralStart.copy(alpha = 0.15f) else SurfaceRaised,
                                            radius = size.minDimension / 2f
                                        )
                                    }
                                    .clip(CircleShape)
                                    .clickable {
                                        if (partner != null) {
                                            viewModel.toggleReaction(
                                                messageId = targetMessage.id,
                                                emoji = emoji,
                                                workspaceId = workspaceId,
                                                receiverId = partner.id
                                            )
                                        }
                                        showReactionPickerFor = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 24.sp)
                            }
                        }
                    }
                }
            }
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
            // ──────────────────── Member Directory ────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
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
                                    drawRoundRect(color = SurfaceRaised, cornerRadius = CornerRadius(12.dp.toPx()))
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
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
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
                                        drawRoundRect(color = SurfaceRaised, cornerRadius = CornerRadius(24.dp.toPx()))
                                    }
                                    .padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(64.dp).clip(CircleShape).background(Surface),
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
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(otherMembers, key = { it.id }) { member ->
                                val isOnline = member.id in onlineUserIds
                                val isTyping = member.id in typingPartnerIds
                                SkeuoMemberRow(
                                    member = member,
                                    isOnline = isOnline,
                                    isTyping = isTyping,
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
            // ──────────────────── Active Conversation ────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Conversation Header
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
                                        drawCircle(color = ShadowDark.copy(alpha = 0.22f), radius = size.minDimension / 2f, center = Offset(center.x + 1.5.dp.toPx(), center.y + 2.dp.toPx()))
                                        drawCircle(color = ShadowLight.copy(alpha = 0.90f), radius = size.minDimension / 2f, center = Offset(center.x - 1.5.dp.toPx(), center.y - 1.5.dp.toPx()))
                                        drawCircle(color = Surface, radius = size.minDimension / 2f)
                                    }
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink, modifier = Modifier.size(20.dp))
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            val partnerInitial = partner.userName.trim().take(1).uppercase().ifEmpty { "U" }
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
                                if (!partner.avatarUrl.isNullOrEmpty()) {
                                    SubcomposeAsyncImage(
                                        model = ImageRequest.Builder(context).data(partner.avatarUrl).crossfade(true).build(),
                                        contentDescription = partner.userName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(38.dp).clip(CircleShape),
                                        error = { Text(text = partnerInitial, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White) }
                                    )
                                } else {
                                    Text(text = partnerInitial, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = partner.userName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    if (isPartnerTyping) {
                                        val infiniteTransition = rememberInfiniteTransition(label = "headerTyping")
                                        val pulseAlpha by infiniteTransition.animateFloat(
                                            initialValue = 0.35f, targetValue = 1f,
                                            animationSpec = infiniteRepeatable(animation = tween(600, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                                            label = "pulseAlpha"
                                        )
                                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(CoralStart.copy(alpha = pulseAlpha)))
                                        Text(text = "typing...", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold), color = CoralStart)
                                    } else if (isPartnerOnline) {
                                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Mint))
                                        Text(text = "Online", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = Mint)
                                    } else {
                                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Muted.copy(alpha = 0.5f)))
                                        Text(text = "Offline", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = Muted)
                                    }
                                }
                            }
                        }
                    }

                    // Chat Messages
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            val isOwnMessage = message.senderId.toLong() == currentUserId
                            val msgReactions = reactions[message.id] ?: emptyMap()
                            val isRead = message.isRead
                            val hasMedia = !message.mediaUrl.isNullOrEmpty()

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
                                    .padding(bottom = if (msgReactions.isNotEmpty()) 8.dp else 2.dp),
                                contentAlignment = if (isOwnMessage) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                Box(
                                    modifier = Modifier.wrapContentSize(),
                                    contentAlignment = if (isOwnMessage) Alignment.BottomEnd else Alignment.BottomStart
                                ) {
                                    // Message bubble
                                    Box(
                                        modifier = Modifier
                                            .widthIn(min = 52.dp, max = 280.dp)
                                            .drawBehind {
                                                if (isOwnMessage) {
                                                    drawRoundRect(
                                                        color = CoralStart.copy(alpha = 0.25f),
                                                        topLeft = Offset(0f, 2.5.dp.toPx()),
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
                                                    drawRoundRect(color = SurfaceRaised, cornerRadius = CornerRadius(16.dp.toPx()))
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
                                                    } else {
                                                        showReactionPickerFor = message
                                                    }
                                                }
                                            )
                                            .padding(horizontal = 14.dp, vertical = 9.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.wrapContentSize(),
                                            horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start
                                        ) {
                                            // Media image
                                            if (hasMedia) {
                                                SubcomposeAsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(message.mediaUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = "Shared image",
                                                    modifier = Modifier
                                                        .widthIn(max = 220.dp)
                                                        .heightIn(max = 220.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .clickable { fullscreenImageUrl = message.mediaUrl },
                                                    contentScale = ContentScale.Crop,
                                                    loading = {
                                                        Box(
                                                            modifier = Modifier.size(120.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            CircularProgressIndicator(
                                                                color = if (isOwnMessage) Color.White else CoralStart,
                                                                modifier = Modifier.size(28.dp),
                                                                strokeWidth = 2.5.dp
                                                            )
                                                        }
                                                    }
                                                )
                                                if (message.dm_content.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                }
                                            }

                                            // Text content
                                            if (message.dm_content.isNotBlank()) {
                                                Text(
                                                    text = message.dm_content,
                                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp, fontSize = 15.sp),
                                                    color = if (isOwnMessage) Color.White else Ink
                                                )
                                            }

                                            // Read receipt (own messages only)
                                            if (isOwnMessage) {
                                                Row(
                                                    horizontalArrangement = Arrangement.End,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (isRead) "✓✓" else "✓",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = if (isRead) Color(0xFF6EE7B7) else Color.White.copy(alpha = 0.70f)
                                                    )
                                                }
                                            }
                                        }

                                        // Action menu for own messages
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
                                                    text = { Text("React", color = Ink) },
                                                    onClick = {
                                                        showReactionPickerFor = message
                                                        showActionMenu = false
                                                        selectedMessage = null
                                                    },
                                                    leadingIcon = { Text("😊", fontSize = 18.sp) }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Edit message", color = Ink) },
                                                    onClick = {
                                                        editingMessage = message
                                                        typedText = message.dm_content
                                                        showActionMenu = false
                                                        selectedMessage = null
                                                    },
                                                    leadingIcon = { Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = CoralStart) }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Delete message", color = Destructive) },
                                                    onClick = {
                                                        viewModel.deleteMessage(message.id, workspaceId, partner.id)
                                                        showActionMenu = false
                                                        selectedMessage = null
                                                        if (editingMessage?.id == message.id) {
                                                            editingMessage = null
                                                            typedText = ""
                                                        }
                                                    },
                                                    leadingIcon = { Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, tint = Destructive) }
                                                )
                                            }
                                        }
                                    }

                                    // Docked Reaction badges overlapping the bottom border of the bubble
                                    if (msgReactions.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier
                                                .offset(
                                                    x = if (isOwnMessage) (-6).dp else 6.dp,
                                                    y = 10.dp
                                                ),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            msgReactions.entries.sortedByDescending { it.value }.forEach { (emoji, count) ->
                                                Box(
                                                    modifier = Modifier
                                                        .drawBehind {
                                                            drawRoundRect(
                                                                color = ShadowDark.copy(alpha = 0.18f),
                                                                topLeft = Offset(0f, 1.5.dp.toPx()),
                                                                size = Size(size.width, size.height),
                                                                cornerRadius = CornerRadius(14.dp.toPx())
                                                            )
                                                            drawRoundRect(
                                                                color = SurfaceRaised,
                                                                cornerRadius = CornerRadius(14.dp.toPx())
                                                            )
                                                            drawRoundRect(
                                                                color = Color.White.copy(alpha = 0.85f),
                                                                cornerRadius = CornerRadius(14.dp.toPx()),
                                                                style = Stroke(width = 1.dp.toPx())
                                                            )
                                                        }
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .clickable {
                                                            viewModel.toggleReaction(
                                                                messageId = message.id,
                                                                emoji = emoji,
                                                                workspaceId = workspaceId,
                                                                receiverId = partner.id
                                                            )
                                                        }
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Text(text = emoji, fontSize = 12.sp)
                                                        if (count > 1) {
                                                            Text(
                                                                text = count.toString(),
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 10.sp
                                                                ),
                                                                color = Ink
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

                        if (isPartnerTyping) {
                            item(key = "typing_bubble") {
                                SkeuoTypingBubble(
                                    partnerName = partner.userName,
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }

                    // ── Input Bar ───────────────────────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                    ) {
                        // Editing banner
                        if (editingMessage != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .drawBehind {
                                        drawRoundRect(
                                            color = ShadowDark.copy(alpha = 0.10f),
                                            topLeft = Offset(0f, 2.dp.toPx()),
                                            size = Size(size.width, size.height),
                                            cornerRadius = CornerRadius(12.dp.toPx())
                                        )
                                        drawRoundRect(color = SurfaceRaised, cornerRadius = CornerRadius(12.dp.toPx()))
                                        drawRoundRect(
                                            color = CoralStart.copy(alpha = 0.5f),
                                            topLeft = Offset(0f, 0f),
                                            size = Size(4.dp.toPx(), size.height),
                                            cornerRadius = CornerRadius(4.dp.toPx())
                                        )
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = CoralStart, modifier = Modifier.size(16.dp))
                                    Column {
                                        Text(text = "Editing message", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CoralStart)
                                        Text(
                                            text = editingMessage!!.dm_content,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(onClick = { editingMessage = null; typedText = "" }, modifier = Modifier.size(28.dp)) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel Edit", tint = Muted, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        // Media upload progress bar
                        AnimatedVisibility(
                            visible = isUploadingMedia,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(3.dp),
                                color = CoralStart,
                                trackColor = CoralStart.copy(alpha = 0.15f)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawBehind {
                                    drawRect(color = ShadowDark.copy(alpha = 0.16f), topLeft = Offset(0f, -3.dp.toPx()), size = Size(size.width, 3.dp.toPx()))
                                    drawRect(color = ShadowLight.copy(alpha = 0.85f), topLeft = Offset(0f, 0f), size = Size(size.width, 1.dp.toPx()))
                                    drawRect(color = SurfaceRaised)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Attach button
                                val attachInteractionSource = remember { MutableInteractionSource() }
                                val isAttachPressed by attachInteractionSource.collectIsPressedAsState()
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .drawBehind {
                                            drawCircle(color = ShadowDark.copy(alpha = if (isAttachPressed) 0.10f else 0.20f), radius = size.minDimension / 2f, center = Offset(center.x + 1.dp.toPx(), center.y + 2.dp.toPx()))
                                            drawCircle(color = ShadowLight.copy(alpha = 0.90f), radius = size.minDimension / 2f, center = Offset(center.x - 1.dp.toPx(), center.y - 1.dp.toPx()))
                                            drawCircle(color = SurfaceRaised, radius = size.minDimension / 2f)
                                        }
                                        .clip(CircleShape)
                                        .clickable(
                                            interactionSource = attachInteractionSource,
                                            indication = null,
                                            enabled = !isUploadingMedia
                                        ) {
                                            mediaPicker.launch("*/*")
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = "Attach file",
                                        tint = if (isUploadingMedia) Muted else CoralStart,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Debossed message field
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp, max = 120.dp)
                                        .skeuoInset(cornerRadius = 16.dp, depth = 2.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    BasicTextField(
                                        value = typedText,
                                        onValueChange = { newText ->
                                            typedText = newText
                                            if (newText.isNotBlank()) {
                                                viewModel.onUserTyping(workspaceId, partner.id)
                                            } else {
                                                viewModel.onUserStoppedTyping(workspaceId, partner.id)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                                        cursorBrush = SolidColor(CoralStart),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                        keyboardActions = KeyboardActions(
                                            onSend = {
                                                if (typedText.trim().isNotEmpty()) {
                                                    val textToSend = typedText.trim()
                                                    val currentlyEditing = editingMessage
                                                    if (currentlyEditing != null) {
                                                        viewModel.updateMessage(dmId = currentlyEditing.id, workspaceId = workspaceId, receiverId = partner.id, newContent = textToSend)
                                                        editingMessage = null; typedText = ""
                                                    } else {
                                                        viewModel.sendMessage(id = 0, workspaceId = workspaceId, senderId = currentUserId.toInt(), receiverId = partner.id, content = textToSend)
                                                        typedText = ""
                                                    }
                                                }
                                            }
                                        ),
                                        decorationBox = { inner ->
                                            Box(contentAlignment = Alignment.CenterStart) {
                                                if (typedText.isEmpty()) {
                                                    Text(
                                                        text = if (editingMessage != null) "Edit message..." else "Message ${partner.userName}...",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = Ink.copy(alpha = 0.65f)
                                                    )
                                                }
                                                inner()
                                            }
                                        }
                                    )
                                }

                                // Send / Update Button
                                val sendInteractionSource = remember { MutableInteractionSource() }
                                val isSendPressed by sendInteractionSource.collectIsPressedAsState()
                                val canSend = typedText.trim().isNotEmpty() && !isUploadingMedia
                                val sendScale by animateFloatAsState(
                                    targetValue = if (isSendPressed) 0.90f else if (canSend) 1f else 0.88f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                                    label = "dmSendScale"
                                )
                                val sendAlpha = if (canSend) 1f else 0.70f

                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .graphicsLayer { scaleX = sendScale; scaleY = sendScale }
                                        .drawBehind {
                                            val shadowOffset = if (isSendPressed) 1.5.dp else 4.dp
                                            val shadowAlpha = if (isSendPressed) 0.12f else 0.32f
                                            drawCircle(color = CoralStart.copy(alpha = shadowAlpha * sendAlpha), radius = size.minDimension / 2f, center = Offset(center.x, center.y + shadowOffset.toPx()))
                                            drawCircle(color = Color.White.copy(alpha = 0.30f), radius = size.minDimension / 2f, center = Offset(center.x - 1.dp.toPx(), center.y - 1.dp.toPx()))
                                            drawCircle(brush = Brush.radialGradient(colors = listOf(CoralStart.copy(alpha = sendAlpha), CoralEnd.copy(alpha = sendAlpha)), center = Offset(center.x - 4.dp.toPx(), center.y - 4.dp.toPx()), radius = size.minDimension / 2f))
                                            drawCircle(color = Color.White.copy(alpha = 0.35f), radius = 7.dp.toPx(), center = Offset(center.x - 7.dp.toPx(), center.y - 7.dp.toPx()))
                                        }
                                        .clip(CircleShape)
                                        .clickable(
                                            enabled = canSend,
                                            interactionSource = sendInteractionSource,
                                            indication = null
                                        ) {
                                            if (typedText.trim().isNotEmpty()) {
                                                val textToSend = typedText.trim()
                                                val currentlyEditing = editingMessage
                                                if (currentlyEditing != null) {
                                                    viewModel.updateMessage(dmId = currentlyEditing.id, workspaceId = workspaceId, receiverId = partner.id, newContent = textToSend)
                                                    editingMessage = null; typedText = ""
                                                } else {
                                                    viewModel.sendMessage(id = 0, workspaceId = workspaceId, senderId = currentUserId.toInt(), receiverId = partner.id, content = textToSend)
                                                    typedText = ""
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (editingMessage != null) Icons.Default.Check else Icons.AutoMirrored.Filled.Send,
                                        contentDescription = if (editingMessage != null) "Update Message" else "Send Message",
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
}

@Composable
fun SkeuoMemberRow(
    member: UserEntity,
    isOnline: Boolean = false,
    isTyping: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "memberScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .skeuoFloatingCard(cornerRadius = 18.dp, isPressed = isPressed)
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
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
                val context = LocalContext.current
                val initial = member.userName.trim().take(1).uppercase().ifEmpty { "U" }

                Box(contentAlignment = Alignment.BottomEnd) {
                    if (!member.avatarUrl.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .drawBehind {
                                    drawCircle(brush = Brush.radialGradient(colors = listOf(CoralLight, CoralStart)))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context).data(member.avatarUrl).crossfade(true).build(),
                                contentDescription = member.userName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(46.dp).clip(CircleShape),
                                error = {
                                    Text(text = initial, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp), color = Color.White)
                                }
                            )
                        }
                    } else {
                        SkeuoDebossedIconWell(wellSize = 46.dp, cornerRadius = 14.dp) {
                            Text(text = initial, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp), color = Color(0xFF2C221E))
                        }
                    }

                    if (isTyping) {
                        Box(modifier = Modifier.size(13.dp).clip(CircleShape).background(Color.White).padding(1.5.dp).clip(CircleShape).background(CoralStart))
                    } else if (isOnline) {
                        Box(modifier = Modifier.size(13.dp).clip(CircleShape).background(Color.White).padding(1.5.dp).clip(CircleShape).background(Mint))
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = member.userName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.5.sp), color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(2.dp))
                    if (isTyping) {
                        Text(text = "typing...", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp), color = CoralStart)
                    } else if (isOnline) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Mint))
                            Text(text = "Online", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = Mint)
                        }
                    } else {
                        Text(text = member.statusMessage?.takeIf { it.isNotBlank() } ?: "Tap to message", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Muted.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        }
    }
}

/** Skeuomorphic animated typing bubble with bouncing dots */
@Composable
fun SkeuoTypingBubble(
    partnerName: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bouncingDots")
    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -4.5f,
        animationSpec = infiniteRepeatable(animation = tween(380, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "dot1"
    )
    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -4.5f,
        animationSpec = infiniteRepeatable(animation = tween(380, delayMillis = 130, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "dot2"
    )
    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -4.5f,
        animationSpec = infiniteRepeatable(animation = tween(380, delayMillis = 260, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "dot3"
    )

    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .drawBehind {
                    drawRoundRect(color = ShadowDark.copy(alpha = 0.12f), topLeft = Offset(1.dp.toPx(), 2.dp.toPx()), size = Size(size.width, size.height), cornerRadius = CornerRadius(14.dp.toPx()))
                    drawRoundRect(color = ShadowLight.copy(alpha = 0.85f), topLeft = Offset(-1.dp.toPx(), -1.dp.toPx()), size = Size(size.width, size.height), cornerRadius = CornerRadius(14.dp.toPx()))
                    drawRoundRect(color = SurfaceRaised, cornerRadius = CornerRadius(14.dp.toPx()))
                }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.offset(y = dot1Offset.dp).size(5.5.dp).clip(CircleShape).background(CoralStart))
                Box(modifier = Modifier.offset(y = dot2Offset.dp).size(5.5.dp).clip(CircleShape).background(CoralStart))
                Box(modifier = Modifier.offset(y = dot3Offset.dp).size(5.5.dp).clip(CircleShape).background(CoralStart))
            }
            Text(text = "$partnerName is typing...", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium), color = Ink.copy(alpha = 0.75f))
        }
    }
}