package com.collabsphere.app.view.NotificationUI

import com.collabsphere.app.view.components.chatHeaderBar
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.collabsphere.app.dto.notification.NotificationResponse
import com.collabsphere.app.model.InboxFilter
import com.collabsphere.app.model.NotificationInbox
import com.collabsphere.app.ui.theme.*
import com.collabsphere.app.view.components.NotificationSkeletonList
import com.collabsphere.app.view.components.rememberNow
import com.collabsphere.app.viewmodel.GitHubUnfurlViewModel
import com.collabsphere.app.viewmodel.NotificationsViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    onBack: () -> Unit,
    onNotificationClick: (NotificationResponse) -> Unit,
    onAcceptInvitation: ((invitationId: Int, notificationId: Int) -> Unit)? = null,
    onDeclineInvitation: ((invitationId: Int, notificationId: Int) -> Unit)? = null,
    pendingInvitationIds: Set<Int> = emptySet()
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val unfurlViewModel: GitHubUnfurlViewModel = koinViewModel()
    val githubPreviews by unfurlViewModel.previews.collectAsStateWithLifecycle()
    val githubActionStates by unfurlViewModel.actionStates.collectAsStateWithLifecycle()
    val githubActionMessages by unfurlViewModel.actionMessages.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val now = rememberNow(60_000L)
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadNotifications() }
    DisposableEffect(Unit) { onDispose { viewModel.commitAllPending() } }

    LaunchedEffect(statusMessage) {
        if (statusMessage.isNotEmpty()) {
            snackbarHostState.showSnackbar(statusMessage)
            viewModel.clearStatus()
        }
    }

    val counts = remember(notifications) { NotificationInbox.unreadCounts(notifications) }
    val visible = remember(notifications, filter) { notifications.filter { filter.matches(it) } }
    val sections = remember(visible, now) { NotificationInbox.sections(visible, now) }

    fun remove(notification: NotificationResponse) {
        viewModel.removeWithUndo(notification.id)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "Notification removed",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove(notification.id)
            else viewModel.commitRemove(notification.id)
        }
    }

    if (confirmClear) {
        com.collabsphere.app.view.components.PrintConfirmDialog(
            title = "Clear all notifications?",
            message = "This permanently removes every notification in your inbox.",
            confirmLabel = "Clear all",
            icon = Icons.Default.DoneAll,
            onConfirm = {
                confirmClear = false
                viewModel.onClearAll()
            },
            onDismiss = { confirmClear = false }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .chatHeaderBar()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Notifications",
                            style = PrintType.H2.copy(fontSize = 22.sp, lineHeight = 26.sp),
                            color = PrintInk,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (unreadCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(PrintCoral)
                                    .border(1.5.dp, PrintInk, RoundedCornerShape(50))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PrintInk
                                )
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.onMarkAllRead() }, enabled = unreadCount > 0) {
                        Icon(
                            Icons.Default.DoneAll,
                            contentDescription = "Mark all as read",
                            tint = if (unreadCount > 0) PrintInk else PrintInk.copy(alpha = 0.4f)
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }, enabled = notifications.isNotEmpty()) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = if (notifications.isNotEmpty()) Ink else Muted.copy(alpha = 0.4f))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.background(SurfaceRaised)) {
                            DropdownMenuItem(
                                text = { Text("Clear all…", color = Destructive) },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Destructive) },
                                onClick = {
                                    menuOpen = false
                                    confirmClear = true
                                }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InboxFilter.entries.forEach { option ->
                        FilterPill(
                            label = option.label,
                            count = counts[option] ?: 0,
                            selected = option == filter,
                            onClick = { viewModel.setFilter(option) }
                        )
                    }
                }
                HorizontalDivider(color = Muted.copy(alpha = 0.12f))
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading && notifications.isEmpty() -> NotificationSkeletonList()
                visible.isEmpty() -> InboxEmptyState(filter)
                else -> {
                    val listState = rememberLazyListState()
                    val nearEnd by remember {
                        derivedStateOf {
                            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            last >= listState.layoutInfo.totalItemsCount - 4
                        }
                    }
                    LaunchedEffect(nearEnd, hasMore, filter) { if (nearEnd && hasMore) viewModel.loadMore() }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .fadingScrollEdges(listState = listState, top = 12.dp, bottom = 32.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sections.forEach { section ->
                            stickyHeader(key = "header_${section.title}") {
                                Text(
                                    text = section.title,
                                    style = PrintType.Tiny,
                                    color = PrintInk,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(PrintPaper)
                                        .padding(start = 4.dp, top = 12.dp, bottom = 6.dp)
                                )
                            }
                            items(section.items, key = { it.id }) { notification ->
                                SwipeableNotification(
                                    notification = notification,
                                    onMarkRead = { viewModel.onMarkRead(listOf(notification.id)) },
                                    onRemove = { remove(notification) },
                                    modifier = Modifier.animateItem()
                                ) {
                                    NotificationRow(
                                        notification = notification,
                                        now = now,
                                        onClick = {
                                            viewModel.onMarkRead(listOf(notification.id))
                                            onNotificationClick(notification)
                                        },
                                        onAcceptInvitation = onAcceptInvitation,
                                        onDeclineInvitation = onDeclineInvitation,
                                        isInvitationActionPending = notification.referenceId in pendingInvitationIds,
                                        unfurlViewModel = unfurlViewModel,
                                        githubPreviews = githubPreviews,
                                        githubActionStates = githubActionStates,
                                        githubActionMessages = githubActionMessages
                                    )
                                }
                            }
                        }
                        if (isLoadingMore) {
                            item(key = "loading_more") {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    com.collabsphere.app.view.components.CollabSpinner(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = PrintInk)
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
private fun FilterPill(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) PrintInk else PrintWhite, label = "filterBg")
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(background)
            .border(1.5.dp, PrintInk, shape)
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) PrintPaper else PrintInk
        )
        if (count > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(PrintCoral)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = if (count > 99) "99+" else count.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                    color = PrintInk
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableNotification(
    notification: NotificationResponse,
    onMarkRead: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val latestMarkRead by rememberUpdatedState(onMarkRead)
    val latestRemove by rememberUpdatedState(onRemove)
    SwipeToDismissBox(
        state = state,
        modifier = modifier.semantics {
            customActions = listOfNotNull(
                if (!notification.isRead) CustomAccessibilityAction("Mark as read") { latestMarkRead(); true } else null,
                CustomAccessibilityAction("Remove") { latestRemove(); true }
            )
        },
        enableDismissFromStartToEnd = !notification.isRead,
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    latestMarkRead()
                    scope.launch { state.reset() }
                }
                SwipeToDismissBoxValue.EndToStart -> latestRemove()
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        backgroundContent = {
            val direction = state.dismissDirection
            val (color, icon, label, alignment) = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> SwipeVisual(PrintLeaf, Icons.Default.DoneAll, "Mark read", Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> SwipeVisual(PrintCoral, Icons.Default.DeleteOutline, "Remove", Alignment.CenterEnd)
                SwipeToDismissBoxValue.Settled -> SwipeVisual(Color.Transparent, null, "", Alignment.Center)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(color)
                    .then(if (icon != null) Modifier.border(1.5.dp, PrintInk, RoundedCornerShape(18.dp)) else Modifier)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                if (icon != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(icon, contentDescription = null, tint = PrintInk, modifier = Modifier.size(20.dp))
                        Text(label, color = PrintInk, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    ) {
        content()
    }
}

private data class SwipeVisual(val color: Color, val icon: ImageVector?, val label: String, val alignment: Alignment)

@Composable
private fun InboxEmptyState(filter: InboxFilter) {
    val (title, subtitle) = NotificationInbox.emptyMessage(filter)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .printSurface(shape = BlobShape(seed = 4, wobble = 0.14f), fill = PrintSky, shadow = null),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.NotificationsNone, contentDescription = null, tint = PrintInk, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = PrintType.H2.copy(fontSize = 22.sp, lineHeight = 26.sp), color = PrintInk)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

private fun iconFor(type: String): ImageVector = when (type) {
    "MENTION" -> Icons.Default.AlternateEmail
    "TASK_ASSIGNED", "TASK_UPDATED", "TASK_DUE" -> Icons.AutoMirrored.Filled.Assignment
    "WORKSPACE_INVITE" -> Icons.Default.GroupAdd
    "GITHUB_PR", "GITHUB_ISSUE", "GITHUB_CI", "GITHUB_RELEASE" -> Icons.Default.Source
    "LEVEL_UP" -> Icons.Default.WorkspacePremium
    "BADGE_UNLOCKED" -> Icons.Default.EmojiEvents
    else -> Icons.AutoMirrored.Filled.Chat
}

private fun accentFor(type: String): Color = when (type) {
    "MENTION" -> PrintCoral
    "TASK_ASSIGNED", "TASK_UPDATED", "TASK_DUE" -> PrintLeaf
    "DM", "CHANNEL_MESSAGE" -> PrintSky
    "LEVEL_UP", "BADGE_UNLOCKED" -> Color(0xFFF6D365)
    else -> PrintPaper
}

@Composable
private fun NotificationRow(
    notification: NotificationResponse,
    now: Long,
    onClick: () -> Unit,
    onAcceptInvitation: ((invitationId: Int, notificationId: Int) -> Unit)? = null,
    onDeclineInvitation: ((invitationId: Int, notificationId: Int) -> Unit)? = null,
    isInvitationActionPending: Boolean = false,
    unfurlViewModel: GitHubUnfurlViewModel? = null,
    githubPreviews: Map<String, com.collabsphere.app.viewmodel.GitHubPreviewItem>? = null,
    githubActionStates: Map<String, com.collabsphere.app.viewmodel.GitHubActionClientState>? = null,
    githubActionMessages: Map<String, String>? = null
) {
    val accent = accentFor(notification.type)
    val unread = !notification.isRead
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (unread) PrintWhite else PrintPaper)
            .border(if (unread) 2.dp else 1.5.dp, if (unread) PrintInk else PrintInk.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.size(44.dp)) {
                val fallback: @Composable () -> Unit = {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .printSurface(shape = CircleShape, fill = accent, shadow = null, outline = 1.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(iconFor(notification.type), contentDescription = null, tint = PrintInk, modifier = Modifier.size(20.dp))
                    }
                }
                val avatarUrl = notification.actorAvatarUrl
                if (avatarUrl.isNullOrBlank()) {
                    fallback()
                } else {
                    SubcomposeAsyncImage(
                        model = avatarUrl,
                        contentDescription = notification.actorUsername,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(42.dp).clip(CircleShape),
                        loading = { fallback() },
                        error = { fallback() }
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(accent)
                            .border(1.5.dp, PrintInk, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(iconFor(notification.type), contentDescription = null, tint = PrintInk, modifier = Modifier.size(12.dp))
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = notification.title,
                        style = PrintType.Body.copy(fontSize = 15.sp, fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium),
                        color = PrintInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = NotificationInbox.relativeTime(notification.createdAt, now),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal),
                        color = if (unread) PrintInk else Muted
                    )
                    if (unread) {
                        Spacer(Modifier.width(6.dp))
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(PrintCoral).border(1.5.dp, PrintInk, CircleShape))
                    }
                }
                if (notification.body.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = notification.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (unread) PrintInk else Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                notification.channelName?.takeIf { it.isNotBlank() }?.let { channel ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "#$channel",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Muted
                    )
                }

                if (notification.type == "WORKSPACE_INVITE" && notification.referenceId != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(PrintLeaf)
                                .border(1.5.dp, PrintInk, RoundedCornerShape(50))
                                .clickable(enabled = !isInvitationActionPending) {
                                    onAcceptInvitation?.invoke(notification.referenceId, notification.id)
                                }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isInvitationActionPending) "Working…" else "Accept",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = PrintInk
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(PrintWhite)
                                .border(1.5.dp, PrintInk, RoundedCornerShape(50))
                                .clickable(enabled = !isInvitationActionPending) {
                                    onDeclineInvitation?.invoke(notification.referenceId, notification.id)
                                }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isInvitationActionPending) "Working…" else "Decline",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = PrintInk
                            )
                        }
                    }
                }

                if (unfurlViewModel != null && githubPreviews != null) {
                    val githubRegex = Regex("(https://github\\.com/[^/]+/[^/]+/[\\w/]+|https://github\\.com/[^/]+/[^/]+/?(?=\\s|$))")
                    val matches = githubRegex.findAll(notification.body).map { it.value }.toSet()
                    matches.forEach { url ->
                        val workspaceId = notification.workspaceId
                        if (workspaceId != null) {
                            unfurlViewModel.requestUnfurl(workspaceId, url)
                            val preview = githubPreviews[url]
                            if (preview != null) {
                                Spacer(Modifier.height(8.dp))
                                com.collabsphere.app.view.components.GitHubUnfurlCard(
                                    preview = preview,
                                    actionState = githubActionStates?.get(url),
                                    actionMessage = githubActionMessages?.get(url),
                                    onActionClick = { action, body ->
                                        unfurlViewModel.performAction(workspaceId, url, action, body)
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
