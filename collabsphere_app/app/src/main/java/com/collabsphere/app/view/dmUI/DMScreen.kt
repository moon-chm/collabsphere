package com.collabsphere.app.view.dmUI

import com.collabsphere.app.view.components.ScheduleSendDialog
import com.collabsphere.app.view.components.ScheduledMessagesChip
import com.collabsphere.app.view.components.ScheduledMessagesDialog
import com.collabsphere.app.view.components.rememberScheduledMessages
import com.collabsphere.app.view.components.scheduleLabel
import com.collabsphere.app.view.components.VoiceMessagePlayer
import com.collabsphere.app.view.components.VoiceRecorder
import com.collabsphere.app.view.components.VoiceRecordingBar
import com.collabsphere.app.view.components.isVoiceMediaUrl
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.filled.Search
import com.collabsphere.app.view.components.ChatSearchBar
import com.collabsphere.app.view.components.chatSearchMatches
import com.collabsphere.app.view.components.rememberChatSearchState
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.PushPin
import com.collabsphere.app.view.components.ForwardDialog
import com.collabsphere.app.view.components.ForwardPerson
import com.collabsphere.app.view.components.PinnedMessagesBanner
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.filled.ContentCopy
import com.collabsphere.app.view.components.FullScreenImageViewer
import com.collabsphere.app.view.components.MessageStatusTicks
import com.collabsphere.app.view.components.NewMessagesDivider
import com.collabsphere.app.view.components.SwipeToReply
import com.collabsphere.app.view.components.JumpToLatestButton
import com.collabsphere.app.view.components.UnreadCountBadge
import com.collabsphere.app.view.components.isAwayFromLatest
import com.collabsphere.app.view.components.rememberJumpToLatestState
import com.collabsphere.app.view.components.CollabSpinner

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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.collabsphere.app.model.DraftStore
import org.koin.compose.koinInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import androidx.compose.material.icons.automirrored.filled.Reply
import com.collabsphere.app.view.components.ReplyComposerBanner
import com.collabsphere.app.view.components.ReplyQuote
import com.collabsphere.app.view.components.MarkdownText
import com.collabsphere.app.view.components.LinkPreviewCard
import com.collabsphere.app.view.components.PendingMessageLabel
import com.collabsphere.app.view.components.AppToast
import com.collabsphere.app.view.components.isStalePending
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
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
import com.collabsphere.app.viewmodel.GitHubUnfurlViewModel
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material.icons.filled.Keyboard
import com.collabsphere.app.view.components.ChatBubbleShape
import com.collabsphere.app.view.components.ChatBubbleTail
import com.collabsphere.app.view.components.ComposerAction
import com.collabsphere.app.view.components.ComposerActionsButton
import com.collabsphere.app.view.components.ComposerEmojiPanel
import com.collabsphere.app.view.components.OwnBubbleColor
import com.collabsphere.app.view.components.insertAtCursor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.collabsphere.app.AppConfig
import com.collabsphere.app.model.UserEntity
import com.collabsphere.app.model.dm.DmEntity
import com.collabsphere.app.ui.theme.*
import com.collabsphere.app.viewmodel.dm.DmViewModel

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.collabsphere.app.view.components.UserListSkeleton


private fun formatDmDateHeader(timestamp: Long): String {
    val messageCal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val todayCal = java.util.Calendar.getInstance()
    val yesterdayCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }

    return when {
        messageCal.get(java.util.Calendar.YEAR) == todayCal.get(java.util.Calendar.YEAR) &&
        messageCal.get(java.util.Calendar.DAY_OF_YEAR) == todayCal.get(java.util.Calendar.DAY_OF_YEAR) -> "Today"

        messageCal.get(java.util.Calendar.YEAR) == yesterdayCal.get(java.util.Calendar.YEAR) &&
        messageCal.get(java.util.Calendar.DAY_OF_YEAR) == yesterdayCal.get(java.util.Calendar.DAY_OF_YEAR) -> "Yesterday"

        messageCal.get(java.util.Calendar.YEAR) == todayCal.get(java.util.Calendar.YEAR) -> {
            java.text.SimpleDateFormat("MMMM d", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        }
        else -> {
            java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        }
    }
}

private fun formatDmTime(timestamp: Long): String {
    return java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
}

private fun isSameDay(t1: Long, t2: Long): Boolean {
    val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = t1 }
    val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = t2 }
    return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
           cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
}

