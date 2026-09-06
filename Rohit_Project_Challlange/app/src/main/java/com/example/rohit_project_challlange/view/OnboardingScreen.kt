package com.example.rohit_project_challlange.view

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rohit_project_challlange.ui.theme.*
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────
// DATA
// ─────────────────────────────────────────────────────────────────

private data class OnboardPage(
    val headline: String,
    val subHeadline: String,
    val body: String,
    val accentColor: Color,
    val illustrationId: Int        // 0 = Brand,  1 = Chat,  2 = Organize
)

private val pages = listOf(
    OnboardPage(
        headline     = "Where teams\nthink together",
        subHeadline  = "CollabSphere",
        body         = "A single, calm workspace for your team's channels, messages, tasks, notes, and files.",
        accentColor  = CoralStart,
        illustrationId = 0
    ),
    OnboardPage(
        headline     = "Chat.\nDecide. Ship.",
        subHeadline  = "Real-time channels & DMs",
        body         = "Create channels, exchange messages, and jump into a direct conversation — all without leaving the app.",
        accentColor  = IndigoStart,
        illustrationId = 1
    ),
    OnboardPage(
        headline     = "One place\nfor everything",
        subHeadline  = "Tasks · Notes · Files",
        body         = "Assign work, capture ideas, and share files. Everything organized in your workspace, always in reach.",
        accentColor  = MintGreen,
        illustrationId = 2
    )
)

