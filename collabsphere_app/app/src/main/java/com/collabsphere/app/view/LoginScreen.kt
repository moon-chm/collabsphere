package com.collabsphere.app.view

import androidx.compose.animation.AnimatedVisibility
import com.collabsphere.app.view.components.AppToast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.core.tween as tweenAnim
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.collabsphere.app.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.ui.window.Dialog
import com.collabsphere.app.ui.theme.*
import com.collabsphere.app.viewmodel.LoginViewModel
import com.collabsphere.app.view.components.CollabSpinner

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onNavigateToRegister: () -> Unit
) {
    // ── All original state & logic preserved exactly ──
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val loginStatus by viewModel.loginStatus.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingLogin.collectAsStateWithLifecycle()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var lastClickTime by remember { mutableStateOf(0L) }
    var hasAttemptedSubmit by remember { mutableStateOf(false) }

    val isEmailError = hasAttemptedSubmit && email.isBlank()
    val isPasswordError = hasAttemptedSubmit && password.isBlank()
    val isFormValid = email.isNotBlank() && password.isNotBlank()

    LaunchedEffect(email, password) {
        if (!loginStatus.isNullOrBlank()) {
            viewModel.clearLoginStatus()
        }
    }

    val unverifiedEmailForLogin by viewModel.unverifiedEmailForLogin.collectAsStateWithLifecycle()
    val forgotPasswordStatus by viewModel.forgotPasswordStatus.collectAsStateWithLifecycle()
    val forgotPasswordStep by viewModel.forgotPasswordStep.collectAsStateWithLifecycle()
    val isResetPasswordSuccess by viewModel.isResetPasswordSuccess.collectAsStateWithLifecycle()
    val verificationStatus by viewModel.verificationStatus.collectAsStateWithLifecycle()
    val isVerificationSuccess by viewModel.isVerificationSuccess.collectAsStateWithLifecycle()
    val isLoadingEmail by viewModel.isLoadingEmail.collectAsStateWithLifecycle()
    val isVerifyingRegistration by viewModel.isVerifyingRegistration.collectAsStateWithLifecycle()

    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var showVerifyEmailDialog by remember { mutableStateOf(false) }

    LaunchedEffect(unverifiedEmailForLogin) {
        if (unverifiedEmailForLogin != null) {
            showVerifyEmailDialog = true
        }
    }

    LaunchedEffect(forgotPasswordStatus) {
        forgotPasswordStatus?.let {
            AppToast.show(it)
        }
    }

    LaunchedEffect(isResetPasswordSuccess) {
        if (isResetPasswordSuccess) {
            showForgotPasswordDialog = false
            viewModel.clearForgotPasswordState()
            AppToast.show("Password reset successfully! Please sign in.", long = true)
        }
    }

    LaunchedEffect(verificationStatus) {
        verificationStatus?.let {
            AppToast.show(it)
            viewModel.clearVerificationStatus()
        }
    }

    LaunchedEffect(isVerificationSuccess) {
        if (isVerificationSuccess) {
            showVerifyEmailDialog = false
            viewModel.resetVerificationSuccess()
            AppToast.show("Email verified! You can now log in.", long = true)
        }
    }

    val handleLogin = {
        hasAttemptedSubmit = true
        val currentTime = System.currentTimeMillis()
        if (isFormValid && currentTime - lastClickTime > 300L) {
            lastClickTime = currentTime
            focusManager.clearFocus()
            viewModel.onLoginClick(email, password)
        }
    }

    val formScroll = rememberScrollState()
    val formScrollEnabled = rememberKeyboardOnlyScroll(formScroll)

    // ── Neumorphic Wave Shell ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PrintPaper)
            .paperGrain()
            .systemBarsPadding()
    ) {
        AuthBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(formScroll, enabled = formScrollEnabled)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CollabSphereMark(size = 40.dp)
                Text(text = "CollabSphere", style = PrintType.H3, color = PrintInk)
            }

            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                MisregisteredTitle(text = "Welcome", style = PrintType.Hero.copy(fontSize = 44.sp, lineHeight = 48.sp))
                MisregisteredTitle(text = "back.", style = PrintType.Hero.copy(fontSize = 44.sp, lineHeight = 48.sp))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Pick up right where your team left off.",
                    style = PrintType.Body,
                    color = Muted
                )
            }

            Spacer(Modifier.height(32.dp))

            SkeuoTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email address",
                placeholder = "Your email address",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                isError = isEmailError,
                errorMessage = "Email cannot be empty",
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Email,
                        contentDescription = null,
                        tint = PrintInk,
                        modifier = Modifier.size(18.dp)
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

            Spacer(Modifier.height(24.dp))

            SkeuoTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                placeholder = "Your password",
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                onImeAction = { handleLogin() },
                visualTransformation = if (passwordVisible) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                isError = isPasswordError,
                errorMessage = "Password cannot be empty",
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = PrintInk,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Outlined.Visibility
                                          else Icons.Outlined.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide" else "Show",
                            tint = PrintInk,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Forgot password?",
                    style = PrintType.Label.copy(fontSize = 14.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                    color = PrintInk,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showForgotPasswordDialog = true
                        }
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 4.dp, vertical = 12.dp)
                )
            }

            AnimatedVisibility(
                visible = !loginStatus.isNullOrBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    text = loginStatus.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = PrintRedDark,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 4.dp)
                )
            }

            Spacer(Modifier.height(28.dp))

            SkeuoPrimaryButton(
                text = "Login",
                isLoading = isLoading,
                enabled = !isLoading,
                accentColor = PrintInk,
                onClick = { handleLogin() }
            )

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Divider(modifier = Modifier.weight(1f).padding(horizontal = 16.dp), color = PrintInk.copy(alpha = 0.2f), thickness = 1.5.dp)
                Text("or", style = PrintType.Caption, color = Muted)
                Divider(modifier = Modifier.weight(1f).padding(horizontal = 16.dp), color = PrintInk.copy(alpha = 0.2f), thickness = 1.5.dp)
            }

            Spacer(Modifier.height(16.dp))

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
                    style = PrintType.Label.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                    color = PrintInk,
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

        if (showForgotPasswordDialog) {
            ForgotPasswordDialog(
                initialEmail = email,
                step = forgotPasswordStep,
                isLoading = isLoadingEmail || isVerifyingRegistration,
                onDismiss = {
                    showForgotPasswordDialog = false
                    viewModel.clearForgotPasswordState()
                },
                onRequestCode = { targetEmail ->
                    viewModel.onForgotPasswordRequest(targetEmail)
                },
                onResetPassword = { targetEmail, otp, newPass, confirmPass ->
                    viewModel.onResetPasswordSubmit(targetEmail, otp, newPass, confirmPass)
                }
            )
        }

        if (showVerifyEmailDialog) {
            val targetEmail = unverifiedEmailForLogin ?: email
            VerifyEmailDialog(
                email = targetEmail,
                isLoading = isLoadingEmail,
                onDismiss = {
                    showVerifyEmailDialog = false
                    viewModel.clearUnverifiedEmailForLogin()
                },
                onVerify = { otp ->
                    viewModel.onVerifyRegistration(targetEmail, otp)
                },
                onResend = {
                    viewModel.onResendVerification(targetEmail)
                }
            )
        }
    }
}

