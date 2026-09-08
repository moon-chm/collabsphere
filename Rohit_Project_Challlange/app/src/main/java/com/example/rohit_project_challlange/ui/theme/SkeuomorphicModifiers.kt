package com.example.rohit_project_challlange.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
 * Procedural matte-resin fine grain texture (2-3% opacity warm-grey noise).
 * Created once and cached in a ShaderBrush for zero per-frame allocation.
 */
private val matteGrainBrush: ShaderBrush by lazy {
    val size = 32
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val random = java.util.Random(42) // deterministic seed for consistent grain pattern
    val pixels = IntArray(size * size)
    for (i in pixels.indices) {
        val v = random.nextInt(14)
        val alpha = (v * 1.5f).toInt() // 0..21 (~0..8% max, avg ~3%)
        pixels[i] = android.graphics.Color.argb(alpha, 160, 150, 140)
    }
    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    ShaderBrush(shader)
}

/**
 * Raised / Extruded Skeuomorphic Surface.
 * Simulates a card or button lifted off the canvas with dual soft shadows.
 * Uses real ARGB values via toArgb() for hardware paint rendering.
 *
 * @param cornerRadius  rounded corner of the surface (defaults to SkeuoTokens.RadiusMedium)
 * @param elevation     shadow intensity scale (defaults to SkeuoTokens.ElevationRaised)
 * @param isDark        swap shadow values for dark mode
 */
