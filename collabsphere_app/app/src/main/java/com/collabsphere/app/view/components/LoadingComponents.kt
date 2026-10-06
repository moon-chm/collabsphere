package com.collabsphere.app.view.components

import com.collabsphere.app.view.components.CollabSpinner

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.collabsphere.app.ui.theme.CoralStart
import com.collabsphere.app.ui.theme.Ink
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.ui.theme.SurfaceRaised
import com.collabsphere.app.ui.theme.skeuoRaised

// ─────────────────────────────────────────────────────────────────
// CollabSphere loading system
//
// One spinner, one linear loader and one full-area loading state, so every
// screen "waits" the same way. Prefer skeletons (ScreenSkeletons.kt) for
// first-load list/content placeholders, and these spinners for short,
// in-place actions (buttons, pagination, sync badges).
// ─────────────────────────────────────────────────────────────────

/**
 * Branded indeterminate spinner: a soft track ring with a gradient arc that
 * breathes (grows/shrinks) while rotating. Drop-in replacement for
 * `CollabSpinner(modifier, color, strokeWidth)`.
 *
 * Size comes from [modifier] (e.g. `Modifier.size(20.dp)`); defaults to 36dp.
 */
@Composable
fun CollabSpinner(
    modifier: Modifier = Modifier,
    color: Color = CoralStart,
    strokeWidth: Dp = 3.dp,
    trackColor: Color = color.copy(alpha = 0.14f)
) {
    val transition = rememberInfiniteTransition(label = "collabSpinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "spinnerRotation"
    )
    val sweep by transition.animateFloat(
        initialValue = 40f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1400
                40f at 0 using FastOutSlowInEasing
                250f at 700 using FastOutSlowInEasing
                40f at 1400
            }
        ),
        label = "spinnerSweep"
    )

    Canvas(
        modifier = modifier
            .size(36.dp)
            .semantics { contentDescription = "Loading" }
    ) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)

        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke)
        )

        rotate(rotation) {
            drawArc(
                brush = Brush.sweepGradient(
                    0f to color.copy(alpha = 0f),
                    (sweep / 360f) to color,
                    1f to color.copy(alpha = 0f),
                    center = center
                ),
                startAngle = 0f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Indeterminate linear loader with a gliding gradient segment. Replaces
 * `LinearProgressIndicator` for upload/sync bars.
 */
@Composable
fun CollabLinearLoader(
    modifier: Modifier = Modifier,
    color: Color = CoralStart,
    trackColor: Color = color.copy(alpha = 0.15f)
) {
    val transition = rememberInfiniteTransition(label = "collabLinear")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "linearProgress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(trackColor)
            .semantics { contentDescription = "Loading" }
            .drawBehind {
                val segment = size.width * 0.38f
                val start = -segment + (size.width + segment) * progress
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(color.copy(alpha = 0f), color, color.copy(alpha = 0f)),
                        startX = start,
                        endX = start + segment
                    ),
                    topLeft = Offset(start, 0f),
                    size = Size(segment, size.height)
                )
            }
    )
}

/**
 * Centered loading state for a whole screen/pane: a raised neumorphic disc
 * holding the spinner, with an optional caption underneath.
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String? = null,
    color: Color = CoralStart
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .skeuoRaised(cornerRadius = 32.dp)
                .background(SurfaceRaised, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CollabSpinner(modifier = Modifier.size(34.dp), color = color, strokeWidth = 3.dp)
        }
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = Muted,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Compact "spinner + label" row for inline actions such as
 * "Sending code…" or "Loading more…".
 */
@Composable
fun InlineLoadingRow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CoralStart,
    textColor: Color = Ink.copy(alpha = 0.75f)
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CollabSpinner(modifier = Modifier.size(16.dp), color = color, strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = textColor
        )
    }
}
