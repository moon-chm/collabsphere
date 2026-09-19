package com.example.rohit_project_challlange.view

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
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
        } else if (!isFormValid) {
            Toast.makeText(context, "Please enter your email and password", Toast.LENGTH_SHORT).show()
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
                val r = size.minDimension / 2f
                // 1. Soft deep ambient drop shadow underneath
                drawCircle(
                    color  = Color(0xFF2C201A).copy(alpha = 0.12f),
                    radius = r,
                    center = Offset(center.x + 3.dp.toPx(), center.y + 6.dp.toPx())
                )
                // 2. Contact drop shadow
                drawCircle(
                    color  = Color(0xFF2C201A).copy(alpha = 0.15f),
                    radius = r,
                    center = Offset(center.x + 1.5.dp.toPx(), center.y + 3.dp.toPx())
                )
                // 3. Top-left specular halo
                drawCircle(
                    color  = Color.White.copy(alpha = 0.95f),
                    radius = r,
                    center = Offset(center.x - 2.5.dp.toPx(), center.y - 2.5.dp.toPx())
                )
                // 4. Medallion disk body
                drawCircle(
                    color = SurfaceRaised,
                    radius = r
                )
                // 5. Specular rim highlight hugging top edge
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.85f),
                            Color.White.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.6f
                    ),
                    radius = r - 0.5.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Accent sphere inside
        Box(
            modifier = Modifier
                .size(52.dp)
                .drawBehind {
                    val r = size.minDimension / 2f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accentColor, accentColor.copy(alpha = 0.75f)),
                            center = Offset(center.x - 6.dp.toPx(), center.y - 6.dp.toPx()),
                            radius = r
                        )
                    )
                    // Specular spot reflection
                    drawCircle(
                        color  = Color.White.copy(alpha = 0.40f),
                        radius = 8.dp.toPx(),
                        center = Offset(center.x - 10.dp.toPx(), center.y - 10.dp.toPx())
                    )
                }
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text  = letter,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
    }
}

/** Raised card wrapping the form fields using the centralized skeuoFloatingCard modifier */
@Composable
internal fun SkeuoFormCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .skeuoFloatingCard(cornerRadius = 24.dp, surfaceColor = SurfaceRaised)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            content  = content
        )
    }
}

/** Inset text field using the centralized skeuoInset debossed cavity modifier */
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
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Ink.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .skeuoInset(cornerRadius = 14.dp, depth = 2.dp)
                .clip(RoundedCornerShape(14.dp)),
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
        cursorBrush            = androidx.compose.ui.graphics.SolidColor(CoralStart),
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
                        color = Ink.copy(alpha = 0.65f)
                    )
                }
                innerTextField()
            }
        }
    )
}

/** Skeuomorphic primary CTA button — Rich Coral gradient with dual shadow & spring press scale */
@Composable
internal fun SkeuoPrimaryButton(
    text       : String,
    isLoading  : Boolean,
    enabled    : Boolean,
    accentColor: Color,
    onClick    : () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(isPressed) {
        if (isPressed) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label         = "btnScale"
    )

    // True Coral (or Indigo) gradient tokens matching DESIGN.md
    val isCoral = (accentColor == CoralStart)
    val gradientStart = if (isCoral) CoralStart else IndigoStart
    val gradientEnd   = if (isCoral) CoralEnd else IndigoEnd

    val shadowColor  = gradientStart
    val shadowAlpha  = if (isPressed) 0.15f else if (enabled) 0.38f else 0.20f
    val shadowOffset = if (isPressed) 2.dp else 5.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                val cr = CornerRadius(16.dp.toPx())
                // 1. Colored ambient drop shadow underneath button
                drawRoundRect(
                    color        = shadowColor.copy(alpha = shadowAlpha),
                    topLeft      = Offset(0f, shadowOffset.toPx()),
                    size         = Size(size.width, size.height),
                    cornerRadius = cr
                )
                // 2. Top-left specular halo
                drawRoundRect(
                    color        = Color.White.copy(alpha = 0.35f),
                    topLeft      = Offset(-1.5.dp.toPx(), -1.5.dp.toPx()),
                    size         = Size(size.width, size.height),
                    cornerRadius = cr
                )
                // 3. True Coral (or Indigo) gradient fill — always vibrant brand tone
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = if (enabled) {
                            listOf(gradientStart, gradientEnd)
                        } else {
                            listOf(
                                gradientStart.copy(alpha = 0.72f),
                                gradientEnd.copy(alpha = 0.72f)
                            )
                        },
                        start  = Offset(0f, 0f),
                        end    = Offset(size.width, size.height)
                    ),
                    cornerRadius = cr
                )
                // 4. Top hairline highlight hugging rounded contour
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.45f),
                            Color.White.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 16.dp.toPx()
                    ),
                    topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                    size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                    cornerRadius = cr,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                enabled           = !isLoading,
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick
            ),
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
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color.White
            )
        }
    }
}