fun Modifier.skeuoRaised(
    cornerRadius: Dp = SkeuoTokens.RadiusMedium,
    elevation: Dp = SkeuoTokens.ElevationRaised,
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
                setShadowLayer(el * 2.5f, el * 1.2f, el * 1.4f, darkColor.toArgb())
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
                setShadowLayer(el * 2f, -el * 0.8f, -el * 0.9f, lightColor.toArgb())
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
 * Simulates a trough or cavity carved directly into the canvas.
 * Implemented with layered physical geometry:
 * 1. Outer cavity edge boundary
 * 2. Sunken interior gradient floor
 * 3. Top-left dark inner stroke
 * 4. Bottom-right specular light rim
 *
 * @param cornerRadius  rounded corner of the debossed region (defaults to SkeuoTokens.RadiusSmall)
 * @param depth         depth of the inset cavity (defaults to SkeuoTokens.DepthInset)
 * @param isDark        swap shadow values for dark mode
 */
fun Modifier.skeuoInset(
    cornerRadius: Dp = SkeuoTokens.RadiusSmall,
    depth: Dp = SkeuoTokens.DepthInset,
    isDark: Boolean = false
): Modifier = this.drawWithCache {
    val cr = cornerRadius.toPx()
    val d  = depth.toPx()

    val bedBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(Color(0xFF181614), Color(0xFF22201D))
        } else {
            listOf(Color(0xFFEDE8DF), Color(0xFFF7F4ED))
        }
    )
    val innerDarkColor = if (isDark) ShadowDarkDark.copy(alpha = 0.5f) else Color(0xFF201612).copy(alpha = 0.18f)
    val innerLightColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.85f)
    val cavityEdgeColor = if (isDark) Color.Black.copy(alpha = 0.3f) else Color(0xFF241A15).copy(alpha = 0.12f)
    val strokeWidth = d.coerceAtLeast(1.dp.toPx())

    onDrawBehind {
        // 1. Soft cavity edge boundary around the perimeter
        drawRoundRect(
            color = cavityEdgeColor,
            topLeft = Offset(0f, 0f),
            size = size,
            cornerRadius = CornerRadius(cr)
        )
        // 2. Sunken interior warm bed
        drawRoundRect(
            brush = bedBrush,
            topLeft = Offset(d * 0.5f, d * 0.5f),
            size = Size(size.width - d, size.height - d),
            cornerRadius = CornerRadius(cr)
        )
        // 3. Top-left dark inner stroke (cavity sink)
        drawRoundRect(
            color = innerDarkColor,
            topLeft = Offset(d * 0.5f, d * 0.7f),
            size = Size(size.width - d, size.height - d),
            cornerRadius = CornerRadius(cr),
            style = Stroke(width = strokeWidth)
        )
        // 4. Bottom-right specular light rim reflection
        drawRoundRect(
            color = innerLightColor,
            topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
            size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
            cornerRadius = CornerRadius(cr),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

/**
 * Tactile Bounce Click Modifier — physical interactive click
 * with spring decompression on release, accessible semantics via Modifier.clickable,
 * and physical haptic tick on press-down.
 */
fun Modifier.skeuoBounceClick(
    pressedScale: Float = 0.96f,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit = {}
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current

    // Trigger physical haptic feedback on touch-down
    LaunchedEffect(isPressed) {
        if (isPressed) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

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
 * Cached via drawWithCache to prevent per-draw Brush allocations.
 */
fun Modifier.skeuoTopRim(
    cornerRadius: Dp = SkeuoTokens.RadiusMedium,
    alpha: Float = 0.5f,
    isDark: Boolean = false
): Modifier = this.drawWithCache {
    val cr = cornerRadius.toPx()
    val rimColor = if (isDark)
        Color.White.copy(alpha = alpha * 0.15f)
    else
        Color.White.copy(alpha = alpha)

    val rimBrush = Brush.verticalGradient(
        colors = listOf(rimColor, Color.Transparent),
        startY = 0f,
        endY = cr * 1.2f
    )
    val rimSize = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx())
    val rimTopLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx())
    val strokeWidth = 1.dp.toPx()

    onDrawBehind {
        drawRoundRect(
            brush = rimBrush,
            topLeft = rimTopLeft,
            size = rimSize,
            cornerRadius = CornerRadius(cr),
            style = Stroke(width = strokeWidth)
        )
    }
}

/**
 * Modern Skeuomorphic Floating Card.
 * Exact replication of reference card: soft warm ambient shadows underneath,
 * subtle specular highlight top-left, warm ivory surface (#FAF8F5), top hairline,
 * and a subtle fine-grain matte-resin finish.
 * Optimized with drawWithCache so brushes and geometry are cached across LazyColumn rows.
 */
fun Modifier.skeuoFloatingCard(
    cornerRadius: Dp = SkeuoTokens.RadiusLarge,
    isPressed: Boolean = false,
    surfaceColor: Color = Surface
): Modifier = this.drawWithCache {
    val cr = cornerRadius.toPx()
    val contactOffset = if (isPressed) 0.8.dp.toPx() else 1.5.dp.toPx()
    val ambientOffset = if (isPressed) 2.5.dp.toPx() else 6.dp.toPx()

    // Key 2.5x ratio: contact shadow alpha is 0.16f vs ambient 0.06f
    val contactAlpha = if (isPressed) 0.10f else 0.16f
    val ambientAlpha = if (isPressed) 0.03f else 0.06f

    val contactColor = Color(0xFF2C201A).copy(alpha = contactAlpha)
    val ambientColor = Color(0xFF2C201A).copy(alpha = ambientAlpha)
    val hairlineBorderColor = Color(0xFF2C2A28).copy(alpha = 0.06f)

    val hairlineBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.90f),
            Color.White.copy(alpha = 0.20f),
            Color.Transparent
        ),
        startY = 0f,
        endY = cr * 1.2f
    )
    val hairlineSize = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx())
    val hairlineTopLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx())
    val strokeWidth = 1.dp.toPx()

    onDrawBehind {
        // 1. Ambient shadow — soft, diffuse, larger offset (diffuse glow underneath)
        drawRoundRect(
            color = ambientColor,
            topLeft = Offset(0f, ambientOffset),
            size = size,
            cornerRadius = CornerRadius(cr)
        )
        // 2. Contact shadow — tight, dark, close, low blur (crisp edge definition)
        drawRoundRect(
            color = contactColor,
            topLeft = Offset(0f, contactOffset),
            size = size,
            cornerRadius = CornerRadius(cr)
        )
        // 3. Elevated warm surface card body
        drawRoundRect(
            color = surfaceColor,
            cornerRadius = CornerRadius(cr)
        )
        // 3b. Fine matte-resin grain texture
        drawRoundRect(
            brush = matteGrainBrush,
            cornerRadius = CornerRadius(cr)
        )
        // 4. Subtle perimeter hairline stroke defining the boundary on top of the shadow
        drawRoundRect(
            color = hairlineBorderColor,
            cornerRadius = CornerRadius(cr),
            style = Stroke(width = strokeWidth)
        )
        // 5. Specular hairline highlight perfectly hugging the top rounded contour & shoulders
        drawRoundRect(
            brush = hairlineBrush,
            topLeft = hairlineTopLeft,
            size = hairlineSize,
            cornerRadius = CornerRadius(cr),
            style = Stroke(width = strokeWidth)
        )
    }
}

