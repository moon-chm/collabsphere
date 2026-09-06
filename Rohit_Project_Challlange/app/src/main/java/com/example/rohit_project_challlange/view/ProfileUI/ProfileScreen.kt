package com.example.rohit_project_challlange.view.ProfileUI

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rohit_project_challlange.ui.theme.*
import com.example.rohit_project_challlange.viewmodel.profile.ProfileViewModel

@Composable
fun ProfileRoute(
    viewModel: ProfileViewModel,
    initialUserName: String,
    onBack: () -> Unit,
    onProfileUpdated: (String) -> Unit,
    onLogoutComplete: () -> Unit
) {
    val updatedName by viewModel.updatedUserName.collectAsState()
    val currentPassword by viewModel.currentPassword.collectAsState()
    val newPassword by viewModel.newPassword.collectAsState()
    val profileStatus by viewModel.profileStatus.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var changePasswordChecked by remember { mutableStateOf(false) }

    LaunchedEffect(initialUserName) {
        if (updatedName.isEmpty()) {
            viewModel.onUserNameChanged(initialUserName)
        }
    }

    LaunchedEffect(profileStatus) {
        if (profileStatus.isNotEmpty()) {
            snackbarHostState.showSnackbar(profileStatus)
            if (profileStatus == "Profile updated successfully!") {
                changePasswordChecked = false
                onProfileUpdated(updatedName)
            }
            viewModel.clearProfileStatus()
        }
    }

    ProfileScreen(
        userId = viewModel.loggedInUserId,
        userName = updatedName,
        currentPassword = currentPassword,
        newPassword = newPassword,
        changePasswordChecked = changePasswordChecked,
        snackbarHostState = snackbarHostState,
        onNameChange = viewModel::onUserNameChanged,
        onCurrentPasswordChange = viewModel::onCurrentPasswordChanged,
        onNewPasswordChange = viewModel::onNewPasswordChanged,
        onChangePasswordCheckedChange = { checked ->
            changePasswordChecked = checked
            if (!checked) {
                viewModel.onCurrentPasswordChanged("")
                viewModel.onNewPasswordChanged("")
            }
        },
        onBack = onBack,
        onUpdateProfile = { viewModel.onUpdateProfile() },
        onLogout = { viewModel.onLogout(onLogoutComplete) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: Int,
    userName: String,
    currentPassword: String,
    newPassword: String,
    changePasswordChecked: Boolean,
    snackbarHostState: SnackbarHostState,
    onNameChange: (String) -> Unit,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onChangePasswordCheckedChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onUpdateProfile: () -> Unit,
    onLogout: () -> Unit
) {
    var showCurrentPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            // Tactile Top Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .drawBehind {
                        drawRect(
                            color = ShadowDark.copy(alpha = 0.12f),
                            topLeft = Offset(0f, size.height),
                            size = Size(size.width, 3.dp.toPx())
                        )
                        drawRect(color = SurfaceRaised)
                        drawRect(
                            color = Color.White.copy(alpha = 0.85f),
                            topLeft = Offset(0f, 0f),
                            size = Size(size.width, 1.dp.toPx())
                        )
                    }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .drawBehind {
                                drawCircle(
                                    color = ShadowDark.copy(alpha = 0.22f),
                                    radius = size.minDimension / 2f,
                                    center = Offset(center.x + 1.5.dp.toPx(), center.y + 2.dp.toPx())
                                )
                                drawCircle(
                                    color = ShadowLight.copy(alpha = 0.90f),
                                    radius = size.minDimension / 2f,
                                    center = Offset(center.x - 1.5.dp.toPx(), center.y - 1.5.dp.toPx())
                                )
                                drawCircle(
                                    color = Surface,
                                    radius = size.minDimension / 2f
                                )
                            }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Ink,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Profile",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Ink
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                // Tactile 3D Avatar Badge
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .drawBehind {
                            drawCircle(
                                color = ShadowDark.copy(alpha = 0.35f),
                                radius = size.minDimension / 2f,
                                center = Offset(center.x + 3.dp.toPx(), center.y + 5.dp.toPx())
                            )
                            drawCircle(
                                color = ShadowLight.copy(alpha = 0.95f),
                                radius = size.minDimension / 2f,
                                center = Offset(center.x - 2.5.dp.toPx(), center.y - 2.5.dp.toPx())
                            )
                        }
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(SurfaceRaised, Surface),
                                start = Offset(0f, 0f),
                                end = Offset(100.dp.value, 100.dp.value)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .drawBehind {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(CoralLight, CoralStart),
                                        center = Offset(center.x - 7.dp.toPx(), center.y - 7.dp.toPx()),
                                        radius = size.minDimension / 2f
                                    )
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.35f),
                                    radius = 12.dp.toPx(),
                                    center = Offset(center.x - 14.dp.toPx(), center.y - 14.dp.toPx())
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val initial = userName.trim().take(1).uppercase().ifEmpty { "U" }
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp
                            ),
                            color = Color.White
                        )
                    }
                }

                // Name & ID Pill
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = userName.ifEmpty { "Welcome" },
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    // Debossed user id pill
                    Box(
                        modifier = Modifier
                            .drawBehind {
                                drawRoundRect(
                                    color = ShadowDark.copy(alpha = 0.15f),
                                    topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                                    size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                                drawRoundRect(
                                    color = ShadowLight.copy(alpha = 0.85f),
                                    topLeft = Offset(-1.dp.toPx(), -1.dp.toPx()),
                                    size = Size(size.width + 1.dp.toPx(), size.height + 1.dp.toPx()),
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                                drawRoundRect(
                                    color = Surface,
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                            }
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Member ID: #$userId",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = IndigoStart
                        )
                    }
                }

                // Tactile Edit Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawRoundRect(
                                color = ShadowDark.copy(alpha = 0.24f),
                                topLeft = Offset(5.dp.toPx(), 7.dp.toPx()),
                                size = Size(size.width, size.height),
                                cornerRadius = CornerRadius(24.dp.toPx())
                            )
                            drawRoundRect(
                                color = ShadowLight.copy(alpha = 0.85f),
                                topLeft = Offset(-3.5.dp.toPx(), -3.5.dp.toPx()),
                                size = Size(size.width, size.height),
                                cornerRadius = CornerRadius(24.dp.toPx())
                            )
                            drawRoundRect(
                                color = SurfaceRaised,
                                cornerRadius = CornerRadius(24.dp.toPx())
                            )
                            // Specular hairline highlight hugging rounded contour
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.70f),
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
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Account settings",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Ink
                        )

                        // Name Field
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Display name",
                                style = MaterialTheme.typography.labelMedium,
                                color = Muted,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .drawBehind {
                                        drawRoundRect(
                                            color = ShadowDark.copy(alpha = 0.22f),
                                            topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                                            size = Size(size.width - 1.5.dp.toPx(), size.height - 1.5.dp.toPx()),
                                            cornerRadius = CornerRadius(14.dp.toPx())
                                        )
                                        drawRoundRect(
                                            color = ShadowLight.copy(alpha = 0.85f),
                                            topLeft = Offset(-1.dp.toPx(), -1.dp.toPx()),
                                            size = Size(size.width + 1.dp.toPx(), size.height + 1.dp.toPx()),
                                            cornerRadius = CornerRadius(14.dp.toPx())
                                        )
                                        drawRoundRect(
                                            color = Background.copy(alpha = 0.85f),
                                            cornerRadius = CornerRadius(14.dp.toPx())
                                        )
                                    },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = CoralStart,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    BasicTextField(
                                        value = userName,
                                        onValueChange = onNameChange,
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink)
                                    )
                                }
                            }
                        }

                        // Password toggle row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawBehind {
                                    drawRoundRect(
                                        color = Surface,
                                        cornerRadius = CornerRadius(14.dp.toPx())
                                    )
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = IndigoStart,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Update password",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Ink
                                )
                            }
                            Switch(
                                checked = changePasswordChecked,
                                onCheckedChange = onChangePasswordCheckedChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = CoralStart,
                                    uncheckedThumbColor = Muted,
                                    uncheckedTrackColor = Surface
                                )
                            )
                        }

                        AnimatedVisibility(visible = changePasswordChecked) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Current Password
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Current password",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Muted,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .drawBehind {
                                                drawRoundRect(
                                                    color = ShadowDark.copy(alpha = 0.22f),
                                                    topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                                                    size = Size(size.width - 1.5.dp.toPx(), size.height - 1.5.dp.toPx()),
                                                    cornerRadius = CornerRadius(14.dp.toPx())
                                                )
                                                drawRoundRect(
                                                    color = ShadowLight.copy(alpha = 0.85f),
                                                    topLeft = Offset(-1.dp.toPx(), -1.dp.toPx()),
                                                    size = Size(size.width + 1.dp.toPx(), size.height + 1.dp.toPx()),
                                                    cornerRadius = CornerRadius(14.dp.toPx())
                                                )
                                                drawRoundRect(
                                                    color = Background.copy(alpha = 0.85f),
                                                    cornerRadius = CornerRadius(14.dp.toPx())
                                                )
                                            },
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            BasicTextField(
                                                value = currentPassword,
                                                onValueChange = onCurrentPasswordChange,
                                                modifier = Modifier.weight(1f),
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                                                visualTransformation = if (showCurrentPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Password,
                                                    imeAction = ImeAction.Next
                                                ),
                                                decorationBox = { inner ->
                                                    Box(contentAlignment = Alignment.CenterStart) {
                                                        if (currentPassword.isEmpty()) {
                                                            Text(
                                                                text = "Verify existing password",
                                                                style = MaterialTheme.typography.bodyLarge,
                                                                color = Muted.copy(alpha = 0.6f)
                                                            )
                                                        }
                                                        inner()
                                                    }
                                                }
                                            )
                                            IconButton(onClick = { showCurrentPassword = !showCurrentPassword }) {
                                                Icon(
                                                    imageVector = if (showCurrentPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = "Toggle password visibility",
                                                    tint = Muted,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // New Password
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "New password",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Muted,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .drawBehind {
                                                drawRoundRect(
                                                    color = ShadowDark.copy(alpha = 0.22f),
                                                    topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                                                    size = Size(size.width - 1.5.dp.toPx(), size.height - 1.5.dp.toPx()),
                                                    cornerRadius = CornerRadius(14.dp.toPx())
                                                )
                                                drawRoundRect(
                                                    color = ShadowLight.copy(alpha = 0.85f),
                                                    topLeft = Offset(-1.dp.toPx(), -1.dp.toPx()),
                                                    size = Size(size.width + 1.dp.toPx(), size.height + 1.dp.toPx()),
                                                    cornerRadius = CornerRadius(14.dp.toPx())
                                                )
                                                drawRoundRect(
                                                    color = Background.copy(alpha = 0.85f),
                                                    cornerRadius = CornerRadius(14.dp.toPx())
                                                )
                                            },
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            BasicTextField(
                                                value = newPassword,
                                                onValueChange = onNewPasswordChange,
                                                modifier = Modifier.weight(1f),
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                                                visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Password,
                                                    imeAction = ImeAction.Done
                                                ),
                                                decorationBox = { inner ->
                                                    Box(contentAlignment = Alignment.CenterStart) {
                                                        if (newPassword.isEmpty()) {
                                                            Text(
                                                                text = "Enter updated password",
                                                                style = MaterialTheme.typography.bodyLarge,
                                                                color = Muted.copy(alpha = 0.6f)
                                                            )
                                                        }
                                                        inner()
                                                    }
                                                }
                                            )
                                            IconButton(onClick = { showNewPassword = !showNewPassword }) {
                                                Icon(
                                                    imageVector = if (showNewPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = "Toggle password visibility",
                                                    tint = Muted,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val isSubmissionReady = userName.isNotBlank() &&
                        (!changePasswordChecked || (currentPassword.isNotBlank() && newPassword.isNotBlank()))

                // Tactile Update Button
                var updatePressed by remember { mutableStateOf(false) }
                val updateScale by animateFloatAsState(
                    targetValue = if (updatePressed) 0.96f else 1f,
                    animationSpec = tween(80),
                    label = "updateScale"
                )

                val updateColor = if (isSubmissionReady) CoralStart else Muted.copy(alpha = 0.45f)
                val updateColorEnd = if (isSubmissionReady) CoralEnd else Muted.copy(alpha = 0.35f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .graphicsLayer { scaleX = updateScale; scaleY = updateScale }
                        .drawBehind {
                            val shadowOffset = if (updatePressed) 2.dp else 5.dp
                            val shadowAlpha = if (updatePressed) 0.12f else 0.32f

                            drawRoundRect(
                                color = updateColor.copy(alpha = shadowAlpha),
                                topLeft = Offset(0f, shadowOffset.toPx()),
                                size = Size(size.width, size.height),
                                cornerRadius = CornerRadius(16.dp.toPx())
                            )
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.25f),
                                topLeft = Offset(-1.5.dp.toPx(), -1.5.dp.toPx()),
                                size = Size(size.width, size.height),
                                cornerRadius = CornerRadius(16.dp.toPx())
                            )
                            drawRoundRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(updateColor, updateColorEnd),
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, size.height)
                                ),
                                cornerRadius = CornerRadius(16.dp.toPx())
                            )
                            // Specular hairline highlight hugging rounded contour
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
                            enabled = isSubmissionReady,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            updatePressed = true
                            focusManager.clearFocus()
                            onUpdateProfile()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Update profile",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = Color.White
                        )
                    }
                }

                // Tactile Logout Button
                var logoutPressed by remember { mutableStateOf(false) }
                val logoutScale by animateFloatAsState(
                    targetValue = if (logoutPressed) 0.96f else 1f,
                    animationSpec = tween(80),
                    label = "logoutScale"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .graphicsLayer { scaleX = logoutScale; scaleY = logoutScale }
                        .drawBehind {
                            val shadowOffset = if (logoutPressed) 1.dp else 3.dp
                            drawRoundRect(
                                color = ShadowDark.copy(alpha = 0.18f),
                                topLeft = Offset(0f, shadowOffset.toPx()),
                                size = Size(size.width, size.height),
                                cornerRadius = CornerRadius(16.dp.toPx())
                            )
                            drawRoundRect(
                                color = ShadowLight.copy(alpha = 0.85f),
                                topLeft = Offset(-1.5.dp.toPx(), -1.5.dp.toPx()),
                                size = Size(size.width, size.height),
                                cornerRadius = CornerRadius(16.dp.toPx())
                            )
                            drawRoundRect(
                                color = SurfaceRaised,
                                cornerRadius = CornerRadius(16.dp.toPx())
                            )
                            // Destructive subtle border
                            drawRoundRect(
                                color = Destructive.copy(alpha = 0.35f),
                                cornerRadius = CornerRadius(16.dp.toPx()),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx())
                            )
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            logoutPressed = true
                            onLogout()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            tint = Destructive,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Log out",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = Destructive
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}