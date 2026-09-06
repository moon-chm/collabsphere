package com.example.rohit_project_challlange.view

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rohit_project_challlange.ui.theme.*
import com.example.rohit_project_challlange.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onNavigateToRegister: () -> Unit
) {
    // ── All original state & logic preserved exactly ──
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val loginStatus by viewModel.loginStatus.collectAsStateWithLifecycle()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var lastClickTime by remember { mutableStateOf(0L) }

    val isFormValid = email.isNotBlank() && password.isNotBlank()

    LaunchedEffect(loginStatus) {
        loginStatus?.let {
            isLoading = false
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearLoginStatus()
        }
    }

    val handleLogin = {
        val currentTime = System.currentTimeMillis()
        if (isFormValid && currentTime - lastClickTime > 500L) {
            lastClickTime = currentTime
            isLoading = true
            focusManager.clearFocus()
            viewModel.onLoginClick(email, password)
        }
    }

    // ── Skeuomorphic shell ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .systemBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            // ── Brand mark ──
            SkeuoBrandMark(letter = "C", accentColor = CoralStart)

            Spacer(Modifier.height(20.dp))

            // ── Headline ──
            Text(
                text = "Welcome back",
                style = MaterialTheme.typography.displaySmall,
                color = Ink,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Sign in to access your workspaces",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(36.dp))

            // ── Form Card ──
            SkeuoFormCard {
                // Email field
                SkeuoTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email address",
                    placeholder = "name@example.com",
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Email, null,
                            tint = CoralStart,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        AnimatedVisibility(email.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                            IconButton(onClick = { email = "" }) {
                                Icon(Icons.Outlined.Clear, "Clear", tint = Muted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                )

                Spacer(Modifier.height(14.dp))

                // Password field
                SkeuoTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    placeholder = "••••••••",
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    onImeAction = { handleLogin() },
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Lock, null,
                            tint = CoralStart,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.Visibility
                                              else Icons.Outlined.VisibilityOff,
                                contentDescription = if (passwordVisible) "Hide" else "Show",
                                tint = Muted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )

                Spacer(Modifier.height(22.dp))

                // CTA Button
                SkeuoPrimaryButton(
                    text = "Sign in",
                    isLoading = isLoading,
                    enabled = isFormValid && !isLoading,
                    accentColor = CoralStart,
                    onClick = { handleLogin() }
                )
            }

            Spacer(Modifier.height(28.dp))

            // ── Footer ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No account yet?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Register",
                    style = MaterialTheme.typography.labelLarge,
                    color = IndigoStart,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            val now = System.currentTimeMillis()
                            if (now - lastClickTime > 500L) {
                                lastClickTime = now
                                onNavigateToRegister()
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// SHARED SKEUOMORPHIC COMPONENTS (used by Login + Register)
// ─────────────────────────────────────────────────────────────────

/** Raised circular brand mark with letter and dual shadow */
@Composable
internal fun SkeuoBrandMark(letter: String, accentColor: Color) {
    Box(
        modifier = Modifier
            .size(84.dp)
            .drawBehind {
                // dark shadow
                drawCircle(
                    color  = ShadowDark.copy(alpha = 0.4f),
                    radius = size.minDimension / 2f,
                    center = Offset(center.x + 4.dp.toPx(), center.y + 5.dp.toPx())
                )
                // light specular
                drawCircle(
                    color  = ShadowLight.copy(alpha = 0.9f),
                    radius = size.minDimension / 2f,
                    center = Offset(center.x - 3.dp.toPx(), center.y - 3.dp.toPx())
                )
            }
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(SurfaceRaised, Surface),
                    start  = Offset(0f, 0f),
                    end    = Offset(84.dp.value, 84.dp.value)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Accent sphere inside
        Box(
            modifier = Modifier
                .size(52.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accentColor, accentColor.copy(alpha = 0.7f)),
                            center = Offset(center.x - 6.dp.toPx(), center.y - 6.dp.toPx()),
                            radius = size.minDimension / 2f
                        )
                    )
                    // specular
                    drawCircle(
                        color  = Color.White.copy(alpha = 0.35f),
                        radius = 8.dp.toPx(),
                        center = Offset(center.x - 10.dp.toPx(), center.y - 10.dp.toPx())
                    )
                }
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text  = letter,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
        }
    }
}

/** Raised card wrapping the form fields */
@Composable
internal fun SkeuoFormCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // dark shadow
                drawRoundRect(
                    color        = ShadowDark.copy(alpha = 0.3f),
                    topLeft      = Offset(6.dp.toPx(), 8.dp.toPx()),
                    size         = Size(size.width, size.height),
                    cornerRadius = CornerRadius(24.dp.toPx())
                )
                // light specular
                drawRoundRect(
                    color        = ShadowLight.copy(alpha = 0.85f),
                    topLeft      = Offset(-4.dp.toPx(), -4.dp.toPx()),
                    size         = Size(size.width, size.height),
                    cornerRadius = CornerRadius(24.dp.toPx())
                )
                // card fill
                drawRoundRect(
                    color        = SurfaceRaised,
                    cornerRadius = CornerRadius(24.dp.toPx())
                )
                // top hairline bevel hugging rounded contour
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.7f),
                            Color.White.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 24.dp.toPx()
                    ),
                    topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                    size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            content  = content
        )
    }
}

