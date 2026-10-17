package com.collabsphere.app.view

import androidx.compose.foundation.border
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.collabsphere.app.ui.theme.*
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────
// DESIGN TOKENS  (from Stitch project 6815327193169070909)
// ─────────────────────────────────────────────────────────────────

private val OnboardCanvas   = PrintPaper
private val OnboardCard     = PrintWhite
private val OnboardCardBg   = PrintPaper
private val InkPrimary      = PrintInk
private val InkMuted        = PrintInk.copy(alpha = 0.72f)
private val BorderLight     = PrintInk

private val CoralAccent     = PrintCoral
private val CoralDark       = PrintRedDark
private val IndigoAccent    = PrintSky
private val MintAccent      = PrintLeaf
private val MintDark        = PrintLeaf

private val BubbleLeft      = PrintPaper
private val BubbleLeftDM    = PrintPaper
private val BubbleRight     = Color(0xFFFFE1D6)
private val StickyNote      = Color(0xFFF6D365)

private data class OnboardPage(
    val accentColor: Color,
    val subLabel   : String,
    val headline   : String,
    val body       : String
)

private val pages = listOf(
    OnboardPage(CoralAccent, "COLLABSPHERE",
        "Where teams\nthink together",
        "One calm workspace for channels, messages, tasks, notes, and files."),
    OnboardPage(IndigoAccent, "REAL-TIME CHANNELS & DMS",
        "Chat at the\nspeed of thought.",
        "Create channels, exchange DMs, and react — all in one place."),
    OnboardPage(MintAccent, "TASKS · NOTES · FILES",
        "Everything\nin reach",
        "Assign tasks, capture notes, and share files — all organized in your workspace.")
)

