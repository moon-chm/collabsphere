package com.collabsphere.app.ui.theme

import com.collabsphere.app.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

val PrintPaper = Color(0xFFFBF5EA)
val PrintInk = Color(0xFF1E2A5A)
val PrintLeaf = Color(0xFF8CC63F)
val PrintLeafDark = Color(0xFF3F7D20)
val PrintCoral = Color(0xFFF28B6C)
val PrintRisoRed = Color(0xFFE2432F)
val PrintRedDark = Color(0xFFB8321F)
val PrintSky = Color(0xFF5BC0EB)
val PrintWhite = Color(0xFFFFFFFF)
val PrintInkSoft = PrintInk.copy(alpha = 0.72f)

object PrintTokens {
    val Outline: Dp = 2.dp
    val ShadowOffset: Dp = 4.dp
    val RadiusCard: Dp = 24.dp
    val RadiusSmall: Dp = 12.dp
    val ButtonHeight: Dp = 56.dp
    val TouchTarget: Dp = 48.dp
    val SidePadding: Dp = 20.dp
    val SpringEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
}

object PrintType {
    val H1 = TextStyle(fontFamily = FrauncesFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 38.sp, letterSpacing = (-0.02).em)
    val H2 = TextStyle(fontFamily = FrauncesFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp)
    val Hero = TextStyle(fontFamily = FrauncesFamily, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 58.sp, letterSpacing = (-0.02).em)
    val H3 = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp)
    val Body = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    val Label = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.01.em)
    val Caption = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
    val Tiny = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.08.em)
}

@Composable
fun rememberPrintReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

fun organicPath(size: Size, seed: Int, phase: Float = 0f, bow: Float = 0.035f): Path {
    val rnd = java.util.Random(seed.toLong())
    val minDim = min(size.width, size.height)
    val inset = minDim * bow
    val l = inset
    val t = inset
    val r = size.width - inset
    val b = size.height - inset
    val radii = FloatArray(4) { (0.22f + rnd.nextFloat() * 0.22f) * minDim }
    val bows = FloatArray(4) { i -> (rnd.nextFloat() * 2f - 1f + 0.35f * sin(phase + i * 1.7f)) * inset }
    return Path().apply {
        moveTo(l + radii[0], t)
        quadraticTo((l + r) / 2f, t - bows[0], r - radii[1], t)
        quadraticTo(r, t, r, t + radii[1])
        quadraticTo(r + bows[1], (t + b) / 2f, r, b - radii[2])
        quadraticTo(r, b, r - radii[2], b)
        quadraticTo((l + r) / 2f, b + bows[2], l + radii[3], b)
        quadraticTo(l, b, l, b - radii[3])
        quadraticTo(l - bows[3], (t + b) / 2f, l, t + radii[0])
        quadraticTo(l, t, l + radii[0], t)
        close()
    }
}

fun blobPath(size: Size, seed: Int, phase: Float = 0f, wobble: Float = 0.12f, points: Int = 7): Path {
    val rnd = java.util.Random(seed.toLong())
    val cx = size.width / 2f
    val cy = size.height / 2f
    val pts = List(points) { i ->
        val a = 2.0 * PI * i / points
        val radius = 1f - wobble + (rnd.nextFloat() * wobble) - wobble * 0.45f * (0.5f + 0.5f * sin(phase + i * 2.1f))
        Offset(cx + cos(a).toFloat() * cx * radius, cy + sin(a).toFloat() * cy * radius)
    }
    fun mid(a: Offset, b: Offset) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    return Path().apply {
        val start = mid(pts.last(), pts.first())
        moveTo(start.x, start.y)
        for (i in pts.indices) {
            val p = pts[i]
            val m = mid(p, pts[(i + 1) % pts.size])
            quadraticTo(p.x, p.y, m.x, m.y)
        }
        close()
    }
}

class OrganicShape(private val seed: Int) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(organicPath(size, seed))
}

class BlobShape(private val seed: Int, private val wobble: Float = 0.12f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(blobPath(size, seed, wobble = wobble))
}

