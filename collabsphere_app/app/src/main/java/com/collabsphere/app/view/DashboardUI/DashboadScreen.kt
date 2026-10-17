package com.collabsphere.app.view

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import com.collabsphere.app.view.components.AppToast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.collabsphere.app.model.workspace.WorkspaceEntity
import com.collabsphere.app.ui.theme.*
import com.collabsphere.app.viewmodel.DashboardViewModel
import java.util.Calendar
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics

private val workspaceAccents = listOf(PrintLeaf, PrintCoral, PrintSky)

internal fun workspaceAccentIndex(workspaceId: Int): Int =
    Math.floorMod(workspaceId, workspaceAccents.size)

internal fun dashboardGreeting(name: String, hour: Int): String {
    val part = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    val first = name.trim().substringBefore(' ')
    return if (first.isEmpty()) "$part." else "$part, $first."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel             : DashboardViewModel,
    currentUserName       : String = "",
    avatarUrl             : String = "",
    onNavigateToWorkspace : () -> Unit,
    onWorkspaceClick      : (WorkspaceEntity) -> Unit,
    onDeleteWorkspaceClick: (WorkspaceEntity) -> Unit,
    onProfileClick        : () -> Unit = {},
    onSearchClick         : () -> Unit = {},
    onNotificationsClick  : () -> Unit = {},
    unreadNotificationCount: Int = 0,
    onJoinByCode          : ((String) -> Unit)? = null,
    joinByCodeStatus      : String? = null,
    onClearJoinByCodeStatus: (() -> Unit)? = null,
    onOpenWorkspaceTasks  : (WorkspaceEntity) -> Unit = {},
    onSavedMessagesClick  : () -> Unit = {}
) {
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()
    val myDayUserId by viewModel.userIdState.collectAsStateWithLifecycle()
    var showJoinByCodeDialog by remember { mutableStateOf(false) }
    val shown = remember { mutableSetOf<String>() }
    val greeting = remember(currentUserName) {
        dashboardGreeting(currentUserName, Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
    }

    LaunchedEffect(joinByCodeStatus) {
        joinByCodeStatus?.let {
            AppToast.show(it)
            onClearJoinByCodeStatus?.invoke()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PrintPaper)
            .paperGrain()
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "header") {
                    DashboardHeader(
                        greeting = greeting,
                        currentUserName = currentUserName,
                        avatarUrl = avatarUrl,
                        unreadNotificationCount = unreadNotificationCount,
                        onSearchClick = onSearchClick,
                        onNotificationsClick = onNotificationsClick,
                        onProfileClick = onProfileClick
                    )
                }
                item(key = "banner") {
                    DashboardSummaryBanner(
                        count = workspaces?.size ?: 0,
                        modifier = Modifier.printEntrance(0, "banner" in shown) { shown += "banner" }
                    )
                }
                item(key = "myday") {
                    Box(Modifier.printEntrance(1, "myday" in shown) { shown += "myday" }) {
                        MyDayCard(
                            userId = myDayUserId,
                            workspaces = workspaces.orEmpty(),
                            unreadCount = unreadNotificationCount,
                            onOpenTasks = onOpenWorkspaceTasks,
                            onNotificationsClick = onNotificationsClick
                        )
                    }
                }
                item(key = "saved") {
                    SavedMessagesRow(
                        onClick = onSavedMessagesClick,
                        modifier = Modifier.printEntrance(2, "saved" in shown) { shown += "saved" }
                    )
                }
                item(key = "section") {
                    Text(
                        text = "Your workspaces",
                        style = PrintType.H2,
                        color = PrintInk,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                val state = workspaces
                when {
                    state == null -> items(3, key = { "skeleton_$it" }) { PrintSkeletonCard() }
                    state.isEmpty() -> item(key = "empty") { DashboardEmptyState() }
                    else -> itemsIndexed(
                        items = state,
                        key = { _, ws -> "${ws.id}_${ws.workspaceName}" }
                    ) { index, workspace ->
                        val key = "ws_${workspace.id}"
                        WorkspaceItem(
                            workspace = workspace,
                            modifier = Modifier
                                .animateItem()
                                .printEntrance(3 + index, key in shown) { shown += key },
                            accentColor = workspaceAccents[workspaceAccentIndex(workspace.id)],
                            onItemClick = { onWorkspaceClick(workspace) },
                            onDeleteClick = { onDeleteWorkspaceClick(workspace) }
                        )
                    }
                }
            }

            DashboardDock(
                onJoinClick = { showJoinByCodeDialog = true },
                onCreateClick = onNavigateToWorkspace
            )
        }

        if (showJoinByCodeDialog) {
            JoinWorkspaceByCodeDialog(
                onDismiss = { showJoinByCodeDialog = false },
                onSubmit = { code ->
                    onJoinByCode?.invoke(code)
                }
            )
        }
    }
}

