package com.collabsphere.app.view.ProfileUI

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.collabsphere.app.AppConfig
import com.collabsphere.app.ui.theme.*
import com.collabsphere.app.viewmodel.profile.ProfileViewModel
import com.yalantis.ucrop.UCrop
import java.io.File

@Composable
fun ProfileRoute(
    viewModel: ProfileViewModel,
    initialUserName: String,
    onBack: () -> Unit,
    onProfileUpdated: (String) -> Unit,
    onAvatarUpdated: (String) -> Unit = {},
    onNavigateToBlockedUsers: () -> Unit = {},
    onLogoutComplete: () -> Unit
) {
    val updatedName by viewModel.updatedUserName.collectAsState()
    val currentPassword by viewModel.currentPassword.collectAsState()
    val newPassword by viewModel.newPassword.collectAsState()
    val profileStatus by viewModel.profileStatus.collectAsState()
    val bio by viewModel.bio.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val email by viewModel.email.collectAsState()
    val avatarUrl by viewModel.avatarUrl.collectAsState()
    val isEmailVerified by viewModel.isEmailVerified.collectAsState()
    val lastSeen by viewModel.lastSeen.collectAsState()
    val isUploadingAvatar by viewModel.isUploadingAvatar.collectAsState()
    val showEmailDialog by viewModel.showEmailDialog.collectAsState()
    val newEmailInput by viewModel.newEmailInput.collectAsState()
    val emailChangePassword by viewModel.emailChangePassword.collectAsState()
    val awaitingVerification by viewModel.awaitingVerification.collectAsState()
    val verificationToken by viewModel.verificationToken.collectAsState()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()
    val deleteAccountPassword by viewModel.deleteAccountPassword.collectAsState()
    val isDeletingAccount by viewModel.isDeletingAccount.collectAsState()
    val showEmail by viewModel.showEmail.collectAsState()
    val showOnlineStatus by viewModel.showOnlineStatus.collectAsState()
    val showLastSeen by viewModel.showLastSeen.collectAsState()
    val profileVisibility by viewModel.profileVisibility.collectAsState()
    val isSavingPrivacy by viewModel.isSavingPrivacy.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var changePasswordChecked by remember { mutableStateOf(false) }

    LaunchedEffect(initialUserName) {
        if (updatedName.isEmpty()) {
            viewModel.onUserNameChanged(initialUserName)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
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

    // Notify parent whenever the avatarUrl changes (upload or remove)
    LaunchedEffect(avatarUrl) {
        onAvatarUpdated(avatarUrl)
    }

    ProfileScreen(
        userId = viewModel.loggedInUserId,
        userName = updatedName,
        currentPassword = currentPassword,
        newPassword = newPassword,
        changePasswordChecked = changePasswordChecked,
        bio = bio,
        statusMessage = statusMessage,
        email = email,
        avatarUrl = avatarUrl,
        isEmailVerified = isEmailVerified,
        lastSeen = lastSeen,
        isUploadingAvatar = isUploadingAvatar,
        showEmailDialog = showEmailDialog,
        newEmailInput = newEmailInput,
        emailChangePassword = emailChangePassword,
        awaitingVerification = awaitingVerification,
        verificationToken = verificationToken,
        showDeleteDialog = showDeleteDialog,
        deleteAccountPassword = deleteAccountPassword,
        isDeletingAccount = isDeletingAccount,
        showEmailToggle = showEmail,
        showOnlineStatus = showOnlineStatus,
        showLastSeenToggle = showLastSeen,
        profileVisibility = profileVisibility,
        isSavingPrivacy = isSavingPrivacy,
        snackbarHostState = snackbarHostState,
        onNameChange = viewModel::onUserNameChanged,
        onCurrentPasswordChange = viewModel::onCurrentPasswordChanged,
        onNewPasswordChange = viewModel::onNewPasswordChanged,
        onBioChange = viewModel::onBioChanged,
        onStatusMessageChange = viewModel::onStatusMessageChanged,
        onChangePasswordCheckedChange = { checked ->
            changePasswordChecked = checked
            if (!checked) {
                viewModel.onCurrentPasswordChanged("")
                viewModel.onNewPasswordChanged("")
            }
        },
        onBack = onBack,
        onUpdateProfile = { viewModel.onUpdateProfile() },
        onLogout = { viewModel.onLogout(onLogoutComplete) },
        onAvatarPicked = { file -> viewModel.onAvatarPicked(file) },
        onRemoveAvatar = { viewModel.onRemoveAvatar() },
        onOpenEmailDialog = { viewModel.onOpenEmailDialog() },
        onDismissEmailDialog = { viewModel.onDismissEmailDialog() },
        onNewEmailChange = viewModel::onNewEmailChanged,
        onEmailChangePasswordChange = viewModel::onEmailChangePasswordChanged,
        onSubmitEmailChange = { viewModel.onSubmitEmailChange() },
        onVerificationTokenChange = viewModel::onVerificationTokenChanged,
        onConfirmVerification = { viewModel.onConfirmVerification() },
        onResendVerification = { viewModel.onResendVerification() },
        onOpenDeleteDialog = { viewModel.onOpenDeleteDialog() },
        onDismissDeleteDialog = { viewModel.onDismissDeleteDialog() },
        onDeleteAccountPasswordChange = viewModel::onDeleteAccountPasswordChanged,
        onConfirmDeleteAccount = { viewModel.onConfirmDeleteAccount(onLogoutComplete) },
        onShowEmailToggleChange = viewModel::onShowEmailChanged,
        onShowOnlineStatusChange = viewModel::onShowOnlineStatusChanged,
        onShowLastSeenToggleChange = viewModel::onShowLastSeenChanged,
        onProfileVisibilityChange = viewModel::onProfileVisibilityChanged,
        onSavePrivacySettings = { viewModel.onSavePrivacySettings() },
        onNavigateToBlockedUsers = onNavigateToBlockedUsers
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
    bio: String,
    statusMessage: String,
    email: String,
    avatarUrl: String,
    isEmailVerified: Boolean,
    lastSeen: Long?,
    isUploadingAvatar: Boolean,
    showEmailDialog: Boolean,
    newEmailInput: String,
    emailChangePassword: String,
    awaitingVerification: Boolean,
    verificationToken: String,
    showDeleteDialog: Boolean,
    deleteAccountPassword: String,
    isDeletingAccount: Boolean,
    showEmailToggle: Boolean,
    showOnlineStatus: Boolean,
    showLastSeenToggle: Boolean,
    profileVisibility: String,
    isSavingPrivacy: Boolean,
    snackbarHostState: SnackbarHostState,
    onNameChange: (String) -> Unit,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
    onStatusMessageChange: (String) -> Unit,
    onChangePasswordCheckedChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onUpdateProfile: () -> Unit,
    onLogout: () -> Unit,
    onAvatarPicked: (File) -> Unit,
    onRemoveAvatar: () -> Unit,
    onOpenEmailDialog: () -> Unit,
    onDismissEmailDialog: () -> Unit,
    onNewEmailChange: (String) -> Unit,
    onEmailChangePasswordChange: (String) -> Unit,
    onSubmitEmailChange: () -> Unit,
    onVerificationTokenChange: (String) -> Unit,
    onConfirmVerification: () -> Unit,
    onResendVerification: () -> Unit,
    onOpenDeleteDialog: () -> Unit,
    onDismissDeleteDialog: () -> Unit,
    onDeleteAccountPasswordChange: (String) -> Unit,
    onConfirmDeleteAccount: () -> Unit,
    onShowEmailToggleChange: (Boolean) -> Unit,
    onShowOnlineStatusChange: (Boolean) -> Unit,
    onShowLastSeenToggleChange: (Boolean) -> Unit,
    onProfileVisibilityChange: (String) -> Unit,
    onSavePrivacySettings: () -> Unit,
    onNavigateToBlockedUsers: () -> Unit
) {
    var showCurrentPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Track pending crop files across activity launches
    var pendingCropSourceFile by remember { mutableStateOf<File?>(null) }
    var pendingCropDestFile by remember { mutableStateOf<File?>(null) }

    // ── uCrop result launcher — receives the cropped image URI ──────────────
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val dest = pendingCropDestFile
        val src = pendingCropSourceFile

        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val resultUri = result.data?.let { UCrop.getOutput(it) }
            val croppedFile = if (resultUri != null && resultUri.scheme == "file" && resultUri.path != null) {
                File(resultUri.path!!)
            } else if (dest != null && dest.exists() && dest.length() > 0L) {
                dest
            } else null

            if (croppedFile != null && croppedFile.exists() && croppedFile.length() > 0L) {
                onAvatarPicked(croppedFile)
            } else if (src != null && src.exists() && src.length() > 0L) {
                // Fallback to source if cropped output file was not found
                onAvatarPicked(src)
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            val cropError = result.data?.let { UCrop.getError(it) }
            android.util.Log.e("ProfileScreen", "uCrop failed: ${cropError?.message}", cropError)
            // If crop errored, fall back to the picked source photo so user is not blocked
            if (src != null && src.exists() && src.length() > 0L) {
                onAvatarPicked(src)
            }
        }
    }

    // ── Image picker — on pick success, launch uCrop for circle cropping ────
    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // 1. Copy picked photo to local cache file first.
                // This ensures UCropActivity has direct file access without PhotoPicker IPC permission expiration.
                val sourceFile = File(context.cacheDir, "avatar_source_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    sourceFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                if (!sourceFile.exists() || sourceFile.length() == 0L) {
                    return@rememberLauncherForActivityResult
                }
                pendingCropSourceFile = sourceFile

                // 2. Destination file in cacheDir using Uri.fromFile(...)
                // uCrop's internal BitmapCropTask requires a file:// Uri where .getPath() returns a real filesystem path.
                val destFile = File(context.cacheDir, "avatar_cropped_${System.currentTimeMillis()}.jpg")
                pendingCropDestFile = destFile

                val sourceUri = Uri.fromFile(sourceFile)
                val destUri = Uri.fromFile(destFile)

                // Coral accent colors matching the app's skeuomorphic theme
                val toolbarColor = AndroidColor.parseColor("#E8784A")   // CoralStart
                val statusBarColor = AndroidColor.parseColor("#C95E30") // CoralEnd
                val activeCtrlColor = AndroidColor.parseColor("#E8784A")

                val cropIntent = UCrop.of(sourceUri, destUri)
                    .withAspectRatio(1f, 1f)        // Force 1:1 for perfect circular avatar
                    .withMaxResultSize(512, 512)    // High-resolution avatar
                    .withOptions(
                        UCrop.Options().apply {
                            setCircleDimmedLayer(true)          // Circular crop mask
                            setShowCropGrid(false)              // Clean view, no grid lines
                            setShowCropFrame(false)             // Circle frame only
                            setToolbarColor(toolbarColor)
                            setStatusBarColor(statusBarColor)
                            setActiveControlsWidgetColor(activeCtrlColor)
                            setToolbarWidgetColor(AndroidColor.WHITE)
                            setToolbarTitle("Adjust your photo")
                            setCompressionQuality(90)
                            setHideBottomControls(false)        // Keep rotation/scale controls visible
                        }
                    )
                    .getIntent(context)

                cropLauncher.launch(cropIntent)
            } catch (e: Exception) {
                android.util.Log.e("ProfileScreen", "Failed to launch crop", e)
                // Fallback: direct upload if uCrop couldn't be launched
                try {
                    val tempFile = File(context.cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { i ->
                        tempFile.outputStream().use { o -> i.copyTo(o) }
                    }
                    if (tempFile.exists()) onAvatarPicked(tempFile)
                } catch (_: Exception) { }
            }
        }
    }

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
                    .drawWithCache {
onDrawBehind {
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
                            .drawWithCache {
onDrawBehind {
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
                // Tactile 3D Avatar Badge — tap to change photo
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .drawWithCache {
onDrawBehind {
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
}
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(SurfaceRaised, Surface),
                                    start = Offset(0f, 0f),
                                    end = Offset(100.dp.value, 100.dp.value)
                                )
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = !isUploadingAvatar
                            ) {
                                avatarPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val initialsFallback: @Composable () -> Unit = {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .drawWithCache {
onDrawBehind {
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
                                    }
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

                        if (avatarUrl.isEmpty()) {
                            initialsFallback()
                        } else {
                            val fullAvatarUrl = if (avatarUrl.startsWith("http")) avatarUrl
                                else "${AppConfig.BASE_URL}$avatarUrl"
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(fullAvatarUrl)
                                    .crossfade(true)
                                    .size(with(LocalDensity.current) { 84.dp.roundToPx() })
                                    .build(),
                                contentDescription = "Profile picture",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(CircleShape),
                                loading = { initialsFallback() },
                                error = { initialsFallback() }
                            )
                        }

                        if (isUploadingAvatar) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                            }
                        }
                    }

                    // Raised camera badge, bottom-right of the avatar
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .drawWithCache {
onDrawBehind {
                                drawCircle(
                                    color = ShadowDark.copy(alpha = 0.30f),
                                    radius = size.minDimension / 2f,
                                    center = Offset(center.x + 1.5.dp.toPx(), center.y + 2.dp.toPx())
                                )
                                drawCircle(
                                    color = ShadowLight.copy(alpha = 0.90f),
                                    radius = size.minDimension / 2f,
                                    center = Offset(center.x - 1.dp.toPx(), center.y - 1.dp.toPx())
                                )
                                drawCircle(color = CoralStart)
                            }
}
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = !isUploadingAvatar
                            ) {
                                avatarPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Change profile picture",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                if (avatarUrl.isNotEmpty() && !avatarUrl.contains("/avatars/default/")) {
                    Text(
                        text = "Remove photo",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Destructive,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = !isUploadingAvatar
                            ) { onRemoveAvatar() }
                            .padding(top = 2.dp, bottom = 2.dp)
                    )
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
                            .drawWithCache {
onDrawBehind {
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
}
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Member ID: #$userId",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = IndigoStart
                        )
                    }

                    if (statusMessage.isNotBlank()) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Muted,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Online indicator — accurate for one's own profile since the app is in active use
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Mint)
                        )
                        Text(
                            text = "Online now",
                            style = MaterialTheme.typography.labelMedium,
                            color = Muted
                        )
                        if (lastSeen != null) {
                            Text(
                                text = "· Last seen ${formatLastSeen(lastSeen)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Muted
                            )
                        }
                    }
                }

                // Tactile Edit Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawWithCache {
