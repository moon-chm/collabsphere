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
import com.collabsphere.app.ui.theme.Ink
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.ui.theme.SurfaceRaised
import com.collabsphere.app.ui.theme.skeuoFloatingCard

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
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
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
                    scaleX = 0.9f + 0.1f * p
                    scaleY = 0.9f + 0.1f * p
                    translationY = (1f - p) * 40.dp.toPx()
                }
                .skeuoFloatingCard(cornerRadius = 28.dp, surfaceColor = SurfaceRaised)
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
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Ink,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Muted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(22.dp))
                        content()
                    }
                }
            }

            if (successMessage == null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(enabled = closeEnabled, onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Muted.copy(alpha = if (closeEnabled) 0.8f else 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }
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
    Box(
        modifier = Modifier
            .size(60.dp)
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
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
            drawCircle(color = accent.copy(alpha = 0.14f))
            drawCircle(color = accent, radius = size.minDimension * 0.36f)
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
                    color = Color.White,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Ink,
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
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "workspaceDialogButtonScale"
    )
    val fill by animateFloatAsState(
        targetValue = if (enabled || loading) 1f else 0.45f,
        animationSpec = tween(220),
        label = "workspaceDialogButtonFill"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = fill }
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(colors))
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
                    CollabSpinner(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = if (isLoading) loadingText else text,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                    color = Color.White
                )
            }
        }
    }
}