// ─────────────────────────────────────────────────────────────────
// ENTRY POINT
// ─────────────────────────────────────────────────────────────────

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope      = rememberCoroutineScope()
    val current    = pagerState.currentPage
    val isLast     = current == pages.lastIndex

    val animatedAccent by animateColorAsState(
        targetValue   = pages[current].accentColor,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label         = "accent"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardCanvas)
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── TOP 70% — SIMULATOR STAGE ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.70f)
            ) {
                HorizontalPager(
                    state    = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    SimulatorStage(
                        page      = pages[pageIndex],
                        pageIndex = pageIndex,
                        isActive  = pagerState.currentPage == pageIndex,
                        onSkip    = onFinish
                    )
                }
            }

            // ── BOTTOM 30% — RAISED PAPER CARD ──
            BottomCard(
                page        = pages[current],
                pageCount   = pages.size,
                currentPage = current,
                isLastPage  = isLast,
                accentColor = animatedAccent,
                onNext = {
                    scope.launch {
                        pagerState.animateScrollToPage(current + 1)
                    }
                },
                onFinish = onFinish
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// SIMULATOR STAGE  (top 70%)
// ─────────────────────────────────────────────────────────────────

@Composable
private fun SimulatorStage(
    page      : OnboardPage,
    pageIndex : Int,
    isActive  : Boolean,
    onSkip    : () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardCanvas)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Ambient glow blob
        val phase = rememberBlobPhase(9000)
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.TopEnd)
                .offset(x = 60.dp, y = 10.dp)
                .graphicsLayer { rotationZ = phase() * 30f }
                .printSurface(shape = BlobShape(seed = pageIndex + 2, wobble = 0.14f), fill = page.accentColor, shadow = null)
        )
        HalftonePatch(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-24).dp, y = 8.dp)
                .size(120.dp)
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar: stage label + Skip
            Row(
                modifier            = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment   = Alignment.CenterVertically
            ) {
                // Stage indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .printSurface(shape = BlobShape(seed = pageIndex + 5, wobble = 0.18f), fill = page.accentColor, shadow = null, outline = 1.5.dp)
                    )
                    Text(
                        text  = "STAGE 0${pageIndex + 1}",
                        style = TextStyle(
                            fontFamily   = ManropeFamily,
                            fontSize     = 12.sp,
                            fontWeight   = FontWeight.Bold,
                            letterSpacing = 0.08.sp,
                            color        = PrintInk
                        )
                    )
                }
                // Skip
                Text(
                    text     = if (pageIndex == pages.lastIndex) "" else "Skip",
                    style    = TextStyle(
                        fontFamily = ManropeFamily,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color      = PrintInk,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                    ),
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null
                        ) { if (pageIndex != pages.lastIndex) onSkip() }
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Floating simulator card — all 4 corners visible
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(end = PrintTokens.ShadowOffset, bottom = PrintTokens.ShadowOffset)
                    .printSurface(shape = RoundedCornerShape(22.dp), fill = OnboardCard)
            ) {
                when (pageIndex) {
                    0 -> WorkspaceSimulator(page = page, isActive = isActive)
                    1 -> DmSimulator(page = page, isActive = isActive)
                    2 -> KanbanSimulator(page = page, isActive = isActive)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// PAGE 1 — WORKSPACE SIMULATOR
// Sidebar slides from left, chat panel from right, messages animate in
// ─────────────────────────────────────────────────────────────────

@Composable
private fun WorkspaceSimulator(page: OnboardPage, isActive: Boolean) {
    val inf = rememberInfiniteTransition(label = "ws")

    // 5-second loop for everything
    val loopRaw by inf.animateFloat(
        initialValue   = 0f,
        targetValue    = 1f,
        animationSpec  = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label          = "loop"
    )

    // Sidebar: 0-12% enter, 90-97% exit
    val sidebarX by inf.animateFloat(
        initialValue   = -1f,
        targetValue    = -1f,
        animationSpec  = infiniteRepeatable(
            keyframes {
                durationMillis = 5000
                -1.05f at 0
                -1.05f at 200
                0f     at 600  with FastOutSlowInEasing
                0f     at 4500
                -0.1f  at 4850
            }
        ),
        label = "sidebarX"
    )
    val sidebarAlpha by inf.animateFloat(
        initialValue  = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 5000
            0f  at 0; 0f  at 200; 1f at 600; 1f at 4500; 0.15f at 4850
        }), label = "sidebarA"
    )

    // Chat panel: 0.8s enter from right
    val chatX by inf.animateFloat(
        initialValue  = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 5000
            1.05f at 0; 1.05f at 400; 0f at 900 with FastOutSlowInEasing
            0f at 4500; 0.1f at 4850
        }), label = "chatX"
    )
    val chatAlpha by inf.animateFloat(
        initialValue  = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 5000
            0f at 0; 0f at 400; 1f at 900; 1f at 4500; 0.15f at 4850
        }), label = "chatA"
    )

    // Typing indicator 1 (left): 1.0s - 1.6s
    val typL by inf.animateFloat(
        initialValue  = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 5000
            0f at 0; 0f at 950; 1f at 1050; 1f at 1550; 0f at 1600
        }), label = "typL"
    )

    // Message 1: 1.6s from left
    val msg1X by inf.animateFloat(
        initialValue  = -55f, targetValue = -55f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 5000
            -55f at 0; -55f at 1550; 0f at 1850 with FastOutSlowInEasing
            0f at 4500; 0f at 4850
        }), label = "msg1X"
    )
    val msg1A by inf.animateFloat(
        initialValue  = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 5000
            0f at 0; 0f at 1550; 1f at 1850; 1f at 4500; 0f at 4850
        }), label = "msg1A"
    )

    // Typing indicator 2 (right): 2.2s - 2.8s
    val typR by inf.animateFloat(
        initialValue  = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 5000
            0f at 0; 0f at 2150; 1f at 2250; 1f at 2750; 0f at 2800
        }), label = "typR"
    )

    // Message 2: 2.8s from right
    val msg2X by inf.animateFloat(
        initialValue  = 55f, targetValue = 55f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 5000
            55f at 0; 55f at 2750; 0f at 3050 with FastOutSlowInEasing
            0f at 4500; 0f at 4850
        }), label = "msg2X"
    )
    val msg2A by inf.animateFloat(
        initialValue  = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 5000
            0f at 0; 0f at 2750; 1f at 3050; 1f at 4500; 0f at 4850
        }), label = "msg2A"
    )

    // Reaction badge: 3.4s
    val reactScale by inf.animateFloat(
        initialValue  = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 5000
            0f at 0; 0f at 3350; 1.35f at 3450; 0.92f at 3500; 1f at 3550
            1f at 4500; 0f at 4850
        }), label = "react"
    )
    val reactA by inf.animateFloat(
        initialValue  = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 5000
            0f at 0; 0f at 3350; 1f at 3450; 1f at 4500; 0f at 4850
        }), label = "reactA"
    )

    // Dot wave for typing indicators
    val dotWave1 by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = 900; 0f at 0; -3.5f at 270 with FastOutSlowInEasing; 0f at 540
    }), label = "d1")
    val dotWave2 by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = 900; 0f at 180; -3.5f at 450; 0f at 720
    }), label = "d2")
    val dotWave3 by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = 900; 0f at 360; -3.5f at 630; 0f at 900
    }), label = "d3")

    Row(modifier = Modifier.fillMaxSize()) {
        // ── SIDEBAR (slides from left) ──
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(90.dp)
                .graphicsLayer {
                    translationX = sidebarX * 90.dp.toPx()
                    alpha        = sidebarAlpha
                }
                .background(OnboardCardBg)
        ) {
            Column(modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo mark — two overlapping circles
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.collabsphere.app.R.drawable.collabsphere),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("CS", style = TextStyle(
                            fontFamily = NewsreaderFamily,
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = InkPrimary
                        ))
                    }
                }
                // Section label
                Text("CHANNELS", style = TextStyle(
                    fontFamily = ManropeFamily, fontSize = 9.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 0.08.sp,
                    color = InkMuted.copy(alpha = 0.7f)
                ), modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))

                // Channel rows
                val channels = listOf("general", "design", "dev")
                channels.forEachIndexed { i, ch ->
                    val isActive2 = i == 0
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isActive2) CoralAccent.copy(alpha = 0.18f) else Color.Transparent)
                            .padding(vertical = 4.dp, horizontal = 6.dp)
                    ) {
                        if (isActive2) {
                            Box(modifier = Modifier
                                .width(2.5.dp).fillMaxHeight()
                                .background(CoralAccent, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = if (isActive2) 4.dp else 0.dp)) {
                            Text("#", style = TextStyle(
                                fontFamily = ManropeFamily, fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isActive2) CoralAccent else InkMuted.copy(alpha = 0.5f)
                            ))
                            Spacer(Modifier.width(3.dp))
                            Text(ch, style = TextStyle(
                                fontFamily = ManropeFamily, fontSize = 11.sp,
                                fontWeight = if (isActive2) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isActive2) InkPrimary else InkMuted.copy(alpha = 0.8f)
                            ))
                        }
                    }
                }
            }
        }

        // Divider line
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(BorderLight))

        // ── CHAT PANEL (slides from right) ──
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .graphicsLayer {
                    translationX = chatX * 200.dp.toPx()
                    alpha        = chatAlpha
                }
                .background(OnboardCard)
                .padding(8.dp)
        ) {
            // Chat header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#", style = TextStyle(
                        fontFamily = ManropeFamily, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, color = CoralAccent
                    ))
                    Spacer(Modifier.width(3.dp))
                    Text("general", style = TextStyle(
                        fontFamily = NewsreaderFamily, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, color = InkPrimary
                    ))
                }
                // Avatar stack K, D, S
                Row(horizontalArrangement = Arrangement.spacedBy((-5).dp)) {
                    listOf("K" to PrintInk, "D" to IndigoAccent, "S" to CoralAccent)
                        .forEach { (l, c) ->
                            Box(modifier = Modifier
                                .size(14.dp)
                                .background(c, CircleShape),
                                contentAlignment = Alignment.Center) {
                                Text(l, style = TextStyle(
                                    fontFamily = ManropeFamily,
                                    fontSize = 7.sp, fontWeight = FontWeight.Bold,
                                    color = PrintInk
                                ))
                            }
                        }
                }
            }
            Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(BorderLight))
            Spacer(Modifier.height(4.dp))

            // Date badge
            Box(modifier = Modifier.align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(50))
                .background(OnboardCardBg)
                .padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text("Today", style = TextStyle(
                    fontFamily = ManropeFamily, fontSize = 9.sp,
                    color = InkMuted
                ))
            }
            Spacer(Modifier.height(6.dp))

            // Typing left
            if (typL > 0f) {
                Row(
                    modifier = Modifier.alpha(typL).padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.size(18.dp)
                        .background(MintAccent.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Text("A", style = TextStyle(fontFamily = ManropeFamily,
                            fontSize = 8.sp, color = PrintInk))
                    }
                    TypingBubble(left = true, dot1Y = dotWave1, dot2Y = dotWave2, dot3Y = dotWave3)
                }
            }

            // Message 1 (left)
            Row(
                modifier = Modifier
                    .graphicsLayer { translationX = msg1X; alpha = msg1A }
                    .fillMaxWidth(0.85f)
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.size(18.dp)
                    .background(MintDark, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Text("A", style = TextStyle(fontFamily = ManropeFamily,
                        fontSize = 8.sp, fontWeight = FontWeight.SemiBold,
                        color = PrintInk))
                }
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom) {
                        Text("Aarav", style = TextStyle(fontFamily = ManropeFamily,
                            fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = InkPrimary))
                        Text("9:02", style = TextStyle(fontFamily = ManropeFamily,
                            fontSize = 9.sp, color = InkMuted))
                    }
                    Spacer(Modifier.height(2.dp))
                    Box {
                        Box(modifier = Modifier
                            .clip(RoundedCornerShape(topEnd = 12.dp, bottomStart = 4.dp,
                                bottomEnd = 12.dp, topStart = 12.dp))
                            .background(BubbleLeft)
                            .padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text("Good morning, everyone!", style = TextStyle(
                                fontFamily = ManropeFamily, fontSize = 11.sp, color = InkPrimary))
                        }
                        // Reaction badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 4.dp, y = 6.dp)
                                .graphicsLayer { scaleX = reactScale; scaleY = reactScale; this.alpha = reactA }
                                .clip(RoundedCornerShape(50))
                                .background(OnboardCard)
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("👍 2", style = TextStyle(fontFamily = ManropeFamily,
                                fontSize = 8.sp, fontWeight = FontWeight.SemiBold,
                                color = PrintRedDark))
                        }
                    }
                }
            }

            // Typing right
            if (typR > 0f) {
                Row(
                    modifier = Modifier
                        .alpha(typR)
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    TypingBubble(left = false, dot1Y = dotWave1, dot2Y = dotWave2, dot3Y = dotWave3,
                        color = BubbleRight, dotColor = CoralAccent)
                    Spacer(Modifier.width(6.dp))
                    Box(modifier = Modifier.size(18.dp)
                        .background(IndigoAccent.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Text("M", style = TextStyle(fontFamily = ManropeFamily,
                            fontSize = 8.sp, color = PrintInk))
                    }
                }
            }

            // Message 2 (right)
            Row(
                modifier = Modifier
                    .graphicsLayer { translationX = msg2X; alpha = msg2A }
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom) {
                        Text("9:03", style = TextStyle(fontFamily = ManropeFamily,
                            fontSize = 9.sp, color = InkMuted))
                        Text("Maya", style = TextStyle(fontFamily = ManropeFamily,
                            fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = InkPrimary))
                    }
                    Spacer(Modifier.height(2.dp))
                    Box(modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 12.dp, bottomEnd = 4.dp,
                            bottomStart = 12.dp, topEnd = 12.dp))
                        .background(BubbleRight)
                        .padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text("Hey! Ready for standup?", style = TextStyle(
                            fontFamily = ManropeFamily, fontSize = 11.sp, color = InkPrimary))
                    }
                }
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.size(18.dp).background(IndigoAccent, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Text("M", style = TextStyle(fontFamily = ManropeFamily,
                        fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = PrintInk))
                }
            }

            Spacer(Modifier.weight(1f))

            // Composer bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Message #general", modifier = Modifier.weight(1f),
                    style = TextStyle(fontFamily = ManropeFamily, fontSize = 10.sp, color = InkMuted))
                Box(modifier = Modifier.size(18.dp).background(CoralAccent, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Text("↑", style = TextStyle(fontSize = 10.sp, color = PrintInk,
                        fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// PAGE 2 — DM CHAT SIMULATOR
// ─────────────────────────────────────────────────────────────────

@Composable
private fun DmSimulator(page: OnboardPage, isActive: Boolean) {
    val inf = rememberInfiniteTransition(label = "dm")

    val priyaBubbleX by inf.animateFloat(-35f, -35f, infiniteRepeatable(keyframes {
        durationMillis = 5000
        -35f at 0; -35f at 450; 0f at 800 with FastOutSlowInEasing; 0f at 4500; 0f at 4800
    }), label = "priyaX")
    val priyaBubbleA by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = 5000
        0f at 0; 0f at 450; 1f at 800; 1f at 4500; 0f at 4800
    }), label = "priyaA")

    val typingA by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = 5000
        0f at 1250; 1f at 1500; 1f at 1900; 0f at 2200
    }), label = "typA")

    val userBubbleX by inf.animateFloat(50f, 50f, infiniteRepeatable(keyframes {
        durationMillis = 5000
        50f at 0; 50f at 1950; 0f at 2300 with FastOutSlowInEasing; 0f at 4500; 0f at 4800
    }), label = "userX")
    val userBubbleA by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = 5000
        0f at 0; 0f at 1950; 1f at 2300; 1f at 4500; 0f at 4800
    }), label = "userA")

    val attachA by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = 5000
        0f at 2550; 25f at 2550; 1f at 2900 with FastOutSlowInEasing; 1f at 4500; 0f at 4800
    }), label = "attachA")
    val attachY by inf.animateFloat(25f, 25f, infiniteRepeatable(keyframes {
        durationMillis = 5000
        25f at 0; 25f at 2550; 0f at 2900; 0f at 4500; 10f at 4800
    }), label = "attachY")

    val checkA by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = 5000
        0f at 3150; 1f at 3400; 1f at 4500; 0f at 4800
    }), label = "checkA")

    val heartScale by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = 5000
        0f at 3650; 1.35f at 3900; 0.92f at 3950; 1f at 4000; 1f at 4500; 0f at 4800
    }), label = "heartS")
    val heartA by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = 5000
        0f at 3650; 1f at 3900; 1f at 4500; 0f at 4800
    }), label = "heartA")

    val dotWave1 by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = 700; 0f at 0; -3.5f at 210; 0f at 420
    }), label = "d1")
    val dotWave2 by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = 700; 0f at 105; -3.5f at 315; 0f at 525
    }), label = "d2")
    val dotWave3 by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = 700; 0f at 210; -3.5f at 420; 0f at 630
    }), label = "d3")

    Column(modifier = Modifier.fillMaxSize()) {
        // DM Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(OnboardCard)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    // Back arrow hint
                    Box(modifier = Modifier.size(24.dp).background(Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center) {
                        Text("←", style = TextStyle(fontSize = 14.sp, color = InkMuted))
                    }
                }
                // Avatar with online pip
                Box {
                    Box(modifier = Modifier.size(24.dp)
                        .background(Color(0xFFFFE1D6), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Text("P", style = TextStyle(fontFamily = ManropeFamily,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrintRedDark))
                    }
                    Box(modifier = Modifier.size(8.dp).align(Alignment.BottomEnd)
                        .background(MintDark, CircleShape))
                }
                Text("Priya", style = TextStyle(fontFamily = NewsreaderFamily,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = InkPrimary))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Call, contentDescription = "Call", tint = InkMuted, modifier = androidx.compose.ui.Modifier.size(14.dp))
                Text("⋮", style = TextStyle(fontSize = 14.sp, color = InkMuted))
            }
        }
        Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(BorderLight))

        // Messages area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Timestamp
            Box(modifier = Modifier.align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(50))
                .background(OnboardCardBg)
                .padding(horizontal = 10.dp, vertical = 2.dp)) {
                Text("Today, 10:42 AM", style = TextStyle(fontFamily = ManropeFamily,
                    fontSize = 9.sp, color = InkMuted))
            }

            // Priya's bubble (left)
            Box(
                modifier = Modifier
                    .graphicsLayer { translationX = priyaBubbleX; alpha = priyaBubbleA }
                    .fillMaxWidth(0.85f)
            ) {
                Row(verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(20.dp)
                        .background(Color(0xFFFFE1D6), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Text("P", style = TextStyle(fontFamily = ManropeFamily,
                            fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                            color = PrintRedDark))
                    }
                    Box {
                        Box(modifier = Modifier
                            .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp,
                                bottomStart = 4.dp, topStart = 12.dp))
                            .background(BubbleLeftDM)
                            .padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text("Can you review the design?", style = TextStyle(
                                fontFamily = ManropeFamily, fontSize = 11.sp, color = InkPrimary))
                        }
                        // Heart reaction
                        Box(modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 6.dp)
                            .graphicsLayer { scaleX = heartScale; scaleY = heartScale; alpha = heartA }
                            .clip(RoundedCornerShape(50))
                            .background(Color.White)
                            .padding(horizontal = 5.dp, vertical = 2.dp)) {
                            Text("❤️ 1", style = TextStyle(fontFamily = ManropeFamily,
                                fontSize = 8.sp, fontWeight = FontWeight.Bold, color = PrintRedDark))
                        }
                    }
                }
            }

            // Typing indicator
            if (typingA > 0f) {
                Row(modifier = Modifier.alpha(typingA)
                    .clip(RoundedCornerShape(50))
                    .background(OnboardCardBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TypingDots(dot1Y = dotWave1, dot2Y = dotWave2, dot3Y = dotWave3,
                        color = PrintInk)
                    Text("Priya is typing", style = TextStyle(fontFamily = ManropeFamily,
                        fontSize = 9.sp, color = InkMuted))
                }
            }

            // User bubble (right) - indigo
            Column(
                modifier = Modifier
                    .graphicsLayer { translationX = userBubbleX; alpha = userBubbleA }
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Box(modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp,
                        bottomEnd = 4.dp, topEnd = 12.dp))
                    .background(IndigoAccent)
                    .padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("On it! Sending feedback now", style = TextStyle(
                        fontFamily = ManropeFamily, fontSize = 11.sp, color = PrintInk))
                }
            }

            // Attachment card
            Row(
                modifier = Modifier
                    .graphicsLayer { translationY = attachY; alpha = attachA }
                    .fillMaxWidth(0.88f)
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(PrintSky),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attachment", tint = IndigoAccent, modifier = androidx.compose.ui.Modifier.size(14.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("design_v3.fig", style = TextStyle(fontFamily = ManropeFamily,
                        fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = InkPrimary))
                    Text("2.4 MB · Figma File", style = TextStyle(fontFamily = ManropeFamily,
                        fontSize = 9.sp, color = InkMuted))
                }
                Box(modifier = Modifier.size(18.dp)
                    .background(PrintSky, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Text("↓", style = TextStyle(fontSize = 10.sp, color = PrintInk,
                        fontWeight = FontWeight.Bold))
                }
            }

            // Delivered checkmark
            Box(modifier = Modifier.align(Alignment.End).alpha(checkA)) {
                Text("✓✓ Delivered", style = TextStyle(fontFamily = ManropeFamily,
                    fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = PrintInk))
            }
        }

        // Composer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.EmojiEmotions, contentDescription = "Emoji", tint = InkMuted, modifier = androidx.compose.ui.Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Message Priya...", modifier = Modifier.weight(1f),
                style = TextStyle(fontFamily = ManropeFamily, fontSize = 10.sp, color = InkMuted))
            Icon(Icons.Default.Mic, contentDescription = "Voice", tint = InkMuted, modifier = androidx.compose.ui.Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Box(modifier = Modifier.size(18.dp).background(IndigoAccent, CircleShape),
                contentAlignment = Alignment.Center) {
                Text("→", style = TextStyle(fontSize = 9.sp, color = PrintInk,
                    fontWeight = FontWeight.Bold))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// PAGE 3 — KANBAN SIMULATOR
// ─────────────────────────────────────────────────────────────────

@Composable
private fun KanbanSimulator(page: OnboardPage, isActive: Boolean) {
    val inf = rememberInfiniteTransition(label = "kb")
    val loopDur = 4600

    val pillTodoA by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = loopDur
        0f at 0; 0f at 161; 1f at 299 with FastOutSlowInEasing; 1f at 3956; 0f at 4278
    }), label = "ptA")
    val pillTodoY by inf.animateFloat(-28f, -28f, infiniteRepeatable(keyframes {
        durationMillis = loopDur
        -28f at 0; -28f at 161; 4f at 299; 0f at 414; 0f at 3956; -10f at 4278
    }), label = "ptY")

    val pillProgA by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = loopDur
        0f at 299; 1f at 452 with FastOutSlowInEasing; 1f at 3956; 0f at 4278
    }), label = "ppA")
    val pillProgY by inf.animateFloat(-28f, -28f, infiniteRepeatable(keyframes {
        durationMillis = loopDur
        -28f at 0; -28f at 299; 4f at 452; 0f at 575; 0f at 3956; -10f at 4278
    }), label = "ppY")

    val pillDoneA by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = loopDur
        0f at 452; 1f at 621 with FastOutSlowInEasing; 1f at 3956; 0f at 4278
    }), label = "pdA")
    val pillDoneY by inf.animateFloat(-28f, -28f, infiniteRepeatable(keyframes {
        durationMillis = loopDur
        -28f at 0; -28f at 452; 4f at 621; 0f at 736; 0f at 3956; -10f at 4278
    }), label = "pdY")

    // card-glide: starts in col1 (-102px), glides to col2 (0px)
    val glideX by inf.animateFloat(-102f, -102f, infiniteRepeatable(keyframes {
        durationMillis = loopDur
        -102f at 0; -102f at 644; -102f at 1748 // deals in col1
        -65f at 1978; 3f at 2300; 0f at 2530 with FastOutSlowInEasing
        0f at 3956; 0f at 4232
    }), label = "glX")
    val glideY by inf.animateFloat(-18f, -18f, infiniteRepeatable(keyframes {
        durationMillis = loopDur
        -18f at 0; -18f at 644; 0f at 828; 0f at 1748
        -8f at 2300; 0f at 2530; 0f at 4232
    }), label = "glY")
    val glideScale by inf.animateFloat(0.92f, 0.92f, infiniteRepeatable(keyframes {
        durationMillis = loopDur
        0.92f at 0; 0.92f at 644; 1f at 828; 1f at 1748
        1.06f at 2070; 1.03f at 2300; 1f at 2530; 1f at 4232
    }), label = "glS")
    val glideA by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = loopDur
        0f at 0; 0f at 644; 1f at 828; 1f at 3956; 0f at 4232
    }), label = "glA")

    val fixCardA by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = loopDur
        0f at 0; 0f at 644; 1f at 920 with FastOutSlowInEasing; 1f at 3956; 0f at 4232
    }), label = "fixA")
    val fixCardY by inf.animateFloat(32f, 32f, infiniteRepeatable(keyframes {
        durationMillis = loopDur
        32f at 0; 32f at 644; 0f at 920; 0f at 3956; 12f at 4232
    }), label = "fixY")

    val doneCardA by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = loopDur
        0f at 0; 0f at 644; 1f at 920; 1f at 3956; 0f at 4232
    }), label = "dcA")
    val doneCardX by inf.animateFloat(36f, 36f, infiniteRepeatable(keyframes {
        durationMillis = loopDur
        36f at 0; 36f at 644; 0f at 920; 0f at 3956; 16f at 4232
    }), label = "dcX")

    val dashedA by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = loopDur
        0f at 1656; 1f at 1932; 1f at 3956; 0f at 4232
    }), label = "daA")

    val badgeScale by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = loopDur
        0f at 2438; 1.3f at 2622; 1f at 2806 with FastOutSlowInEasing
        1f at 3956; 0f at 4232
    }), label = "bs")
    val badgeA by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = loopDur
        0f at 2438; 1f at 2622; 1f at 3956; 0f at 4232
    }), label = "ba")

    val stickyA by inf.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = loopDur
        0f at 2806; 1f at 3082 with FastOutSlowInEasing; 1f at 3956; 0f at 4232
    }), label = "stA")
    val stickyRot by inf.animateFloat(4f, 4f, infiniteRepeatable(keyframes {
        durationMillis = loopDur
        4f at 2806; -3f at 3082; -1.5f at 3266; -1.5f at 3956; 0f at 4232
    }), label = "stR")

    Column(modifier = Modifier.fillMaxSize()) {
        // Header bar
        Box(modifier = Modifier.fillMaxWidth().height(40.dp).background(OnboardCard)) {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Sprint 12", style = TextStyle(fontFamily = NewsreaderFamily,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = InkPrimary))
                    Box(modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(PrintLeaf)
                        .padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("Active", style = TextStyle(fontFamily = ManropeFamily,
                            fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PrintInk))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                    listOf("EL" to IndigoAccent, "MR" to MintDark, "KT" to CoralAccent)
                        .forEach { (l, c) ->
                            Box(modifier = Modifier.size(16.dp).background(c, CircleShape),
                                contentAlignment = Alignment.Center) {
                                Text(l, style = TextStyle(fontFamily = ManropeFamily,
                                    fontSize = 7.sp, fontWeight = FontWeight.Bold, color = PrintInk))
                            }
                        }
                }
            }
        }
        Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(BorderLight))

        // 3-column kanban grid
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
                .padding(10.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Column 1: Todo
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Pill header
                Box(
                    modifier = Modifier
                        .graphicsLayer { translationY = pillTodoY; alpha = pillTodoA }
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(PrintCoral.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Todo", style = TextStyle(fontFamily = ManropeFamily,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = PrintInk))
                        Box(modifier = Modifier.clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.8f))
                            .padding(horizontal = 4.dp)) {
                            Text("0", style = TextStyle(fontFamily = ManropeFamily,
                                fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                color = PrintInk))
                        }
                    }
                }
                // Dashed empty slot
                Box(modifier = Modifier
                    .alpha(dashedA)
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .drawWithCache {
                        val dashColor = PrintInk.copy(alpha = 0.5f)
                        onDrawBehind {
                        drawRoundRect(color = dashColor,
                            cornerRadius = CornerRadius(12.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                    floatArrayOf(6f, 6f), 0f)
                            ))
                        }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⠿", style = TextStyle(fontSize = 13.sp, color = InkMuted))
                        Text("Moved", style = TextStyle(fontFamily = ManropeFamily,
                            fontSize = 9.sp, color = InkMuted))
                    }
                }
            }

            // Column 2: In Progress
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Pill header with badge
                Box(
                    modifier = Modifier
                        .graphicsLayer { translationY = pillProgY; alpha = pillProgA }
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFF6D365).copy(alpha = 0.22f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("In Prog", style = TextStyle(fontFamily = ManropeFamily,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = PrintInk))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.8f))
                                .padding(horizontal = 4.dp)) {
                                Text("2", style = TextStyle(fontFamily = ManropeFamily,
                                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                    color = PrintInk))
                            }
                            // +1 badge
                            Box(modifier = Modifier
                                .graphicsLayer { scaleX = badgeScale; scaleY = badgeScale; alpha = badgeA }
                                .clip(RoundedCornerShape(50))
                                .background(MintDark)
                                .padding(horizontal = 4.dp, vertical = 1.dp)) {
                                Text("+1", style = TextStyle(fontFamily = ManropeFamily,
                                    fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PrintInk))
                            }
                        }
                    }
                }
                // Gliding card (Design login screen)
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = glideX; translationY = glideY
                            scaleX = glideScale; scaleY = glideScale; alpha = glideA
                        }
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PrintPaper)
                        .padding(8.dp)
                ) {
                    Box(modifier = Modifier.width(3.dp).height(36.dp)
                        .background(Color(0xFFF6D365), RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)))
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text("Design login screen", style = TextStyle(fontFamily = ManropeFamily,
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = InkPrimary))
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("✓", style = TextStyle(fontSize = 11.sp, color = PrintLeafDark))
                            Text("UX Specs", style = TextStyle(fontFamily = ManropeFamily,
                                fontSize = 9.sp, color = InkMuted))
                        }
                    }
                }
                // Fix API bug card
                Box(
                    modifier = Modifier
                        .graphicsLayer { translationY = fixCardY; alpha = fixCardA }
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PrintPaper)
                        .padding(8.dp)
                ) {
                    Box(modifier = Modifier.width(3.dp).height(32.dp)
                        .background(Color(0xFFF6D365), RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)))
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text("Fix API bug", style = TextStyle(fontFamily = ManropeFamily,
                            fontSize = 11.sp, color = InkPrimary))
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("#v1.4", style = TextStyle(fontFamily = ManropeFamily,
                                fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = PrintRedDark))
                            Box(modifier = Modifier.size(18.dp)
                                .background(Color(0xFFFFE1D6), CircleShape),
                                contentAlignment = Alignment.Center) {
                                Text("JS", style = TextStyle(fontFamily = ManropeFamily,
                                    fontSize = 8.sp, fontWeight = FontWeight.Bold, color = CoralDark))
                            }
                        }
                    }
                }
            }

            // Column 3: Done
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .graphicsLayer { translationY = pillDoneY; alpha = pillDoneA }
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(PrintLeaf.copy(alpha = 0.25f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Done", style = TextStyle(fontFamily = ManropeFamily,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = PrintInk))
                        Box(modifier = Modifier.clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.8f))
                            .padding(horizontal = 4.dp)) {
                            Text("1", style = TextStyle(fontFamily = ManropeFamily,
                                fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                color = PrintInk))
                        }
                    }
                }
                // Write tests card (done — strikethrough)
                Box(
                    modifier = Modifier
                        .graphicsLayer { translationX = doneCardX; alpha = doneCardA }
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PrintPaper.copy(alpha = 0.9f))
                        .padding(8.dp)
                ) {
                    Box(modifier = Modifier.width(3.dp).height(32.dp)
                        .background(PrintLeaf, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)))
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text("Write tests", style = TextStyle(fontFamily = ManropeFamily,
                            fontSize = 11.sp, color = InkMuted,
                            textDecoration = TextDecoration.LineThrough))
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End) {
                            Box(modifier = Modifier.size(16.dp)
                                .background(PrintLeaf.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center) {
                                Text("✓", style = TextStyle(fontSize = 11.sp,
                                    color = PrintInk, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }

        // Sticky note (floating, bottom-right)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .width(120.dp)
                .graphicsLayer { rotationZ = stickyRot; alpha = stickyA }
                .clip(RoundedCornerShape(8.dp))
                .background(StickyNote)
                .padding(8.dp)
        ) {
            Column {
                Text("Don't forget standup", style = TextStyle(
                    fontFamily  = NewsreaderFamily,
                    fontSize    = 11.sp,
                    fontStyle   = FontStyle.Italic,
                    color       = InkPrimary
                ))
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("10:00 AM", style = TextStyle(fontFamily = ManropeFamily,
                        fontSize = 8.sp, color = InkMuted))
                    Box(modifier = Modifier.size(6.dp)
                        .background(CoralAccent, CircleShape))
                }
            }
        }
    }
}
}