@Composable
private fun DashboardHeader(
    greeting: String,
    currentUserName: String,
    avatarUrl: String,
    unreadNotificationCount: Int,
    onSearchClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Column {
        androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 360.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                CollabSphereMark(size = if (compact) 26.dp else 30.dp)
                Text(
                    text = "CollabSphere",
                    style = PrintType.H3.copy(fontFamily = FrauncesFamily, fontSize = if (compact) 15.sp else 17.sp),
                    color = PrintInk,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrintIconButton(
                    icon = Icons.Outlined.Search,
                    contentDescription = "Search",
                    onClick = onSearchClick
                )
                val reduced = rememberPrintReducedMotion()
                val bellSwing = remember { Animatable(0f) }
                val badgeScale = remember { Animatable(1f) }
                var lastUnread by remember { mutableIntStateOf(unreadNotificationCount) }
                LaunchedEffect(unreadNotificationCount) {
                    if (!reduced && unreadNotificationCount > lastUnread) {
                        launch {
                            badgeScale.snapTo(0.4f)
                            badgeScale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f))
                        }
                        bellSwing.animateTo(0f, keyframes {
                            durationMillis = 650
                            16f at 80
                            -13f at 200
                            9f at 320
                            -5f at 440
                            0f at 650
                        })
                    }
                    lastUnread = unreadNotificationCount
                }
                Box(modifier = Modifier.size(48.dp)) {
                    PrintIconButton(
                        icon = Icons.Outlined.Notifications,
                        contentDescription = if (unreadNotificationCount > 0) "Notifications, $unreadNotificationCount unread" else "Notifications",
                        onClick = onNotificationsClick,
                        modifier = Modifier.graphicsLayer {
                            rotationZ = bellSwing.value
                            transformOrigin = TransformOrigin(0.5f, 0.1f)
                        }
                    )
                    if (unreadNotificationCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-6).dp)
                                .graphicsLayer {
                                    scaleX = badgeScale.value
                                    scaleY = badgeScale.value
                                }
                                .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                                .printSurface(shape = CircleShape, fill = PrintCoral, shadow = null)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (unreadNotificationCount > 9) "9+" else unreadNotificationCount.toString(),
                                style = PrintType.Tiny.copy(letterSpacing = 0.sp),
                                color = PrintInk
                            )
                        }
                    }
                }
                ProfileAvatarButton(
                    currentUserName = currentUserName,
                    avatarUrl = avatarUrl,
                    onClick = onProfileClick
                )
            }
        }
        }

        Spacer(Modifier.height(24.dp))

        MisregisteredTitle(text = greeting)

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Here's what's happening across your teams.",
            style = PrintType.Body,
            color = PrintInkSoft
        )
    }
}

@Composable
private fun ProfileAvatarButton(
    currentUserName: String,
    avatarUrl: String,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPrintPress(interaction)
    val context = LocalContext.current
    val initial = currentUserName.trim().take(1).uppercase()
    Box(
        modifier = Modifier
            .size(48.dp)
            .printSurface(shape = CircleShape, fill = PrintSky, shadowOffset = 3.dp, pressProgress = press)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = "Profile" },
        contentAlignment = Alignment.Center
    ) {
        val fallback: @Composable () -> Unit = {
            if (initial.isNotEmpty()) {
                Text(
                    text = initial,
                    style = PrintType.H3.copy(fontFamily = FrauncesFamily, fontSize = 20.sp),
                    color = PrintInk
                )
            } else {
                Icon(Icons.Outlined.AccountCircle, contentDescription = null, tint = PrintInk, modifier = Modifier.size(26.dp))
            }
        }
        if (avatarUrl.isNotEmpty()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(com.collabsphere.app.view.UserUI.resolveAvatarUrl(avatarUrl))
                    .crossfade(true)
                    .size(with(LocalDensity.current) { 48.dp.roundToPx() })
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { fallback() } },
                error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { fallback() } }
            )
        } else {
            fallback()
        }
    }
}

