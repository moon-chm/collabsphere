package com.collabsphere.app.view.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.collabsphere.app.ui.theme.*

/** Inner-shadow strength shared by every text field: a soft, even carve-in (1f is the full card-inset depth). */
const val FieldInsetShadow = 0.77f

@Composable
fun SkeuoTextField(
    value               : String,
    onValueChange       : (String) -> Unit,
    label               : String?              = null,
    placeholder         : String               = "",
    modifier            : Modifier             = Modifier,
    keyboardType        : KeyboardType         = KeyboardType.Text,
    imeAction           : ImeAction            = ImeAction.Next,
    onImeAction         : () -> Unit           = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon         : (@Composable () -> Unit)? = null,
    trailingIcon        : (@Composable () -> Unit)? = null,
    isError             : Boolean              = false,
    errorMessage        : String?              = null,
    singleLine          : Boolean              = true,
    minLines            : Int                  = 1,
    maxLines            : Int                  = if (singleLine) 1 else 4,
    height              : Dp?                  = null,
    cornerRadius        : Dp                   = 27.dp,
    readOnly            : Boolean              = false,
    enabled             : Boolean              = true
) {
    var isFocused by remember { mutableStateOf(false) }

    val focusRingAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "focusRing"
    )
    val ringColor = if (isError) DestructiveStart else CoralStart
    val ringAlpha = if (isError) 1f else focusRingAlpha

    Column(modifier = modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .padding(start = if (cornerRadius > 18.dp) 16.dp else 8.dp)
                    .drawWithCache {
                        val cr = 20.dp.toPx()
                        onDrawBehind {
                            drawRoundRect(
                                color = Color(0xFF2C201A).copy(alpha = 0.06f),
                                topLeft = Offset(0f, 1.5.dp.toPx()),
                                size = size,
                                cornerRadius = CornerRadius(cr)
                            )
                            drawRoundRect(
                                color = Color(0xFFF0EBE3),
                                cornerRadius = CornerRadius(cr)
                            )
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.80f),
                                        Color.Transparent
                                    ),
                                    startY = 0f,
                                    endY = cr
                                ),
                                topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                                size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                                cornerRadius = CornerRadius(cr),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text  = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 10.sp,
                        letterSpacing = 0.3.sp
                    ),
                    color = if (isError) DestructiveStart
                    else if (isFocused) CoralStart
                    else Ink.copy(alpha = 0.60f)
                )
            }
            Spacer(Modifier.height(5.dp))
        }

        val containerModifier = Modifier
            .fillMaxWidth()
            .then(
                if (height != null) Modifier.height(height)
                else if (singleLine) Modifier.height(54.dp)
                else Modifier.heightIn(min = 54.dp)
            )
            .drawWithCache {
                val cr = cornerRadius.toPx()
                onDrawBehind {
                    if (ringAlpha > 0f) {
                        drawRoundRect(
                            color = ringColor.copy(alpha = 0.55f * ringAlpha),
                            topLeft = Offset(-2.dp.toPx(), -2.dp.toPx()),
                            size = Size(
                                size.width + 4.dp.toPx(),
                                size.height + 4.dp.toPx()
                            ),
                            cornerRadius = CornerRadius(cr + 2.dp.toPx()),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
            }
            .skeuoInset(cornerRadius = cornerRadius, depth = 3.dp, symmetric = true, shadowStrength = FieldInsetShadow)
            .clip(RoundedCornerShape(cornerRadius))

        Box(
            modifier = containerModifier,
            contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 0.dp,
                        end = 4.dp,
                        top = if (singleLine) 0.dp else 12.dp,
                        bottom = if (singleLine) 0.dp else 12.dp
                    ),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
            ) {
                if (leadingIcon != null) {
                    Spacer(Modifier.width(if (cornerRadius > 18.dp) 18.dp else 12.dp))
                    Box(modifier = Modifier.padding(top = if (singleLine) 0.dp else 2.dp)) {
                        leadingIcon()
                    }
                    Spacer(Modifier.width(10.dp))
                } else {
                    Spacer(Modifier.width(if (cornerRadius > 18.dp) 20.dp else 14.dp))
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    readOnly = readOnly,
                    enabled = enabled,
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = maxLines,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                    cursorBrush = SolidColor(CoralStart),
                    visualTransformation = visualTransformation,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = imeAction
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { onImeAction() },
                        onDone = { onImeAction() }
                    ),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart) {
                            if (value.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Ink.copy(alpha = 0.40f)
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { isFocused = it.isFocused }
                )

                if (trailingIcon != null) {
                    trailingIcon()
                } else {
                    Spacer(Modifier.width(if (cornerRadius > 18.dp) 16.dp else 10.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = isError && !errorMessage.isNullOrBlank(),
            enter = fadeIn(tween(180)) + expandVertically(),
            exit  = fadeOut(tween(120)) + shrinkVertically()
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = DestructiveStart,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text  = errorMessage ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = DestructiveStart
                )
            }
        }
    }
}
