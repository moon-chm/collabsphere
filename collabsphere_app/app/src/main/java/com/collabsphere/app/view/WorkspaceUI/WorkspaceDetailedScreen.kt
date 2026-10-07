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
    onBack: () -> Unit,
    onSearchClick: () -> Unit = {},
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
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }
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

    // Skeuomorphic Add Member Dialog
    if (showAddMemberDialog) {
        Dialog(onDismissRequest = {
            showAddMemberDialog = false
            memberEmailInput = ""
        }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithCache {
                        onDrawBehind {
                        drawRoundRect(
                            color = ShadowDark.copy(alpha = 0.35f),
                            topLeft = Offset(4.dp.toPx(), 8.dp.toPx()),
                            size = Size(size.width, size.height),
                            cornerRadius = CornerRadius(24.dp.toPx())
                        )
                        drawRoundRect(
                            color = ShadowLight.copy(alpha = 0.90f),
                            topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()),
                            size = Size(size.width, size.height),
                            cornerRadius = CornerRadius(24.dp.toPx())
                        )
                        drawRoundRect(
                            color = SurfaceRaised,
                            cornerRadius = CornerRadius(24.dp.toPx())
                        )
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.75f),
                                    Color.White.copy(alpha = 0.15f),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = 24.dp.toPx()
                            ),
                            topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                            size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                            cornerRadius = CornerRadius(24.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx())
                        )
                                            }
                    }
                    .padding(24.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header badge
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .drawWithCache {
                                onDrawBehind {
                                drawCircle(
                                    color = ShadowDark.copy(alpha = 0.25f),
                                    radius = size.minDimension / 2f,
                                    center = Offset(center.x + 2.dp.toPx(), center.y + 3.dp.toPx())
                                )
                                drawCircle(
                                    color = ShadowLight.copy(alpha = 0.9f),
                                    radius = size.minDimension / 2f,
                                    center = Offset(center.x - 1.5.dp.toPx(), center.y - 1.5.dp.toPx())
                                )
                                drawCircle(
                                    color = Surface,
                                    radius = size.minDimension / 2f
                                )
                                                            }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GroupAdd,
                            contentDescription = null,
                            tint = CoralStart,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Text(
                        text = "Invite team member",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Ink
                    )

                    Text(
                        text = "Enter email address to send an invitation to $workspaceName. An invitation code will be emailed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    // Inset debossed email field
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .skeuoInset(cornerRadius = 14.dp, depth = 2.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = Muted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            BasicTextField(
                                value = memberEmailInput,
                                onValueChange = { memberEmailInput = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(CoralStart),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        val email = memberEmailInput.trim()
                                        if (email.isNotEmpty()) {
                                            onAddMemberSubmit(email)
                                            showAddMemberDialog = false
                                            memberEmailInput = ""
                                        }
                                    }
                                ),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (memberEmailInput.isEmpty()) {
                                            Text(
                                                text = "colleague@company.com",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Ink.copy(alpha = 0.65f)
                                            )
                                        }
                                        inner()
                                    }
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cancel button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .drawWithCache {
                                    onDrawBehind {
                                    drawRoundRect(
                                        color = ShadowDark.copy(alpha = 0.15f),
                                        topLeft = Offset(0f, 2.dp.toPx()),
                                        size = Size(size.width, size.height),
                                        cornerRadius = CornerRadius(12.dp.toPx())
                                    )
                                    drawRoundRect(
                                        color = Surface,
                                        cornerRadius = CornerRadius(12.dp.toPx())
                                    )
                                                                    }
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    showAddMemberDialog = false
                                    memberEmailInput = ""
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cancel",
                                style = MaterialTheme.typography.labelLarge,
                                color = Muted
                            )
                        }

                        // Add button
                        val canAdd = memberEmailInput.trim().isNotEmpty()
                        val addAlpha = if (canAdd) 1f else 0.72f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .drawWithCache {
                                    onDrawBehind {
                                    drawRoundRect(
                                        color = CoralStart.copy(alpha = 0.25f * addAlpha),
                                        topLeft = Offset(0f, 2.dp.toPx()),
                                        size = Size(size.width, size.height),
                                        cornerRadius = CornerRadius(12.dp.toPx())
                                    )
                                    drawRoundRect(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                CoralStart.copy(alpha = addAlpha),
                                                CoralEnd.copy(alpha = addAlpha)
                                            ),
                                            start = Offset(0f, 0f),
                                            end = Offset(size.width, size.height)
                                        ),
                                        cornerRadius = CornerRadius(12.dp.toPx())
                                    )
                                                                    }
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !isSendingInvitation) {
                                    val email = memberEmailInput.trim()
                                    if (email.isNotEmpty()) {
                                        onAddMemberSubmit(email)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSendingInvitation) {
                                CollabSpinner(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text(
                                    text = "Send Invite",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
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
                    // Floating Context Pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .height(52.dp)
                            .drawWithCache {
                                onDrawBehind {
                                    drawRoundRect(
                                        color = ShadowDark.copy(alpha = 0.15f),
                                        topLeft = Offset(0f, 4.dp.toPx()),
                                        size = Size(size.width, size.height),
                                        cornerRadius = CornerRadius(26.dp.toPx())
                                    )
                                    drawRoundRect(
                                        color = SurfaceRaised.copy(alpha = 0.95f),
                                        cornerRadius = CornerRadius(26.dp.toPx())
                                    )
                                    drawRoundRect(
                                        color = Color.White.copy(alpha = 0.8f),
                                        topLeft = Offset(0f, -1.dp.toPx()),
                                        size = Size(size.width, size.height),
                                        cornerRadius = CornerRadius(26.dp.toPx()),
                                        style = Stroke(width = 1.dp.toPx())
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Back button
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(36.dp)
                                    .drawWithCache {
                                        onDrawBehind {
                                            drawCircle(color = ShadowDark.copy(alpha = 0.22f), radius = size.minDimension / 2f, center = Offset(center.x + 1.5.dp.toPx(), center.y + 2.dp.toPx()))
                                            drawCircle(color = ShadowLight.copy(alpha = 0.90f), radius = size.minDimension / 2f, center = Offset(center.x - 1.5.dp.toPx(), center.y - 1.5.dp.toPx()))
                                            drawCircle(color = Surface, radius = size.minDimension / 2f)
                                        }
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Ink,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Workspace Title
                            Text(
                                text = workspaceName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            )

                            // Trailing action icons with consistent spacing
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Mute / Unmute
                                IconButton(
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
                                    modifier = Modifier
                                        .size(32.dp)
                                        .drawWithCache {
                                            onDrawBehind {
                                                drawCircle(color = ShadowDark.copy(alpha = 0.22f), radius = size.minDimension / 2f, center = Offset(center.x + 1.5.dp.toPx(), center.y + 2.dp.toPx()))
                                                drawCircle(color = ShadowLight.copy(alpha = 0.90f), radius = size.minDimension / 2f, center = Offset(center.x - 1.5.dp.toPx(), center.y - 1.5.dp.toPx()))
                                                drawCircle(color = Surface, radius = size.minDimension / 2f)
                                            }
                                        }
                                ) {
                                    Icon(
                                        imageVector = if (isWorkspaceMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                        contentDescription = if (isWorkspaceMuted) "Unmute workspace" else "Mute workspace",
                                        tint = if (isWorkspaceMuted) Muted else IndigoStart,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Search
                                IconButton(
                                    onClick = onSearchClick,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .drawWithCache {
                                            onDrawBehind {
                                                drawCircle(color = ShadowDark.copy(alpha = 0.22f), radius = size.minDimension / 2f, center = Offset(center.x + 1.5.dp.toPx(), center.y + 2.dp.toPx()))
                                                drawCircle(color = ShadowLight.copy(alpha = 0.90f), radius = size.minDimension / 2f, center = Offset(center.x - 1.5.dp.toPx(), center.y - 1.5.dp.toPx()))
                                                drawCircle(color = Surface, radius = size.minDimension / 2f)
                                            }
                                        }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search workspace",
                                        tint = IndigoStart,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Members / Add Member
                                IconButton(
                                    onClick = { showMembersDialog = true },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .drawWithCache {
                                            onDrawBehind {
                                                drawCircle(color = ShadowDark.copy(alpha = 0.22f), radius = size.minDimension / 2f, center = Offset(center.x + 1.5.dp.toPx(), center.y + 2.dp.toPx()))
                                                drawCircle(color = ShadowLight.copy(alpha = 0.90f), radius = size.minDimension / 2f, center = Offset(center.x - 1.5.dp.toPx(), center.y - 1.5.dp.toPx()))
                                                drawCircle(color = Surface, radius = size.minDimension / 2f)
                                            }
                                        }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GroupAdd,
                                        contentDescription = "Add Member",
                                        tint = CoralStart,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                // Ultra-frosted Tactile Capsule Dock
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(68.dp)
                        .drawWithCache {
                            onDrawBehind {
                                val cr = size.height / 2f
                                // 1. Ambient capsule drop shadow
                                drawRoundRect(
                                    color = ShadowDark.copy(alpha = 0.12f),
                                    topLeft = Offset(0f, 8.dp.toPx()),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(cr)
                                )
                                // 2. Frosted glassmorphic body
                                drawRoundRect(
                                    color = SurfaceRaised.copy(alpha = 0.85f),
                                    cornerRadius = CornerRadius(cr)
                                )
                                // 3. Subtle perimeter boundary
                                drawRoundRect(
                                    color = ShadowDark.copy(alpha = 0.05f),
                                    cornerRadius = CornerRadius(cr),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                                // 4. Inner specular highlight
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.95f),
                                    topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                                    size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                                    cornerRadius = CornerRadius(cr),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                            }
                        }
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SkeuoTabItem(
                            selected = selectedTab == 0,
                            icon = Icons.Outlined.GridView,
                            label = "Spaces",
                            onClick = { selectedTab = 0 }
                        )
                        SkeuoTabItem(
                            selected = selectedTab == 4,
                            icon = Icons.Outlined.ChatBubbleOutline,
                            label = "Chat",
                            onClick = { selectedTab = 4 }
                        )
                        SkeuoTabItem(
                            selected = selectedTab == 1,
                            icon = Icons.Outlined.CheckBox,
                            label = "Tasks",
                            onClick = { selectedTab = 1 }
                        )
                        SkeuoTabItem(
                            selected = selectedTab == 3,
                            icon = Icons.Outlined.Description,
                            label = "Docs",
                            onClick = { selectedTab = 3 }
                        )
                        SkeuoTabItem(
                            selected = selectedTab == 2,
                            icon = Icons.Outlined.Folder,
                            label = "Files",
                            onClick = { selectedTab = 2 }
                        )
                        SkeuoTabItem(
                            selected = selectedTab == 5,
                            icon = Icons.Outlined.AccountTree,
                            label = "GitHub",
                            onClick = { selectedTab = 5 }
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
                        onExitModule = { selectedTab = 0 }
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

/** Individual skeuomorphic bottom tab squircle tile matching reference image */
@Composable
private fun RowScope.SkeuoTabItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetScale = if (isPressed) 0.93f else 1.0f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tabScale"
    )

    val weight by animateFloatAsState(
        targetValue = if (selected) 2.5f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "tabWeight"
    )

    val selectedAccent = CoralStart
    val unselectedIconColor = Muted
    
    val iconColor by animateColorAsState(
        targetValue = if (selected) Color.White else unselectedIconColor,
        animationSpec = tween(180),
        label = "tabIconColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (selected) Color.White else unselectedIconColor,
        animationSpec = tween(180),
        label = "tabTextColor"
    )

    Box(
        modifier = Modifier
            .weight(weight)
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawWithCache {
                onDrawBehind {
                    val cr = size.height / 2f
                    if (selected) {
                        // Glowing tactile Coral Pill
                        drawRoundRect(
                            brush = Brush.linearGradient(listOf(CoralStart, CoralEnd)),
                            cornerRadius = CornerRadius(cr)
                        )
                        // Specular highlight
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.4f),
                            topLeft = Offset(0f, 0f),
                            size = Size(size.width, size.height),
                            cornerRadius = CornerRadius(cr),
                            style = Stroke(width = 1.dp.toPx())
                        )
                        // Inner glow/shadow
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.2f),
                            topLeft = Offset(0f, 1.dp.toPx()),
                            size = Size(size.width, size.height - 2.dp.toPx()),
                            cornerRadius = CornerRadius(cr),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
            }
            .clip(RoundedCornerShape(26.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            androidx.compose.animation.AnimatedVisibility(visible = selected) {
                Row {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}
