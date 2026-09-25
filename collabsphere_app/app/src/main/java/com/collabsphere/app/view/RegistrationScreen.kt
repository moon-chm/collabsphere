package com.collabsphere.app.view

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.collabsphere.app.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.collabsphere.app.ui.theme.*
import com.collabsphere.app.viewmodel.LoginViewModel

@Composable
fun RegistrationScreen(
    viewModel: LoginViewModel,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val loginStatus by viewModel.loginStatus.collectAsStateWithLifecycle()
    val registrationSuccessEmail by viewModel.registrationSuccessEmail.collectAsStateWithLifecycle()
    val verificationStatus by viewModel.verificationStatus.collectAsStateWithLifecycle()
    val isVerificationSuccess by viewModel.isVerificationSuccess.collectAsStateWithLifecycle()

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var lastClickTime by remember { mutableStateOf(0L) }

    val isFormValid = email.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    LaunchedEffect(loginStatus) {
        loginStatus?.let {
            isLoading = false
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearLoginStatus()
        }
    }

    LaunchedEffect(verificationStatus) {
        verificationStatus?.let {
            isLoading = false
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearVerificationStatus()
        }
    }

    LaunchedEffect(isVerificationSuccess) {
        if (isVerificationSuccess) {
            isLoading = false
            Toast.makeText(context, "Account verified! You can now log in.", Toast.LENGTH_LONG).show()
            viewModel.resetVerificationSuccess()
            viewModel.clearRegistrationEmail()
            onNavigateToLogin()
        }
    }

    val handleRegister = {
        val currentTime = System.currentTimeMillis()
        if (isFormValid && currentTime - lastClickTime > 500L) {
            lastClickTime = currentTime
            isLoading = true
            focusManager.clearFocus()
            viewModel.onRegisterClick(email, username, password)
        }
    }

    val handleVerify = {
        val currentTime = System.currentTimeMillis()
        val targetEmail = registrationSuccessEmail ?: email
        if (otpCode.trim().length == 6 && currentTime - lastClickTime > 500L) {
            lastClickTime = currentTime
            isLoading = true
            focusManager.clearFocus()
            viewModel.onVerifyRegistration(targetEmail, otpCode.trim())
        } else if (otpCode.trim().length != 6) {
            Toast.makeText(context, "Please enter all 6 digits", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Neumorphic Wave Shell ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .systemBarsPadding()
            .imePadding()
    ) {
        // Inner wave shape
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp) // Leave space at bottom for social/footer
                .skeuoWaveBackground()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            if (registrationSuccessEmail != null) {
                // ── VERIFY EMAIL OTP FLOW ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.collabsphere),
                        contentDescription = "CollabSphere Logo",
                        modifier = Modifier.size(64.dp)
                    )
                }
                Spacer(Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Verify",
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                        color = Ink
                    )
                    Text(
                        text = "Account.",
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                        color = Ink
                    )
                }

                Spacer(Modifier.height(48.dp))

                SkeuoTextField(
                    value = otpCode,
                    onValueChange = { if (it.length <= 6) otpCode = it.filter { ch -> ch.isDigit() } },
                    label = "Verification Code",
                    placeholder = "",
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                    onImeAction = { handleVerify() },
                    leadingIcon = null,
                    trailingIcon = {
                        AnimatedVisibility(otpCode.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                            IconButton(onClick = { otpCode = "" }) {
                                Icon(Icons.Outlined.Clear, "Clear", tint = Muted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                )

                Spacer(Modifier.height(36.dp))

                SkeuoPrimaryButton(
                    text = "Verify & Activate",
                    isLoading = isLoading,
                    enabled = otpCode.trim().length == 6 && !isLoading,
                    accentColor = MintGreen,
                    onClick = { handleVerify() }
                )

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            viewModel.onResendVerification(registrationSuccessEmail!!)
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Refresh, null, tint = IndigoStart, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Resend Code",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = IndigoStart
                    )
                }

                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Wrong email?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Change details",
                        style = MaterialTheme.typography.labelLarge,
                        color = CoralStart,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                viewModel.clearRegistrationEmail()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            } else {
                // ── REGISTRATION FORM FLOW ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.collabsphere),
                        contentDescription = "CollabSphere Logo",
                        modifier = Modifier.size(64.dp)
                    )
                }
                Spacer(Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Create",
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                        color = Ink
                    )
                    Text(
                        text = "Account.",
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                        color = Ink
                    )
                }

                Spacer(Modifier.height(48.dp))

                // Username field
                SkeuoTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "Username",
                    placeholder = "",
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                    onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    leadingIcon = null,
                    trailingIcon = {
                        AnimatedVisibility(username.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                            IconButton(onClick = { username = "" }) {
                                Icon(Icons.Outlined.Clear, "Clear", tint = Muted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                )

                Spacer(Modifier.height(24.dp))

                // Email field
                SkeuoTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email address",
                    placeholder = "",
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    leadingIcon = null,
                    trailingIcon = {
                        AnimatedVisibility(email.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                            IconButton(onClick = { email = "" }) {
                                Icon(Icons.Outlined.Clear, "Clear", tint = Muted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                )

                Spacer(Modifier.height(24.dp))

                // Password field
                SkeuoTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    placeholder = "",
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    onImeAction = { handleRegister() },
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    leadingIcon = null,
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

                Spacer(Modifier.height(36.dp))

                SkeuoPrimaryButton(
                    text = "Register",
                    isLoading = isLoading,
                    enabled = isFormValid && !isLoading,
                    accentColor = IndigoStart,
                    onClick = { handleRegister() }
                )

                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(48.dp))
                
                // ── Footer ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Divider(modifier = Modifier.weight(1f).padding(horizontal = 16.dp), color = Muted.copy(alpha = 0.2f))
                    Text("or", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Divider(modifier = Modifier.weight(1f).padding(horizontal = 16.dp), color = Muted.copy(alpha = 0.2f))
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Already have an account?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Log in",
                        style = MaterialTheme.typography.labelLarge,
                        color = CoralStart,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onNavigateToLogin() }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}