// ─────────────────────────────────────────────────────────────────
// BOTTOM CARD  (30%)
// ─────────────────────────────────────────────────────────────────

@Composable
private fun BottomCard(
    page       : OnboardPage,
    pageCount  : Int,
    currentPage: Int,
    isLastPage : Boolean,
    accentColor: Color,
    onNext     : () -> Unit,
    onFinish   : () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .printSurface(shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), fill = OnboardCard, shadow = null)
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = 24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Sub-label row + pill dots
            Row(
                modifier            = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment   = Alignment.CenterVertically
            ) {
                Text(
                    text  = page.subLabel,
                    style = TextStyle(
                        fontFamily    = ManropeFamily,
                        fontSize      = 12.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 0.12.sp,
                        color         = PrintInk
                    )
                )
                // Pill dot indicators
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    repeat(pageCount) { i ->
                        val isActive = i == currentPage
                        val dotWidth by animateDpAsState(
                            targetValue   = if (isActive) 28.dp else 8.dp,
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                            label         = "dot$i"
                        )
                        Box(modifier = Modifier
                            .height(10.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(if (isActive) accentColor else PrintWhite)
                            .border(1.5.dp, PrintInk, CircleShape))
                    }
                }
            }

            // Headline
            Column {
                page.headline.split("\n").forEach { line ->
                    MisregisteredTitle(text = line, style = PrintType.H1.copy(fontSize = 30.sp, lineHeight = 34.sp))
                }
            }

            // Body
            Text(
                text  = page.body,
                style = TextStyle(
                    fontFamily = ManropeFamily,
                    fontSize   = 14.sp,
                    lineHeight = 20.sp,
                    color      = InkMuted
                )
            )

            Spacer(Modifier.height(4.dp))

            // CTA button
            CtaButton(
                text    = if (isLastPage) "Get started" else "Continue",
                onClick = if (isLastPage) onFinish else onNext
            )

            // Android home bar gesture indicator
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// CTA BUTTON
// ─────────────────────────────────────────────────────────────────

