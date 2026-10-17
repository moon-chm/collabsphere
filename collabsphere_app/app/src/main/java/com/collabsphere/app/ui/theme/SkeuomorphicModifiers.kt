package com.collabsphere.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun printAccentFor(color: Color): Color = when (color) {
    CoralStart, CoralEnd, CoralLight, AmberWarn -> PrintCoral
    IndigoStart, IndigoEnd -> PrintSky
    MintGreen, MintLight -> PrintLeaf
    DestructiveStart, DestructiveEnd, DestructiveLight -> PrintRisoRed
    else -> color
}

fun isDestructiveColor(color: Color): Boolean =
    color == DestructiveStart || color == DestructiveEnd || color == DestructiveLight || color == PrintRisoRed || color == PrintRedDark

fun printIconTint(color: Color): Color = if (isDestructiveColor(color)) PrintRedDark else PrintInk

fun Modifier.skeuoRaised(
    cornerRadius: Dp = SkeuoTokens.RadiusMedium,
    elevation: Dp = SkeuoTokens.ElevationRaised,
    isDark: Boolean = false
): Modifier = this.drawBehind {
    val cr = CornerRadius(cornerRadius.toPx())
    val shadow = if (elevation <= 2.dp) 2.dp.toPx() else PrintTokens.ShadowOffset.toPx()
    translate(shadow, shadow) { drawRoundRect(PrintInk, cornerRadius = cr) }
    drawRoundRect(PrintWhite, cornerRadius = cr)
    drawRoundRect(PrintInk, cornerRadius = cr, style = Stroke(PrintTokens.Outline.toPx() * 2f))
}

fun Modifier.skeuoInset(
    cornerRadius: Dp = SkeuoTokens.RadiusSmall,
    depth: Dp = SkeuoTokens.DepthInset,
    isDark: Boolean = false,
    symmetric: Boolean = false,
    shadowStrength: Float = 1f
): Modifier = this.drawBehind {
    val stroke = PrintTokens.Outline.toPx()
    val half = stroke / 2f
    val cr = CornerRadius((cornerRadius.toPx() - half).coerceAtLeast(0f))
    drawRoundRect(PrintWhite, cornerRadius = CornerRadius(cornerRadius.toPx()))
    drawRoundRect(
        color = PrintInk,
        topLeft = Offset(half, half),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = cr,
        style = Stroke(stroke)
    )
}

fun Modifier.skeuoFloatingCard(
    cornerRadius: Dp = SkeuoTokens.RadiusLarge,
    isPressed: Boolean = false,
    surfaceColor: Color = Surface
): Modifier = this.drawWithCache {
    val cr = CornerRadius(cornerRadius.toPx())
    val stroke = PrintTokens.Outline.toPx()
    val half = stroke / 2f
    val shadow = if (isPressed) 1.dp.toPx() else 3.dp.toPx()
    val fill = if (surfaceColor == Surface || surfaceColor == SurfaceRaised) PrintWhite else surfaceColor
    onDrawBehind {
        translate(shadow, shadow) { drawRoundRect(PrintInk, cornerRadius = cr) }
        drawRoundRect(fill, cornerRadius = cr)
        drawRoundRect(
            color = PrintInk,
            topLeft = Offset(half, half),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius((cr.x - half).coerceAtLeast(0f)),
            style = Stroke(stroke)
        )
    }
}

@Composable
fun SkeuoActionIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 32.dp,
    iconSize: Dp = 16.dp,
    tint: Color = DestructiveStart,
    fill: Color = PrintWhite
) {
    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberPrintPress(interactionSource)
    Box(
        modifier = modifier
            .size(size)
            .printSurface(
                shape = CircleShape,
                fill = fill,
                shadow = null,
                outline = if (size < 40.dp) 1.5.dp else PrintTokens.Outline,
                shadowOffset = 0.dp,
                pressProgress = press
            )
            .graphicsLayer {
                val s = 1f - 0.08f * press()
                scaleX = s
                scaleY = s
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) printIconTint(tint) else PrintInk.copy(alpha = 0.35f),
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun SkeuoHeaderActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val press = rememberPrintPress(interactionSource)

    LaunchedEffect(isPressed) {
        if (isPressed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Box(
        modifier = modifier
            .printSurface(
                shape = RoundedCornerShape(percent = 50),
                fill = PrintInk,
                shadow = PrintCoral,
                shadowOffset = 3.dp,
                pressProgress = press
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = PrintPaper,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = PrintType.Caption.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                color = PrintPaper,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

fun Modifier.fadingEdge(
    top: Dp = 0.dp,
    bottom: Dp = 0.dp
): Modifier = if (top <= 0.dp && bottom <= 0.dp) {
    this
} else {
    this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val topPx = top.toPx()
            val bottomPx = bottom.toPx()

            if (topPx > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startY = 0f,
                        endY = topPx
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
            if (bottomPx > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startY = size.height - bottomPx,
                        endY = size.height
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
        }
}

@Composable
fun Modifier.fadingScrollEdges(
    listState: LazyListState,
    top: Dp = 24.dp,
    bottom: Dp = 48.dp
): Modifier {
    val canScrollBackward by remember { derivedStateOf { listState.canScrollBackward } }
    val canScrollForward by remember { derivedStateOf { listState.canScrollForward } }

    val animatedTop by animateDpAsState(
        targetValue = if (canScrollBackward) top else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "topFadingEdge"
    )
    val animatedBottom by animateDpAsState(
        targetValue = if (canScrollForward) bottom else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "bottomFadingEdge"
    )

    return this.fadingEdge(top = animatedTop, bottom = animatedBottom)
}

@Composable
fun Modifier.fadingScrollEdges(
    scrollState: ScrollState,
    top: Dp = 24.dp,
    bottom: Dp = 48.dp
): Modifier {
    val canScrollBackward by remember { derivedStateOf { scrollState.canScrollBackward } }
    val canScrollForward by remember { derivedStateOf { scrollState.canScrollForward } }

    val animatedTop by animateDpAsState(
        targetValue = if (canScrollBackward) top else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "scrollStateTopFadingEdge"
    )
    val animatedBottom by animateDpAsState(
        targetValue = if (canScrollForward) bottom else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "scrollStateBottomFadingEdge"
    )

    return this.fadingEdge(top = animatedTop, bottom = animatedBottom)
}

fun Modifier.skeuoFocusRing(
    isFocused: Boolean,
    cornerRadius: Dp = 14.dp,
    ringColor: Color = CoralStart,
    isError: Boolean = false
): Modifier = composed {
    val ringAlpha by animateFloatAsState(
        targetValue = if (isError || isFocused) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "skeuoFocusRingAlpha"
    )
    val color = if (isError) PrintRedDark else PrintLeafDark

    this.drawWithCache {
        val cr = cornerRadius.toPx()
        val gap = 2.dp.toPx()
        val ring = 3.dp.toPx()
        val reach = gap + ring / 2f
        onDrawBehind {
            if (ringAlpha > 0f) {
                drawRoundRect(
                    color = color.copy(alpha = ringAlpha),
                    topLeft = Offset(-reach, -reach),
                    size = Size(size.width + reach * 2f, size.height + reach * 2f),
                    cornerRadius = CornerRadius(cr + reach),
                    style = Stroke(width = ring)
                )
            }
        }
    }
}
