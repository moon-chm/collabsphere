package com.collabsphere.app.view.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.collabsphere.app.ui.theme.Ink
import com.collabsphere.app.ui.theme.Muted
import com.collabsphere.app.ui.theme.PrintCoral
import com.collabsphere.app.ui.theme.PrintInk
import com.collabsphere.app.ui.theme.PrintPaper
import com.collabsphere.app.ui.theme.PrintTokens
import com.collabsphere.app.ui.theme.PrintType
import com.collabsphere.app.ui.theme.PrintWhite
import com.collabsphere.app.ui.theme.printAccentFor
import com.collabsphere.app.ui.theme.printSurface

val ChatBubbleTail: Dp = 7.dp
val OwnBubbleColor = PrintInk
val ChatBubbleOutline: Dp = 1.5.dp

fun Modifier.chatBubbleSurface(isOwn: Boolean, shape: Shape): Modifier =
    if (isOwn) this.background(OwnBubbleColor, shape)
    else this.background(PrintWhite, shape).border(ChatBubbleOutline, PrintInk, shape)

fun Modifier.chatComposerDock(): Modifier =
    this.printSurface(shape = RoundedCornerShape(28.dp), shadowOffset = 3.dp)

fun Modifier.chatComposerField(focused: Boolean): Modifier =
    this
        .clip(RoundedCornerShape(23.dp))
        .background(PrintPaper)
        .border(if (focused) PrintTokens.Outline else ChatBubbleOutline, PrintInk, RoundedCornerShape(23.dp))

fun Modifier.chatSendButton(enabled: Boolean, pressProgress: () -> Float): Modifier =
    this.printSurface(
        shape = CircleShape,
        fill = if (enabled) PrintCoral else PrintPaper,
        shadow = if (enabled) PrintInk else null,
        shadowOffset = 3.dp,
        pressProgress = pressProgress
    )

fun Modifier.chatHeaderBar(): Modifier =
    this.drawBehind {
        drawRect(PrintPaper)
        drawRect(
            color = PrintInk,
            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - PrintTokens.Outline.toPx()),
            size = Size(size.width, PrintTokens.Outline.toPx())
        )
    }

class ChatBubbleShape(
    private val isOwn: Boolean,
    private val showTail: Boolean,
    private val cornerRadius: Dp = 10.dp,
    private val tailWidth: Dp = ChatBubbleTail
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { cornerRadius.toPx() }
        val t = with(density) { tailWidth.toPx() }
        val c = with(density) { 2.dp.toPx() }
        val w = size.width
        val h = size.height
        val left = if (isOwn) 0f else t
        val right = if (isOwn) w - t else w
        val corner = CornerRadius(r)
        val body = Path().apply {
            addRoundRect(
                RoundRect(
                    left = left,
                    top = 0f,
                    right = right,
                    bottom = h,
                    topLeftCornerRadius = if (!isOwn && showTail) CornerRadius.Zero else corner,
                    topRightCornerRadius = if (isOwn && showTail) CornerRadius.Zero else corner,
                    bottomRightCornerRadius = corner,
                    bottomLeftCornerRadius = corner
                )
            )
        }
        if (!showTail) return Outline.Generic(body)
        val tailDepth = minOf(t * 1.6f, h)
        val tail = Path().apply {
            if (isOwn) {
                moveTo(right - c, 0f)
                lineTo(w - c, 0f)
                cubicTo(w, 0f, w, c * 0.6f, w - c * 0.5f, c)
                lineTo(right, tailDepth)
                lineTo(right - c, tailDepth)
            } else {
                moveTo(left + c, 0f)
                lineTo(c, 0f)
                cubicTo(0f, 0f, 0f, c * 0.6f, c * 0.5f, c)
                lineTo(left, tailDepth)
                lineTo(left + c, tailDepth)
            }
            close()
        }
        return Outline.Generic(Path.combine(PathOperation.Union, body, tail))
    }
}

