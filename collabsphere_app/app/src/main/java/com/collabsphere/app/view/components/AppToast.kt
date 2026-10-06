package com.collabsphere.app.view.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.collabsphere.app.ui.theme.AmberWarn
import com.collabsphere.app.ui.theme.DestructiveStart
import com.collabsphere.app.ui.theme.IndigoStart
import com.collabsphere.app.ui.theme.Ink
import com.collabsphere.app.ui.theme.MintGreen
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.ui.theme.SurfaceRaised
import com.collabsphere.app.ui.theme.skeuoRaised
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

// ─────────────────────────────────────────────────────────────────
// In-app toast system
//
// Replaces android.widget.Toast with a styled, typed notification card that
// slides in from the top. Call from anywhere (any thread):
//
//     AppToast.show("Workspace created")            // type inferred → Success
//     AppToast.error("Couldn't upload file")
//
// Render <AppToastHost/> once at the app root (MainActivity).
// ─────────────────────────────────────────────────────────────────

enum class ToastType(val title: String, val accent: Color, val icon: ImageVector) {
    Success("Success", MintGreen, Icons.Rounded.CheckCircle),
    Error("Something went wrong", DestructiveStart, Icons.Rounded.ErrorOutline),
    Warning("Heads up", AmberWarn, Icons.Rounded.WarningAmber),
    Info("Info", IndigoStart, Icons.Rounded.Info)
}

data class ToastMessage(
    val id: Long,
    val message: String,
    val type: ToastType,
    val durationMs: Long
)

object AppToast {
    private val _current = MutableStateFlow<ToastMessage?>(null)
    val current: StateFlow<ToastMessage?> = _current.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ids = AtomicLong(0)
    private var dismissJob: Job? = null

    fun success(message: String?) = show(message, ToastType.Success)
    fun error(message: String?) = show(message, ToastType.Error)
    fun warning(message: String?) = show(message, ToastType.Warning)
    fun info(message: String?) = show(message, ToastType.Info)

    /**
     * Shows a toast. When [type] is null it is inferred from the wording so
     * existing call-sites get sensible colours without extra work.
     */
    fun show(message: String?, type: ToastType? = null, long: Boolean = false) {
        val text = clean(message ?: return)
        if (text.isBlank()) return
        val resolvedType = type ?: infer(text)
        val duration = (2600L + text.length * 45L)
            .coerceIn(2600L, 6500L)
            .let { if (long) maxOf(it, 4500L) else it }
            .let { if (resolvedType == ToastType.Error) maxOf(it, 4000L) else it }

        scope.launch {
            val existing = _current.value
            // Same message already on screen → keep the card, just restart its timer.
            val msg = if (existing != null && existing.message == text && existing.type == resolvedType) {
                existing.copy(id = ids.incrementAndGet(), durationMs = duration)
            } else {
                ToastMessage(ids.incrementAndGet(), text, resolvedType, duration)
            }
            _current.value = msg
            dismissJob?.cancel()
            dismissJob = scope.launch {
                delay(duration)
                if (_current.value?.id == msg.id) _current.value = null
            }
        }
    }

    fun dismiss() {
        scope.launch {
            dismissJob?.cancel()
            _current.value = null
        }
    }