// ─────────────────────────────────────────────────────────────────
// ENTRY POINT
// ─────────────────────────────────────────────────────────────────

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit   // navigates to "login"
) {
    val pagerState  = rememberPagerState(pageCount = { pages.size })
    val scope       = rememberCoroutineScope()
    val isLastPage  = pagerState.currentPage == pages.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // ── Subtle warm radial gradient backdrop ──
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFEDEAE2),
                        Color(0x00EFEDE7)
                    ),
                    radius = size.minDimension * 0.9f
                ),
                radius = size.minDimension * 0.9f,
                center = Offset(size.width * 0.5f, size.height * 0.15f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // ── Skip button ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (!isLastPage) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.labelLarge,
                        color = Muted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onFinish() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // ── Pager ──
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                OnboardPageContent(
                    page = pages[pageIndex],
                    isActive = pagerState.currentPage == pageIndex
                )
            }

            // ── Bottom controls ──
            BottomControls(
                pageCount   = pages.size,
                currentPage = pagerState.currentPage,
                isLastPage  = isLastPage,
                accentColor = pages[pagerState.currentPage].accentColor,
                onNext = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
                onFinish = onFinish
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// SINGLE PAGE
// ─────────────────────────────────────────────────────────────────

@Composable
private fun OnboardPageContent(page: OnboardPage, isActive: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(400),
        label = "pageAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .alpha(alpha),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        // ── Tactile Illustration Card ──
        SkeuoIllustrationCard(
            illustrationId = page.illustrationId,
            accentColor    = page.accentColor,
            modifier       = Modifier
                .fillMaxWidth()
                .height(280.dp)
        )

        Spacer(Modifier.height(36.dp))

        // ── Sub-label ──
        Text(
            text  = page.subHeadline,
            style = MaterialTheme.typography.labelLarge.copy(
                color         = page.accentColor,
                letterSpacing = 1.5.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(10.dp))

        // ── Headline (Fraunces) ──
        Text(
            text      = page.headline,
            style     = MaterialTheme.typography.displaySmall,
            color     = Ink,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        // ── Body (Manrope) ──
        Text(
            text      = page.body,
            style     = MaterialTheme.typography.bodyMedium,
            color     = Muted,
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(horizontal = 8.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// ILLUSTRATION CARD
// ─────────────────────────────────────────────────────────────────

@Composable
private fun SkeuoIllustrationCard(
    illustrationId: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .drawBehind {
                // ── Card background gradient ──
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(SurfaceRaised, Surface),
                        start  = Offset(0f, 0f),
                        end    = Offset(size.width, size.height)
                    ),
                    cornerRadius = CornerRadius(28.dp.toPx())
                )
                // ── Dark ambient shadow (bottom-right) ──
                drawRoundRect(
                    color        = ShadowDark.copy(alpha = 0.35f),
                    topLeft      = Offset(6.dp.toPx(), 8.dp.toPx()),
                    size         = Size(size.width, size.height),
                    cornerRadius = CornerRadius(28.dp.toPx())
                )
                // ── Light specular (top-left) ──
                drawRoundRect(
                    color        = ShadowLight.copy(alpha = 0.9f),
                    topLeft      = Offset(-4.dp.toPx(), -4.dp.toPx()),
                    size         = Size(size.width, size.height),
                    cornerRadius = CornerRadius(28.dp.toPx())
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Top hairline bevel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.7f),
                            Color.White.copy(alpha = 0.7f),
                            Color.Transparent
                        )
                    )
                )
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            when (illustrationId) {
                0 -> drawBrandIllustration(accentColor)
                1 -> drawChatIllustration(accentColor)
                2 -> drawOrganizeIllustration(accentColor)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// ILLUSTRATION 1 — BRAND (Logo mark: orbiting circles)
// ─────────────────────────────────────────────────────────────────

private fun DrawScope.drawBrandIllustration(accent: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f

    // Outer ring
    drawCircle(
        color  = accent.copy(alpha = 0.08f),
        radius = 105.dp.toPx(),
        center = Offset(cx, cy)
    )
    drawCircle(
        color  = accent.copy(alpha = 0.05f),
        radius = 130.dp.toPx(),
        center = Offset(cx, cy)
    )

    // Center "C" sphere
    drawCircle(
        brush  = Brush.radialGradient(
            colors = listOf(accent, accent.copy(alpha = 0.7f)),
            center = Offset(cx - 12.dp.toPx(), cy - 12.dp.toPx()),
            radius = 50.dp.toPx()
        ),
        radius = 44.dp.toPx(),
        center = Offset(cx, cy)
    )
    // Specular on sphere
    drawCircle(
        color  = Color.White.copy(alpha = 0.35f),
        radius = 14.dp.toPx(),
        center = Offset(cx - 14.dp.toPx(), cy - 16.dp.toPx())
    )

    // Orbiting nodes
    val orbitR = 90.dp.toPx()
    val nodeAngles = listOf(330f, 90f, 210f)
    val nodeColors = listOf(
        Color(0xFF4C55C4),   // Indigo
        Color(0xFF4E9E78),   // Mint
        Color(0xFFE0A030)    // Amber
    )
    nodeAngles.forEachIndexed { i, angle ->
        val rad = Math.toRadians(angle.toDouble())
        val nx  = cx + (orbitR * Math.cos(rad)).toFloat()
        val ny  = cy + (orbitR * Math.sin(rad)).toFloat()

        // Node shadow
        drawCircle(
            color  = Color.Black.copy(alpha = 0.15f),
            radius = 18.dp.toPx(),
            center = Offset(nx + 2.dp.toPx(), ny + 3.dp.toPx())
        )
        // Node fill
        drawCircle(
            brush  = Brush.radialGradient(
                colors = listOf(nodeColors[i].copy(alpha = 0.9f), nodeColors[i].copy(alpha = 0.6f)),
                center = Offset(nx - 4.dp.toPx(), ny - 4.dp.toPx()),
                radius = 18.dp.toPx()
            ),
            radius = 16.dp.toPx(),
            center = Offset(nx, ny)
        )
        // Specular
        drawCircle(
            color  = Color.White.copy(alpha = 0.4f),
            radius = 5.dp.toPx(),
            center = Offset(nx - 5.dp.toPx(), ny - 6.dp.toPx())
        )

        // Connector line
        drawLine(
            color       = accent.copy(alpha = 0.18f),
            start       = Offset(cx, cy),
            end         = Offset(nx, ny),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// ILLUSTRATION 2 — CHAT (Channel sidebar + message bubbles)
// ─────────────────────────────────────────────────────────────────

private fun DrawScope.drawChatIllustration(accent: Color) {
    val pad   = 24.dp.toPx()
    val cardW = size.width  - pad * 2
    val cardH = size.height - pad * 2

    // ── Phone frame ──
    drawRoundRect(
        color        = Surface.copy(alpha = 0.0f),
        topLeft      = Offset(pad, pad),
        size         = Size(cardW, cardH),
        cornerRadius = CornerRadius(12.dp.toPx())
    )

    // Sidebar
    val sideW = cardW * 0.28f
    drawRoundRect(
        color        = accent.copy(alpha = 0.12f),
        topLeft      = Offset(pad, pad),
        size         = Size(sideW, cardH),
        cornerRadius = CornerRadius(12.dp.toPx())
    )

    // Sidebar channel rows
    val sideRows = listOf("# general", "# design", "# dev", "# random")
    sideRows.forEachIndexed { i, _ ->
        val rowY  = pad + 20.dp.toPx() + i * 28.dp.toPx()
        val rowW  = sideW - 16.dp.toPx()
        val rowH  = 10.dp.toPx()
        val rowColor = if (i == 0) accent else Muted.copy(alpha = 0.3f)
        // Highlight first row (active)
        if (i == 0) {
            drawRoundRect(
                color        = accent.copy(alpha = 0.2f),
                topLeft      = Offset(pad + 6.dp.toPx(), rowY - 6.dp.toPx()),
                size         = Size(rowW, rowH + 12.dp.toPx()),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
        }
        drawRoundRect(
            color        = rowColor,
            topLeft      = Offset(pad + 12.dp.toPx(), rowY),
            size         = Size(rowW - 8.dp.toPx(), rowH * 0.7f),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
    }

    // Chat area
    val chatX = pad + sideW + 8.dp.toPx()
    val chatW = cardW - sideW - 8.dp.toPx()

    // Messages
    data class Bubble(val fromMe: Boolean, val widthFrac: Float, val row: Int)
    val bubbles = listOf(
        Bubble(false, 0.55f, 0),
        Bubble(true,  0.45f, 1),
        Bubble(false, 0.65f, 2),
        Bubble(false, 0.38f, 3),
        Bubble(true,  0.50f, 4),
    )
    bubbles.forEach { b ->
        val bY     = pad + 16.dp.toPx() + b.row * 36.dp.toPx()
        val bH     = 22.dp.toPx()
        val bW     = chatW * b.widthFrac
        val bX     = if (b.fromMe) chatX + chatW - bW - 4.dp.toPx() else chatX + 4.dp.toPx()
        val bColor = if (b.fromMe) accent else Ink.copy(alpha = 0.08f)

        // Shadow
        drawRoundRect(
            color        = Color.Black.copy(alpha = 0.08f),
            topLeft      = Offset(bX + 2.dp.toPx(), bY + 2.dp.toPx()),
            size         = Size(bW, bH),
            cornerRadius = CornerRadius(10.dp.toPx())
        )
        // Bubble
        drawRoundRect(
            color        = bColor,
            topLeft      = Offset(bX, bY),
            size         = Size(bW, bH),
            cornerRadius = CornerRadius(10.dp.toPx())
        )
        // Text lines inside bubble
        drawRoundRect(
            color        = Color.White.copy(alpha = if (b.fromMe) 0.5f else 0.4f),
            topLeft      = Offset(bX + 8.dp.toPx(), bY + 7.dp.toPx()),
            size         = Size(bW * 0.65f, 5.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx())
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// ILLUSTRATION 3 — ORGANIZE (Kanban cards + task rows)
// ─────────────────────────────────────────────────────────────────

private fun DrawScope.drawOrganizeIllustration(accent: Color) {
    val pad  = 20.dp.toPx()
    val colW = (size.width - pad * 2 - 16.dp.toPx()) / 3f

    data class Col(val label: Color, val cards: Int, val cardColor: Color)
    val cols = listOf(
        Col(Color(0xFFD64545), 2, Color(0x18D64545)),
        Col(Color(0xFFE0A030), 3, Color(0x18E0A030)),
        Col(Color(0xFF4E9E78), 2, Color(0x184E9E78))
    )

    cols.forEachIndexed { ci, col ->
        val colX = pad + ci * (colW + 8.dp.toPx())
        val colY = pad

        // Column header pill
        drawRoundRect(
            color        = col.label.copy(alpha = 0.85f),
            topLeft      = Offset(colX, colY),
            size         = Size(colW, 12.dp.toPx()),
            cornerRadius = CornerRadius(6.dp.toPx())
        )

        // Cards
        repeat(col.cards) { ri ->
            val cY  = colY + 20.dp.toPx() + ri * 68.dp.toPx()
            val cH  = 58.dp.toPx()

            // Card shadow
            drawRoundRect(
                color        = Color.Black.copy(alpha = 0.08f),
                topLeft      = Offset(colX + 2.dp.toPx(), cY + 3.dp.toPx()),
                size         = Size(colW, cH),
                cornerRadius = CornerRadius(10.dp.toPx())
            )
            // Card bg
            drawRoundRect(
                brush        = Brush.linearGradient(
                    colors = listOf(Color(0xFFF9F7F2), Color(0xFFF0EDE7)),
                    start  = Offset(colX, cY),
                    end    = Offset(colX, cY + cH)
                ),
                topLeft      = Offset(colX, cY),
                size         = Size(colW, cH),
                cornerRadius = CornerRadius(10.dp.toPx())
            )
            // Top rim
            drawRoundRect(
                color        = Color.White.copy(alpha = 0.8f),
                topLeft      = Offset(colX, cY),
                size         = Size(colW, 1.5.dp.toPx()),
                cornerRadius = CornerRadius(10.dp.toPx())
            )
            // Left accent stripe
            drawRoundRect(
                color        = col.label,
                topLeft      = Offset(colX + 6.dp.toPx(), cY + 8.dp.toPx()),
                size         = Size(3.dp.toPx(), cH - 16.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
            // Title line
            drawRoundRect(
                color        = Ink.copy(alpha = 0.5f),
                topLeft      = Offset(colX + 14.dp.toPx(), cY + 10.dp.toPx()),
                size         = Size(colW * 0.55f, 6.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
            // Sub line
            drawRoundRect(
                color        = Muted.copy(alpha = 0.35f),
                topLeft      = Offset(colX + 14.dp.toPx(), cY + 22.dp.toPx()),
                size         = Size(colW * 0.38f, 4.5.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
            // Avatar dot
            drawCircle(
                color  = col.label.copy(alpha = 0.7f),
                radius = 6.dp.toPx(),
                center = Offset(colX + 14.dp.toPx() + 6.dp.toPx(), cY + cH - 12.dp.toPx())
            )
        }
    }

    // Floating "+" FAB
    val fabCx = size.width - 36.dp.toPx()
    val fabCy = size.height - 40.dp.toPx()
    drawCircle(
        color  = Color.Black.copy(alpha = 0.12f),
        radius = 20.dp.toPx(),
        center = Offset(fabCx + 2.dp.toPx(), fabCy + 3.dp.toPx())
    )
    drawCircle(
        brush  = Brush.radialGradient(
            colors = listOf(accent, accent.copy(alpha = 0.75f)),
            center = Offset(fabCx - 4.dp.toPx(), fabCy - 4.dp.toPx()),
            radius = 20.dp.toPx()
        ),
        radius = 18.dp.toPx(),
        center = Offset(fabCx, fabCy)
    )
    drawCircle(
        color  = Color.White.copy(alpha = 0.3f),
        radius = 6.dp.toPx(),
        center = Offset(fabCx - 6.dp.toPx(), fabCy - 7.dp.toPx())
    )
    // Plus icon
    val ps = 7.dp.toPx()
    drawLine(Color.White, Offset(fabCx - ps, fabCy), Offset(fabCx + ps, fabCy), 2.dp.toPx(), StrokeCap.Round)
    drawLine(Color.White, Offset(fabCx, fabCy - ps), Offset(fabCx, fabCy + ps), 2.dp.toPx(), StrokeCap.Round)
}

// ─────────────────────────────────────────────────────────────────
// BOTTOM CONTROLS
// ─────────────────────────────────────────────────────────────────

@Composable
private fun BottomControls(
    pageCount  : Int,
    currentPage: Int,
    isLastPage : Boolean,
    accentColor: Color,
    onNext     : () -> Unit,
    onFinish   : () -> Unit
) {
    val animatedAccent by animateColorAsState(
        targetValue    = accentColor,
        animationSpec  = tween(400, easing = FastOutSlowInEasing),
        label          = "accentAnim"
    )

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Dot indicators ──
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            repeat(pageCount) { i ->
                val isActive = i == currentPage
                val dotWidth by animateDpAsState(
                    targetValue   = if (isActive) 28.dp else 8.dp,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    label         = "dotWidth$i"
                )
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(dotWidth)
                        .clip(CircleShape)
                        .background(
                            if (isActive) animatedAccent
                            else Muted.copy(alpha = 0.3f)
                        )
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── CTA Button ──
        TactileCtaButton(
            text        = if (isLastPage) "Get started" else "Continue",
            accentColor = animatedAccent,
            onClick     = if (isLastPage) onFinish else onNext
        )

        Spacer(Modifier.height(8.dp))
    }
}

// ─────────────────────────────────────────────────────────────────
// TACTILE CTA BUTTON
// ─────────────────────────────────────────────────────────────────

@Composable
private fun TactileCtaButton(
    text       : String,
    accentColor: Color,
    onClick    : () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.95f else 1f,
        animationSpec = tween(80),
        label         = "btnScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .drawBehind {
                // ── Button gradient fill ──
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(accentColor, accentColor.copy(alpha = 0.82f)),
                        start  = Offset(0f, 0f),
                        end    = Offset(size.width, size.height)
                    ),
                    cornerRadius = CornerRadius(18.dp.toPx())
                )
                // ── Dark shadow below ──
                drawRoundRect(
                    color        = accentColor.copy(alpha = if (pressed) 0.15f else 0.35f),
                    topLeft      = Offset(0f, if (pressed) 1.dp.toPx() else 5.dp.toPx()),
                    size         = Size(size.width, size.height),
                    cornerRadius = CornerRadius(18.dp.toPx())
                )
                // ── Light specular top-left ──
                drawRoundRect(
                    color        = Color.White.copy(alpha = 0.25f),
                    topLeft      = Offset(-2.dp.toPx(), -2.dp.toPx()),
                    size         = Size(size.width, size.height),
                    cornerRadius = CornerRadius(18.dp.toPx())
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null
            ) {
                pressed = true
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        // Top rim highlight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    )
                )
        )

        Text(
            text  = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
            color = Color.White
        )
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(120)
            pressed = false
        }
    }
}
