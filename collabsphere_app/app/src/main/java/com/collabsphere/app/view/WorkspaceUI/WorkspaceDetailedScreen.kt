package com.collabsphere.app.view.WorkspaceUI

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import com.collabsphere.app.model.MuteRepo
import com.collabsphere.app.view.components.AppToast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.collabsphere.app.model.channels.ChannelEntity
import com.collabsphere.app.model.UserEntity
import com.collabsphere.app.ui.theme.*
import com.collabsphere.app.view.channel.ChannelScreen
import com.collabsphere.app.viewmodel.channel.ChannelViewModel
import com.collabsphere.app.view.TaskUI.TaskScreen
import com.collabsphere.app.view.FileUI.FileScreen
import com.collabsphere.app.view.NotesUI.NotesScreen
import com.collabsphere.app.view.dmUI.DMScreen
import com.collabsphere.app.viewmodel.file.FileViewModel
import com.collabsphere.app.viewmodel.notes.NotesViewModel
import com.collabsphere.app.viewmodel.task.TaskViewModel
import com.collabsphere.app.viewmodel.dm.DmViewModel
import com.collabsphere.app.viewmodel.GitHubViewModel
import com.collabsphere.app.view.components.CollabSpinner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceDetailedScreen(
    workspaceName: String,
    userId: Long,
    workspaceId: Int,
    initialTab: Int = 0,
    initialPartnerId: Int? = null,
    currentTab: Int? = null,
    onCurrentTabChange: (Int) -> Unit = {},
    onDmPartnerChange: (Int?) -> Unit = {},
    onBack: () -> Unit,
    onSearchClick: () -> Unit = {},
    onInsightsClick: () -> Unit = {},
    onLeftWorkspace: () -> Unit = {},
    onChannelClick: (ChannelEntity) -> Unit,
    onAddMemberSubmit: (email: String) -> Unit,
    channelViewModel: ChannelViewModel,
    taskViewModel: TaskViewModel,
    notesViewModel: NotesViewModel,
    fileViewModel: FileViewModel,
    dmViewModel: DmViewModel,
    gitHubViewModel: GitHubViewModel,
    workspaceMembers: List<UserEntity>,
    isSendingInvitation: Boolean = false,
    invitationStatus: String? = null
) {
    var localSelectedTab by remember(initialTab) { mutableStateOf(initialTab) }
    val selectedTab = currentTab ?: localSelectedTab
    val changeSelectedTab: (Int) -> Unit = { tab ->
        localSelectedTab = tab
        onCurrentTabChange(tab)
    }
    val muteRepo = koinInject<MuteRepo>()
    val mutes by muteRepo.mutes.collectAsStateWithLifecycle()
    val isWorkspaceMuted = MuteRepo.isWorkspaceMuted(mutes, workspaceId)
    val muteScope = rememberCoroutineScope()
    val muteContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(workspaceId) { muteRepo.refresh() }
    var isDmInConversation by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showMembersDialog by remember { mutableStateOf(false) }

    LaunchedEffect(invitationStatus) {
        if (invitationStatus != null && !invitationStatus.contains("Failed", ignoreCase = true)) {
            showAddMemberDialog = false
        }
    }

    if (showMembersDialog) {
        WorkspaceMembersDialog(
            workspaceId = workspaceId,
            currentUserId = userId.toInt(),
            onInviteClick = {
                showMembersDialog = false
                showAddMemberDialog = true
            },
            onLeftWorkspace = {
                showMembersDialog = false
                onLeftWorkspace()
            },
            onDismiss = { showMembersDialog = false }
        )
    }
    var memberEmailInput by remember { mutableStateOf("") }

    if (showAddMemberDialog) {
        val dismissInvite = {
            showAddMemberDialog = false
            memberEmailInput = ""
        }
        Dialog(
            onDismissRequest = dismissInvite,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            com.collabsphere.app.view.components.WorkspaceDialogCard(
                icon = Icons.Default.GroupAdd,
                accent = CoralStart,
                title = "Invite a teammate",
                subtitle = "We'll email them a code to join $workspaceName.",
                onClose = dismissInvite,
                closeEnabled = !isSendingInvitation
            ) {
                val canAdd = memberEmailInput.trim().isNotEmpty()
                com.collabsphere.app.view.components.SkeuoTextField(
                    value = memberEmailInput,
                    onValueChange = { memberEmailInput = it },
                    label = "Email address",
                    placeholder = "colleague@company.com",
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                    onImeAction = {
                        val email = memberEmailInput.trim()
                        if (email.isNotEmpty() && !isSendingInvitation) onAddMemberSubmit(email)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = PrintInk,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
                Spacer(Modifier.height(20.dp))
                com.collabsphere.app.view.components.WorkspaceDialogButton(
                    text = "Send invite",
                    colors = listOf(PrintInk),
                    enabled = canAdd,
                    loading = isSendingInvitation,
                    loadingText = "Sending invite",
                    onClick = {
                        val email = memberEmailInput.trim()
                        if (email.isNotEmpty()) onAddMemberSubmit(email)
                    }
                )
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (selectedTab == 4 && isDmInConversation) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
    bottomBar = {
            if (!(selectedTab == 4 && isDmInConversation)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 18.dp, top = 8.dp, bottom = 4.dp)
                            .height(56.dp)
                            .printSurface(shape = RoundedCornerShape(percent = 50), shadowOffset = 3.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SkeuoActionIconButton(
                            onClick = onBack,
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            size = 40.dp,
                            iconSize = 20.dp,
                            tint = Ink
                        )
                        Text(
                            text = workspaceName,
                            style = PrintType.H3.copy(fontFamily = FrauncesFamily, fontSize = 19.sp),
                            color = PrintInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SkeuoActionIconButton(
                                onClick = onInsightsClick,
                                icon = Icons.Outlined.Insights,
                                contentDescription = "Workspace insights",
                                size = 36.dp,
                                iconSize = 18.dp,
                                tint = Ink
                            )
                            SkeuoActionIconButton(
                                onClick = {
                                    muteScope.launch {
                                        muteRepo.setMuted(workspaceId, null, !isWorkspaceMuted)
                                            .onSuccess {
                                                AppToast.show(if (isWorkspaceMuted) "Workspace channels unmuted" else "Workspace channels muted — you'll still get @mentions and DMs")
                                            }
                                            .onFailure {
                                                AppToast.error("Couldn't update mute. Check your connection.")
                                            }
                                    }
                                },
                                icon = if (isWorkspaceMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                contentDescription = if (isWorkspaceMuted) "Unmute workspace" else "Mute workspace",
                                size = 36.dp,
                                iconSize = 18.dp,
                                tint = Ink
                            )
                            SkeuoActionIconButton(
                                onClick = onSearchClick,
                                icon = Icons.Default.Search,
                                contentDescription = "Search workspace",
                                size = 36.dp,
                                iconSize = 18.dp,
                                tint = Ink
                            )
                            SkeuoActionIconButton(
                                onClick = { showMembersDialog = true },
                                icon = Icons.Default.GroupAdd,
                                contentDescription = "Members",
                                size = 36.dp,
                                iconSize = 18.dp,
                                tint = Ink
                            )
                        }
                    }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 18.dp, top = 8.dp, bottom = 16.dp)
                        .height(68.dp)
                        .printSurface(shape = RoundedCornerShape(percent = 50))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SkeuoTabItem(
                            selected = selectedTab == 0,
                            icon = Icons.Outlined.GridView,
                            label = "Spaces",
                            onClick = { changeSelectedTab(0) }
                        )
                        SkeuoTabItem(
                            selected = selectedTab == 4,
                            icon = Icons.Outlined.ChatBubbleOutline,
                            label = "Chat",
                            onClick = { changeSelectedTab(4) }
                        )
                        SkeuoTabItem(
                            selected = selectedTab == 1,
                            icon = Icons.Outlined.CheckBox,
                            label = "Tasks",
                            onClick = { changeSelectedTab(1) }
                        )
                        SkeuoTabItem(
                            selected = selectedTab == 3,
                            icon = Icons.Outlined.Description,
                            label = "Docs",
                            onClick = { changeSelectedTab(3) }
                        )
                        SkeuoTabItem(
                            selected = selectedTab == 2,
                            icon = Icons.Outlined.Folder,
                            label = "Files",
                            onClick = { changeSelectedTab(2) }
                        )
                        SkeuoTabItem(
                            selected = selectedTab == 5,
                            icon = Icons.Outlined.AccountTree,
                            label = "GitHub",
                            onClick = { changeSelectedTab(5) }
                        )
                    }
                }
            }
        }
   }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (selectedTab == 4 && isDmInConversation) PaddingValues(0.dp) else paddingValues)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(
                            animationSpec = tween(260, easing = FastOutSlowInEasing),
                            initialOffsetX = { width -> width / 4 }
                        ) + fadeIn(animationSpec = tween(200)))
                            .togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                                    targetOffsetX = { width -> -width / 4 }
                                ) + fadeOut(animationSpec = tween(200))
                            )
                    } else {
                        (slideInHorizontally(
                            animationSpec = tween(260, easing = FastOutSlowInEasing),
                            initialOffsetX = { width -> -width / 4 }
                        ) + fadeIn(animationSpec = tween(200)))
                            .togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                                    targetOffsetX = { width -> width / 4 }
                                ) + fadeOut(animationSpec = tween(200))
                            )
                    }
                },
                modifier = Modifier.fillMaxSize(),
                label = "workspaceTabContent"
            ) { tab ->
                when (tab) {
                    0 -> ChannelScreen(
                        viewModel = channelViewModel,
                        onChannelClick = onChannelClick
                    )
                    1 -> TaskScreen(viewModel = taskViewModel, gitHubViewModel = gitHubViewModel)
                    2 -> FileScreen(viewModel = fileViewModel)
                    3 -> NotesScreen(viewModel = notesViewModel)
                    4 -> DMScreen(
                        viewModel = dmViewModel,
                        workspaceId = workspaceId,
                        currentUserId = userId,
                        initialPartnerId = initialPartnerId,
                        onConversationActiveChange = { active -> isDmInConversation = active },
                        onActivePartnerChange = onDmPartnerChange,
                        onExitModule = { changeSelectedTab(0) }
                    )
                    5 -> WorkspaceGitHubScreen(
                        workspaceId = workspaceId,
                        viewModel = gitHubViewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.SkeuoTabItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val reduced = rememberPrintReducedMotion()
    val weight by animateFloatAsState(
        targetValue = if (selected) 2.5f else 1f,
        animationSpec = if (reduced) tween(150) else spring(dampingRatio = 0.75f, stiffness = 320f),
        label = "tabWeight"
    )
    val fill by animateColorAsState(
        targetValue = if (selected) PrintLeaf else Color.Transparent,
        animationSpec = tween(180),
        label = "tabFill"
    )
    val outline by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(180),
        label = "tabOutline"
    )
    val iconPop = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected && !reduced) {
            iconPop.snapTo(0.7f)
            iconPop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
        }
    }

    Box(
        modifier = Modifier
            .weight(weight)
            .height(50.dp)
            .drawWithCache {
                val cr = CornerRadius(size.height / 2f)
                val stroke = 2.dp.toPx()
                onDrawBehind {
                    drawRoundRect(color = fill, cornerRadius = cr)
                    if (outline > 0f) {
                        drawRoundRect(
                            color = PrintInk.copy(alpha = outline),
                            topLeft = Offset(stroke / 2f, stroke / 2f),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(size.height / 2f - stroke / 2f),
                            style = Stroke(stroke)
                        )
                    }
                }
            }
            .clip(RoundedCornerShape(percent = 50))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = androidx.compose.ui.semantics.Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = PrintInk,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconPop.value
                        scaleY = iconPop.value
                    }
            )
            androidx.compose.animation.AnimatedVisibility(visible = selected) {
                Row {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = PrintType.Caption.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                        color = PrintInk,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}
