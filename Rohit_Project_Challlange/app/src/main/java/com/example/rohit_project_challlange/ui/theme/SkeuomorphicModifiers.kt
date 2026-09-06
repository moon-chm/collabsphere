package com.example.rohit_project_challlange.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────
// MODERN SKEUOMORPHIC SURFACE MODIFIERS — CollabSphere
//
// Light source: top-left 315° (northwest)
// Raised elements: bright highlight top-left + dark shadow bottom-right
// Inset elements: dark shadow top-left inside + bright rim bottom-right
// ─────────────────────────────────────────────────────────────────

/**
 * Raised / Extruded Skeuomorphic Surface.
 * Simulates a card or button lifted off the canvas with dual soft shadows.
 *
 * @param cornerRadius  rounded corner of the surface
 * @param elevation     shadow intensity scale (1dp = subtle, 4dp = prominent)
 * @param isDark        swap shadow values for dark mode
 */
fun Modifier.skeuoRaised(
    cornerRadius: Dp = 16.dp,
    elevation: Dp = 3.dp,
    isDark: Boolean = false
): Modifier = this.drawBehind {
    val cr = cornerRadius.toPx()
    val el = elevation.toPx()

    val lightColor = if (isDark) ShadowLightDark else ShadowLight
    val darkColor  = if (isDark) ShadowDarkDark  else ShadowDark

    drawIntoCanvas { canvas ->
        // ── Bottom-right dark ambient shadow ──
        val darkPaint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                color = android.graphics.Color.TRANSPARENT
                setShadowLayer(el * 2.5f, el * 1.2f, el * 1.4f, darkColor.hashCode())
            }
        }
        canvas.drawRoundRect(
            left   = 0f,
            top    = 0f,
            right  = size.width,
            bottom = size.height,
            radiusX = cr,
            radiusY = cr,
            paint   = darkPaint
        )

        // ── Top-left specular light reflection ──
        val lightPaint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                color = android.graphics.Color.TRANSPARENT
                setShadowLayer(el * 2f, -el * 0.8f, -el * 0.9f, lightColor.hashCode())
            }
        }
        canvas.drawRoundRect(
            left   = 0f,
            top    = 0f,
            right  = size.width,
            bottom = size.height,
            radiusX = cr,
            radiusY = cr,
            paint   = lightPaint
        )
    }
}

/**
 * Debossed / Inset Skeuomorphic Surface.
 * Simulates an input field or track carved into the surface canvas.
 *
 * @param cornerRadius  rounded corner of the debossed region
 * @param depth         depth of the inset shadow
 * @param isDark        swap shadow values for dark mode
 */
fun Modifier.skeuoInset(
    cornerRadius: Dp = 12.dp,
    depth: Dp = 2.dp,
    isDark: Boolean = false
): Modifier = this.drawBehind {
    val cr = cornerRadius.toPx()
    val d  = depth.toPx()

    val lightColor = if (isDark) ShadowLightDark else ShadowLight
    val darkColor  = if (isDark) ShadowDarkDark  else ShadowDark

    drawIntoCanvas { canvas ->
        // ── Inner dark shadow (top-left inner) ──
        val innerDark = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                color = android.graphics.Color.TRANSPARENT
                setShadowLayer(d * 2f, d, d, darkColor.hashCode())
            }
        }
        canvas.drawRoundRect(0f, 0f, size.width, size.height, cr, cr, innerDark)

        // ── Inner light rim (bottom-right inner highlight) ──
        val innerLight = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                color = android.graphics.Color.TRANSPARENT
                setShadowLayer(d * 1.5f, -d * 0.7f, -d * 0.7f, lightColor.hashCode())
            }
        }
        canvas.drawRoundRect(0f, 0f, size.width, size.height, cr, cr, innerLight)
    }
}

/**
 * Tactile Press Modifier — physical key depression on tap.
 * Scale compresses on press; releases with natural spring bounce.
 */
fun Modifier.skeuoPress(
    pressedScale: Float = 0.96f,
    onClick: () -> Unit = {}
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pressScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(onClick) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                    onClick()
                }
            )
        }
}