@Composable
private fun DashboardSummaryBanner(count: Int, modifier: Modifier = Modifier) {
    val phase = rememberBlobPhase()
    val reduced = rememberPrintReducedMotion()
    val label = if (count == 1) "workspace" else "workspaces"
    val shownCount = remember { Animatable(0f) }
    LaunchedEffect(count) {
        if (reduced) shownCount.snapTo(count.toFloat())
        else shownCount.animateTo(count.toFloat(), tween(800, easing = FastOutSlowInEasing))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(136.dp)
            .clearAndSetSemantics { contentDescription = "$count active $label" }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val path = organicPath(size, seed = 11, phase = phase(), bow = 0.05f)
                    val shadow = PrintTokens.ShadowOffset.toPx()
                    translate(shadow, shadow) { drawPath(path, PrintInk) }
                    drawPath(path, PrintLeaf)
                    drawCircle(
                        color = PrintCoral,
                        radius = size.height * 0.40f,
                        center = Offset(size.width - size.height * 0.58f, size.height * 0.50f),
                        blendMode = BlendMode.Multiply
                    )
                    drawPath(path, PrintInk, style = Stroke(PrintTokens.Outline.toPx()))
                }
        )
        HalftonePatch(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 18.dp)
                .size(64.dp),
            color = PrintInk.copy(alpha = 0.55f)
        )
        PrintStar(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 30.dp, bottom = 22.dp)
                .size(20.dp),
            color = PrintPaper,
            twinkle = true
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = shownCount.value.roundToInt().toString(), style = PrintType.Hero, color = PrintInk)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(text = "ACTIVE", style = PrintType.Tiny, color = PrintInk)
                Text(text = label, style = PrintType.H3, color = PrintInk)
            }
        }
    }
}

@Composable
fun WorkspaceItem(
    workspace    : WorkspaceEntity,
    modifier     : Modifier = Modifier,
    accentColor  : Color = PrintLeaf,
    onItemClick  : () -> Unit,
    onDeleteClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPrintPress(interaction)
    val tagShape = remember(workspace.id) { BlobShape(seed = workspace.id, wobble = 0.14f) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .printSurface(shape = RoundedCornerShape(PrintTokens.RadiusCard), pressProgress = press)
            .clickable(interactionSource = interaction, indication = null, onClick = onItemClick)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .printSurface(shape = tagShape, fill = accentColor, shadow = null),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = workspace.workspaceName.firstOrNull()?.uppercase() ?: "W",
                style = PrintType.H2.copy(fontSize = 22.sp, lineHeight = 24.sp),
                color = PrintInk
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = workspace.workspaceName,
                style = PrintType.H3,
                color = PrintInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = PrintInkSoft,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = workspace.workspaceOwner,
                    style = PrintType.Caption,
                    color = PrintInkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        PrintIconButton(
            icon = Icons.Outlined.Delete,
            contentDescription = "Delete ${workspace.workspaceName}",
            onClick = onDeleteClick,
            fill = PrintPaper,
            tint = PrintRedDark,
            iconSize = 20.dp,
            shadow = false
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = PrintInk,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun PrintSkeletonCard() {
    val reduced = rememberPrintReducedMotion()
    val pulse = if (reduced) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
            label = "skeletonPulse"
        ).value
    }
    val bar = PrintInk.copy(alpha = 0.12f * pulse)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .printSurface(shape = RoundedCornerShape(PrintTokens.RadiusCard), shadow = PrintInk.copy(alpha = 0.2f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(bar))
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.55f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(bar))
            Box(Modifier.fillMaxWidth(0.35f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(bar))
        }
    }
}

@Composable
fun EmptyState(onNavigateToWorkspace: () -> Unit) {
    DashboardEmptyState()
}