    /** Strips machine codes / exception prefixes and tidies capitalisation. */
    internal fun clean(raw: String): String {
        var s = raw.trim()
        s = s.replace(Regex("^[A-Z][A-Z0-9_]{3,}:\\s*"), "")            // EMAIL_NOT_VERIFIED: ...
        s = s.replace(Regex("^(java|kotlin|io)\\.[\\w.]+(Exception|Error):\\s*"), "")
        s = s.replace(Regex("^(Exception|Error):\\s*", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("\\s+"), " ")
        if (s.isNotEmpty()) s = s.replaceFirstChar { it.uppercaseChar() }
        return s
    }

    internal fun infer(text: String): ToastType {
        val t = text.lowercase()
        return when {
            ERROR_WORDS.any { it in t } -> ToastType.Error
            WARNING_WORDS.any { it in t } -> ToastType.Warning
            SUCCESS_WORDS.any { it in t } -> ToastType.Success
            else -> ToastType.Info
        }
    }

    private val ERROR_WORDS = listOf(
        "fail", "error", "couldn't", "could not", "can't", "cannot", "unable",
        "invalid", "denied", "wrong", "expired", "not found", "incorrect",
        "network issue", "rejected", "unauthorized", "forbidden", "no app found",
        "do not match", "don't match", "not allowed", "timed out", "timeout"
    )
    private val WARNING_WORDS = listOf(
        "please", "must", "required", "cannot be empty", "can't be empty",
        "verify your", "not verified", "waking up", "queued", "offline", "scroll up",
        "at least", "already"
    )
    private val SUCCESS_WORDS = listOf(
        "success", "verified", "sent", "created", "saved", "deleted", "updated",
        "removed", "added", "copied", "joined", "left ", "uploaded", "linked",
        "unlinked", "connected", "disconnected", "reset", "welcome", "done",
        "muted", "unmuted", "blocked", "unblocked", "invited", "pinned", "unpinned",
        "archived", "restored", "synced", "complete"
    )
}

/**
 * Root-level host that renders the active [AppToast] as a top-aligned card.
 * Tap or swipe up to dismiss; a thin bar shows the remaining time.
 */
@Composable
fun AppToastHost(modifier: Modifier = Modifier) {
    val current by AppToast.current.collectAsStateLifecycleSafe()
    val visibleState = remember { MutableTransitionState(false) }
    var shown by remember { mutableStateOf<ToastMessage?>(null) }

    LaunchedEffect(current) {
        val c = current
        if (c != null) {
            shown = c
            visibleState.targetState = true
        } else {
            visibleState.targetState = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
                initialOffsetY = { -it - 40 }
            ) + fadeIn(tween(180)),
            exit = slideOutVertically(
                animationSpec = tween(220),
                targetOffsetY = { -it - 40 }
            ) + fadeOut(tween(200))
        ) {
            shown?.let { ToastCard(it, onDismiss = AppToast::dismiss) }
        }
    }
}

@Composable
private fun ToastCard(toast: ToastMessage, onDismiss: () -> Unit) {
    val accent = toast.type.accent
    val remaining = remember(toast.id) { Animatable(1f) }
    LaunchedEffect(toast.id) {
        remaining.snapTo(1f)
        remaining.animateTo(0f, tween(toast.durationMs.toInt(), easing = LinearEasing))
    }
    var dragY by remember(toast.id) { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .graphicsLayer {
                translationY = dragY.coerceAtMost(0f)
                alpha = 1f - (-dragY / 300f).coerceIn(0f, 0.8f)
            }
            .pointerInput(toast.id) {
                detectVerticalDragGestures(
                    onDragEnd = { if (dragY < -60f) onDismiss() else dragY = 0f },
                    onDragCancel = { dragY = 0f },
                    onVerticalDrag = { _, delta -> dragY = (dragY + delta).coerceAtMost(0f) }
                )
            }
            .skeuoRaised(cornerRadius = 18.dp, elevation = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceRaised)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        // Left accent stripe
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(5.dp)
                .fillMaxHeight()
                .background(accent)
        )

        Column {
            Row(
                modifier = Modifier.padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(toast.type.icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = toast.type.title,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = accent
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = toast.message,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.5.sp,
                            lineHeight = 20.sp
                        ),
                        color = Ink,
                        maxLines = 4
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = Muted, modifier = Modifier.size(18.dp))
                }
            }
            // Time-remaining bar
            Box(
                Modifier
                    .fillMaxWidth(remaining.value)
                    .height(3.dp)
                    .background(accent.copy(alpha = 0.55f))
            )
        }
    }
}

/**
 * Inline status banner for use inside dialogs, where a root-level toast would
 * be hidden behind the dialog window.
 */
@Composable
fun InlineStatusBanner(message: String?, modifier: Modifier = Modifier, type: ToastType? = null) {
    val text = message?.let { AppToast.clean(it) }.orEmpty()
    AnimatedVisibility(
        visible = text.isNotBlank(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        val resolved = type ?: AppToast.infer(text)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(resolved.accent.copy(alpha = 0.10f))
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(resolved.icon, contentDescription = null, tint = resolved.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp, lineHeight = 18.sp),
                color = Ink
            )
        }
    }
}

@Composable
private fun <T> StateFlow<T>.collectAsStateLifecycleSafe() =
    this.collectAsStateWithLifecycle()