/**
 * Tactile Bounce Click Modifier — physical interactive click
 * with spring decompression on release and MutableInteractionSource support.
 */
fun Modifier.skeuoBounceClick(
    pressedScale: Float = 0.96f,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit = {}
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bounceScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = source,
            indication = null,
            onClick = onClick
        )
}

/**
 * Specular Top Rim — draws a subtle bright gradient hairline
 * along the very top edge, simulating a raised bevel highlight.
 */
fun Modifier.skeuoTopRim(
    cornerRadius: Dp = 16.dp,
    alpha: Float = 0.5f,
    isDark: Boolean = false
): Modifier = this.drawBehind {
    val cr = cornerRadius.toPx()
    val rimColor = if (isDark)
        Color.White.copy(alpha = alpha * 0.15f)
    else
        Color.White.copy(alpha = alpha)

    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(rimColor, Color.Transparent),
            startY = 0f,
            endY = cr * 1.2f
        ),
        topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
        size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
        cornerRadius = CornerRadius(cr),
        style = Stroke(width = 1.dp.toPx())
    )
}

/**
 * Modern Skeuomorphic Floating Card.
 * Exact replication of reference card: soft warm ambient shadows underneath,
 * subtle specular highlight top-left, warm ivory surface (#FAF8F5), and top hairline.
 */
fun Modifier.skeuoFloatingCard(
    cornerRadius: Dp = 18.dp,
    isPressed: Boolean = false,
    surfaceColor: Color = Color(0xFFFAF8F5)
): Modifier = this.drawBehind {
    val cr = cornerRadius.toPx()
    val shadowOffset = if (isPressed) 1.5.dp else 3.5.dp
    val ambientAlpha = if (isPressed) 0.05f else 0.09f

    // 1. Soft deep ambient drop shadow underneath
    drawRoundRect(
        color = Color(0xFF2C201A).copy(alpha = ambientAlpha * 0.5f),
        topLeft = Offset(0f, (shadowOffset + 2.dp).toPx()),
        size = Size(size.width, size.height),
        cornerRadius = CornerRadius(cr)
    )
    // 2. Direct contact drop shadow underneath
    drawRoundRect(
        color = Color(0xFF2C201A).copy(alpha = ambientAlpha),
        topLeft = Offset(0f, shadowOffset.toPx()),
        size = Size(size.width, size.height),
        cornerRadius = CornerRadius(cr)
    )
    // 3. Elevated warm ivory card body
    drawRoundRect(
        color = surfaceColor,
        cornerRadius = CornerRadius(cr)
    )
    // 4. Specular hairline highlight perfectly hugging the top rounded contour & shoulders
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.90f),
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

/**
 * Sunken / Debossed Squircle Icon Well matching reference design.
 * Creates an inset cavity carved into the card surface with soft dark inner shadow
 * on top-left, warm recessed gradient bed, and specular reflection on bottom-right.
 */
@Composable
fun SkeuoDebossedIconWell(
    modifier: Modifier = Modifier,
    wellSize: Dp = 46.dp,
    cornerRadius: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(wellSize)
            .drawBehind {
                val cr = cornerRadius.toPx()
                // 1. Soft cavity edge around the debossed perimeter
                drawRoundRect(
                    color = Color(0xFF241A15).copy(alpha = 0.12f),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(cr)
                )
                // 2. Top-left dark inner shadow (cavity sink)
                drawRoundRect(
                    color = Color(0xFF201612).copy(alpha = 0.16f),
                    topLeft = Offset(1.5.dp.toPx(), 2.dp.toPx()),
                    size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                    cornerRadius = CornerRadius(cr)
                )
                // 3. Sunken interior warm bed
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFEDE8DF),
                            Color(0xFFF7F4ED)
                        )
                    ),
                    topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                    size = Size(size.width - 3.dp.toPx(), size.height - 3.dp.toPx()),
                    cornerRadius = CornerRadius(cr)
                )
                // 4. Bottom-right inner specular reflection rim
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.88f),
                    topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                    size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                    cornerRadius = CornerRadius(cr),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
            .clip(RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
        content = content
    )
}

