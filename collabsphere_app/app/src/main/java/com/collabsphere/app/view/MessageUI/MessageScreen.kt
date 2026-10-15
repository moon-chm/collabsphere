package com.collabsphere.app.view.MessageUI

import androidx.compose.material.icons.automirrored.filled.Comment
import com.collabsphere.app.view.components.VoiceMessagePlayer
import com.collabsphere.app.view.components.VoiceRecorder
import com.collabsphere.app.view.components.VoiceRecordingBar
import com.collabsphere.app.view.components.isVoiceMediaUrl
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Search
import com.collabsphere.app.view.components.ChatSearchBar
import com.collabsphere.app.view.components.chatSearchMatches
import com.collabsphere.app.view.components.rememberChatSearchState
import androidx.compose.material.icons.automirrored.filled.Forward
import com.collabsphere.app.view.components.ForwardDialog
import com.collabsphere.app.view.components.ForwardPerson
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.filled.ContentCopy
import com.collabsphere.app.view.components.FullScreenImageViewer
import com.collabsphere.app.view.components.NewMessagesDivider
import com.collabsphere.app.view.components.SwipeToReply
import com.collabsphere.app.view.components.CollabSpinner

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
import androidx.compose.foundation.lazy.itemsIndexed
import com.collabsphere.app.view.components.ChatDateSeparator
import com.collabsphere.app.view.components.JumpToLatestButton
import com.collabsphere.app.view.components.chatTime
import com.collabsphere.app.view.components.isAwayFromLatest
import com.collabsphere.app.view.components.isSameChatDay
import com.collabsphere.app.view.components.rememberJumpToLatestState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import com.collabsphere.app.view.components.AppToast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import com.collabsphere.app.view.components.MessageSkeletonList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.collabsphere.app.model.message.MessageEntity
import com.collabsphere.app.ui.theme.*
import com.collabsphere.app.view.components.ReplyComposerBanner
import com.collabsphere.app.view.components.ReplyQuote
import com.collabsphere.app.view.components.ReactionChipsRow
import com.collabsphere.app.view.components.ReactionPickerDialog
import com.collabsphere.app.view.components.typingLabel
import com.collabsphere.app.view.components.MarkdownText
import com.collabsphere.app.view.components.LinkPreviewCard
import com.collabsphere.app.view.components.seenByLabel
import com.collabsphere.app.view.components.PendingMessageLabel
import com.collabsphere.app.view.components.PinnedMessagesBanner
import com.collabsphere.app.view.components.PinnedMessagesDialog
import com.collabsphere.app.model.MuteRepo
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import org.koin.compose.koinInject
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.automirrored.filled.Reply
import com.collabsphere.app.viewmodel.message.MessageViewModel
import kotlinx.coroutines.launch
import com.collabsphere.app.viewmodel.GitHubUnfurlViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MessageScreen(
    viewModel: MessageViewModel,
    channelName: String,
    onBack: () -> Unit
) {
    val unfurlViewModel: GitHubUnfurlViewModel = koinViewModel()
    val githubPreviews by unfurlViewModel.previews.collectAsStateWithLifecycle()
    val githubActionStates by unfurlViewModel.actionStates.collectAsStateWithLifecycle()
    val githubActionMessages by unfurlViewModel.actionMessages.collectAsStateWithLifecycle()
    
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val messageContent by viewModel.messageContent.collectAsStateWithLifecycle()
    val isLoadingOlder by viewModel.isLoadingOlder.collectAsStateWithLifecycle()
    val replyingTo by viewModel.replyingTo.collectAsStateWithLifecycle()
    val reactions by viewModel.reactions.collectAsStateWithLifecycle()
    val typingUsers by viewModel.typingUsers.collectAsStateWithLifecycle()
    var reactionPickerFor by remember { mutableStateOf<MessageEntity?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val isUploadingMedia by viewModel.isUploadingMedia.collectAsStateWithLifecycle()
    val uploadProgress by viewModel.uploadProgress.collectAsStateWithLifecycle()
    val isSendingMessage by viewModel.isSendingMessage.collectAsStateWithLifecycle()
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()
    val readStates by viewModel.readStates.collectAsStateWithLifecycle()
    var showPinnedDialog by remember { mutableStateOf(false) }
    val muteRepo = koinInject<MuteRepo>()
    val mutes by muteRepo.mutes.collectAsStateWithLifecycle()
    val isChannelMuted = MuteRepo.isChannelMuted(mutes, viewModel.currentWorkspaceId, viewModel.currentChannelId)
    LaunchedEffect(Unit) { muteRepo.refresh() }
    val workspaceApi = koinInject<com.collabsphere.app.remote.workspace.WorkspaceApiService>()
    var canModerate by remember { mutableStateOf(false) }
    var forwardPeople by remember { mutableStateOf<List<ForwardPerson>>(emptyList()) }
    var forwardTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var threadRoot by remember { mutableStateOf<MessageEntity?>(null) }
    val threadSummaries by viewModel.threadSummaries.collectAsStateWithLifecycle()
    val savedStore = koinInject<com.collabsphere.app.model.message.SavedMessagesStore>()
    val savedKeys by savedStore.keys.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { savedStore.refresh() }
    LaunchedEffect(viewModel.currentWorkspaceId) {
        val members = runCatching { workspaceApi.getWorkspaceMembers(viewModel.currentWorkspaceId) }.getOrNull().orEmpty()
        forwardPeople = members.map { ForwardPerson(it.userId, it.userName) }
        val myRole = members.firstOrNull { it.userId == viewModel.currentUserId }?.role
        canModerate = myRole == "OWNER" || myRole == "ADMIN"
    }
    var confirmDeleteFor by remember { mutableStateOf<MessageEntity?>(null) }
    val haptics = LocalHapticFeedback.current
    androidx.lifecycle.compose.LifecycleResumeEffect(viewModel) {
        viewModel.onScreenVisible(true)
        onPauseOrDispose { viewModel.onScreenVisible(false) }
    }
    val clipboard = LocalClipboardManager.current
    val firstUnreadAfterId by viewModel.firstUnreadAfterId.collectAsStateWithLifecycle()
    val dividerMessageId = remember(firstUnreadAfterId, messages?.firstOrNull()?.id) {
        val after = firstUnreadAfterId ?: return@remember null
        messages?.firstOrNull { it.id > after && it.userId != viewModel.currentUserId }?.id
    }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val voiceRecorder = remember { VoiceRecorder(context) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    val startVoice = {
        if (voiceRecorder.start()) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            isRecordingVoice = true
        } else {
            AppToast.error("Couldn't start recording")
        }
    }
    val micPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startVoice() else AppToast.show("Allow microphone access to record voice messages") }
    DisposableEffect(Unit) { onDispose { voiceRecorder.cancel() } }
    LaunchedEffect(Unit) {
        viewModel.uiMessages.collect { AppToast.show(it) }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val payload = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    bytes?.let { Triple(it, mimeType, "channel_image_${System.currentTimeMillis()}") }
                }.getOrNull()
            }
            if (payload == null) {
                AppToast.error("Couldn't read that image.")
            } else {
                viewModel.onSendMedia(payload.first, payload.second, payload.third)
            }
        }
    }
    val messagesById = remember(messages) { messages.orEmpty().associateBy { it.id } }
    val currentUserId = viewModel.currentUserId

    var selectedMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var showActionMenu by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val jumpToLatest = rememberJumpToLatestState(viewModel.currentChannelId)
    // The newest message's row: the "loading older" spinner row sits above the messages when shown.
    val latestRow = (messages?.lastIndex ?: -1) + if (isLoadingOlder) 1 else 0
    val awayFromLatest by remember(latestRow) { derivedStateOf { listState.isAwayFromLatest(latestRow) } }
    LaunchedEffect(awayFromLatest) { if (!awayFromLatest) jumpToLatest.reset() }
    var initialScrollDone by remember { mutableStateOf(false) }
    val search = rememberChatSearchState(viewModel.currentChannelId)
    val searchMatches = remember(messages, search.query) { chatSearchMatches(messages.orEmpty(), search.query) { it.content } }
    val searchTargetIndex = searchMatches.getOrNull(searchMatches.size - 1 - search.position)
    LaunchedEffect(searchTargetIndex, search.isOpen) {
        val index = searchTargetIndex
        if (!search.isOpen || index == null) {
            search.highlightedId = null
            return@LaunchedEffect
        }
        search.highlightedId = messages?.getOrNull(index)?.id
        listState.animateScrollToItem(index + if (isLoadingOlder) 1 else 0)
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex <= 2 && listState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { it }
            .collect { viewModel.loadOlder() }
    }
    LaunchedEffect(dividerMessageId) {
        val id = dividerMessageId ?: return@LaunchedEffect
        if (!initialScrollDone || listState.isScrollInProgress) return@LaunchedEffect
        val index = messages?.indexOfFirst { it.id == id } ?: -1
        if (index >= 0) listState.scrollToItem(index + if (isLoadingOlder) 1 else 0)
    }

    LaunchedEffect(messages?.lastOrNull()?.id) {
        val msgs = messages ?: return@LaunchedEffect
        if (msgs.isEmpty()) return@LaunchedEffect
        val target = msgs.lastIndex + if (isLoadingOlder) 1 else 0

        // Opening the chat lands on the newest message at once instead of animating through history.
        if (!initialScrollDone) {
            val dividerIndex = dividerMessageId?.let { id -> msgs.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
            listState.scrollToItem(dividerIndex?.let { it + if (isLoadingOlder) 1 else 0 } ?: target)
            initialScrollDone = true
            return@LaunchedEffect
        }

        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        val wasNearBottom = lastVisibleIndex == null || lastVisibleIndex >= msgs.size - 2
        val isOwnMessage = msgs.last().userId == currentUserId

        // Only yank the view to the newest message if the user was already reading near the
        // bottom, or it's their own outgoing message — not while they've scrolled up to read history;
        // then the jump-to-latest button counts it instead.
        if (wasNearBottom || isOwnMessage) {
            listState.animateScrollToItem(target)
        } else {
            jumpToLatest.onNewMessages(1)
        }
    }

    confirmDeleteFor?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDeleteFor = null },
            containerColor = SurfaceRaised,
            title = {
                Text(
                    "Delete message?",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Ink
                )
            },
            text = {
                Text(
                    "This removes ${target.userName}'s message for everyone in the channel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDeleteMessage(target.id)
                    confirmDeleteFor = null
                }) { Text("Delete", color = Destructive, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteFor = null }) { Text("Cancel", color = Muted) }
            }
        )
    }

    fullscreenImageUrl?.let { imageUrl ->
        FullScreenImageViewer(url = imageUrl, onDismiss = { fullscreenImageUrl = null })
    }

    threadRoot?.let { root ->
        ThreadSheet(root = root, viewModel = viewModel, onDismiss = { threadRoot = null })
    }

    forwardTarget?.let { target ->
        ForwardDialog(
            workspaceId = viewModel.currentWorkspaceId,
            currentUserId = viewModel.currentUserId,
            currentUserName = target.userName.takeIf { target.userId == viewModel.currentUserId }
                ?: forwardPeople.firstOrNull { it.userId == viewModel.currentUserId }?.name.orEmpty(),
            people = forwardPeople,
            content = target.content,
            mediaUrl = target.mediaUrl,
            onDismiss = { forwardTarget = null }
        )
    }

    if (showPinnedDialog) {
        PinnedMessagesDialog(
            pinned = pinned,
            onSelect = { target ->
                showPinnedDialog = false
                val index = messages.orEmpty().indexOfFirst { it.id == target.id }
                if (index >= 0) {
                    coroutineScope.launch { listState.animateScrollToItem(index + if (isLoadingOlder) 1 else 0) }
                } else {
                    AppToast.show("Scroll up to load older messages to see this one.")
                }
            },
            onUnpin = { viewModel.togglePin(it) },
            onDismiss = { showPinnedDialog = false }
        )
    }

    reactionPickerFor?.let { target ->
        ReactionPickerDialog(
            selectedEmojis = reactions[target.id].orEmpty().filterValues { currentUserId in it }.keys,
            onPick = { emoji -> viewModel.toggleReaction(target.id, emoji) },
            onDismiss = { reactionPickerFor = null }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Tactile Skeuomorphic Top Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .drawWithCache {
                        onDrawBehind {
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
                            .drawWithCache {
                                onDrawBehind {
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

                    IconButton(onClick = { if (search.isOpen) search.close() else search.isOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search in channel",
                            tint = if (search.isOpen) CoralStart else IndigoStart
                        )
                    }

                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                muteRepo.setMuted(viewModel.currentWorkspaceId, viewModel.currentChannelId, !isChannelMuted)
                                    .onSuccess {
                                        AppToast.show(if (isChannelMuted) "Channel unmuted" else "Channel muted — you'll still get @mentions")
                                    }
                                    .onFailure {
                                        AppToast.error("Couldn't update mute. Check your connection.")
                                    }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isChannelMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                            contentDescription = if (isChannelMuted) "Unmute channel" else "Mute channel",
                            tint = if (isChannelMuted) Muted else IndigoStart
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
                    .imePadding()
                    .drawWithCache {
                        onDrawBehind {
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
                                viewModel.endEdit()
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

                if (isRecordingVoice) {
                    VoiceRecordingBar(
                        recorder = voiceRecorder,
                        onCancel = {
                            voiceRecorder.cancel()
                            isRecordingVoice = false
                        },
                        onSend = {
                            val file = voiceRecorder.stop()
                            isRecordingVoice = false
                            if (file == null) {
                                AppToast.show("Hold on a little longer to record")
                            } else {
                                viewModel.onSendMedia(file.readBytes(), "audio/mp4", file.name)
                                file.delete()
                            }
                        }
                    )
                }

                if (isUploadingMedia) {
                    com.collabsphere.app.view.components.UploadProgressBar(
                        progress = uploadProgress,
                        onCancel = viewModel::cancelUpload,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                typingLabel(typingUsers)?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Muted,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                    )
                }

                if (!isEditing) {
                    replyingTo?.let { target ->
                        ReplyComposerBanner(
                            authorName = if (target.userId == currentUserId) "yourself" else target.userName,
                            snippet = target.content,
                            onCancel = viewModel::cancelReply,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isEditing) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isUploadingMedia) {
                                CollabSpinner(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = CoralStart
                                )
                            } else {
                                IconButton(onClick = { imagePicker.launch("image/*") }) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = "Attach image",
                                        tint = IndigoStart
                                    )
                                }
                            }
                        }
                    }
                    // Debossed text input field
                    var isComposerFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp, max = 120.dp)
                            .skeuoFocusRing(isComposerFocused, cornerRadius = 24.dp)
                            .skeuoInset(cornerRadius = 24.dp, depth = 3.dp, symmetric = true, shadowStrength = com.collabsphere.app.view.components.FieldInsetShadow)
                            .clip(RoundedCornerShape(24.dp))
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = messageContent,
                            onValueChange = { viewModel.onMessageContentChange(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isComposerFocused = it.isFocused },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                            cursorBrush = SolidColor(CoralStart),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (messageContent.trim().isNotEmpty()) {
                                        if (isEditing) {
                                            val currentMsg = selectedMessage
                                            if (currentMsg != null) {
                                                viewModel.changeStatus(
                                                    currentMsg.copy(content = messageContent.trim(), editedAt = System.currentTimeMillis()),
                                                    currentMsg.status
                                                )
                                            }
                                            isEditing = false
                                            selectedMessage = null
                                            viewModel.endEdit()
                                        } else {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Ink.copy(alpha = 0.40f)
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

                    val showMic = messageContent.isBlank() && !isEditing && !isUploadingMedia && !isRecordingVoice
                    val canSend = (messageContent.trim().isNotEmpty() && !isSendingMessage && !isUploadingMedia) || showMic
                    val sendAlpha = if (canSend) 1f else 0.70f

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer { scaleX = sendScale; scaleY = sendScale }
                            .drawWithCache {
                                onDrawBehind {
                                val shadowOffset = if (isSendPressed) 1.5.dp else 4.dp
                                val shadowAlpha = if (isSendPressed) 0.12f else 0.32f

                                drawCircle(
                                    color = CoralStart.copy(alpha = shadowAlpha * sendAlpha),
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
                                        colors = listOf(
                                            CoralStart.copy(alpha = sendAlpha),
                                            CoralEnd.copy(alpha = sendAlpha)
                                        ),
                                        center = Offset(center.x - 4.dp.toPx(), center.y - 4.dp.toPx()),
                                        radius = size.minDimension / 2f
                                    )
                                )
                                                            }
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
                                            currentMsg.copy(content = messageContent.trim(), editedAt = System.currentTimeMillis()),
                                            currentMsg.status
                                        )
                                    }
                                    isEditing = false
                                    selectedMessage = null
                                    viewModel.endEdit()
                                } else if (showMic) {
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                                        android.content.pm.PackageManager.PERMISSION_GRANTED
                                    ) startVoice() else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                                } else {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.onSendMessageUser()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (showMic) Icons.Default.Mic else Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (showMic) "Record voice message" else "Send",
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
            pinned.firstOrNull()?.let { latest ->
                PinnedMessagesBanner(
                    latest = latest,
                    count = pinned.size,
                    onClick = { showPinnedDialog = true },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(1f)
                )
            }
            androidx.compose.animation.Crossfade(
                targetState = messages,
                label = "messagesCrossfade",
                modifier = Modifier.fillMaxSize()
            ) { state ->
                when {
                    state == null -> {
                        MessageSkeletonList()
                    }
                    state.isEmpty() -> {
                        com.collabsphere.app.view.components.SkeuoEmptyState(
                            icon = Icons.Default.Tag,
                            title = "Welcome to #$channelName",
                            description = "This is the start of the #$channelName channel. Send a message to start collaborating!",
                            actionLabel = null,
                            onAction = {},
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .fadingScrollEdges(listState = listState, top = 24.dp, bottom = 24.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = if (pinned.isNotEmpty()) 76.dp else 12.dp,
                        bottom = 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (isLoadingOlder) {
                        item(key = "loading_older") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CollabSpinner(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = CoralStart
                                )
                            }
                        }
                    }
                    itemsIndexed(messages ?: emptyList(), key = { _, it -> it.id ?: it.hashCode() }) { index, message ->
                        val isOwnMessage = message.userId == currentUserId
                        val sentAt = message.createdAt
                        val previous = messages?.getOrNull(index - 1)
                        val previousSentAt = previous?.createdAt
                        val startsNewDay = sentAt != null && (previousSentAt == null || !isSameChatDay(previousSentAt, sentAt))
                        val showsDivider = message.id == dividerMessageId
                        val continuesGroup = previous != null && !startsNewDay && !showsDivider &&
                            previous.userId == message.userId &&
                            sentAt != null && previousSentAt != null && sentAt - previousSentAt < 5 * 60_000L

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .padding(top = if (continuesGroup) 0.dp else 8.dp)
                                .background(
                                    if (search.highlightedId == message.id) CoralStart.copy(alpha = 0.10f) else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                        if (startsNewDay) {
                            ChatDateSeparator(timestamp = sentAt!!)
                        }
                        if (showsDivider) {
                            NewMessagesDivider()
                        }
                        SwipeToReply(
                            enabled = message.id > 0,
                            onReply = {
                                isEditing = false
                                viewModel.startReply(message)
                            }
                        ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = if (isOwnMessage) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Column(
                                horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start,
                                modifier = Modifier.widthIn(max = 300.dp)
                            ) {
                                val authorLine = if (continuesGroup) {
                                    listOfNotNull(
                                        if (message.editedAt != null) "edited" else null,
                                        if (message.pinnedAt != null) "Pinned" else null
                                    )
                                } else {
                                    listOfNotNull(
                                        if (isOwnMessage) "You" else message.userName,
                                        sentAt?.let(::chatTime),
                                        if (message.editedAt != null) "edited" else null,
                                        if (message.pinnedAt != null) "Pinned" else null
                                    )
                                }
                                if (authorLine.isNotEmpty()) Text(
                                    text = authorLine.joinToString(" · "),
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
                                        .drawWithCache {
                                            onDrawBehind {
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
                                        }
                                        .clip(RoundedCornerShape(16.dp))
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = {
                                                if (isOwnMessage || message.id > 0) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    selectedMessage = message
                                                    showActionMenu = true
                                                }
                                            }
                                        )
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Column {
                                        message.replyToId?.let { targetId ->
                                            val target = messagesById[targetId]
                                            ReplyQuote(
                                                authorName = target?.let { if (it.userId == currentUserId) "You" else it.userName },
                                                snippet = target?.content,
                                                onDarkBubble = isOwnMessage,
                                                onClick = target?.let {
                                                    {
                                                        val index = messages.orEmpty().indexOfFirst { m -> m.id == targetId }
                                                        if (index >= 0) {
                                                            coroutineScope.launch {
                                                                listState.animateScrollToItem(index + if (isLoadingOlder) 1 else 0)
                                                            }
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                        message.mediaUrl?.takeIf { isVoiceMediaUrl(it) }?.let { voiceUrl ->
                                            VoiceMessagePlayer(url = voiceUrl, onDarkBubble = isOwnMessage)
                                        }
                                        message.mediaUrl?.takeUnless { isVoiceMediaUrl(it) }?.let { mediaUrl ->
                                            SubcomposeAsyncImage(
                                                model = ImageRequest.Builder(context).data(mediaUrl).crossfade(true).build(),
                                                contentDescription = "Shared image",
                                                modifier = Modifier
                                                    .widthIn(max = 240.dp)
                                                    .heightIn(max = 240.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable { fullscreenImageUrl = mediaUrl },
                                                contentScale = ContentScale.Crop,
                                                loading = {
                                                    Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                                                        CollabSpinner(
                                                            color = if (isOwnMessage) Color.White else CoralStart,
                                                            modifier = Modifier.size(28.dp),
                                                            strokeWidth = 2.5.dp
                                                        )
                                                    }
                                                }
                                            )
                                            if (message.content.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                            }
                                        }
                                        if (message.content.isNotBlank() || message.mediaUrl == null) {
                                            MarkdownText(
                                                text = message.content,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    lineHeight = 20.sp,
                                                    fontSize = 15.sp
                                                ),
                                                color = if (isOwnMessage) Color.White else Ink,
                                                linkColor = if (isOwnMessage) Color.White else IndigoStart
                                            )
                                            LinkPreviewCard(text = message.content, onDarkBubble = isOwnMessage)
                                            
                                            val githubRegex = Regex("(https://github\\.com/[^/]+/[^/]+/[\\w/]+|https://github\\.com/[^/]+/[^/]+/?(?=\\s|$))")
                                            val matches = githubRegex.findAll(message.content).map { it.value }.toSet()
                                            matches.forEach { url ->
                                                unfurlViewModel.requestUnfurl(viewModel.currentWorkspaceId, url)
                                                val preview = githubPreviews[url]
                                                if (preview != null) {
                                                    com.collabsphere.app.view.components.GitHubUnfurlCard(
                                                        preview = preview,
                                                        actionState = githubActionStates[url],
                                                        actionMessage = githubActionMessages[url],
                                                        onActionClick = { action, body -> 
                                                            unfurlViewModel.performAction(viewModel.currentWorkspaceId, url, action, body)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        if (isOwnMessage && message.id < 0) {
                                            PendingMessageLabel(stale = true, onDarkBubble = true) {
                                                viewModel.retryMessage(message)
                                            }
                                        }
                                    }

                                    if (selectedMessage == message && showActionMenu) {
                                        DropdownMenu(
                                            expanded = showActionMenu,
                                            onDismissRequest = { showActionMenu = false },
                                            modifier = Modifier.background(SurfaceRaised)
                                        ) {
                                            if (message.id > 0) {
                                                DropdownMenuItem(
                                                    text = { Text("Reply", color = Ink) },
                                                    onClick = {
                                                        showActionMenu = false
                                                        isEditing = false
                                                        viewModel.startReply(message)
                                                        selectedMessage = null
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            Icons.AutoMirrored.Filled.Reply,
                                                            contentDescription = null,
                                                            tint = IndigoStart
                                                        )
                                                    }
                                                )
                                            }
                                            if (message.id > 0 && message.threadRootId == null) {
                                                DropdownMenuItem(
                                                    text = { Text("Reply in thread", color = Ink) },
                                                    onClick = {
                                                        showActionMenu = false
                                                        threadRoot = message
                                                        selectedMessage = null
                                                    },
                                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = null, tint = IndigoStart, modifier = Modifier.size(20.dp)) }
                                                )
                                            }
                                            if (message.content.isNotBlank()) {
                                                DropdownMenuItem(
                                                    text = { Text("Copy", color = Ink) },
                                                    onClick = {
                                                        showActionMenu = false
                                                        clipboard.setText(AnnotatedString(message.content))
                                                        AppToast.show("Copied")
                                                        selectedMessage = null
                                                    },
                                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp)) }
                                                )
                                            }
                                            if (message.id > 0) {
                                                DropdownMenuItem(
                                                    text = { Text("Forward", color = Ink) },
                                                    onClick = {
                                                        showActionMenu = false
                                                        forwardTarget = message
                                                        selectedMessage = null
                                                    },
                                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null, tint = IndigoStart, modifier = Modifier.size(20.dp)) }
                                                )
                                                val isSaved = com.collabsphere.app.model.message.SavedMessagesStore.key("CHANNEL", message.id) in savedKeys
                                                DropdownMenuItem(
                                                    text = { Text(if (isSaved) "Unsave" else "Save", color = Ink) },
                                                    onClick = {
                                                        showActionMenu = false
                                                        selectedMessage = null
                                                        coroutineScope.launch {
                                                            savedStore.toggle("CHANNEL", message.id)
                                                                .onSuccess { saved -> AppToast.show(if (saved) "Saved" else "Removed from saved") }
                                                                .onFailure { AppToast.error("Couldn't update saved. Check your connection.") }
                                                        }
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            if (isSaved) Icons.Default.BookmarkRemove else Icons.Default.BookmarkAdd,
                                                            contentDescription = null,
                                                            tint = CoralStart,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                )
                                            }
                                            if (message.id > 0) {
                                                DropdownMenuItem(
                                                    text = { Text("React", color = Ink) },
                                                    onClick = {
                                                        showActionMenu = false
                                                        reactionPickerFor = message
                                                        selectedMessage = null
                                                    },
                                                    leadingIcon = { Icon(Icons.Default.EmojiEmotions, contentDescription = null, tint = CoralStart, modifier = Modifier.size(20.dp)) }
                                                )
                                            }
                                            if (message.id > 0) {
                                                val isPinned = message.pinnedAt != null || pinned.any { it.id == message.id }
                                                DropdownMenuItem(
                                                    text = { Text(if (isPinned) "Unpin" else "Pin", color = Ink) },
                                                    onClick = {
                                                        showActionMenu = false
                                                        viewModel.togglePin(message)
                                                        selectedMessage = null
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            Icons.Default.PushPin,
                                                            contentDescription = null,
                                                            tint = CoralStart
                                                        )
                                                    }
                                                )
                                            }
                                            if (message.id > 0 && !isOwnMessage) {
                                                DropdownMenuItem(
                                                    text = { Text("Mark helpful", color = Ink) },
                                                    onClick = {
                                                        showActionMenu = false
                                                        viewModel.markHelpful(message)
                                                        selectedMessage = null
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            Icons.Default.ThumbUp,
                                                            contentDescription = null,
                                                            tint = MintGreen
                                                        )
                                                    }
                                                )
                                            }
                                            if (isOwnMessage) {
                                                DropdownMenuItem(
                                                    text = { Text("Edit", color = Ink) },
                                                    onClick = {
                                                        showActionMenu = false
                                                        isEditing = true
                                                        viewModel.beginEdit(message.content)
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            Icons.Default.Edit,
                                                            contentDescription = null,
                                                            tint = IndigoStart
                                                        )
                                                    }
                                                )
                                            }
                                            if (isOwnMessage || (canModerate && message.id > 0)) {
                                                DropdownMenuItem(
                                                    text = { Text("Delete", color = Destructive) },
                                                    onClick = {
                                                        showActionMenu = false
                                                        if (isOwnMessage) viewModel.onDeleteMessage(message.id)
                                                        else confirmDeleteFor = message
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

                                ReactionChipsRow(
                                    reactions = reactions[message.id].orEmpty(),
                                    currentUserId = currentUserId,
                                    onToggle = { emoji -> viewModel.toggleReaction(message.id, emoji) }
                                )

                                threadSummaries[message.id]?.let { summary ->
                                    Row(
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(IndigoStart.copy(alpha = 0.08f))
                                            .clickable { threadRoot = message }
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = null, tint = IndigoStart, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = listOfNotNull(
                                                if (summary.count == 1) "1 reply" else "${summary.count} replies",
                                                summary.lastReplyAt?.let(::chatTime)
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = IndigoStart
                                        )
                                    }
                                }

                                if (message.id > 0 && message.id == messages?.lastOrNull()?.id) {
                                    seenByLabel(readStates.values, message, currentUserId)?.let { label ->
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Muted,
                                            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                        }
                        } // Column (day separator + row)
                    }
                } // LazyColumn
                    } // else
                } // when
            } // Crossfade
            if (search.isOpen) {
                ChatSearchBar(
                    state = search,
                    matchCount = searchMatches.size,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(2f)
                )
            }
            JumpToLatestButton(
                visible = !messages.isNullOrEmpty() && awayFromLatest,
                unseenCount = jumpToLatest.unseenCount,
                onClick = {
                    coroutineScope.launch { listState.animateScrollToItem(latestRow.coerceAtLeast(0)) }
                    jumpToLatest.reset()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
            )
        } // Box
    } // Scaffold
} // MessageScreen function