fun Modifier.printSurface(
    shape: Shape,
    fill: Color = PrintWhite,
    shadow: Color? = PrintInk,
    outline: Dp = PrintTokens.Outline,
    shadowOffset: Dp = PrintTokens.ShadowOffset,
    pressProgress: () -> Float = { 0f }
): Modifier = this
    .graphicsLayer {
        val shift = shadowOffset.toPx() * pressProgress()
        translationX = shift
        translationY = shift
    }
    .drawWithCache {
        val shapeOutline = shape.createOutline(size, layoutDirection, this)
        val offsetPx = shadowOffset.toPx()
        val strokePx = outline.toPx()
        onDrawBehind {
            val remaining = offsetPx * (1f - pressProgress().coerceIn(0f, 1f))
            if (shadow != null && remaining > 0f) {
                translate(remaining, remaining) { drawOutline(shapeOutline, shadow) }
            }
            drawOutline(shapeOutline, fill)
            if (strokePx > 0f) drawOutline(shapeOutline, PrintInk, style = Stroke(strokePx))
        }
    }
    .clip(shape)

@Composable
fun rememberPrintPress(interactionSource: MutableInteractionSource): () -> Float {
    val reduced = rememberPrintReducedMotion()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(interactionSource, reduced) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> if (reduced) progress.snapTo(1f) else progress.animateTo(1f, tween(110, easing = FastOutSlowInEasing))
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    if (reduced) {
                        progress.snapTo(0f)
                    } else {
                        if (progress.value < 1f) progress.animateTo(1f, tween(70))
                        progress.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 700f))
                    }
                }
            }
        }
    }
    return { progress.value }
}

private val grainBitmap: ImageBitmap by lazy {
    val side = 192
    val rnd = java.util.Random(7L)
    val pixels = IntArray(side * side) {
        if (rnd.nextFloat() < 0.32f) {
            val alpha = 14 + rnd.nextInt(46)
            (alpha shl 24) or 0x1E2A5A
        } else 0
    }
    Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888).asImageBitmap()
}

fun Modifier.paperGrain(alpha: Float = 0.55f): Modifier = this.drawWithCache {
    val brush = ShaderBrush(ImageShader(grainBitmap, TileMode.Repeated, TileMode.Repeated))
    onDrawWithContent {
        drawContent()
        drawRect(brush = brush, alpha = alpha, blendMode = BlendMode.Multiply)
    }
}

@Composable
fun MisregisteredTitle(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = PrintType.H1,
    offsetColor: Color = PrintCoral,
    stampIn: Boolean = true
) {
    val reduced = rememberPrintReducedMotion()
    var played by rememberSaveable { mutableStateOf(!stampIn) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            if (reduced) progress.animateTo(1f, tween(150)) else progress.animateTo(1f, tween(400, easing = PrintTokens.SpringEasing))
            played = true
        }
    }
    Box(
        modifier = modifier.graphicsLayer {
            val p = progress.value
            if (reduced) {
                alpha = p.coerceIn(0f, 1f)
            } else {
                val s = 1.15f - 0.15f * p
                scaleX = s
                scaleY = s
                alpha = (p * 2f).coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
        }
    ) {
        Text(
            text = text,
            style = style,
            color = offsetColor,
            modifier = Modifier
                .graphicsLayer {
                    val settle = if (reduced) 1f else progress.value
                    translationX = (3.dp.toPx()) + (1f - settle) * 6.dp.toPx()
                    translationY = (2.dp.toPx()) + (1f - settle) * 4.dp.toPx()
                    blendMode = BlendMode.Multiply
                }
                .clearAndSetSemantics { }
        )
        Text(text = text, style = style, color = PrintInk)
    }
}

@Composable
fun PrintButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPrintPress(interaction)
    val pill = RoundedCornerShape(percent = 50)
    val fill = when {
        !enabled -> PrintPaper
        primary -> PrintInk
        else -> PrintPaper
    }
    val content = if (primary && enabled) PrintPaper else PrintInk.copy(alpha = if (enabled) 1f else 0.45f)
    Box(
        modifier = modifier
            .height(PrintTokens.ButtonHeight)
            .printSurface(
                shape = pill,
                fill = fill,
                shadow = if (primary && enabled) PrintCoral else null,
                pressProgress = if (enabled) press else ({ 0f })
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
            Text(text = text, style = PrintType.Label, color = content, maxLines = 1)
        }
    }
}