private fun DrawScope.drawWhatsAppTexture(baseTint: Color) {
    val stepX = 64.dp.toPx()
    val stepY = 64.dp.toPx()
    val motifColor = baseTint.copy(alpha = 0.042f)
    val stroke = Stroke(width = 1.2.dp.toPx())

    var row = 0
    var y = 20.dp.toPx()
    while (y < size.height) {
        val offsetX = if (row % 2 == 1) stepX / 2f else 0f
        var x = offsetX
        var col = 0
        while (x < size.width) {
            when ((row + col) % 4) {
                0 -> {
                    drawRoundRect(
                        color = motifColor,
                        topLeft = Offset(x, y),
                        size = Size(14.dp.toPx(), 10.dp.toPx()),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                        style = stroke
                    )
                }
                1 -> {
                    val cx = x + 7.dp.toPx()
                    val cy = y + 5.dp.toPx()
                    val r = 4.dp.toPx()
                    drawLine(motifColor, Offset(cx - r, cy), Offset(cx + r, cy), strokeWidth = 1.2.dp.toPx())
                    drawLine(motifColor, Offset(cx, cy - r), Offset(cx, cy + r), strokeWidth = 1.2.dp.toPx())
                }
                2 -> {
                    val cx = x + 7.dp.toPx()
                    val cy = y + 5.dp.toPx()
                    val path = Path().apply {
                        moveTo(cx, cy - 4.dp.toPx())
                        lineTo(cx + 4.dp.toPx(), cy)
                        lineTo(cx, cy + 4.dp.toPx())
                        lineTo(cx - 4.dp.toPx(), cy)
                        close()
                    }
                    drawPath(path, motifColor, style = stroke)
                }
                3 -> {
                    drawCircle(
                        color = motifColor,
                        radius = 3.5.dp.toPx(),
                        center = Offset(x + 7.dp.toPx(), y + 5.dp.toPx()),
                        style = stroke
                    )
                }
            }
            x += stepX
            col++
        }
        y += stepY
        row++
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DMScreen(
    viewModel: DmViewModel,
    workspaceId: Int,
    currentUserId: Long,
    initialPartnerId: Int? = null,
    baseUrl: String = AppConfig.BASE_URL,
    onConversationActiveChange: (Boolean) -> Unit = {},
    onActivePartnerChange: (Int?) -> Unit = {},
    onExitModule: () -> Unit
) {
    val unfurlViewModel: GitHubUnfurlViewModel = koinViewModel()
    val githubPreviews by unfurlViewModel.previews.collectAsStateWithLifecycle()
    val githubActionStates by unfurlViewModel.actionStates.collectAsStateWithLifecycle()
    val githubActionMessages by unfurlViewModel.actionMessages.collectAsStateWithLifecycle()

    val workspaceMembers by viewModel.workspaceMembers.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoadingOlder by viewModel.isLoadingOlder.collectAsStateWithLifecycle()
    val onlineUserIds by viewModel.onlineUserIds.collectAsStateWithLifecycle()
    val typingPartnerIds by viewModel.typingPartnerIds.collectAsStateWithLifecycle()
    val reactions by viewModel.reactions.collectAsStateWithLifecycle()
    val myReactedEmojis by viewModel.myReactedEmojis.collectAsStateWithLifecycle()
    val isUploadingMedia by viewModel.isUploadingMedia.collectAsStateWithLifecycle()
    val uploadProgress by viewModel.uploadProgress.collectAsStateWithLifecycle()
    val isSendingMessage by viewModel.isSendingMessage.collectAsStateWithLifecycle()
    val isLoadingMembers by viewModel.isLoadingMembers.collectAsStateWithLifecycle()

    var activeChatPartner by remember { mutableStateOf<UserEntity?>(null) }
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val firstUnreadId by viewModel.firstUnreadId.collectAsStateWithLifecycle()
    val unreadByPartner by remember(workspaceId, currentUserId) {
        viewModel.unreadCounts(workspaceId, currentUserId.toInt())
    }.collectAsStateWithLifecycle(initialValue = emptyMap())

    LaunchedEffect(activeChatPartner) {
        onConversationActiveChange(activeChatPartner != null)
        onActivePartnerChange(activeChatPartner?.id)
    }
    var typedText by remember { mutableStateOf("") }
    val commitmentRepo = org.koin.compose.koinInject<com.collabsphere.app.model.commitment.CommitmentRepo>()
    val trackedCommitments by commitmentRepo.trackedKeys.collectAsStateWithLifecycle(initialValue = emptySet())
    var commitmentTarget by remember { mutableStateOf<com.collabsphere.app.view.components.CommitmentTarget?>(null) }
    var composerState by remember { mutableStateOf(TextFieldValue()) }
    val composerValue = composerState.copy(text = typedText)
    var showComposerActions by remember { mutableStateOf(false) }
    var showEmojiPanel by remember { mutableStateOf(false) }
    val composerFocus = remember { FocusRequester() }
    val composerKeyboard = LocalSoftwareKeyboardController.current
    val composerFocusManager = LocalFocusManager.current
    val openEmojiPanel = {
        composerKeyboard?.hide()
        composerFocusManager.clearFocus()
        showEmojiPanel = true
    }
    val openKeyboard = {
        showEmojiPanel = false
        composerFocus.requestFocus()
        composerKeyboard?.show()
    }
    var selectedMessage by remember { mutableStateOf<DmEntity?>(null) }
    var editingMessage by remember { mutableStateOf<DmEntity?>(null) }
    var showActionMenu by remember { mutableStateOf(false) }
    var showReactionPickerFor by remember { mutableStateOf<DmEntity?>(null) }
    var forwardTarget by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var infoFor by remember { mutableStateOf<DmEntity?>(null) }
    val scheduled = rememberScheduledMessages("DM", workspaceId, null, activeChatPartner?.id)
    var showSchedule by remember { mutableStateOf(false) }
    var showScheduledList by remember { mutableStateOf(false) }
    val savedStore = org.koin.compose.koinInject<com.collabsphere.app.model.message.SavedMessagesStore>()
    val savedKeys by savedStore.keys.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { savedStore.refresh() }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    var showPartnerProfileDialog by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val context = LocalContext.current
    val currentPartnerId = activeChatPartner?.id
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
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice() else AppToast.show("Allow microphone access to record voice messages")
    }
    DisposableEffect(Unit) { onDispose { voiceRecorder.cancel() } }
    val currentMembersList = workspaceMembers
    val isPartnerTyping = activeChatPartner?.let { it.id in typingPartnerIds } ?: false
    var replyingTo by remember { mutableStateOf<DmEntity?>(null) }
    LaunchedEffect(activeChatPartner?.id) { replyingTo = null }
    val draftStore = koinInject<DraftStore>()
    var draftBeforeEdit by remember { mutableStateOf("") }
    var draftLoadedFor by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(currentPartnerId) {
        val partnerId = currentPartnerId
        if (partnerId != null) {
            draftLoadedFor = null
        }
        onDispose {
            if (partnerId != null && draftLoadedFor == partnerId) {
                val finalDraft = if (editingMessage != null) draftBeforeEdit else typedText
                com.collabsphere.app.MyApplication.appScope.launch {
                    draftStore.save(DraftStore.dmKey(workspaceId, partnerId), finalDraft)
                }
            }
        }
    }
    LaunchedEffect(currentPartnerId) {
        val partnerId = currentPartnerId ?: return@LaunchedEffect
        editingMessage = null
        draftBeforeEdit = ""
        typedText = draftStore.load(DraftStore.dmKey(workspaceId, partnerId))
        draftLoadedFor = partnerId
    }
    LaunchedEffect(typedText, currentPartnerId, draftLoadedFor) {
        val partnerId = currentPartnerId ?: return@LaunchedEffect
        if (draftLoadedFor != partnerId || editingMessage != null) return@LaunchedEffect
        delay(400)
        draftStore.save(DraftStore.dmKey(workspaceId, partnerId), typedText)
    }
    val messagesById = remember(messages) { messages.associateBy { it.id } }
    val hasPendingMessages = messages.any { it.id < 0 }
    val pendingClock by produceState(System.currentTimeMillis(), hasPendingMessages) {
        while (hasPendingMessages) {
            value = System.currentTimeMillis()
            delay(2_000)
        }
    }
    val replyScope = rememberCoroutineScope()
    val scrollToMessage: (Int) -> Unit = { targetId ->
        val index = messages.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            replyScope.launch { lazyListState.animateScrollToItem(index + if (isLoadingOlder) 1 else 0) }
        }
    }
    val isPartnerOnline = activeChatPartner?.let { it.id in onlineUserIds } ?: false
    val userRepo = org.koin.compose.koinInject<com.collabsphere.app.model.UserRepo>()
    var partnerLastSeen by remember(activeChatPartner?.id) { mutableStateOf<Long?>(null) }
    LaunchedEffect(activeChatPartner?.id, isPartnerOnline) {
        val partnerId = activeChatPartner?.id ?: return@LaunchedEffect
        if (isPartnerOnline) return@LaunchedEffect
        partnerLastSeen = userRepo.getPublicProfile(partnerId).getOrNull()?.lastSeen
    }

    val focusRepo = org.koin.compose.koinInject<com.collabsphere.app.model.focus.FocusRepo>()
    var partnerFocusUntil by remember(activeChatPartner?.id) { mutableStateOf<Long?>(null) }
    var focusSendMode by remember(activeChatPartner?.id) { mutableStateOf(com.collabsphere.app.model.focus.FocusSendMode.HOLD) }
    LaunchedEffect(activeChatPartner?.id, workspaceId) {
        val partnerId = activeChatPartner?.id ?: return@LaunchedEffect
        while (true) {
            partnerFocusUntil = focusRepo.focusOf(partnerId, workspaceId)
            kotlinx.coroutines.delay(60_000L)
        }
    }
    fun dispatchNewMessage(partnerId: Int, text: String) {
        val replyToId = replyingTo?.id?.takeIf { it > 0 }
        val partnerFocusing = (partnerFocusUntil ?: 0L) > System.currentTimeMillis()
        if (partnerFocusing && replyToId == null && focusSendMode == com.collabsphere.app.model.focus.FocusSendMode.URGENT) {
            focusSendMode = com.collabsphere.app.model.focus.FocusSendMode.HOLD
            replyScope.launch {
                when (val result = focusRepo.sendUrgent(workspaceId, partnerId, text)) {
                    is com.collabsphere.app.model.focus.UrgentResult.Sent ->
                        AppToast.show("Urgent message sent · ${result.remainingToday} left today")
                    is com.collabsphere.app.model.focus.UrgentResult.LimitReached -> {
                        AppToast.error(result.message)
                        if (typedText.isBlank()) typedText = text
                    }
                    is com.collabsphere.app.model.focus.UrgentResult.Failed -> {
                        AppToast.error(result.message)
                        if (typedText.isBlank()) typedText = text
                    }
                }
            }
            return
        }
        val holdUntil = partnerFocusUntil?.takeIf {
            focusSendMode == com.collabsphere.app.model.focus.FocusSendMode.HOLD && replyToId == null && it > System.currentTimeMillis() + 30_000L
        }
        if (holdUntil == null) {
            viewModel.sendMessage(id = 0, workspaceId = workspaceId, senderId = currentUserId.toInt(), receiverId = partnerId, content = text, replyToId = replyToId)
            return
        }
        replyScope.launch {
            when (val result = focusRepo.hold(workspaceId, partnerId, text)) {
                is com.collabsphere.app.model.focus.HoldResult.Held -> {
                    scheduled.refresh()
                    AppToast.show("Will be delivered at ${com.collabsphere.app.view.components.chatTime(result.sendAt)}")
                }
                com.collabsphere.app.model.focus.HoldResult.NotFocusing -> {
                    partnerFocusUntil = null
                    viewModel.sendMessage(id = 0, workspaceId = workspaceId, senderId = currentUserId.toInt(), receiverId = partnerId, content = text, replyToId = null)
                }
                is com.collabsphere.app.model.focus.HoldResult.Failed -> {
                    AppToast.error(result.message)
                    if (typedText.isBlank()) typedText = text
                }
            }
        }
    }

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

    LaunchedEffect(lazyListState, activeChatPartner?.id) {
        if (activeChatPartner == null) return@LaunchedEffect
        snapshotFlow { lazyListState.firstVisibleItemIndex <= 2 && lazyListState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { it }
            .collect { viewModel.loadOlderMessages() }
    }

    val jumpToLatest = rememberJumpToLatestState(activeChatPartner?.id)
    // Last row of the conversation: messages, plus the "loading older" row above and typing row below.
    val latestRow = messages.size + (if (isPartnerTyping) 1 else 0) + (if (isLoadingOlder) 1 else 0) - 1
    val awayFromLatest by remember(latestRow) { derivedStateOf { lazyListState.isAwayFromLatest(latestRow) } }
    LaunchedEffect(awayFromLatest) { if (!awayFromLatest) jumpToLatest.reset() }
    var initialScrollDoneFor by remember { mutableStateOf<Int?>(null) }
    var lastCountedMessageId by remember(activeChatPartner?.id) { mutableStateOf<Int?>(null) }
    val search = rememberChatSearchState(activeChatPartner?.id)
    val searchMatches = remember(messages, search.query) { chatSearchMatches(messages, search.query) { it.dm_content } }
    val searchTargetIndex = searchMatches.getOrNull(searchMatches.size - 1 - search.position)
    LaunchedEffect(searchTargetIndex, search.isOpen) {
        val index = searchTargetIndex
        if (!search.isOpen || index == null) {
            search.highlightedId = null
            return@LaunchedEffect
        }
        search.highlightedId = messages.getOrNull(index)?.id
        lazyListState.animateScrollToItem(index + if (isLoadingOlder) 1 else 0)
    }

    LaunchedEffect(firstUnreadId) {
        val id = firstUnreadId ?: return@LaunchedEffect
        if (initialScrollDoneFor != activeChatPartner?.id || lazyListState.isScrollInProgress) return@LaunchedEffect
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) lazyListState.scrollToItem(index + if (isLoadingOlder) 1 else 0)
    }

    LaunchedEffect(messages.lastOrNull()?.id, isPartnerTyping) {
        val totalCount = latestRow + 1
        if (totalCount <= 0) return@LaunchedEffect
        val partnerId = activeChatPartner?.id

        // Opening a conversation lands on the newest message at once instead of animating through it.
        // Skip a list still showing the previous conversation while the new one loads.
        val belongsToPartner = messages.lastOrNull()?.let { it.senderId == partnerId || it.receiverId == partnerId } == true
        if (!belongsToPartner) return@LaunchedEffect
        if (initialScrollDoneFor != partnerId) {
            val dividerIndex = firstUnreadId?.let { id -> messages.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
            lazyListState.scrollToItem(dividerIndex?.let { it + if (isLoadingOlder) 1 else 0 } ?: (totalCount - 1))
            initialScrollDoneFor = partnerId
            lastCountedMessageId = messages.lastOrNull()?.id
            return@LaunchedEffect
        }

        val lastVisibleIndex = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        val wasNearBottom = lastVisibleIndex == null || lastVisibleIndex >= totalCount - 2
        val isOwnMessage = messages.lastOrNull()?.senderId == currentUserId.toInt()

        // Only yank the view to the newest message if the user was already reading near the
        // bottom, or it's their own outgoing message — not while they've scrolled up to read history;
        // then the jump-to-latest button counts the new message instead.
        if (wasNearBottom || isOwnMessage) {
            lazyListState.animateScrollToItem(totalCount - 1)
        } else {
            val newestId = messages.lastOrNull()?.id
            if (newestId != null && newestId != lastCountedMessageId) jumpToLatest.onNewMessages(1)
        }
        lastCountedMessageId = messages.lastOrNull()?.id
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
        FullScreenImageViewer(url = imgUrl, onDismiss = { fullscreenImageUrl = null })
    }

    if (showSchedule) {
        ScheduleSendDialog(
            onPick = { at ->
                showSchedule = false
                val text = typedText.trim()
                replyScope.launch {
                    if (scheduled.schedule(text, at)) {
                        typedText = ""
                        AppToast.show("Scheduled for ${scheduleLabel(at)}")
                    } else {
                        AppToast.error("Couldn't schedule it. Pick a later time and check your connection.")
                    }
                }
            },
            onDismiss = { showSchedule = false }
        )
    }
    if (showScheduledList) {
        ScheduledMessagesDialog(state = scheduled, onDismiss = { showScheduledList = false })
    }

    infoFor?.let { target ->
        com.collabsphere.app.view.components.MessageInfoDialog(
            preview = target.dm_content,
            sentAt = target.timestamp,
            editedAt = target.editedAt,
            statusLine = if (target.isRead) true to "Read" else false to "Delivered",
            sections = emptyList(),
            onDismiss = { infoFor = null }
        )
    }

    forwardTarget?.let { (content, mediaUrl) ->
        ForwardDialog(
            workspaceId = workspaceId,
            currentUserId = currentUserId.toInt(),
            currentUserName = workspaceMembers.firstOrNull { it.id == currentUserId.toInt() }?.userName ?: "",
            people = workspaceMembers.map { ForwardPerson(it.id, it.userName) },
            content = content,
            mediaUrl = mediaUrl,
            onDismiss = { forwardTarget = null }
        )
    }

    // Partner Profile Card dialog
    if (showPartnerProfileDialog && activeChatPartner != null) {
        val partner = activeChatPartner!!
        val partnerInitialDialog = partner.userName.trim().take(1).uppercase().ifEmpty { "U" }
        val isOnlineDialog = partner.id in onlineUserIds

        Dialog(onDismissRequest = { showPartnerProfileDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .drawWithCache {
                        onDrawBehind {
                        // Outer drop shadow
                        drawRoundRect(
                            color = ShadowDark.copy(alpha = 0.28f),
                            topLeft = Offset(4.dp.toPx(), 8.dp.toPx()),
                            size = Size(size.width, size.height),
                            cornerRadius = CornerRadius(28.dp.toPx())
                        )
                        // Top-left highlight
                        drawRoundRect(
                            color = ShadowLight.copy(alpha = 0.90f),
                            topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()),
                            size = Size(size.width, size.height),
                            cornerRadius = CornerRadius(28.dp.toPx())
                        )
                        // Base surface
                        drawRoundRect(color = SurfaceRaised, cornerRadius = CornerRadius(28.dp.toPx()))
                        // Top gloss shine
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.72f), Color.Transparent),
                                startY = 0f,
                                endY = 56.dp.toPx()
                            ),
                            topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                            size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                            cornerRadius = CornerRadius(27.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx())
                        )
                        }
                    }
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Close button (top-right)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .drawWithCache {
                                    onDrawBehind {
                                    drawCircle(color = ShadowDark.copy(alpha = 0.16f), radius = size.minDimension / 2f, center = Offset(center.x + 1.dp.toPx(), center.y + 1.5.dp.toPx()))
                                    drawCircle(color = SurfaceRaised, radius = size.minDimension / 2f)
                                    }
                                }
                                .clip(CircleShape)
                                .clickable { showPartnerProfileDialog = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close profile",
                                tint = Muted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .drawWithCache {
                                onDrawBehind {
                                drawCircle(color = ShadowDark.copy(alpha = 0.22f), radius = size.minDimension / 2f, center = Offset(center.x + 2.dp.toPx(), center.y + 3.dp.toPx()))
                                drawCircle(color = ShadowLight.copy(alpha = 0.90f), radius = size.minDimension / 2f, center = Offset(center.x - 2.dp.toPx(), center.y - 2.dp.toPx()))
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(IndigoStart, IndigoEnd),
                                        center = Offset(center.x - 8.dp.toPx(), center.y - 8.dp.toPx()),
                                        radius = size.minDimension / 2f
                                    )
                                )
                                }
                            }
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!partner.avatarUrl.isNullOrEmpty()) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context).data(partner.avatarUrl).crossfade(true)
                                    .size(with(LocalDensity.current) { 84.dp.roundToPx() }).build(),
                                contentDescription = partner.userName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(84.dp).clip(CircleShape),
                                error = {
                                    Text(
                                        text = partnerInitialDialog,
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                            )
                        } else {
                            Text(
                                text = partnerInitialDialog,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }

                    // Online status pill
                    Box(
                        modifier = Modifier
                            .drawWithCache {
                                onDrawBehind {
                                drawRoundRect(
                                    color = if (isOnlineDialog) Mint.copy(alpha = 0.18f) else Muted.copy(alpha = 0.12f),
                                    cornerRadius = CornerRadius(20.dp.toPx())
                                )
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isOnlineDialog) Mint else Muted.copy(alpha = 0.5f))
                            )
                            Text(
                                text = if (isOnlineDialog) "Online" else "Offline",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                                color = if (isOnlineDialog) Mint else Muted
                            )
                        }
                    }

                    // Username
                    Text(
                        text = partner.userName,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Ink,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Email
                    Text(
                        text = partner.email,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = Muted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .drawWithCache {
                                onDrawBehind {
                                drawRect(color = ShadowDark.copy(alpha = 0.10f), topLeft = Offset(0f, 0f))
                                drawRect(color = Color.White.copy(alpha = 0.60f), topLeft = Offset(0f, 0.5.dp.toPx()))
                                }
                            }
                    )

                    // Bio
                    if (!partner.bio.isNullOrBlank()) {
                        Text(
                            text = partner.bio,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp, fontSize = 14.sp),
                            color = Ink.copy(alpha = 0.80f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "No bio yet",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = Muted.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }

                    // Status message
                    if (!partner.statusMessage.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawWithCache {
                                    onDrawBehind {
                                    drawRoundRect(
                                        color = ShadowDark.copy(alpha = 0.10f),
                                        topLeft = Offset(1.dp.toPx(), 2.dp.toPx()),
                                        size = Size(size.width, size.height),
                                        cornerRadius = CornerRadius(12.dp.toPx())
                                    )
                                    drawRoundRect(color = Surface, cornerRadius = CornerRadius(12.dp.toPx()))
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "\"${partner.statusMessage}\"",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = Muted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Message button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawWithCache {
                                onDrawBehind {
                                drawRoundRect(
                                    color = ShadowDark.copy(alpha = 0.22f),
                                    topLeft = Offset(2.dp.toPx(), 4.dp.toPx()),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(14.dp.toPx())
                                )
                                drawRoundRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(CoralStart, CoralEnd),
                                        start = Offset(0f, 0f),
                                        end = Offset(size.width, size.height)
                                    ),
                                    cornerRadius = CornerRadius(14.dp.toPx())
                                )
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.25f),
                                    topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                                    size = Size(size.width - 1.dp.toPx(), size.height / 2),
                                    cornerRadius = CornerRadius(13.dp.toPx())
                                )
                                }
                            }
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showPartnerProfileDialog = false }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Message ${partner.userName}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    commitmentTarget?.let { target ->
        com.collabsphere.app.view.components.CommitmentTaskDialog(
            target = target,
            currentUserId = currentUserId.toInt(),
            onDismiss = { commitmentTarget = null }
        )
    }

    // Emoji reaction picker popup
    showReactionPickerFor?.let { targetMessage ->
        com.collabsphere.app.view.components.ReactionPickerDialog(
            selectedEmojis = myReactedEmojis[targetMessage.id].orEmpty(),
            onPick = { emoji ->
                activeChatPartner?.let { partner ->
                    viewModel.toggleReaction(
                        messageId = targetMessage.id,
                        emoji = emoji,
                        workspaceId = workspaceId,
                        receiverId = partner.id
                    )
                }
            },
            onDismiss = { showReactionPickerFor = null }
        )
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
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        val otherMembersCount = workspaceMembers.filter { it.id != currentUserId.toInt() }.size
                        Box(
                            modifier = Modifier
                                .drawWithCache {
                                    onDrawBehind {
                                    drawRoundRect(
                                        color = ShadowDark.copy(alpha = 0.12f),
                                        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                                        size = Size(size.width, size.height),
                                        cornerRadius = CornerRadius(12.dp.toPx())
                                    )
                                    drawRoundRect(color = SurfaceRaised, cornerRadius = CornerRadius(12.dp.toPx()))
                                    }
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

                    if (isLoadingMembers) {
                        UserListSkeleton(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                            count = 6,
                            trailing = false
                        )
                    } else if (otherMembers.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .drawWithCache {
                                        onDrawBehind {
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
                        val membersListState = rememberLazyListState()
                        LazyColumn(
                            state = membersListState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .fadingScrollEdges(listState = membersListState, top = 20.dp, bottom = 48.dp),
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
                                    unreadCount = unreadByPartner[member.id] ?: 0,
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
                            .drawWithCache {
                                onDrawBehind {
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
                            }
                            .statusBarsPadding()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    activeChatPartner = null
                                    viewModel.closeChatSessionUi()
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .drawWithCache {
                                        onDrawBehind {
                                        drawCircle(color = ShadowDark.copy(alpha = 0.22f), radius = size.minDimension / 2f, center = Offset(center.x + 1.dp.toPx(), center.y + 1.5.dp.toPx()))
                                        drawCircle(color = ShadowLight.copy(alpha = 0.90f), radius = size.minDimension / 2f, center = Offset(center.x - 1.dp.toPx(), center.y - 1.dp.toPx()))
                                        drawCircle(color = Surface, radius = size.minDimension / 2f)
                                        }
                                    }
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink, modifier = Modifier.size(20.dp))
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Partner Info (clickable to view profile card)
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showPartnerProfileDialog = true }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val partnerInitial = partner.userName.trim().take(1).uppercase().ifEmpty { "U" }
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .drawWithCache {
                                            onDrawBehind {
                                            drawCircle(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(IndigoStart, IndigoEnd),
                                                    center = Offset(center.x - 4.dp.toPx(), center.y - 4.dp.toPx()),
                                                    radius = size.minDimension / 2f
                                                )
                                            )
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!partner.avatarUrl.isNullOrEmpty()) {
                                        SubcomposeAsyncImage(
                                            model = ImageRequest.Builder(context).data(partner.avatarUrl).crossfade(true)
                                                .size(with(LocalDensity.current) { 38.dp.roundToPx() }).build(),
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
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.5.sp),
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
                                            Text(
                                                text = partnerLastSeen?.let { com.collabsphere.app.view.components.lastSeenLabel(it) } ?: "Offline",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                color = Muted,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            // Info action button
                            IconButton(onClick = { if (search.isOpen) search.close() else search.isOpen = true }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search in conversation",
                                    tint = if (search.isOpen) CoralStart else Muted
                                )
                            }
                            IconButton(
                                onClick = { showPartnerProfileDialog = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .drawWithCache {
                                        onDrawBehind {
                                        drawCircle(color = ShadowDark.copy(alpha = 0.16f), radius = size.minDimension / 2f, center = Offset(center.x + 1.dp.toPx(), center.y + 1.5.dp.toPx()))
                                        drawCircle(color = ShadowLight.copy(alpha = 0.90f), radius = size.minDimension / 2f, center = Offset(center.x - 1.dp.toPx(), center.y - 1.dp.toPx()))
                                        drawCircle(color = Surface, radius = size.minDimension / 2f)
                                        }
                                    }
                            ) {
                                Icon(imageVector = Icons.Default.Info, contentDescription = "Profile info", tint = CoralStart, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // Chat Area with WhatsApp-Style Background Texture
                    val chatBgColor = MaterialTheme.colorScheme.background
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .drawWithCache {
                                onDrawBehind {
                                drawRect(color = chatBgColor)
                                drawWhatsAppTexture(Ink)
                                }
                            }
                    ) {
                        if (messages.isEmpty()) {
                            // Friendly Empty State Hero Card
                            val partnerInitial = partner.userName.trim().take(1).uppercase().ifEmpty { "U" }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .drawWithCache {
                                            onDrawBehind {
                                            drawRoundRect(
                                                color = ShadowDark.copy(alpha = 0.18f),
                                                topLeft = Offset(2.dp.toPx(), 4.dp.toPx()),
                                                size = Size(size.width, size.height),
                                                cornerRadius = CornerRadius(24.dp.toPx())
                                            )
                                            drawRoundRect(
                                                color = ShadowLight.copy(alpha = 0.90f),
                                                topLeft = Offset(-2.dp.toPx(), -2.dp.toPx()),
                                                size = Size(size.width, size.height),
                                                cornerRadius = CornerRadius(24.dp.toPx())
                                            )
                                            drawRoundRect(color = SurfaceRaised, cornerRadius = CornerRadius(24.dp.toPx()))
                                            }
                                        }
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(CircleShape)
                                                .drawWithCache {
                                                    onDrawBehind {
                                                    drawCircle(brush = Brush.radialGradient(listOf(IndigoStart, IndigoEnd)), radius = size.minDimension / 2f)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = partnerInitial, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                        }
                                        Text(
                                            text = "Start chatting with ${partner.userName}",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Ink,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = "Messages are delivered instantly and synced in real-time.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Muted,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .drawWithCache {
                                                    onDrawBehind {
                                                    drawRoundRect(
                                                        brush = Brush.linearGradient(listOf(CoralLight, CoralStart)),
                                                        cornerRadius = CornerRadius(12.dp.toPx())
                                                    )
                                                    }
                                                }
                                                .clickable {
                                                    viewModel.sendMessage(
                                                        id = 0,
                                                        workspaceId = workspaceId,
                                                        senderId = currentUserId.toInt(),
                                                        receiverId = partner.id,
                                                        content = "Hi there!"
                                                    )
                                                }
                                                .padding(horizontal = 20.dp, vertical = 8.dp)
                                        ) {
                                            Text("Say Hello", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .fadingScrollEdges(listState = lazyListState, top = 20.dp, bottom = 20.dp),
                                contentPadding = PaddingValues(
                                    start = 14.dp,
                                    end = 14.dp,
                                    top = if (messages.any { it.pinnedAt != null }) 72.dp else 10.dp,
                                    bottom = 10.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                itemsIndexed(messages, key = { _, it -> it.id }) { index, message ->
                                    val isOwnMessage = message.senderId.toLong() == currentUserId
                                    val msgReactions = reactions[message.id] ?: emptyMap()
                                    val isRead = message.isRead
                                    val hasMedia = !message.mediaUrl.isNullOrEmpty()

                                    // Floating Date Header Chip
                                    val showDateHeader = index == 0 || !isSameDay(messages[index - 1].timestamp, message.timestamp)
                                    val showBubbleTail = showDateHeader || messages[index - 1].senderId != message.senderId || message.id == firstUnreadId
                                    val ownBubbleShape = ChatBubbleShape(isOwn = true, showTail = showBubbleTail)
                                    val incomingBubbleShape = ChatBubbleShape(isOwn = false, showTail = showBubbleTail)
                                    if (showDateHeader) {
                                        Box(
                                            modifier = Modifier
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
                                                    text = formatDmDateHeader(message.timestamp),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    ),
                                                    color = Muted
                                                )
                                            }
                                        }
                                    }

                                    if (message.id == firstUnreadId) {
                                        NewMessagesDivider()
                                    }
                                    SwipeToReply(
                                        enabled = message.id > 0,
                                        onReply = { replyingTo = message },
                                        modifier = Modifier
                                            .animateItem()
                                            .background(
                                                if (search.highlightedId == message.id) CoralStart.copy(alpha = 0.12f) else Color.Transparent,
                                                RoundedCornerShape(12.dp)
                                            )
                                    ) {
                                    if (isOwnMessage) {
                                        // Outgoing message (Aligned End)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = if (msgReactions.isNotEmpty()) 10.dp else 2.dp),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Box(
                                                modifier = Modifier.wrapContentSize(),
                                                contentAlignment = Alignment.BottomEnd
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .widthIn(min = 52.dp, max = 280.dp + ChatBubbleTail)
                                                        .background(OwnBubbleColor, ownBubbleShape)
                                                        .clip(ownBubbleShape)
                                                        .combinedClickable(
                                                            onClick = {},
                                                            onLongClick = {
                                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                selectedMessage = message
                                                                showActionMenu = true
                                                            }
                                                        )
                                                        .padding(start = 11.dp, end = 11.dp + ChatBubbleTail, top = 7.dp, bottom = 7.dp)
                                                ) {
                                                    Column(
                                                        modifier = Modifier.wrapContentSize(),
                                                        horizontalAlignment = Alignment.End
                                                    ) {
                                                        message.replyToId?.let { targetId ->
                                                            val target = messagesById[targetId]
                                                            ReplyQuote(
                                                                authorName = target?.let { if (it.senderId.toLong() == currentUserId) "You" else partner.userName },
                                                                snippet = target?.dm_content,
                                                                onDarkBubble = true,
                                                                onClick = target?.let { { scrollToMessage(targetId) } }
                                                            )
                                                        }
                                                        // Media image
                                                        if (hasMedia && isVoiceMediaUrl(message.mediaUrl)) {
                                                            VoiceMessagePlayer(url = message.mediaUrl!!, onDarkBubble = isOwnMessage)
                                                        } else if (hasMedia) {
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
                                                                        CollabSpinner(
                                                                            color = Color.White,
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
                                                            val commitmentKey = com.collabsphere.app.model.commitment.CommitmentRepo.dmKey(message.id)
                                                            MarkdownText(
                                                                text = message.dm_content,
                                                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp, fontSize = 15.sp),
                                                                color = Color.White,
                                                                linkColor = Color.White,
                                                                commitmentTracked = commitmentKey in trackedCommitments,
                                                                onCommitmentClick = if (message.id > 0) { commitment ->
                                                                    commitmentTarget = com.collabsphere.app.view.components.CommitmentTarget(
                                                                        commitment = commitment,
                                                                        source = com.collabsphere.app.model.commitment.CommitmentSource(
                                                                            key = commitmentKey,
                                                                            workspaceId = workspaceId,
                                                                            assigneeId = currentUserId.toInt(),
                                                                            assigneeName = workspaceMembers.firstOrNull { it.id == currentUserId.toInt() }?.userName ?: "Me",
                                                                            placeLabel = "a DM",
                                                                            quote = commitment.phrase
                                                                        )
                                                                    )
                                                                } else null
                                                            )
                                                            if (commitmentKey in trackedCommitments) {
                                                                com.collabsphere.app.view.components.TrackedCommitmentLabel(onDarkBubble = true)
                                                            }
                                                            LinkPreviewCard(text = message.dm_content, onDarkBubble = true)
                                                            val githubRegex = Regex("(https://github\\.com/[^/]+/[^/]+/[\\w/]+|https://github\\.com/[^/]+/[^/]+/?(?=\\s|$))")
                                                            val matches = githubRegex.findAll(message.dm_content).map { it.value }.toSet()
                                                            matches.forEach { url ->
                                                                unfurlViewModel.requestUnfurl(workspaceId, url)
                                                                val preview = githubPreviews[url]
                                                                if (preview != null) {
                                                                    com.collabsphere.app.view.components.GitHubUnfurlCard(
                                                                        preview = preview,
                                                                        actionState = githubActionStates[url],
                                                                        actionMessage = githubActionMessages[url],
                                                                        onActionClick = { action, body -> 
                                                                            unfurlViewModel.performAction(workspaceId, url, action, body)
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        if (message.id < 0) {
                                                            PendingMessageLabel(
                                                                stale = isStalePending(message.id, message.timestamp, pendingClock),
                                                                onDarkBubble = true
                                                            ) {
                                                                viewModel.retryMessage(message) { feedback ->
                                                                    feedback?.let { AppToast.show(it) }
                                                                }
                                                            }
                                                        }

                                                        // Timestamp + Read receipt row
                                                        Row(
                                                            horizontalArrangement = Arrangement.End,
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(top = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = (if (message.editedAt != null) "edited · " else "") + formatDmTime(message.timestamp),
                                                                fontSize = 10.sp,
                                                                color = Color.White.copy(alpha = 0.75f)
                                                            )
                                                            if (message.id > 0) {
                                                                Spacer(modifier = Modifier.width(3.dp))
                                                                MessageStatusTicks(isRead = isRead)
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
                                                            if (message.id > 0) {
                                                                DropdownMenuItem(
                                                                    text = { Text("Reply", color = Ink) },
                                                                    onClick = {
                                                                        replyingTo = message
                                                                        editingMessage = null
                                                                        showActionMenu = false
                                                                        selectedMessage = null
                                                                    },
                                                                    leadingIcon = { Icon(imageVector = Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = IndigoStart) }
                                                                )
                                                            }
                                                            DropdownMenuItem(
                                                                text = { Text("React", color = Ink) },
                                                                onClick = {
                                                                    showReactionPickerFor = message
                                                                    showActionMenu = false
                                                                    selectedMessage = null
                                                                },
                                                                leadingIcon = { Icon(Icons.Default.EmojiEmotions, contentDescription = null, tint = CoralStart, modifier = Modifier.size(20.dp)) }
                                                            )
                                                            if (message.dm_content.isNotBlank()) {
                                                                DropdownMenuItem(
                                                                    text = { Text("Copy", color = Ink) },
                                                                    onClick = {
                                                                        clipboard.setText(AnnotatedString(message.dm_content))
                                                                        AppToast.show("Copied")
                                                                        showActionMenu = false
                                                                        selectedMessage = null
                                                                    },
                                                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp)) }
                                                                )
                                                            }
                                                            if (message.id > 0) {
                                                                DropdownMenuItem(
                                                                    text = { Text("Forward", color = Ink) },
                                                                    onClick = {
                                                                        forwardTarget = message.dm_content to message.mediaUrl
                                                                        showActionMenu = false
                                                                        selectedMessage = null
                                                                    },
                                                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null, tint = IndigoStart, modifier = Modifier.size(20.dp)) }
                                                                )
                                                                DropdownMenuItem(
                                                                    text = { Text(if (message.pinnedAt != null) "Unpin" else "Pin", color = Ink) },
                                                                    onClick = {
                                                                        viewModel.togglePin(message) { feedback -> feedback?.let { AppToast.show(it) } }
                                                                        showActionMenu = false
                                                                        selectedMessage = null
                                                                    },
                                                                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null, tint = CoralStart, modifier = Modifier.size(20.dp)) }
                                                                )
                                                                val isSaved = com.collabsphere.app.model.message.SavedMessagesStore.key("DM", message.id) in savedKeys
                                                                DropdownMenuItem(
                                                                    text = { Text(if (isSaved) "Unsave" else "Save", color = Ink) },
                                                                    onClick = {
                                                                        showActionMenu = false
                                                                        selectedMessage = null
                                                                        replyScope.launch {
                                                                            savedStore.toggle("DM", message.id)
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
                                                                    text = { Text("Info", color = Ink) },
                                                                    onClick = {
                                                                        infoFor = message
                                                                        showActionMenu = false
                                                                        selectedMessage = null
                                                                    },
                                                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp)) }
                                                                )
                                                            }
                                                            DropdownMenuItem(
                                                                text = { Text("Edit message", color = Ink) },
                                                                onClick = {
                                                                    if (editingMessage == null) {
                                                                        draftBeforeEdit = typedText
                                                                    }
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
                                                                        typedText = draftBeforeEdit
                                                                        draftBeforeEdit = ""
                                                                    }
                                                                },
                                                                leadingIcon = { Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, tint = Destructive) }
                                                            )
                                                        }
                                                    }
                                                }

                                                // Docked Reaction badges
                                                if (msgReactions.isNotEmpty()) {
                                                    Row(
                                                        modifier = Modifier
                                                            .offset(x = (-6).dp, y = 10.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        msgReactions.entries.sortedByDescending { it.value }.forEach { (emoji, count) ->
                                                            Box(
                                                                modifier = Modifier
                                                                    .drawWithCache {
                                                                        onDrawBehind {
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
                                    } else {
                                        // Incoming message (Aligned Start with small partner avatar)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = if (msgReactions.isNotEmpty()) 10.dp else 2.dp),
                                            horizontalArrangement = Arrangement.Start,
                                            verticalAlignment = Alignment.Bottom
                                        ) {
                                            // 28dp partner avatar
                                            val partnerInitial = partner.userName.trim().take(1).uppercase().ifEmpty { "U" }
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .drawWithCache {
                                                        onDrawBehind {
                                                        drawCircle(
                                                            color = ShadowDark.copy(alpha = 0.14f),
                                                            radius = size.minDimension / 2f,
                                                            center = Offset(center.x, center.y + 1.dp.toPx())
                                                        )
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (!partner.avatarUrl.isNullOrEmpty()) {
                                                    SubcomposeAsyncImage(
                                                        model = ImageRequest.Builder(context).data(partner.avatarUrl).crossfade(true)
                                                            .size(with(LocalDensity.current) { 28.dp.roundToPx() }).build(),
                                                        contentDescription = partner.userName,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.size(28.dp).clip(CircleShape),
                                                        error = {
                                                            Box(
                                                                modifier = Modifier.size(28.dp).background(IndigoStart),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(text = partnerInitial, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                            }
                                                        }
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier.size(28.dp).background(IndigoStart),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(text = partnerInitial, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            Box(
                                                modifier = Modifier.wrapContentSize(),
                                                contentAlignment = Alignment.BottomStart
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .widthIn(min = 52.dp, max = 280.dp + ChatBubbleTail)
                                                        .background(SurfaceRaised, incomingBubbleShape)
                                                        .clip(incomingBubbleShape)
                                                        .combinedClickable(
                                                            onClick = {},
                                                            onLongClick = {
                                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                selectedMessage = message
                                                                showActionMenu = true
                                                            }
                                                        )
                                                        .padding(start = 11.dp + ChatBubbleTail, end = 11.dp, top = 7.dp, bottom = 7.dp)
                                                ) {
                                                    Column(
                                                        modifier = Modifier.wrapContentSize(),
                                                        horizontalAlignment = Alignment.Start
                                                    ) {
                                                        message.replyToId?.let { targetId ->
                                                            val target = messagesById[targetId]
                                                            ReplyQuote(
                                                                authorName = target?.let { if (it.senderId.toLong() == currentUserId) "You" else partner.userName },
                                                                snippet = target?.dm_content,
                                                                onDarkBubble = false,
                                                                onClick = target?.let { { scrollToMessage(targetId) } }
                                                            )
                                                        }
                                                        // Media image
                                                        if (hasMedia && isVoiceMediaUrl(message.mediaUrl)) {
                                                            VoiceMessagePlayer(url = message.mediaUrl!!, onDarkBubble = isOwnMessage)
                                                        } else if (hasMedia) {
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
                                                                        CollabSpinner(
                                                                            color = CoralStart,
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
                                                            val commitmentKey = com.collabsphere.app.model.commitment.CommitmentRepo.dmKey(message.id)
                                                            MarkdownText(
                                                                text = message.dm_content,
                                                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp, fontSize = 15.sp),
                                                                color = Ink,
                                                                linkColor = IndigoStart,
                                                                commitmentTracked = commitmentKey in trackedCommitments,
                                                                onCommitmentClick = if (message.id > 0) { commitment ->
                                                                    commitmentTarget = com.collabsphere.app.view.components.CommitmentTarget(
                                                                        commitment = commitment,
                                                                        source = com.collabsphere.app.model.commitment.CommitmentSource(
                                                                            key = commitmentKey,
                                                                            workspaceId = workspaceId,
                                                                            assigneeId = partner.id,
                                                                            assigneeName = partner.userName,
                                                                            placeLabel = "a DM",
                                                                            quote = commitment.phrase
                                                                        )
                                                                    )
                                                                } else null
                                                            )
                                                            if (commitmentKey in trackedCommitments) {
                                                                com.collabsphere.app.view.components.TrackedCommitmentLabel(onDarkBubble = false)
                                                            }
                                                            LinkPreviewCard(text = message.dm_content, onDarkBubble = false)
                                                            val githubRegex = Regex("(https://github\\.com/[^/]+/[^/]+/[\\w/]+|https://github\\.com/[^/]+/[^/]+/?(?=\\s|$))")
                                                            val matches = githubRegex.findAll(message.dm_content).map { it.value }.toSet()
                                                            matches.forEach { url ->
                                                                unfurlViewModel.requestUnfurl(workspaceId, url)
                                                                val preview = githubPreviews[url]
                                                                if (preview != null) {
                                                                    com.collabsphere.app.view.components.GitHubUnfurlCard(
                                                                        preview = preview,
                                                                        actionState = githubActionStates[url],
                                                                        actionMessage = githubActionMessages[url],
                                                                        onActionClick = { action, body -> 
                                                                            unfurlViewModel.performAction(workspaceId, url, action, body)
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        // Timestamp row
                                                        Row(
                                                            horizontalArrangement = Arrangement.End,
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(top = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = (if (message.editedAt != null) "edited · " else "") + formatDmTime(message.timestamp),
                                                                fontSize = 10.sp,
                                                                color = Muted
                                                            )
                                                        }
                                                    }

                                                    if (showActionMenu && selectedMessage?.id == message.id) {
                                                        DropdownMenu(
                                                            expanded = showActionMenu,
                                                            onDismissRequest = {
                                                                showActionMenu = false
                                                                selectedMessage = null
                                                            },
                                                            modifier = Modifier.background(SurfaceRaised)
                                                        ) {
                                                            if (message.id > 0) {
                                                                DropdownMenuItem(
                                                                    text = { Text("Reply", color = Ink) },
                                                                    onClick = {
                                                                        replyingTo = message
                                                                        editingMessage = null
                                                                        showActionMenu = false
                                                                        selectedMessage = null
                                                                    },
                                                                    leadingIcon = { Icon(imageVector = Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = IndigoStart) }
                                                                )
                                                            }
                                                            DropdownMenuItem(
                                                                text = { Text("React", color = Ink) },
                                                                onClick = {
                                                                    showReactionPickerFor = message
                                                                    showActionMenu = false
                                                                    selectedMessage = null
                                                                },
                                                                leadingIcon = { Icon(Icons.Default.EmojiEmotions, contentDescription = null, tint = CoralStart, modifier = Modifier.size(20.dp)) }
                                                            )
                                                            if (message.dm_content.isNotBlank()) {
                                                                DropdownMenuItem(
                                                                    text = { Text("Copy", color = Ink) },
                                                                    onClick = {
                                                                        clipboard.setText(AnnotatedString(message.dm_content))
                                                                        AppToast.show("Copied")
                                                                        showActionMenu = false
                                                                        selectedMessage = null
                                                                    },
                                                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp)) }
                                                                )
                                                            }
                                                            if (message.id > 0) {
                                                                DropdownMenuItem(
                                                                    text = { Text("Forward", color = Ink) },
                                                                    onClick = {
                                                                        forwardTarget = message.dm_content to message.mediaUrl
                                                                        showActionMenu = false
                                                                        selectedMessage = null
                                                                    },
                                                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null, tint = IndigoStart, modifier = Modifier.size(20.dp)) }
                                                                )
                                                                DropdownMenuItem(
                                                                    text = { Text(if (message.pinnedAt != null) "Unpin" else "Pin", color = Ink) },
                                                                    onClick = {
                                                                        viewModel.togglePin(message) { feedback -> feedback?.let { AppToast.show(it) } }
                                                                        showActionMenu = false
                                                                        selectedMessage = null
                                                                    },
                                                                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null, tint = CoralStart, modifier = Modifier.size(20.dp)) }
                                                                )
                                                                val isSaved = com.collabsphere.app.model.message.SavedMessagesStore.key("DM", message.id) in savedKeys
                                                                DropdownMenuItem(
                                                                    text = { Text(if (isSaved) "Unsave" else "Save", color = Ink) },
                                                                    onClick = {
                                                                        showActionMenu = false
                                                                        selectedMessage = null
                                                                        replyScope.launch {
                                                                            savedStore.toggle("DM", message.id)
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
                                                        }
                                                    }
                                                }

                                                // Docked Reaction badges for incoming
                                                if (msgReactions.isNotEmpty()) {
                                                    Row(
                                                        modifier = Modifier
                                                            .offset(x = 6.dp, y = 10.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        msgReactions.entries.sortedByDescending { it.value }.forEach { (emoji, count) ->
                                                            Box(
                                                                modifier = Modifier
                                                                    .drawWithCache {
                                                                        onDrawBehind {
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
                                    }
                                }

                                if (isPartnerTyping) {
                                    item(key = "typing_bubble") {
                                        SkeuoTypingBubble(
                                            partnerName = partner.userName,
                                            avatarUrl = partner.avatarUrl,
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                            }
                        }
                        val pinnedDms = messages.filter { it.pinnedAt != null }.sortedByDescending { it.pinnedAt }
                        pinnedDms.firstOrNull()?.let { latestPin ->
                            PinnedMessagesBanner(
                                preview = latestPin.dm_content.ifBlank { "Attachment" },
                                count = pinnedDms.size,
                                onClick = { scrollToMessage(latestPin.id) },
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
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
                            visible = messages.isNotEmpty() && awayFromLatest,
                            unseenCount = jumpToLatest.unseenCount,
                            onClick = {
                                replyScope.launch { lazyListState.animateScrollToItem(latestRow.coerceAtLeast(0)) }
                                jumpToLatest.reset()
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 12.dp)
                        )
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
                                    .drawWithCache {
                                        onDrawBehind {
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
                                IconButton(onClick = { editingMessage = null; typedText = draftBeforeEdit; draftBeforeEdit = "" }, modifier = Modifier.size(28.dp)) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel Edit", tint = Muted, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        val replyTarget = replyingTo
                        if (replyTarget != null && editingMessage == null) {
                            ReplyComposerBanner(
                                authorName = if (replyTarget.senderId.toLong() == currentUserId) "yourself" else partner.userName,
                                snippet = replyTarget.dm_content,
                                onCancel = { replyingTo = null },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }

                        partnerFocusUntil?.takeIf { editingMessage == null && it > System.currentTimeMillis() }?.let { until ->
                            com.collabsphere.app.view.components.PartnerFocusBanner(
                                partnerName = partner.userName,
                                focusUntil = until,
                                mode = focusSendMode,
                                onModeChange = { focusSendMode = it },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }

                        // Media upload progress bar
                        ScheduledMessagesChip(count = scheduled.items.size, onClick = { showScheduledList = true })

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
                                        viewModel.sendMediaMessage(
                                            baseUrl = baseUrl,
                                            workspaceId = workspaceId,
                                            senderId = currentUserId.toInt(),
                                            receiverId = partner.id,
                                            fileBytes = file.readBytes(),
                                            mimeType = "audio/mp4",
                                            fileName = file.name
                                        )
                                        file.delete()
                                    }
                                }
                            )
                        }

                        AnimatedVisibility(
                            visible = isUploadingMedia,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            com.collabsphere.app.view.components.UploadProgressBar(
                                progress = uploadProgress,
                                onCancel = viewModel::cancelUpload
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                                            MaterialTheme.colorScheme.background
                                        )
                                    )
                                )
                                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp)
                        ) {
                            // Floating Tactile Pill Dock
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .drawWithCache {
                                        onDrawBehind {
                                            val cr = 28.dp.toPx()
                                            // 1. Ambient soft diffuse shadow
                                            drawRoundRect(
                                                color = Color(0xFF2C201A).copy(alpha = 0.12f),
                                                topLeft = Offset(0f, 4.dp.toPx()),
                                                size = size,
                                                cornerRadius = CornerRadius(cr)
                                            )
                                            // 2. Crisp contact shadow
                                            drawRoundRect(
                                                color = Color(0xFF2C201A).copy(alpha = 0.04f),
                                                topLeft = Offset(0f, 1.dp.toPx()),
                                                size = size,
                                                cornerRadius = CornerRadius(cr)
                                            )
                                            // 3. Crisp white dock plate
                                            drawRoundRect(
                                                color = SurfaceRaised,
                                                cornerRadius = CornerRadius(cr)
                                            )
                                            // 4. Specular gloss top rim highlight
                                            drawRoundRect(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.White.copy(alpha = 0.95f),
                                                        Color.White.copy(alpha = 0.20f),
                                                        Color.Transparent
                                                    ),
                                                    startY = 0f,
                                                    endY = 16.dp.toPx()
                                                ),
                                                topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                                                size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                                                cornerRadius = CornerRadius(cr),
                                                style = Stroke(width = 1.dp.toPx())
                                            )
                                            // 5. Hairline contour border
                                            drawRoundRect(
                                                color = Color(0xFF2C2A28).copy(alpha = 0.07f),
                                                cornerRadius = CornerRadius(cr),
                                                style = Stroke(width = 1.dp.toPx())
                                            )
                                        }
                                    }
                                    .clip(RoundedCornerShape(28.dp))
                                    .padding(start = 4.dp, end = 5.dp, top = 5.dp, bottom = 5.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    ComposerActionsButton(
                                        modifier = Modifier.padding(bottom = 3.dp),
                                        expanded = showComposerActions,
                                        onExpandedChange = { showComposerActions = it },
                                        busy = isUploadingMedia,
                                        actions = listOfNotNull(
                                            ComposerAction(
                                                label = if (showEmojiPanel) "Keyboard" else "Emoji",
                                                icon = if (showEmojiPanel) Icons.Default.Keyboard else Icons.Default.EmojiEmotions,
                                                tint = Color(0xFFF2A93B)
                                            ) {
                                                if (showEmojiPanel) openKeyboard() else openEmojiPanel()
                                            },
                                            if (editingMessage != null) null else ComposerAction(
                                                label = "Photo or file",
                                                icon = Icons.Default.AttachFile,
                                                tint = IndigoStart,
                                                enabled = !isUploadingMedia
                                            ) { mediaPicker.launch("*/*") }
                                        )
                                    )

                                    var isComposerFocused by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 46.dp, max = 140.dp)
                                            .drawWithCache {
                                                onDrawBehind {
                                                    val cr = 23.dp.toPx()
                                                    // Recessed well surface
                                                    drawRoundRect(
                                                        color = Color(0xFFF3ECE6),
                                                        cornerRadius = CornerRadius(cr)
                                                    )
                                                    // Top inner shadow
                                                    drawRoundRect(
                                                        color = Color(0xFF2C201A).copy(alpha = 0.06f),
                                                        topLeft = Offset(0f, 0.5.dp.toPx()),
                                                        size = size,
                                                        cornerRadius = CornerRadius(cr),
                                                        style = Stroke(width = 1.dp.toPx())
                                                    )
                                                    // Outline hairline
                                                    drawRoundRect(
                                                        color = if (isComposerFocused) CoralStart.copy(alpha = 0.5f) else Color(0xFFE2DDD7),
                                                        cornerRadius = CornerRadius(cr),
                                                        style = Stroke(width = 1.dp.toPx())
                                                    )
                                                }
                                            }
                                            .clip(RoundedCornerShape(23.dp))
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        BasicTextField(
                                            value = composerValue,
                                            onValueChange = { newValue ->
                                                composerState = newValue
                                                val newText = newValue.text
                                                if (newText == typedText) return@BasicTextField
                                                typedText = newText
                                                if (newText.isNotBlank()) {
                                                    viewModel.onUserTyping(workspaceId, partner.id)
                                                } else {
                                                    viewModel.onUserStoppedTyping(workspaceId, partner.id)
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(composerFocus)
                                                .onFocusChanged {
                                                    isComposerFocused = it.isFocused
                                                    if (it.isFocused) showEmojiPanel = false
                                                },
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink, fontSize = 15.5.sp, lineHeight = 21.sp),
                                            cursorBrush = SolidColor(CoralStart),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                            keyboardActions = KeyboardActions(
                                                onSend = {
                                                    if (typedText.trim().isNotEmpty()) {
                                                        val textToSend = typedText.trim()
                                                        val currentlyEditing = editingMessage
                                                        if (currentlyEditing != null) {
                                                            viewModel.updateMessage(dmId = currentlyEditing.id, workspaceId = workspaceId, receiverId = partner.id, newContent = textToSend)
                                                            editingMessage = null; typedText = draftBeforeEdit; draftBeforeEdit = ""
                                                        } else {
                                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            dispatchNewMessage(partner.id, textToSend)
                                                            replyingTo = null
                                                            typedText = ""
                                                        }
                                                    }
                                                }
                                            ),
                                            decorationBox = { inner ->
                                                Box(contentAlignment = Alignment.CenterStart) {
                                                    if (typedText.isEmpty()) {
                                                        Text(
                                                            text = if (editingMessage != null) "Edit message..." else "Write a message...",
                                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                                                            color = Muted.copy(alpha = 0.65f),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    inner()
                                                }
                                            }
                                        )
                                    }

                                    // ── Vibrant Coral Send Button CTA ──
                                    val sendInteractionSource = remember { MutableInteractionSource() }
                                    val isSendPressed by sendInteractionSource.collectIsPressedAsState()
                                    val showMic = typedText.isBlank() && editingMessage == null && !isUploadingMedia && !isRecordingVoice
                                    val willHold = editingMessage == null && replyingTo == null &&
                                        focusSendMode == com.collabsphere.app.model.focus.FocusSendMode.HOLD &&
                                        (partnerFocusUntil ?: 0L) > System.currentTimeMillis() + 30_000L
                                    val willUrgent = editingMessage == null && replyingTo == null &&
                                        focusSendMode == com.collabsphere.app.model.focus.FocusSendMode.URGENT &&
                                        (partnerFocusUntil ?: 0L) > System.currentTimeMillis()
                                    val canSend = (typedText.trim().isNotEmpty() && !isUploadingMedia && !isSendingMessage) || editingMessage != null || showMic
                                    val sendScale by animateFloatAsState(
                                        targetValue = if (isSendPressed) 0.90f else 1f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                                        label = "dmSendScale"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .graphicsLayer { scaleX = sendScale; scaleY = sendScale }
                                            .drawWithCache {
                                                onDrawBehind {
                                                    val shadowOffset = if (isSendPressed) 1.dp else 2.5.dp
                                                    val shadowAlpha = if (isSendPressed) 0.15f else 0.35f
                                                    drawCircle(
                                                        color = CoralStart.copy(alpha = shadowAlpha),
                                                        radius = size.minDimension / 2f,
                                                        center = Offset(center.x, center.y + shadowOffset.toPx())
                                                    )
                                                    drawCircle(
                                                        brush = Brush.radialGradient(
                                                            colors = listOf(Color(0xFFFC6955), CoralStart),
                                                            center = Offset(center.x - 3.dp.toPx(), center.y - 3.dp.toPx()),
                                                            radius = size.minDimension / 2f
                                                        )
                                                    )
                                                    drawCircle(
                                                        color = Color.White.copy(alpha = 0.30f),
                                                        style = Stroke(width = 1.dp.toPx())
                                                    )
                                                }
                                            }
                                            .clip(CircleShape)
                                            .combinedClickable(
                                                enabled = canSend,
                                                interactionSource = sendInteractionSource,
                                                indication = null,
                                                onLongClick = {
                                                    if (typedText.isNotBlank() && editingMessage == null) {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        showSchedule = true
                                                    }
                                                }
                                            ) {
                                                if (showMic) {
                                                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                                                            context,
                                                            android.Manifest.permission.RECORD_AUDIO
                                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                    ) {
                                                        startVoice()
                                                    } else {
                                                        micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                                                    }
                                                } else if (typedText.trim().isNotEmpty()) {
                                                    val textToSend = typedText.trim()
                                                    val currentlyEditing = editingMessage
                                                    if (currentlyEditing != null) {
                                                        viewModel.updateMessage(dmId = currentlyEditing.id, workspaceId = workspaceId, receiverId = partner.id, newContent = textToSend)
                                                        editingMessage = null; typedText = draftBeforeEdit; draftBeforeEdit = ""
                                                    } else {
                                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        dispatchNewMessage(partner.id, textToSend)
                                                        replyingTo = null
                                                        typedText = ""
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                editingMessage != null -> Icons.Default.Check
                                                showMic -> Icons.Default.Mic
                                                willHold -> Icons.Outlined.Schedule
                                                willUrgent -> Icons.Outlined.NotificationsActive
                                                else -> Icons.AutoMirrored.Filled.Send
                                            },
                                            contentDescription = when {
                                                editingMessage != null -> "Update message"
                                                showMic -> "Record voice message"
                                                willHold -> "Deliver when they're free"
                                                willUrgent -> "Send as urgent"
                                                else -> "Send message"
                                            },
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp).offset(x = if (editingMessage == null && !showMic) 1.dp else 0.dp)
                                        )
                                    }
                                }
                            }
                        }

                        ComposerEmojiPanel(
                            visible = showEmojiPanel,
                            onEmoji = { emoji ->
                                val updated = composerValue.insertAtCursor(emoji)
                                composerState = updated
                                typedText = updated.text
                            },
                            onClose = { showEmojiPanel = false }
                        )
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
    unreadCount: Int = 0,
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
                                .drawWithCache {
                                    onDrawBehind {
                                    drawCircle(brush = Brush.radialGradient(colors = listOf(CoralLight, CoralStart)))
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context).data(member.avatarUrl).crossfade(true)
                                    .size(with(LocalDensity.current) { 46.dp.roundToPx() }).build(),
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
                        val infiniteTransition = rememberInfiniteTransition(label = "memberTypingPulse")
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 0.88f,
                            targetValue = 1.12f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(550, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulseScale"
                        )
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(CoralStart)
                        )
                    } else if (isOnline) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(Mint)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = member.userName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.5.sp),
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    if (isTyping) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(CoralStart))
                            Text(
                                text = "typing...",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                                color = CoralStart
                            )
                        }
                    } else if (isOnline) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Mint))
                            Text(
                                text = "Online",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                color = Mint
                            )
                        }
                    } else {
                        Text(
                            text = member.statusMessage?.takeIf { it.isNotBlank() } ?: "Tap to message",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (unreadCount > 0) {
                UnreadCountBadge(count = unreadCount, modifier = Modifier.padding(end = 6.dp))
            }
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Muted.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        }
    }
}

/** Skeuomorphic animated typing bubble with partner avatar and bouncing dots matching Stitch spec */
@Composable
fun SkeuoTypingBubble(
    partnerName: String,
    avatarUrl: String? = null,
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

    val context = LocalContext.current
    val initial = partnerName.trim().take(1).uppercase().ifEmpty { "U" }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Start
    ) {
        // 28dp partner avatar circle matching Stitch spec
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .drawWithCache {
                    onDrawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(IndigoStart, IndigoEnd),
                                center = Offset(center.x - 3.dp.toPx(), center.y - 3.dp.toPx()),
                                radius = size.minDimension / 2f
                            )
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrEmpty()) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context).data(avatarUrl).crossfade(true)
                        .size(with(LocalDensity.current) { 28.dp.roundToPx() }).build(),
                    contentDescription = partnerName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(28.dp).clip(CircleShape),
                    error = {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                            color = Color.White
                        )
                    }
                )
            } else {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Tactile white rounded pill with 3 coral pulsing dots
        Box(
            modifier = Modifier
                .drawWithCache {
                    onDrawBehind {
                        val cr = 16.dp.toPx()
                        // 1. Soft contact shadow
                        drawRoundRect(
                            color = ShadowDark.copy(alpha = 0.08f),
                            topLeft = Offset(0f, 2.dp.toPx()),
                            size = size,
                            cornerRadius = CornerRadius(cr)
                        )
                        // 2. White pill plate
                        drawRoundRect(
                            color = Color.White,
                            cornerRadius = CornerRadius(cr)
                        )
                        // 3. Hairline border
                        drawRoundRect(
                            color = Color(0xFF2C2A28).copy(alpha = 0.07f),
                            cornerRadius = CornerRadius(cr),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
                .clip(RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .offset(y = dot1Offset.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(CoralStart)
                )
                Box(
                    modifier = Modifier
                        .offset(y = dot2Offset.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(CoralStart)
                )
                Box(
                    modifier = Modifier
                        .offset(y = dot3Offset.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(CoralStart)
                )
            }
        }
    }
}
