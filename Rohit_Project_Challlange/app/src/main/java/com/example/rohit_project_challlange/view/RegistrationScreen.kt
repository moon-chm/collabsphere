package com.example.rohit_project_challlange.view

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
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rohit_project_challlange.ui.theme.*
import com.example.rohit_project_challlange.viewmodel.LoginViewModel

@Composable
fun RegistrationScreen(
    viewModel: LoginViewModel,
    onNavigateToLogin: () -> Unit
) {
    // ── All original state & logic preserved exactly ──
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val loginStatus by viewModel.loginStatus.collectAsStateWithLifecycle()

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
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

    val handleRegister = {
        val currentTime = System.currentTimeMillis()
        if (isFormValid && currentTime - lastClickTime > 500L) {
            lastClickTime = currentTime
            isLoading = true
            focusManager.clearFocus()
            viewModel.onRegisterClick(email, username, password)
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
            Spacer(Modifier.height(20.dp))

            // ── Brand mark (Indigo for Register) ──
            SkeuoBrandMark(letter = "C", accentColor = IndigoStart)

            Spacer(Modifier.height(20.dp))

            // ── Headline ──
            Text(
                text  = "Create account",
                style = MaterialTheme.typography.displaySmall,
                color = Ink,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text  = "Join CollabSphere and start collaborating",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // ── Form Card ──
            SkeuoFormCard {
                // Username field
                SkeuoTextField(
                    value         = username,
                    onValueChange = { username = it },
                    label         = "Username",
                    placeholder   = "john_doe",
                    keyboardType  = KeyboardType.Text,
                    imeAction     = ImeAction.Next,
                    onImeAction   = { focusManager.moveFocus(FocusDirection.Down) },
                    leadingIcon   = {
                        Icon(
                            Icons.Outlined.Person, null,
                            tint     = IndigoStart,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        AnimatedVisibility(username.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                            IconButton(onClick = { username = "" }) {
                                Icon(Icons.Outlined.Clear, "Clear", tint = Muted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                )

                Spacer(Modifier.height(14.dp))

                // Email field
                SkeuoTextField(
                    value         = email,
                    onValueChange = { email = it },
                    label         = "Email address",
                    placeholder   = "name@example.com",
                    keyboardType  = KeyboardType.Email,
                    imeAction     = ImeAction.Next,
                    onImeAction   = { focusManager.moveFocus(FocusDirection.Down) },
                    leadingIcon   = {
                        Icon(
                            Icons.Outlined.Email, null,
                            tint     = IndigoStart,
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
                    value                = password,
                    onValueChange        = { password = it },
                    label                = "Password",
                    placeholder          = "At least 6 characters",
                    keyboardType         = KeyboardType.Password,
                    imeAction            = ImeAction.Done,
                    onImeAction          = { handleRegister() },
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Lock, null,
                            tint     = IndigoStart,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector    = if (passwordVisible) Icons.Outlined.Visibility
                                                 else Icons.Outlined.VisibilityOff,
                                contentDescription = if (passwordVisible) "Hide" else "Show",
                                tint           = Muted,
                                modifier       = Modifier.size(18.dp)
                            )
                        }
                    }
                )

                Spacer(Modifier.height(22.dp))

                // CTA
                SkeuoPrimaryButton(
                    text        = "Create account",
                    isLoading   = isLoading,
                    enabled     = isFormValid && !isLoading,
                    accentColor = IndigoStart,
                    onClick     = { handleRegister() }
                )
            }

            Spacer(Modifier.height(28.dp))

            // ── Footer ──
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text  = "Already have an account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text  = "Log in",
                    style = MaterialTheme.typography.labelLarge,
                    color = CoralStart,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null
                        ) { onNavigateToLogin() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}