/** Inset text field with skeuomorphic deboss effect */
@Composable
internal fun SkeuoTextField(
    value               : String,
    onValueChange       : (String) -> Unit,
    label               : String,
    placeholder         : String,
    keyboardType        : KeyboardType         = KeyboardType.Text,
    imeAction           : ImeAction            = ImeAction.Next,
    onImeAction         : () -> Unit           = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon         : (@Composable () -> Unit)? = null,
    trailingIcon        : (@Composable () -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelMedium,
            color = Muted
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .drawBehind {
                    // inset dark top-left
                    drawRoundRect(
                        color        = ShadowDark.copy(alpha = 0.2f),
                        topLeft      = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                        size         = Size(size.width - 1.5.dp.toPx(), size.height - 1.5.dp.toPx()),
                        cornerRadius = CornerRadius(14.dp.toPx())
                    )
                    // inset light bottom-right rim
                    drawRoundRect(
                        color        = ShadowLight.copy(alpha = 0.8f),
                        topLeft      = Offset(-1.dp.toPx(), -1.dp.toPx()),
                        size         = Size(size.width + 1.dp.toPx(), size.height + 1.dp.toPx()),
                        cornerRadius = CornerRadius(14.dp.toPx())
                    )
                    // field background
                    drawRoundRect(
                        color        = Background.copy(alpha = 0.8f),
                        cornerRadius = CornerRadius(14.dp.toPx())
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier          = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(10.dp))
                }
                BasicTextField_Compat(
                    value                = value,
                    onValueChange        = onValueChange,
                    placeholder          = placeholder,
                    keyboardType         = keyboardType,
                    imeAction            = imeAction,
                    onImeAction          = onImeAction,
                    visualTransformation = visualTransformation,
                    modifier             = Modifier.weight(1f)
                )
                if (trailingIcon != null) {
                    trailingIcon()
                }
            }
        }
    }
}

/** Thin wrapper around BasicTextField for clean typography */
@Composable
private fun BasicTextField_Compat(
    value               : String,
    onValueChange       : (String) -> Unit,
    placeholder         : String,
    keyboardType        : KeyboardType,
    imeAction           : ImeAction,
    onImeAction         : () -> Unit,
    visualTransformation: VisualTransformation,
    modifier            : Modifier
) {
    androidx.compose.foundation.text.BasicTextField(
        value                  = value,
        onValueChange          = onValueChange,
        modifier               = modifier,
        singleLine             = true,
        textStyle              = MaterialTheme.typography.bodyLarge.copy(color = Ink),
        visualTransformation   = visualTransformation,
        keyboardOptions        = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction    = imeAction
        ),
        keyboardActions        = KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() }
        ),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text  = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Muted.copy(alpha = 0.6f)
                    )
                }
                innerTextField()
            }
        }
    )
}

/** Skeuomorphic primary CTA button — Coral gradient with dual shadow & press scale */
@Composable
internal fun SkeuoPrimaryButton(
    text       : String,
    isLoading  : Boolean,
    enabled    : Boolean,
    accentColor: Color,
    onClick    : () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.96f else 1f,
        animationSpec = tween(80),
        label         = "btnScale"
    )

    val activeColor  = if (enabled) accentColor else Muted.copy(alpha = 0.4f)
    val shadowAlpha  = if (pressed) 0.12f else 0.30f
    val shadowOffset = if (pressed) 2.dp else 5.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                // dark shadow
                drawRoundRect(
                    color        = activeColor.copy(alpha = shadowAlpha),
                    topLeft      = Offset(0f, shadowOffset.toPx()),
                    size         = Size(size.width, size.height),
                    cornerRadius = CornerRadius(16.dp.toPx())
                )
                // light specular
                drawRoundRect(
                    color        = Color.White.copy(alpha = 0.25f),
                    topLeft      = Offset(-2.dp.toPx(), -2.dp.toPx()),
                    size         = Size(size.width, size.height),
                    cornerRadius = CornerRadius(16.dp.toPx())
                )
                // gradient fill
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(activeColor, activeColor.copy(alpha = 0.80f)),
                        start  = Offset(0f, 0f),
                        end    = Offset(size.width, size.height)
                    ),
                    cornerRadius = CornerRadius(16.dp.toPx())
                )
                // top hairline hugging rounded contour
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.35f),
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 16.dp.toPx()
                    ),
                    topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                    size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                enabled           = enabled && !isLoading,
                interactionSource = remember { MutableInteractionSource() },
                indication        = null
            ) {
                pressed = true
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier    = Modifier.size(22.dp),
                color       = Color.White,
                strokeWidth = 2.5.dp
            )
        } else {
            Text(
                text  = text,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                color = Color.White
            )
        }
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(120)
            pressed = false
        }
    }
}