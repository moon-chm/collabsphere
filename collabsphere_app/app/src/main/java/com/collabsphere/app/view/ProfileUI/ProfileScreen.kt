package com.collabsphere.app.view.ProfileUI

import androidx.compose.foundation.border
import com.collabsphere.app.view.components.chatHeaderBar
import com.collabsphere.app.view.components.CollabSpinner
import com.collabsphere.app.view.components.SkeuoTextField

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
import androidx.compose.runtime.saveable.rememberSaveable
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
    onLogoutComplete: () -> Unit,
    initialTab: ProfileTab = ProfileTab.PROFILE
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
        onNavigateToBlockedUsers = onNavigateToBlockedUsers,
        careerContent = {
            CareerSection(userId = viewModel.loggedInUserId, snackbarHostState = snackbarHostState)
        },
        initialTab = initialTab
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
    onNavigateToBlockedUsers: () -> Unit,
    /** Career progression and achievements (the Career tab). */
    careerContent: @Composable () -> Unit = {},
    initialTab: ProfileTab = ProfileTab.PROFILE
) {
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
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

                // uCrop 2.2.11+ is edge-to-edge: the toolbar color also fills the status bar area,
                // and the separate status-bar color option no longer exists.
                val toolbarColor = AndroidColor.parseColor("#1E2A5A")
                val activeCtrlColor = AndroidColor.parseColor("#F28B6C")

                val cropIntent = UCrop.of(sourceUri, destUri)
                    .withAspectRatio(1f, 1f)        // Force 1:1 for perfect circular avatar
                    .withMaxResultSize(512, 512)    // High-resolution avatar
                    .withOptions(
                        UCrop.Options().apply {
                            setCircleDimmedLayer(true)          // Circular crop mask
                            setShowCropGrid(false)              // Clean view, no grid lines
                            setShowCropFrame(false)             // Circle frame only
                            setToolbarColor(toolbarColor)
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
                    .chatHeaderBar()
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
                            .printSurface(shape = CircleShape, shadowOffset = 2.dp)
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
                        style = PrintType.H2.copy(fontSize = 22.sp, lineHeight = 26.sp),
                        color = PrintInk
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
                            .printSurface(shape = CircleShape, fill = PrintPaper, shadowOffset = 3.dp)
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
                                    .printSurface(shape = CircleShape, fill = PrintCoral, shadow = null, outline = 1.5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val initial = userName.trim().take(1).uppercase().ifEmpty { "U" }
                                Text(
                                    text = initial,
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 32.sp
                                    ),
                                    color = PrintInk
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
                                CollabSpinner(
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
                            .printSurface(shape = CircleShape, fill = PrintCoral, shadowOffset = 2.dp)
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
                            tint = PrintInk,
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
                        style = PrintType.H2,
                        color = PrintInk,
                        textAlign = TextAlign.Center
                    )

                    // Debossed user id pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp)).background(PrintPaper).border(1.5.dp, PrintInk, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Member ID: #$userId",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = PrintInk
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
                                .background(PrintLeaf)
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

                ProfileTabRow(selected = selectedTab, onSelect = { selectedTab = it })

                if (selectedTab == ProfileTab.PROFILE) {
                // Tactile Edit Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .printSurface(shape = RoundedCornerShape(24.dp))
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
                        SkeuoTextField(
                            value = userName,
                            onValueChange = onNameChange,
                            label = "Display name",
                            placeholder = "Your name",
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = PrintInk,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )

                        Spacer(Modifier.height(12.dp))

                        // Status message field
                        SkeuoTextField(
                            value = statusMessage,
                            onValueChange = { if (it.length <= 60) onStatusMessageChange(it) },
                            label = "Status",
                            placeholder = "What are you up to?",
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = PrintInk,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )

                        Spacer(Modifier.height(12.dp))

                        // Bio field
                        SkeuoTextField(
                            value = bio,
                            onValueChange = { if (it.length <= 300) onBioChange(it) },
                            label = "Bio",
                            placeholder = "Tell your team a little about yourself...",
                            singleLine = false,
                            minLines = 3,
                            maxLines = 5
                        )

                        // Password toggle row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp)).background(PrintPaper).border(1.5.dp, PrintInk, RoundedCornerShape(14.dp))
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
                                    tint = PrintInk,
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
                                    checkedThumbColor = PrintInk,
                                    checkedTrackColor = PrintLeaf,
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
                                // Current Password
                                SkeuoTextField(
                                    value = currentPassword,
                                    onValueChange = onCurrentPasswordChange,
                                    label = "Current password",
                                    placeholder = "Verify existing password",
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Next,
                                    visualTransformation = if (showCurrentPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    leadingIcon = {
                                        Icon(Icons.Default.Lock, null, tint = PrintInk, modifier = Modifier.size(18.dp))
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { showCurrentPassword = !showCurrentPassword }) {
                                            Icon(
                                                imageVector = if (showCurrentPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle password visibility",
                                                tint = PrintInk,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                )

                                Spacer(Modifier.height(12.dp))

                                // New Password
                                SkeuoTextField(
                                    value = newPassword,
                                    onValueChange = onNewPasswordChange,
                                    label = "New password",
                                    placeholder = "Enter updated password",
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                    visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    leadingIcon = {
                                        Icon(Icons.Default.Lock, null, tint = PrintInk, modifier = Modifier.size(18.dp))
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { showNewPassword = !showNewPassword }) {
                                            Icon(
                                                imageVector = if (showNewPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle password visibility",
                                                tint = PrintInk,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                }

                if (selectedTab == ProfileTab.CAREER) careerContent()

                if (selectedTab == ProfileTab.SETTINGS) {
                // ── Email card ──────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .printSurface(shape = RoundedCornerShape(24.dp))
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
                                    tint = PrintInk,
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
                                    tint = if (isEmailVerified) PrintLeafDark else PrintRedDark,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "Change",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = PrintInk,
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
                                    SkeuoTextField(
                                        value = verificationToken,
                                        onValueChange = onVerificationTokenChange,
                                        placeholder = "Verification code",
                                        cornerRadius = 23.dp,
                                        height = 46.dp,
                                        keyboardType = KeyboardType.NumberPassword,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "Verify",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = PrintLeafDark,
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

                com.collabsphere.app.view.components.FocusModeCard()

                com.collabsphere.app.view.components.AiToolsCard()

                com.collabsphere.app.view.components.NotificationSettingsCard()

                // ── Privacy card ────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .printSurface(shape = RoundedCornerShape(24.dp))
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
                                        colors = RadioButtonDefaults.colors(selectedColor = PrintInk)
                                    )
                                    Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink)
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (isSavingPrivacy) "Saving..." else "Save privacy settings",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = PrintInk,
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

                }

                if (selectedTab == ProfileTab.PROFILE) {
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
                        .printSurface(shape = RoundedCornerShape(16.dp), fill = PrintInk, shadow = PrintCoral, shadowOffset = 3.dp)
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
                            tint = PrintPaper,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Update profile",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = PrintPaper
                        )
                    }
                }

                }

                if (selectedTab == ProfileTab.SETTINGS) {
// ── Danger zone ──────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .printSurface(shape = RoundedCornerShape(24.dp))
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
                        .clip(RoundedCornerShape(16.dp)).background(PrintPaper).border(1.5.dp, PrintInk, RoundedCornerShape(16.dp))
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
                    SkeuoTextField(
                        value = newEmailInput,
                        onValueChange = onNewEmailChange,
                        label = "New email",
                        placeholder = "name@example.com",
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        leadingIcon = {
                            Icon(Icons.Default.Email, null, tint = PrintInk, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    var showPw by remember { mutableStateOf(false) }
                    SkeuoTextField(
                        value = emailChangePassword,
                        onValueChange = onEmailChangePasswordChange,
                        label = "Current password",
                        placeholder = "Your current password",
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(Icons.Default.Lock, null, tint = PrintInk, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPw = !showPw }) {
                                Icon(
                                    imageVector = if (showPw) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    tint = PrintInk,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onSubmitEmailChange,
                    enabled = newEmailInput.isNotBlank() && emailChangePassword.isNotBlank()
                ) {
                    Text("Update email", color = PrintInk, fontWeight = FontWeight.Bold)
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
                    SkeuoTextField(
                        value = deleteAccountPassword,
                        onValueChange = onDeleteAccountPasswordChange,
                        label = "Confirm password",
                        placeholder = "Your password to confirm",
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(Icons.Default.Lock, null, tint = PrintRedDark, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPw = !showPw }) {
                                Icon(
                                    imageVector = if (showPw) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    tint = PrintInk,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
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
                checkedThumbColor = PrintInk,
                checkedTrackColor = PrintLeaf,
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