@Composable
fun PrintIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = PrintWhite,
    tint: Color = PrintInk,
    size: Dp = PrintTokens.TouchTarget,
    iconSize: Dp = 22.dp,
    shadow: Boolean = true,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPrintPress(interaction)
    Box(
        modifier = modifier
            .size(size)
            .printSurface(
                shape = CircleShape,
                fill = fill,
                shadow = if (shadow && enabled) PrintInk else null,
                shadowOffset = if (shadow) 3.dp else 0.dp,
                pressProgress = press
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(iconSize))
    }
}

@Composable
fun PrintChip(
    text: String,
    modifier: Modifier = Modifier,
    fill: Color = PrintPaper,
    onClick: (() -> Unit)? = null
) {
    val pill = RoundedCornerShape(percent = 50)
    val reduced = rememberPrintReducedMotion()
    val animatedFill by animateColorAsState(fill, tween(if (reduced) 0 else 200), label = "chipFill")
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(pill)
            .drawBehind {
                drawRect(animatedFill)
            }
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .drawWithContent {
                drawContent()
                val stroke = PrintTokens.Outline.toPx()
                drawRoundRect(
                    color = PrintInk,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                    style = Stroke(stroke)
                )
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                if (reduced) {
                    fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                } else {
                    (slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220))) togetherWith
                        (slideOutVertically(tween(160)) { -it / 2 } + fadeOut(tween(160))) using
                        SizeTransform(clip = false)
                }
            },
            label = "chipText"
        ) { value ->
            Text(text = value, style = PrintType.Caption.copy(fontWeight = FontWeight.Bold), color = PrintInk, maxLines = 1)
        }
    }
}

@Composable
fun CollabSphereMark(modifier: Modifier = Modifier, size: Dp = 40.dp, dots: Boolean = true) {
    val reduced = rememberPrintReducedMotion()
    var played by rememberSaveable { mutableStateOf(false) }
    val logoIn = remember { Animatable(if (played || reduced) 1f else 0f) }
    val dotsIn = remember { List(LogoDotCount) { Animatable(if (played || reduced) 1f else 0f) } }
    val spin = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val phase = rememberBlobPhase(24000)
    LaunchedEffect(Unit) {
        if (!played && !reduced) {
            launch { logoIn.animateTo(1f, tween(520, easing = PrintTokens.SpringEasing)) }
            dotsIn.forEachIndexed { index, dot ->
                launch {
                    delay(180L + index * 35L)
                    dot.animateTo(1f, tween(360, easing = PrintTokens.SpringEasing))
                }
            }
            delay(180L + LogoDotCount * 35L + 360L)
        }
        played = true
    }
    val box = if (dots) size * 1.5f else size
    Box(
        modifier = modifier
            .size(box)
            .drawBehind {
                if (!dots) return@drawBehind
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val ring = size.toPx() * 0.66f
                val big = size.toPx() * 0.055f
                val small = size.toPx() * 0.032f
                val outline = (size.toPx() * 0.022f).coerceIn(0.75.dp.toPx(), 1.5.dp.toPx())
                val turn = phase() * (180f / PI.toFloat())
                for (i in 0 until LogoDotCount) {
                    val p = dotsIn[i].value
                    if (p <= 0f) continue
                    val angle = Math.toRadians((turn + i * 360f / LogoDotCount).toDouble())
                    val pos = Offset(center.x + ring * cos(angle).toFloat(), center.y + ring * sin(angle).toFloat())
                    val isBig = i % 2 == 0
                    val r = (if (isBig) big else small) * p
                    drawCircle(LogoDotColors[(i / 2) % LogoDotColors.size].takeIf { isBig } ?: PrintInk, radius = r, center = pos)
                    if (isBig) drawCircle(PrintInk, radius = r, center = pos, style = Stroke(outline))
                }
            }
            .pointerInput(reduced) {
                detectTapGestures {
                    if (!reduced && !spin.isRunning) {
                        scope.launch { spin.animateTo(spin.value + 360f, tween(700, easing = PrintTokens.SpringEasing)) }
                    }
                }
            }
            .semantics { contentDescription = "CollabSphere logo" },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.collabsphere),
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val p = logoIn.value
                    scaleX = 0.6f + 0.4f * p
                    scaleY = 0.6f + 0.4f * p
                    alpha = p.coerceIn(0f, 1f)
                    rotationZ = spin.value
                }
        )
    }
}

