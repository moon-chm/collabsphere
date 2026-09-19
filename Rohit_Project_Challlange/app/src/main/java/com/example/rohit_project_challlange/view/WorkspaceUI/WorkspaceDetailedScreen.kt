package com.example.rohit_project_challlange.view.WorkspaceUI

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
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.rohit_project_challlange.model.channels.ChannelEntity
import com.example.rohit_project_challlange.model.UserEntity
import com.example.rohit_project_challlange.ui.theme.*
import com.example.rohit_project_challlange.view.channel.ChannelScreen
import com.example.rohit_project_challlange.viewmodel.channel.ChannelViewModel
import com.example.rohit_project_challlange.view.TaskUI.TaskScreen
import com.example.rohit_project_challlange.view.FileUI.FileScreen
import com.example.rohit_project_challlange.view.NotesUI.NotesScreen
import com.example.rohit_project_challlange.view.dmUI.DMScreen
import com.example.rohit_project_challlange.viewmodel.file.FileViewModel
import com.example.rohit_project_challlange.viewmodel.notes.NotesViewModel
import com.example.rohit_project_challlange.viewmodel.task.TaskViewModel
import com.example.rohit_project_challlange.viewmodel.dm.DmViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceDetailedScreen(
    workspaceName: String,
    userId: Long,
    workspaceId: Int,
    initialTab: Int = 0,
    initialPartnerId: Int? = null,
    onBack: () -> Unit,
    onChannelClick: (ChannelEntity) -> Unit,
    onAddMemberSubmit: (email: String) -> Unit,
    channelViewModel: ChannelViewModel,
    taskViewModel: TaskViewModel,
    notesViewModel: NotesViewModel,
    fileViewModel: FileViewModel,
    dmViewModel: DmViewModel,
    workspaceMembers: List<UserEntity>
) {
    var selectedTab by remember { mutableStateOf(initialTab) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
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
                    .drawBehind {
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
                            .drawBehind {
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
                        text = "Add team member",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Ink
                    )

                    Text(
                        text = "Enter email address to invite to $workspaceName.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted
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
                                modifier = Modifier.weight(1f),
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
                                .drawBehind {
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
                                .drawBehind {
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
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val email = memberEmailInput.trim()
                                    if (email.isNotEmpty()) {
                                        onAddMemberSubmit(email)
                                        showAddMemberDialog = false
                                        memberEmailInput = ""
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Add",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Tactile Top Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .drawBehind {
                        // Bottom rim shadow
                        drawRect(
                            color = ShadowDark.copy(alpha = 0.12f),
                            topLeft = Offset(0f, size.height),
                            size = Size(size.width, 3.dp.toPx())
                        )
                        // Fill
                        drawRect(color = SurfaceRaised)
                        // Top hairline
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

                    // Workspace Title
                    Text(
                        text = workspaceName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    )

                    // Add Member Action
                    IconButton(
                        onClick = { showAddMemberDialog = true },
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
                            imageVector = Icons.Default.GroupAdd,
                            contentDescription = "Add Member",
                            tint = CoralStart,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Skeuomorphic Tactile Bottom Navigation Bar — floating tray with true contact & ambient shadow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(82.dp)
                    .drawBehind {
                        // 1. Upward ambient diffuse shadow
                        drawRect(
                            color = Color(0xFF2C201A).copy(alpha = 0.07f),
                            topLeft = Offset(0f, -6.dp.toPx()),
                            size = Size(size.width, 6.dp.toPx())
                        )
                        // 2. Upward tight contact shadow (2.3x ambient alpha)
                        drawRect(
                            color = Color(0xFF2C201A).copy(alpha = 0.16f),
                            topLeft = Offset(0f, -1.5.dp.toPx()),
                            size = Size(size.width, 1.5.dp.toPx())
                        )
                        // 3. Elevated SurfaceRaised tray body (#FDFBF7)
                        drawRect(color = SurfaceRaised)
                        // 4. Subtle perimeter hairline boundary
                        drawRect(
                            color = Color(0xFF2C2A28).copy(alpha = 0.07f),
                            topLeft = Offset(0f, 0f),
                            size = Size(size.width, 1.dp.toPx())
                        )
                        // 5. Bright specular rim highlight along top edge
                        drawRect(
                            color = Color.White.copy(alpha = 0.95f),
                            topLeft = Offset(0f, 1.dp.toPx()),
                            size = Size(size.width, 1.dp.toPx())
                        )
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
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
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTab = 0 }
                    )
                    SkeuoTabItem(
                        selected = selectedTab == 4,
                        icon = Icons.Outlined.ChatBubbleOutline,
                        label = "Chat",
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTab = 4 }
                    )
                    SkeuoTabItem(
                        selected = selectedTab == 1,
                        icon = Icons.Outlined.CheckBox,
                        label = "Tasks",
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTab = 1 }
                    )
                    SkeuoTabItem(
                        selected = selectedTab == 3,
                        icon = Icons.Outlined.Description,
                        label = "Docs",
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTab = 3 }
                    )
                    SkeuoTabItem(
                        selected = selectedTab == 2,
                        icon = Icons.Outlined.Folder,
                        label = "Files",
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTab = 2 }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                    1 -> TaskScreen(viewModel = taskViewModel)
                    2 -> FileScreen(viewModel = fileViewModel)
                    3 -> NotesScreen(viewModel = notesViewModel)
                    4 -> DMScreen(
                        viewModel = dmViewModel,
                        workspaceId = workspaceId,
                        currentUserId = userId,
                        initialPartnerId = initialPartnerId,
                        onExitModule = { selectedTab = 0 }
                    )
                }
            }
        }
    }
}