@Composable
private fun CtaButton(
    text   : String,
    onClick: () -> Unit
) {
    PrintButton(
        text = "$text  →",
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    )
}

// ─────────────────────────────────────────────────────────────────
// SHARED COMPOSABLES
// ─────────────────────────────────────────────────────────────────

@Composable
private fun TypingBubble(
    left     : Boolean,
    dot1Y    : Float,
    dot2Y    : Float,
    dot3Y    : Float,
    color    : Color = BubbleLeft,
    dotColor : Color = PrintInk.copy(alpha = 0.6f)
) {
    val shape = if (left)
        RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 4.dp, topStart = 12.dp)
    else
        RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, bottomEnd = 4.dp, topEnd = 12.dp)

    Box(modifier = Modifier
        .clip(shape)
        .background(color)
        .padding(horizontal = 8.dp, vertical = 6.dp)) {
        TypingDots(dot1Y = dot1Y, dot2Y = dot2Y, dot3Y = dot3Y, color = dotColor)
    }
}

@Composable
private fun TypingDots(dot1Y: Float, dot2Y: Float, dot3Y: Float, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically) {
        listOf(dot1Y, dot2Y, dot3Y).forEach { dy ->
            Box(modifier = Modifier
                .size(6.dp)
                .offset(y = dy.dp)
                .background(color, CircleShape))
        }
    }
}