private const val LogoDotCount = 16
private val LogoDotColors = listOf(PrintCoral, PrintLeaf, PrintSky, Color(0xFFF6D365))

@Composable
fun HalftonePatch(modifier: Modifier = Modifier, color: Color = PrintInk, dot: Dp = 2.5.dp, gap: Dp = 7.dp) {
    Canvas(modifier = modifier) {
        val step = gap.toPx()
        val c = Offset(size.width / 2f, size.height / 2f)
        val maxR = min(size.width, size.height) / 2f
        var y = step / 2f
        while (y < size.height) {
            var x = step / 2f
            while (x < size.width) {
                val d = (Offset(x, y) - c).getDistance() / maxR
                if (d < 1f) drawCircle(color, radius = dot.toPx() * (1f - d * 0.7f), center = Offset(x, y))
                x += step
            }
            y += step
        }
    }
}

@Composable
fun PrintStar(modifier: Modifier = Modifier, color: Color = PrintInk, twinkle: Boolean = false) {
    val reduced = rememberPrintReducedMotion()
    val animate = twinkle && !reduced
    val transition = rememberInfiniteTransition(label = "star")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animate) 90f else 0f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "starSpin"
    )
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (animate) 0.78f else 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "starPulse"
    )
    Canvas(modifier = modifier.graphicsLayer {
        rotationZ = spin
        scaleX = pulse
        scaleY = pulse
    }) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = min(cx, cy)
        val inner = r * 0.28f
        val path = Path().apply {
            moveTo(cx, cy - r)
            quadraticTo(cx + inner * 0.3f, cy - inner * 0.3f, cx + r, cy)
            quadraticTo(cx + inner * 0.3f, cy + inner * 0.3f, cx, cy + r)
            quadraticTo(cx - inner * 0.3f, cy + inner * 0.3f, cx - r, cy)
            quadraticTo(cx - inner * 0.3f, cy - inner * 0.3f, cx, cy - r)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun rememberBlobPhase(durationMillis: Int = 8000): () -> Float {
    val reduced = rememberPrintReducedMotion()
    if (reduced) return { 0f }
    val transition = rememberInfiniteTransition(label = "blobPhase")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "blobPhaseValue"
    )
    return { phase }
}

@Composable
fun Modifier.printEntrance(index: Int, alreadyShown: Boolean, onShown: () -> Unit): Modifier {
    val reduced = rememberPrintReducedMotion()
    val progress = remember { Animatable(if (alreadyShown) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            if (index < 8) kotlinx.coroutines.delay(index * 70L)
            if (reduced) progress.animateTo(1f, tween(150)) else progress.animateTo(1f, tween(400, easing = PrintTokens.SpringEasing))
            onShown()
        }
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        if (!reduced) translationY = (1f - p) * 24.dp.toPx()
    }
}

@Composable
fun PrintSheetHandle() {
    Box(
        modifier = Modifier
            .padding(top = 12.dp, bottom = 8.dp)
            .size(width = 44.dp, height = 6.dp)
            .clip(RoundedCornerShape(percent = 50))
            .drawBehind { drawRect(PrintInk) }
    )
}

val PrintSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

fun Modifier.printSheetOutline(): Modifier = this.drawWithContent {
    drawContent()
    val stroke = PrintTokens.Outline.toPx()
    val r = 28.dp.toPx()
    val path = Path().apply {
        moveTo(stroke / 2f, size.height + r)
        lineTo(stroke / 2f, r)
        quadraticTo(stroke / 2f, stroke / 2f, r, stroke / 2f)
        lineTo(size.width - r, stroke / 2f)
        quadraticTo(size.width - stroke / 2f, stroke / 2f, size.width - stroke / 2f, r)
        lineTo(size.width - stroke / 2f, size.height + r)
    }
    drawPath(path, PrintInk, style = Stroke(stroke))
}

@Composable
fun rememberEntranceRegistry(): MutableSet<Any> = remember { mutableSetOf() }

@Composable
fun Modifier.printListEntrance(key: Any, index: Int, registry: MutableSet<Any>): Modifier =
    printEntrance(index, key in registry) { registry += key }