@Composable
private fun DashboardEmptyState() {
    val blob = remember { BlobShape(seed = 5, wobble = 0.16f) }
    val phase = rememberBlobPhase(durationMillis = 4000)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(150.dp)) {
            HalftonePatch(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(64.dp),
                color = PrintCoral
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(120.dp)
                    .graphicsLayer { translationY = sin(phase()) * 5.dp.toPx() }
                    .printSurface(shape = blob, fill = PrintSky),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Folder, contentDescription = null, tint = PrintInk, modifier = Modifier.size(48.dp))
            }
            PrintStar(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp),
                color = PrintInk,
                twinkle = true
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(text = "No workspaces yet", style = PrintType.H2, color = PrintInk, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Start one below, or join your team with a code.",
            style = PrintType.Body,
            color = PrintInkSoft,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DashboardDock(onJoinClick: () -> Unit, onCreateClick: () -> Unit) {
    val reduced = rememberPrintReducedMotion()
    var played by rememberSaveable { mutableStateOf(false) }
    val rise = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            delay(250)
            if (reduced) rise.animateTo(1f, tween(150)) else rise.animateTo(1f, tween(450, easing = PrintTokens.SpringEasing))
            played = true
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = rise.value
                if (reduced) alpha = p.coerceIn(0f, 1f) else translationY = (1f - p) * size.height
            }
            .drawBehind {
                drawRect(PrintPaper)
                drawLine(
                    color = PrintInk,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = PrintTokens.Outline.toPx()
                )
            }
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PrintButton(
            text = "Join with code",
            onClick = onJoinClick,
            primary = false,
            modifier = Modifier.weight(0.9f)
        )
        PrintButton(
            text = "New workspace",
            onClick = onCreateClick,
            icon = Icons.Outlined.Add,
            modifier = Modifier.weight(1.1f)
        )
    }
}

@Composable
private fun JoinWorkspaceByCodeDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var codeInput by remember { mutableStateOf("") }
    val canSubmit = codeInput.length == 6
    val submit = {
        if (canSubmit) {
            onSubmit(codeInput)
            onDismiss()
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        com.collabsphere.app.view.components.WorkspaceDialogCard(
            icon = Icons.Outlined.GroupAdd,
            accent = IndigoStart,
            title = "Join workspace",
            subtitle = "Enter the 6-character code from your invite.",
            onClose = onDismiss
        ) {
            var isInviteFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .skeuoFocusRing(isInviteFocused, cornerRadius = 29.dp, ringColor = IndigoStart)
                    .skeuoInset(cornerRadius = 29.dp, depth = 3.dp, symmetric = true, shadowStrength = com.collabsphere.app.view.components.FieldInsetShadow)
                    .clip(RoundedCornerShape(29.dp)),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = codeInput,
                    onValueChange = { input ->
                        codeInput = input.uppercase().filter { it.isLetterOrDigit() }.take(6)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isInviteFocused = it.isFocused },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Go
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { submit() }),
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        letterSpacing = 6.sp
                    ),
                    cursorBrush = SolidColor(PrintInk),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.Center) {
                            if (codeInput.isEmpty()) {
                                Text(
                                    text = "ABC123",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = Muted.copy(alpha = 0.6f),
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        letterSpacing = 6.sp
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "${codeInput.length}/6",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (canSubmit) PrintInk else Muted
            )

            Spacer(Modifier.height(16.dp))

            com.collabsphere.app.view.components.WorkspaceDialogButton(
                text = "Join workspace",
                colors = listOf(IndigoStart, IndigoStart.copy(red = IndigoStart.red * 0.8f, green = IndigoStart.green * 0.8f, blue = IndigoStart.blue * 0.85f)),
                enabled = canSubmit,
                onClick = submit
            )
        }
    }
}

@Composable
private fun SavedMessagesRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val store = org.koin.compose.koinInject<com.collabsphere.app.model.message.SavedMessagesStore>()
    val items by store.items.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { store.refresh() }
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPrintPress(interaction)
    val tagShape = remember { BlobShape(seed = 23, wobble = 0.14f) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .printSurface(shape = RoundedCornerShape(PrintTokens.RadiusCard), pressProgress = press)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .printSurface(shape = tagShape, fill = PrintSky, shadow = null),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, tint = PrintInk, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = "Saved messages",
            style = PrintType.H3,
            color = PrintInk,
            modifier = Modifier.weight(1f)
        )
        if (items.isNotEmpty()) {
            PrintChip(text = items.size.toString())
            Spacer(Modifier.width(8.dp))
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = PrintInk, modifier = Modifier.size(24.dp))
    }
}