@Composable
fun ForgotPasswordDialog(
    initialEmail: String,
    step: Int,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onRequestCode: (String) -> Unit,
    onResetPassword: (email: String, otp: String, pass: String, confirmPass: String) -> Unit
) {
    var emailInput by remember { mutableStateOf(initialEmail) }
    var otpInput by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var newPasswordVisible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .skeuoFloatingCard(cornerRadius = 24.dp, surfaceColor = SurfaceRaised)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (step == 1) "Reset password" else "Set new password",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Ink
                )

                Text(
                    text = if (step == 1)
                        "Enter your registered email address and we'll send a 6-digit reset code."
                    else
                        "Enter the 6-digit code sent to $emailInput along with your new password.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    textAlign = TextAlign.Center
                )

                if (step == 1) {
                    SkeuoTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = "Email address",
                        placeholder = "",
                        keyboardType = KeyboardType.Email,
                        leadingIcon = {
                            Icon(Icons.Outlined.Email, null, tint = PrintInk, modifier = Modifier.size(20.dp))
                        }
                    )

                    Spacer(Modifier.height(8.dp))

                    SkeuoPrimaryButton(
                        text = "Send Reset Code",
                        isLoading = isLoading,
                        enabled = !isLoading,
                        accentColor = PrintInk,
                        onClick = { onRequestCode(emailInput.trim()) }
                    )
                } else {
                    SkeuoTextField(
                        value = otpInput,
                        onValueChange = { if (it.length <= 6) otpInput = it.filter { ch -> ch.isDigit() } },
                        label = "6-Digit Code",
                        placeholder = "123456",
                        keyboardType = KeyboardType.Number,
                        leadingIcon = {
                            Icon(Icons.Outlined.Lock, null, tint = PrintInk, modifier = Modifier.size(20.dp))
                        }
                    )

                    SkeuoTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = "New Password",
                        placeholder = "At least 6 characters",
                        keyboardType = KeyboardType.Password,
                        visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(Icons.Outlined.Lock, null, tint = PrintInk, modifier = Modifier.size(20.dp))
                        }
                    )

                    SkeuoTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = "Confirm Password",
                        placeholder = "Re-enter new password",
                        keyboardType = KeyboardType.Password,
                        visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(Icons.Outlined.Lock, null, tint = PrintInk, modifier = Modifier.size(20.dp))
                        }
                    )

                    Spacer(Modifier.height(8.dp))

                    SkeuoPrimaryButton(
                        text = "Reset Password",
                        isLoading = isLoading,
                        enabled = !isLoading,
                        accentColor = PrintInk,
                        onClick = { onResetPassword(emailInput.trim(), otpInput.trim(), newPassword, confirmPassword) }
                    )
                }

                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onDismiss() }
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun VerifyEmailDialog(
    email: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onVerify: (String) -> Unit,
    onResend: () -> Unit
) {
    var otpInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .skeuoFloatingCard(cornerRadius = 24.dp, surfaceColor = SurfaceRaised)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Verify your email",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Ink
                )

                Text(
                    text = "Your email is not verified yet. Enter the 6-digit code sent to $email.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    textAlign = TextAlign.Center
                )

                SkeuoTextField(
                    value = otpInput,
                    onValueChange = { if (it.length <= 6) otpInput = it.filter { ch -> ch.isDigit() } },
                    label = "6-Digit Code",
                    placeholder = "123456",
                    keyboardType = KeyboardType.Number,
                    leadingIcon = {
                        Icon(Icons.Outlined.Lock, null, tint = PrintInk, modifier = Modifier.size(20.dp))
                    }
                )

                Spacer(Modifier.height(8.dp))

                SkeuoPrimaryButton(
                    text = "Verify & Sign In",
                    isLoading = isLoading,
                    enabled = !isLoading,
                    accentColor = PrintInk,
                    onClick = { onVerify(otpInput.trim()) }
                )

                Row(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !isLoading
                        ) { onResend() }
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLoading) {
                        CollabSpinner(modifier = Modifier.size(16.dp), color = PrintInk, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Refresh, null, tint = PrintInk, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Resend Code",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = PrintInk
                    )
                }

                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onDismiss() }
                        .padding(4.dp)
                )
            }
        }
    }
}