/** Individual skeuomorphic bottom tab squircle tile matching reference image */
@Composable
private fun SkeuoTabItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Tactile depth compression on tap: sinks down when pressed or active
    val targetScale = if (isPressed) 0.93f else if (selected) 0.98f else 1.0f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tabTileScale"
    )

    // Colors matching screenshot:
    // Terracotta accent for selected: #A63F20 (WCAG AA compliant against #EAE5DC)
    // Charcoal warm brown for unselected icon: #4F423F
    // Muted warm brown for unselected text: #70625E
    val selectedAccent = Color(0xFFA63F20)
    val unselectedIconColor = Color(0xFF4F423F)
    val unselectedTextColor = Color(0xFF70625E)

    val iconColor by animateColorAsState(
        targetValue = if (selected) selectedAccent else unselectedIconColor,
        animationSpec = tween(180),
        label = "tabIconColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (selected) selectedAccent else unselectedTextColor,
        animationSpec = tween(180),
        label = "tabTextColor"
    )

    Box(
        modifier = modifier
            .height(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                val cr = 18.dp.toPx()
                if (selected) {
                    // ── SUNKEN / INSET / DEBOSSED BUTTON (Exact match to "Spaces" in screenshot) ──
                    // 1. Soft cavity edge around the debossed perimeter
                    drawRoundRect(
                        color = Color(0xFF281E19).copy(alpha = 0.12f),
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(cr)
                    )
                    // 2. Top-left dark inner shadow (the physical sink into the tray)
                    drawRoundRect(
                        color = Color(0xFF221713).copy(alpha = 0.16f),
                        topLeft = Offset(1.5.dp.toPx(), 2.dp.toPx()),
                        size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                        cornerRadius = CornerRadius(cr)
                    )
                    // 3. Sunken interior warm bed (slightly recessed warm gradient)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFEAE5DC),
                                Color(0xFFF3EFE7)
                            )
                        ),
                        topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                        size = Size(size.width - 3.dp.toPx(), size.height - 3.dp.toPx()),
                        cornerRadius = CornerRadius(cr)
                    )
                    // 4. Bottom-right inner specular reflection rim
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.85f),
                        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                        size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                        cornerRadius = CornerRadius(cr),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                } else {
                    // ── RAISED EXTRUDED BUTTON (Exact match to "Chat", "Tasks", "Docs" in screenshot) ──
                    // 1. Bottom-right soft dark drop shadow
                    drawRoundRect(
                        color = Color(0xFF2C201A).copy(alpha = 0.10f),
                        topLeft = Offset(1.5.dp.toPx(), 2.5.dp.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(cr)
                    )
                    // 2. Top-left bright specular highlight
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.95f),
                        topLeft = Offset(-1.5.dp.toPx(), -1.5.dp.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(cr)
                    )
                    // 3. Elevated warm cream button fill
                    drawRoundRect(
                        color = Color(0xFFFAF7F2),
                        cornerRadius = CornerRadius(cr)
                    )
                    // 4. Subtle top specular hairline perfectly hugging rounded shoulders
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.85f),
                                Color.White.copy(alpha = 0.20f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = cr * 1.2f
                        ),
                        topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                        size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                        cornerRadius = CornerRadius(cr),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(23.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = textColor,
                maxLines = 1
            )
        }
    }
}