/**
 * Skeuomorphic Action Icon Button — tactile circular or squircle socket
 * for secondary row actions (e.g. Delete, Edit) with raised physical geometry,
 * tight contact shadow, specular halo, and spring compression.
 */
@Composable
fun SkeuoActionIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 32.dp,
    iconSize: Dp = 16.dp,
    tint: Color = DestructiveStart
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "actionIconScale"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                val cr = (size / 2).toPx()
                if (isPressed) {
                    // Sunken pressed state
                    drawRoundRect(
                        color = Color(0xFF201612).copy(alpha = 0.15f),
                        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                        size = Size(this.size.width - 2.dp.toPx(), this.size.height - 2.dp.toPx()),
                        cornerRadius = CornerRadius(cr)
                    )
                    drawRoundRect(
                        color = Background.copy(alpha = 0.8f),
                        cornerRadius = CornerRadius(cr)
                    )
                } else {
                    // Ambient diffuse shadow
                    drawRoundRect(
                        color = Color(0xFF2C201A).copy(alpha = 0.05f),
                        topLeft = Offset(0f, 2.5.dp.toPx()),
                        size = this.size,
                        cornerRadius = CornerRadius(cr)
                    )
                    // Contact shadow
                    drawRoundRect(
                        color = Color(0xFF2C201A).copy(alpha = 0.14f),
                        topLeft = Offset(0f, 1.2.dp.toPx()),
                        size = this.size,
                        cornerRadius = CornerRadius(cr)
                    )
                    // Specular top highlight
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.85f),
                        topLeft = Offset(0f, -0.8.dp.toPx()),
                        size = this.size,
                        cornerRadius = CornerRadius(cr)
                    )
                    // Button body
                    drawRoundRect(
                        color = Surface,
                        cornerRadius = CornerRadius(cr)
                    )
                    // 1px hairline border
                    drawRoundRect(
                        color = Color(0xFF2C2A28).copy(alpha = 0.08f),
                        cornerRadius = CornerRadius(cr),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }
            .clip(RoundedCornerShape(size / 2))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else Muted.copy(alpha = 0.35f),
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Sunken / Debossed Squircle Icon Well matching reference design.
 * Creates an inset cavity carved into the card surface with soft dark inner shadow
 * on top-left, warm recessed gradient bed, specular reflection on bottom-right,
 * and fine matte-resin grain texture.
 * Cached with drawWithCache for smooth scrolling in LazyColumn rows.
 */
@Composable
fun SkeuoDebossedIconWell(
    modifier: Modifier = Modifier,
    wellSize: Dp = 46.dp,
    cornerRadius: Dp = SkeuoTokens.RadiusSmall,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(wellSize)
            .drawWithCache {
                val cr = cornerRadius.toPx()
                val cavityEdgeColor = Color(0xFF241A15).copy(alpha = 0.12f)
                val innerDarkColor = Color(0xFF201612).copy(alpha = 0.16f)
                val bedBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFEDE8DF),
                        Color(0xFFF7F4ED)
                    )
                )
                val innerLightColor = Color.White.copy(alpha = 0.88f)
                val strokeWidth = 1.2.dp.toPx()

                onDrawBehind {
                    // 1. Soft cavity edge around the debossed perimeter
                    drawRoundRect(
                        color = cavityEdgeColor,
                        topLeft = Offset(0f, 0f),
                        size = size,
                        cornerRadius = CornerRadius(cr)
                    )
                    // 2. Top-left dark inner shadow (cavity sink)
                    drawRoundRect(
                        color = innerDarkColor,
                        topLeft = Offset(1.5.dp.toPx(), 2.dp.toPx()),
                        size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                        cornerRadius = CornerRadius(cr)
                    )
                    // 3. Sunken interior warm bed
                    drawRoundRect(
                        brush = bedBrush,
                        topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                        size = Size(size.width - 3.dp.toPx(), size.height - 3.dp.toPx()),
                        cornerRadius = CornerRadius(cr)
                    )
                    // 3b. Fine matte-resin micro-grain on sunken bed
                    drawRoundRect(
                        brush = matteGrainBrush,
                        topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                        size = Size(size.width - 3.dp.toPx(), size.height - 3.dp.toPx()),
                        cornerRadius = CornerRadius(cr)
                    )
                    // 4. Bottom-right inner specular reflection rim
                    drawRoundRect(
                        color = innerLightColor,
                        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                        size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                        cornerRadius = CornerRadius(cr),
                        style = Stroke(width = strokeWidth)
                    )
                }
            }
            .clip(RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
        content = content
    )
}