@Composable
internal fun AuthBackdrop() {
    val phase = rememberBlobPhase(9000)
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = (-50).dp)
                .size(230.dp)
                .graphicsLayer { alpha = 0.9f }
                .printSurface(shape = BlobShape(seed = 3, wobble = 0.12f), fill = PrintLeaf, shadow = null)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-40).dp, y = 120.dp)
                .size(70.dp)
                .graphicsLayer { rotationZ = phase() * 40f }
                .printSurface(shape = BlobShape(seed = 8, wobble = 0.18f), fill = PrintCoral, shadow = null)
        )
        PrintStar(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-120).dp, y = 60.dp)
                .size(22.dp),
            color = PrintInk,
            twinkle = true
        )
        HalftonePatch(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-20).dp, y = 10.dp)
                .size(160.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberKeyboardOnlyScroll(scrollState: androidx.compose.foundation.ScrollState): Boolean {
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (!imeVisible) scrollState.animateScrollTo(0)
    }
    return imeVisible || scrollState.maxValue > 0
}

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
    trailingIcon        : (@Composable () -> Unit)? = null,
    isError             : Boolean              = false,
    errorMessage        : String?              = null
) {
    com.collabsphere.app.view.components.SkeuoTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        keyboardType = keyboardType,
        imeAction = imeAction,
        onImeAction = onImeAction,
        visualTransformation = visualTransformation,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        errorMessage = errorMessage
    )
}

@Composable
internal fun SkeuoPrimaryButton(
    text       : String,
    isLoading  : Boolean = false,
    enabled    : Boolean,
    accentColor: Color,
    onClick    : () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Box(modifier = Modifier.fillMaxWidth()) {
        PrintButton(
            text = if (isLoading) "" else text,
            onClick = {
                if (!isLoading) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            },
            enabled = enabled || isLoading,
            modifier = Modifier.fillMaxWidth()
        )
        if (isLoading) {
            com.collabsphere.app.view.components.CollabSpinner(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp),
                color = PrintPaper
            )
        }
    }
}
