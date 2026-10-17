package com.collabsphere.app.view.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.collabsphere.app.ui.theme.BlobShape
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.ui.theme.PrintCoral
import com.collabsphere.app.ui.theme.PrintIconButton
import com.collabsphere.app.ui.theme.PrintInk
import com.collabsphere.app.ui.theme.PrintPaper
import com.collabsphere.app.ui.theme.PrintRedDark
import com.collabsphere.app.ui.theme.PrintTokens
import com.collabsphere.app.ui.theme.PrintType
import com.collabsphere.app.ui.theme.isDestructiveColor
import com.collabsphere.app.ui.theme.printAccentFor
import com.collabsphere.app.ui.theme.printSurface
import com.collabsphere.app.ui.theme.rememberPrintPress
import com.collabsphere.app.ui.theme.rememberPrintReducedMotion

@Composable
fun WorkspaceDialogCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    closeEnabled: Boolean = true,
    successMessage: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val reduced = rememberPrintReducedMotion()
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduced) entrance.animateTo(1f, tween(150))
        else entrance.animateTo(1f, tween(380, easing = PrintTokens.SpringEasing))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val p = entrance.value
                    alpha = p.coerceIn(0f, 1f)
                    if (!reduced) {
                        scaleX = 0.9f + 0.1f * p
                        scaleY = 0.9f + 0.1f * p
                        translationY = (1f - p) * 40.dp.toPx()
                    }
                }
                .printSurface(shape = RoundedCornerShape(28.dp))
        ) {
            AnimatedContent(
                targetState = successMessage,
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 80)) + scaleIn(initialScale = 0.92f, animationSpec = tween(260, delayMillis = 80)))
                        .togetherWith(fadeOut(tween(120)))
                },
                label = "workspaceDialogContent"
            ) { success ->
                if (success != null) {
                    WorkspaceSuccessState(message = success, accent = accent)
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        WorkspaceDialogBadge(icon = icon, accent = accent)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = title,
                            style = PrintType.H2.copy(fontSize = 24.sp, lineHeight = 28.sp),
                            color = PrintInk,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = subtitle,
                            style = PrintType.Body.copy(fontSize = 15.sp, lineHeight = 22.sp),
                            color = Muted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(22.dp))
                        content()
                    }
                }
            }

            if (successMessage == null) {
                PrintIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Close",
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    fill = PrintPaper,
                    size = 40.dp,
                    iconSize = 18.dp,
                    shadow = false,
                    enabled = closeEnabled
                )
            }
        }
    }
}

@Composable
private fun WorkspaceDialogBadge(icon: ImageVector, accent: Color) {
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    val blob = remember { BlobShape(seed = 17, wobble = 0.14f) }
    Box(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .printSurface(shape = blob, fill = printAccentFor(accent), shadow = null),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = PrintInk, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun WorkspaceSuccessState(message: String, accent: Color) {
    val ring = remember { Animatable(0f) }
    val tick = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        ring.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) {
        tick.animateTo(1f, tween(360, delayMillis = 180, easing = FastOutSlowInEasing))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .size(76.dp)
                .graphicsLayer { scaleX = ring.value; scaleY = ring.value }
        ) {
            val fill = printAccentFor(accent)
            val radius = size.minDimension * 0.42f
            drawCircle(color = PrintInk, radius = radius, center = Offset(center.x + 4.dp.toPx(), center.y + 4.dp.toPx()))
            drawCircle(color = fill, radius = radius)
            drawCircle(color = PrintInk, radius = radius, style = Stroke(2.dp.toPx()))
            val w = size.width
            val h = size.height
            val start = Offset(w * 0.36f, h * 0.51f)
            val mid = Offset(w * 0.46f, h * 0.61f)
            val end = Offset(w * 0.65f, h * 0.41f)
            val firstLen = (mid - start).getDistance()
            val secondLen = (end - mid).getDistance()
            val drawn = tick.value * (firstLen + secondLen)
            val path = Path().apply {
                moveTo(start.x, start.y)
                if (drawn <= firstLen) {
                    val f = drawn / firstLen
                    lineTo(start.x + (mid.x - start.x) * f, start.y + (mid.y - start.y) * f)
                } else {
                    lineTo(mid.x, mid.y)
                    val f = (drawn - firstLen) / secondLen
                    lineTo(mid.x + (end.x - mid.x) * f, mid.y + (end.y - mid.y) * f)
                }
            }
            if (tick.value > 0f) {
                drawPath(
                    path = path,
                    color = PrintInk,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = PrintType.H3,
            color = PrintInk,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun WorkspaceDialogButton(
    text: String,
    colors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingText: String = text
) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPrintPress(interaction)
    val active = enabled || loading
    val destructive = colors.firstOrNull()?.let(::isDestructiveColor) == true
    val fill = when {
        !active -> PrintPaper
        destructive -> PrintRedDark
        else -> PrintInk
    }
    val content = if (active) PrintPaper else PrintInk.copy(alpha = 0.45f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PrintTokens.ButtonHeight)
            .printSurface(
                shape = RoundedCornerShape(percent = 50),
                fill = fill,
                shadow = if (active) (if (destructive) PrintInk else PrintCoral) else null,
                pressProgress = if (active) press else ({ 0f })
            )
            .clickable(interactionSource = interaction, indication = null, enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = { fadeIn(tween(160)).togetherWith(fadeOut(tween(120))) },
            label = "workspaceDialogButtonLabel"
        ) { isLoading ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (isLoading) {
                    CollabSpinner(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = content)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = if (isLoading) loadingText else text,
                    style = PrintType.Label,
                    color = content
                )
            }
        }
    }
}

@Composable
fun PrintConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector = androidx.compose.material.icons.Icons.Default.Close,
    destructive: Boolean = true,
    dismissLabel: String = "Cancel"
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        WorkspaceDialogCard(
            icon = icon,
            accent = if (destructive) com.collabsphere.app.ui.theme.PrintRisoRed else com.collabsphere.app.ui.theme.PrintSky,
            title = title,
            subtitle = message,
            onClose = onDismiss
        ) {
            WorkspaceDialogButton(
                text = confirmLabel,
                colors = listOf(if (destructive) com.collabsphere.app.ui.theme.PrintRedDark else PrintInk),
                onClick = onConfirm
            )
            Spacer(Modifier.height(12.dp))
            com.collabsphere.app.ui.theme.PrintButton(
                text = dismissLabel,
                onClick = onDismiss,
                primary = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