data class ComposerAction(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ComposerActionsButton(
    actions: List<ComposerAction>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "composerActionsRotation"
    )
    val menuState = remember { MutableTransitionState(false) }
    menuState.targetState = expanded

    Box(modifier = modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (expanded) PrintCoral else Color.Transparent)
                .border(1.5.dp, if (expanded) PrintInk else Color.Transparent, CircleShape)
                .clickable { onExpandedChange(!expanded) },
            contentAlignment = Alignment.Center
        ) {
            if (busy && !expanded) {
                CollabSpinner(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = PrintInk)
            } else {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (expanded) "Close attachments" else "Open attachments",
                    tint = PrintInk,
                    modifier = Modifier.size(24.dp).rotate(rotation)
                )
            }
        }

        if (menuState.currentState || menuState.targetState) {
            val gap = with(androidx.compose.ui.platform.LocalDensity.current) { 52.dp.roundToPx() }
            Popup(
                alignment = Alignment.BottomStart,
                offset = IntOffset(0, -gap),
                onDismissRequest = { onExpandedChange(false) },
                properties = PopupProperties(focusable = true)
            ) {
                AnimatedVisibility(
                    visibleState = menuState,
                    enter = fadeIn(tween(160)) +
                        scaleIn(initialScale = 0.85f, transformOrigin = TransformOrigin(0f, 1f), animationSpec = tween(200)) +
                        expandVertically(expandFrom = Alignment.Bottom, animationSpec = tween(220)),
                    exit = fadeOut(tween(140)) +
                        scaleOut(targetScale = 0.9f, transformOrigin = TransformOrigin(0f, 1f), animationSpec = tween(160)) +
                        shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = tween(180))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(end = 4.dp, bottom = 4.dp)
                            .width(204.dp)
                            .printSurface(shape = RoundedCornerShape(20.dp))
                            .padding(vertical = 8.dp)
                    ) {
                        actions.forEachIndexed { index, action ->
                            val delay = (actions.lastIndex - index) * 45
                            Row(
                                modifier = Modifier
                                    .animateEnterExit(
                                        enter = fadeIn(tween(180, delayMillis = delay)) +
                                            slideInVertically(tween(220, delayMillis = delay)) { it / 2 },
                                        exit = fadeOut(tween(100)) + slideOutVertically(tween(120)) { it / 3 }
                                    )
                                    .fillMaxWidth()
                                    .clickable(enabled = action.enabled) {
                                        onExpandedChange(false)
                                        action.onClick()
                                    }
                                    .heightIn(min = 52.dp)
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .printSurface(
                                            shape = CircleShape,
                                            fill = if (action.enabled) printAccentFor(action.tint) else PrintPaper,
                                            shadow = null,
                                            outline = 1.5.dp
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = action.icon,
                                        contentDescription = null,
                                        tint = if (action.enabled) PrintInk else Muted,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Text(
                                    text = action.label,
                                    style = PrintType.Body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                    color = if (action.enabled) PrintInk else Muted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ComposerEmojiPanel(
    visible: Boolean,
    onEmoji: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnEmoji by rememberUpdatedState(onEmoji)
    BackHandler(enabled = visible, onBack = onClose)
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(240)) + fadeIn(tween(200)),
        exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(200)) + fadeOut(tween(150)),
        modifier = modifier
    ) {
        AndroidView(
            factory = { ctx ->
                androidx.emoji2.emojipicker.EmojiPickerView(ctx).apply {
                    emojiGridColumns = 8
                    setOnEmojiPickedListener { item -> latestOnEmoji(item.emoji) }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(PrintWhite)
                .border(1.5.dp, PrintInk, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
        )
    }
}

fun TextFieldValue.insertAtCursor(insert: String): TextFieldValue {
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    return TextFieldValue(
        text = text.replaceRange(start, end, insert),
        selection = TextRange(start + insert.length)
    )
}
