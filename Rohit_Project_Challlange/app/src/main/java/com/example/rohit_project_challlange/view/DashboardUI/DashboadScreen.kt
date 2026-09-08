package com.example.rohit_project_challlange.view

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rohit_project_challlange.model.workspace.WorkspaceEntity
import com.example.rohit_project_challlange.ui.theme.*
import com.example.rohit_project_challlange.viewmodel.DashboardViewModel

// Color palette for workspace initials (cycles through accent colors)
private val workspaceAccents = listOf(CoralStart, IndigoStart, MintGreen, AmberWarn, Color(0xFF9B59B6))

@Composable
fun DashboardScreen(
    viewModel             : DashboardViewModel,
    onNavigateToWorkspace : () -> Unit,
    onWorkspaceClick      : (WorkspaceEntity) -> Unit,
    onDeleteWorkspaceClick: (WorkspaceEntity) -> Unit,
    onProfileClick        : () -> Unit = {}
) {
    // ── All original state preserved ──
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Top Bar ──
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text  = "CollabSphere",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Ink
                    )
                    Text(
                        text  = "Your workspaces",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted
                    )
                }

                // Profile button — raised circle with spring bounce
                val profileInteractionSource = remember { MutableInteractionSource() }
                val isProfilePressed by profileInteractionSource.collectIsPressedAsState()
                val profileScale by animateFloatAsState(
                    targetValue = if (isProfilePressed) 0.92f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "profileScale"
                )

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .graphicsLayer { scaleX = profileScale; scaleY = profileScale }
                        .drawBehind {
                            val r = size.minDimension / 2f
                            // 1. Ambient diffuse shadow
                            drawCircle(
                                color = Color(0xFF2C201A).copy(alpha = if (isProfilePressed) 0.03f else 0.06f),
                                radius = r,
                                center = Offset(center.x, center.y + if (isProfilePressed) 2.dp.toPx() else 5.dp.toPx())
                            )
                            // 2. Tight contact shadow (2.5x alpha)
                            drawCircle(
                                color = Color(0xFF2C201A).copy(alpha = if (isProfilePressed) 0.10f else 0.16f),
                                radius = r,
                                center = Offset(center.x, center.y + if (isProfilePressed) 1.dp.toPx() else 1.8.dp.toPx())
                            )
                            // 3. Specular top halo
                            drawCircle(
                                color = Color.White.copy(alpha = 0.90f),
                                radius = r,
                                center = Offset(center.x, center.y - 1.dp.toPx())
                            )
                            // 4. Elevated button body
                            drawCircle(color = Surface)
                            // 5. 1px hairline border
                            drawCircle(
                                color = Color(0xFF2C2A28).copy(alpha = 0.06f),
                                radius = r - 0.5.dp.toPx(),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = profileInteractionSource,
                            indication        = null,
                            onClick           = onProfileClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Outlined.AccountCircle,
                        contentDescription = "Profile",
                        tint               = CoralStart,
                        modifier           = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Summary banner ──
            DashboardSummaryBanner(count = workspaces.size)

            Spacer(Modifier.height(20.dp))

            // ── Section label ──
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text  = "Active workspaces",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink
                )
                if (workspaces.isNotEmpty()) {
                    Text(
                        text  = "${workspaces.size} total",
                        style = MaterialTheme.typography.labelMedium,
                        color = Muted
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── List or empty state ──
            if (workspaces.isEmpty()) {
                DashboardEmptyState()
            } else {
                LazyColumn(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding      = PaddingValues(bottom = 100.dp)
                ) {
                    items(
                        items = workspaces,
                        key   = { ws -> "${ws.id}_${ws.workspaceName}" }
                    ) { workspace ->
                        val accentColor = workspaceAccents[workspace.id % workspaceAccents.size]
                        WorkspaceItem(
                            workspace     = workspace,
                            modifier      = Modifier.animateItem(),
                            accentColor   = accentColor,
                            onItemClick   = { onWorkspaceClick(workspace) },
                            onDeleteClick = { onDeleteWorkspaceClick(workspace) }
                        )
                    }
                }
            }
        }

        // ── Skeuomorphic FAB ──
        SkeuoFab(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 32.dp),
            onClick  = onNavigateToWorkspace
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// SUMMARY BANNER
// ─────────────────────────────────────────────────────────────────

@Composable
private fun DashboardSummaryBanner(count: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .drawBehind {
                val cr = CornerRadius(20.dp.toPx())
                // 1. Ambient soft diffuse shadow
                drawRoundRect(
                    color        = CoralStart.copy(alpha = 0.14f),
                    topLeft      = Offset(0f, 7.dp.toPx()),
                    size         = Size(size.width, size.height),
                    cornerRadius = cr
                )
                // 2. Tight contact shadow (2.3x alpha)
                drawRoundRect(
                    color        = CoralStart.copy(alpha = 0.32f),
                    topLeft      = Offset(0f, 2.dp.toPx()),
                    size         = Size(size.width, size.height),
                    cornerRadius = cr
                )
                // 3. Coral gradient fill
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(CoralStart, CoralEnd),
                        start  = Offset(0f, 0f),
                        end    = Offset(size.width, size.height)
                    ),
                    cornerRadius = cr
                )
                // 4. Subtle 1px perimeter hairline border
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.20f),
                    cornerRadius = cr,
                    style = Stroke(width = 1.dp.toPx())
                )
                // 5. Specular hairline highlight along top rounded contour
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 22.dp.toPx()
                    ),
                    topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                    size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                    cornerRadius = cr,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
    ) {
        Row(
            modifier          = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text  = "Your workspaces",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f)
                )
                Text(
                    text  = "$count active",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
            }

            // Count badge circle with debossed socket appearance
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .drawBehind {
                        val r = size.minDimension / 2f
                        // Inset socket dark inner shadow top-left
                        drawCircle(
                            color  = Color(0xFF5A1D0B).copy(alpha = 0.25f),
                            radius = r,
                            center = Offset(center.x + 1.dp.toPx(), center.y + 1.5.dp.toPx())
                        )
                        // Inset translucent fill
                        drawCircle(
                            color  = Color.White.copy(alpha = 0.18f),
                            radius = r
                        )
                        // Specular bottom-right reflection
                        drawCircle(
                            color  = Color.White.copy(alpha = 0.40f),
                            radius = r - 0.5.dp.toPx(),
                            style  = Stroke(width = 1.dp.toPx())
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = count.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// WORKSPACE ITEM CARD
// ─────────────────────────────────────────────────────────────────

@Composable
fun WorkspaceItem(
    workspace    : WorkspaceEntity,
    modifier     : Modifier = Modifier,
    accentColor  : Color = CoralStart,
    onItemClick  : () -> Unit,
    onDeleteClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMediumLow
        ),
        label         = "wsScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .skeuoFloatingCard(cornerRadius = 18.dp, isPressed = isPressed)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onItemClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Initial in debossed squircle well
                val initial = workspace.workspaceName.firstOrNull()?.uppercase() ?: "W"
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

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workspace.workspaceName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.5.sp
                        ),
                        color = Color(0xFF1F1A17),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = Color(0xFF8C7E75),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = workspace.workspaceOwner,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                            color = Color(0xFF6E635C),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeuoActionIconButton(
                    onClick = onDeleteClick,
                    icon = Icons.Outlined.Delete,
                    contentDescription = "Delete workspace",
                    tint = DestructiveStart,
                    size = 32.dp,
                    iconSize = 16.dp
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFFA89E97),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// EMPTY STATE
// ─────────────────────────────────────────────────────────────────

@Composable
fun EmptyState() {
    DashboardEmptyState()
}

@Composable
private fun DashboardEmptyState() {
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(32.dp)
        ) {
            // Empty state icon circle with contact shadow & hairline
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .drawBehind {
                        val r = size.minDimension / 2f
                        // Ambient shadow
                        drawCircle(
                            color  = Color(0xFF2C201A).copy(alpha = 0.05f),
                            radius = r,
                            center = Offset(center.x, center.y + 6.dp.toPx())
                        )
                        // Contact shadow
                        drawCircle(
                            color  = Color(0xFF2C201A).copy(alpha = 0.14f),
                            radius = r,
                            center = Offset(center.x, center.y + 2.dp.toPx())
                        )
                        // Specular top halo
                        drawCircle(
                            color  = Color.White.copy(alpha = 0.90f),
                            radius = r,
                            center = Offset(center.x, center.y - 1.5.dp.toPx())
                        )
                        // Surface fill
                        drawCircle(color = Surface)
                        // 1px hairline border
                        drawCircle(
                            color  = Color(0xFF2C2A28).copy(alpha = 0.06f),
                            radius = r - 0.5.dp.toPx(),
                            style  = Stroke(width = 1.dp.toPx())
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint               = Muted.copy(alpha = 0.5f),
                    modifier           = Modifier.size(38.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text  = "No workspaces yet",
                style = MaterialTheme.typography.headlineSmall,
                color = Ink
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text      = "Tap the + button below to create your first workspace and start collaborating.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = Muted,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// SKEUOMORPHIC FAB
// ─────────────────────────────────────────────────────────────────

@Composable
private fun SkeuoFab(modifier: Modifier, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.93f else 1f,
        animationSpec = tween(80),
        label         = "fabScale"
    )

    Box(
        modifier = modifier
            .height(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                val r = CornerRadius(16.dp.toPx())
                // 1. Ambient diffuse shadow
                drawRoundRect(
                    color        = CoralStart.copy(alpha = if (pressed) 0.08f else 0.16f),
                    topLeft      = Offset(0f, if (pressed) 3.dp.toPx() else 8.dp.toPx()),
                    size         = Size(size.width, size.height),
                    cornerRadius = r
                )
                // 2. Tight contact shadow (2.3x alpha)
                drawRoundRect(
                    color        = CoralStart.copy(alpha = if (pressed) 0.18f else 0.38f),
                    topLeft      = Offset(0f, if (pressed) 1.5.dp.toPx() else 2.5.dp.toPx()),
                    size         = Size(size.width, size.height),
                    cornerRadius = r
                )
                // 3. Vibrant coral gradient fill
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(CoralStart, CoralEnd),
                        start  = Offset(0f, 0f),
                        end    = Offset(size.width, size.height)
                    ),
                    cornerRadius = r
                )
                // 4. Subtle 1px perimeter hairline border
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.20f),
                    cornerRadius = r,
                    style = Stroke(width = 1.dp.toPx())
                )
                // 5. Specular top rim highlight hugging rounded contour
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.50f),
                            Color.White.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 16.dp.toPx()
                    ),
                    topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                    size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                    cornerRadius = r,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null
            ) {
                pressed = true
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(
                text  = "New workspace",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
        }
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(120)
            pressed = false
        }
    }
}