onDrawBehind {
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
                                    .skeuoInset(cornerRadius = 14.dp, depth = 2.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)),
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
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                                        cursorBrush = SolidColor(CoralStart)
                                    )
                                }
                            }
                        }

                        // Status message field
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Status",
                                style = MaterialTheme.typography.labelMedium,
                                color = Muted,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .skeuoInset(cornerRadius = 14.dp, depth = 2.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = IndigoStart,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    BasicTextField(
                                        value = statusMessage,
                                        onValueChange = { if (it.length <= 60) onStatusMessageChange(it) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                                        cursorBrush = SolidColor(CoralStart),
                                        decorationBox = { inner ->
                                            Box(contentAlignment = Alignment.CenterStart) {
                                                if (statusMessage.isEmpty()) {
                                                    Text(
                                                        text = "What are you up to?",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = Ink.copy(alpha = 0.5f)
                                                    )
                                                }
                                                inner()
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Bio field
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Bio",
                                style = MaterialTheme.typography.labelMedium,
                                color = Muted,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 80.dp)
                                    .skeuoInset(cornerRadius = 14.dp, depth = 2.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                BasicTextField(
                                    value = bio,
                                    onValueChange = { if (it.length <= 300) onBioChange(it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                                    cursorBrush = SolidColor(CoralStart),
                                    decorationBox = { inner ->
                                        Box {
                                            if (bio.isEmpty()) {
                                                Text(
                                                    text = "Tell your team a little about yourself",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = Ink.copy(alpha = 0.5f)
                                                )
                                            }
                                            inner()
                                        }
                                    }
                                )
                            }
                        }

                        // Password toggle row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawWithCache {
onDrawBehind {
                                    drawRoundRect(
                                        color = Surface,
                                        cornerRadius = CornerRadius(14.dp.toPx())
                                    )
                                }
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
                                            .skeuoInset(cornerRadius = 14.dp, depth = 2.dp)
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)),
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
                                                cursorBrush = SolidColor(CoralStart),
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
                                                                color = Ink.copy(alpha = 0.65f)
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
                                            .skeuoInset(cornerRadius = 14.dp, depth = 2.dp)
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)),
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
                                                cursorBrush = SolidColor(CoralStart),
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
                                                                color = Ink.copy(alpha = 0.65f)
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

                // ── Email card ──────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawWithCache {
onDrawBehind {
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
                            drawRoundRect(color = SurfaceRaised, cornerRadius = CornerRadius(24.dp.toPx()))
                        }
}
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Email",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Ink
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = IndigoStart,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Ink,
                                    maxLines = 1
                                )
                                Icon(
                                    imageVector = if (isEmailVerified) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = if (isEmailVerified) "Verified" else "Unverified",
                                    tint = if (isEmailVerified) Mint else AmberWarn,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "Change",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = CoralStart,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onOpenEmailDialog() }
                            )
                        }

                        AnimatedVisibility(visible = awaitingVerification && !isEmailVerified) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Enter the 6-digit code we emailed you to confirm this email",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Muted
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .skeuoInset(cornerRadius = 12.dp, depth = 2.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        BasicTextField(
                                            value = verificationToken,
                                            onValueChange = onVerificationTokenChange,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                                            cursorBrush = SolidColor(CoralStart),
                                            decorationBox = { inner ->
                                                Box(contentAlignment = Alignment.CenterStart) {
                                                    if (verificationToken.isEmpty()) {
                                                        Text(
                                                            text = "Verification code",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = Ink.copy(alpha = 0.5f)
                                                        )
                                                    }
                                                    inner()
                                                }
                                            }
                                        )
                                    }
                                    Text(
                                        text = "Verify",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = Mint,
                                        modifier = Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { onConfirmVerification() }
                                    )
                                    Text(
                                        text = "Resend",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Muted,
                                        modifier = Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { onResendVerification() }
                                    )
                                }
                            }
                        }
                    }
                }

                com.collabsphere.app.view.components.NotificationSettingsCard()

                // ── Privacy card ────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawWithCache {
onDrawBehind {
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
                            drawRoundRect(color = SurfaceRaised, cornerRadius = CornerRadius(24.dp.toPx()))
                        }
}
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Privacy",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Ink
                        )
                        Spacer(Modifier.height(8.dp))

                        PrivacyToggleRow(
                            label = "Show my email to others",
                            checked = showEmailToggle,
                            onCheckedChange = onShowEmailToggleChange
                        )
                        PrivacyToggleRow(
                            label = "Show my online status",
                            checked = showOnlineStatus,
                            onCheckedChange = onShowOnlineStatusChange
                        )
                        PrivacyToggleRow(
                            label = "Show my last seen",
                            checked = showLastSeenToggle,
                            onCheckedChange = onShowLastSeenToggleChange
                        )

                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Who can view my profile",
                            style = MaterialTheme.typography.labelMedium,
                            color = Muted,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            listOf("public" to "Everyone", "members_only" to "Workspace members").forEach { (value, label) ->
                                val selected = profileVisibility == value
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { onProfileVisibilityChange(value) }
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = { onProfileVisibilityChange(value) },
                                        colors = RadioButtonDefaults.colors(selectedColor = CoralStart)
                                    )
                                    Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink)
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (isSavingPrivacy) "Saving..." else "Save privacy settings",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = CoralStart,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = !isSavingPrivacy
                            ) { onSavePrivacySettings() }
                        )

                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onNavigateToBlockedUsers() },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Blocked users",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Ink
                            )
                            Text("Manage ›", style = MaterialTheme.typography.labelMedium, color = Muted)
                        }
                    }
                }

                // ── Danger zone ──────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawWithCache {
onDrawBehind {
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
                            drawRoundRect(color = SurfaceRaised, cornerRadius = CornerRadius(24.dp.toPx()))
                        }
}
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Danger zone",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Destructive
                        )
                        Text(
                            text = "Deleting your account permanently removes your profile, messages, files, and workspace memberships. This can't be undone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Muted
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onOpenDeleteDialog() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = Destructive,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Delete account",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Destructive
                            )
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

                val updateAlpha = if (isSubmissionReady) 1f else 0.72f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .graphicsLayer { scaleX = updateScale; scaleY = updateScale }
                        .drawWithCache {
onDrawBehind {
                            val shadowOffset = if (updatePressed) 2.dp else 5.dp
                            val shadowAlpha = if (updatePressed) 0.12f else 0.32f

                            drawRoundRect(
                                color = CoralStart.copy(alpha = shadowAlpha * updateAlpha),
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
                                    colors = listOf(
                                        CoralStart.copy(alpha = updateAlpha),
                                        CoralEnd.copy(alpha = updateAlpha)
                                    ),
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
}
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (isSubmissionReady) {
                                updatePressed = true
                                focusManager.clearFocus()
                                onUpdateProfile()
                            }
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
                        .drawWithCache {
onDrawBehind {
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

    if (showEmailDialog) {
        AlertDialog(
            onDismissRequest = onDismissEmailDialog,
            containerColor = SurfaceRaised,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Change email",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Ink
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Verify your current password to update your email address.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted
                    )
                    OutlinedTextField(
                        value = newEmailInput,
                        onValueChange = onNewEmailChange,
                        label = { Text("New email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    var showPw by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = emailChangePassword,
                        onValueChange = onEmailChangePasswordChange,
                        label = { Text("Current password") },
                        singleLine = true,
                        visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPw = !showPw }) {
                                Icon(
                                    imageVector = if (showPw) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onSubmitEmailChange,
                    enabled = newEmailInput.isNotBlank() && emailChangePassword.isNotBlank()
                ) {
                    Text("Update email", color = CoralStart, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissEmailDialog) {
                    Text("Cancel", color = Muted)
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = onDismissDeleteDialog,
            containerColor = SurfaceRaised,
            shape = RoundedCornerShape(24.dp),
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Destructive)
            },
            title = {
                Text(
                    text = "Delete account?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Destructive
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "This permanently deletes your account and all associated data — workspaces you own, messages, files, and tasks. This cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted
                    )
                    var showPw by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = deleteAccountPassword,
                        onValueChange = onDeleteAccountPasswordChange,
                        label = { Text("Confirm password") },
                        singleLine = true,
                        visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPw = !showPw }) {
                                Icon(
                                    imageVector = if (showPw) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDeleteAccount,
                    enabled = deleteAccountPassword.isNotBlank() && !isDeletingAccount
                ) {
                    Text("Delete permanently", color = Destructive, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteDialog, enabled = !isDeletingAccount) {
                    Text("Cancel", color = Muted)
                }
            }
        )
    }
}

@Composable
private fun PrivacyToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = CoralStart,
                uncheckedThumbColor = Muted,
                uncheckedTrackColor = Surface
            )
        )
    }
}

/** Rough human-readable relative time for the "Last seen" line — e.g. "5m ago", "3h ago", "2d ago". */
private fun formatLastSeen(timestampMillis: Long): String {
    val diffMs = (System.currentTimeMillis() - timestampMillis).coerceAtLeast(0)
    val minutes = diffMs / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}