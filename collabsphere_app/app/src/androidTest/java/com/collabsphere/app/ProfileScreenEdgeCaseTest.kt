package com.collabsphere.app.view.ProfileUI

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.collabsphere.app.ui.theme.CollabSphereTheme
import com.collabsphere.app.viewmodel.profile.ProfileViewModel
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Rule
import org.junit.Test

class ProfileScreenEdgeCaseTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun logoutButtonTriggersViewModelLogout() {
        val viewModel = mockk<ProfileViewModel>(relaxed = true)
        var logoutCompleteCalled = false
        
        compose.setContent {
            CollabSphereTheme(darkTheme = false) {
                ProfileScreen(
                    userId = 1,
                    userName = "Test User",
                    currentPassword = "",
                    newPassword = "",
                    changePasswordChecked = false,
                    bio = "",
                    statusMessage = "",
                    email = "",
                    avatarUrl = "",
                    isEmailVerified = true,
                    lastSeen = null,
                    isUploadingAvatar = false,
                    showEmailDialog = false,
                    newEmailInput = "",
                    emailChangePassword = "",
                    awaitingVerification = false,
                    verificationToken = "",
                    showDeleteDialog = false,
                    deleteAccountPassword = "",
                    isDeletingAccount = false,
                    showEmailToggle = false,
                    showOnlineStatus = false,
                    showLastSeenToggle = false,
                    profileVisibility = "public",
                    isSavingPrivacy = false,
                    snackbarHostState = SnackbarHostState(),
                    onNameChange = {},
                    onCurrentPasswordChange = {},
                    onNewPasswordChange = {},
                    onBioChange = {},
                    onStatusMessageChange = {},
                    onChangePasswordCheckedChange = {},
                    onBack = {},
                    onUpdateProfile = {},
                    onLogout = { viewModel.onLogout { logoutCompleteCalled = true } },
                    onAvatarPicked = {},
                    onRemoveAvatar = {},
                    onOpenEmailDialog = {},
                    onDismissEmailDialog = {},
                    onNewEmailChange = {},
                    onEmailChangePasswordChange = {},
                    onSubmitEmailChange = {},
                    onVerificationTokenChange = {},
                    onConfirmVerification = {},
                    onResendVerification = {},
                    onOpenDeleteDialog = {},
                    onDismissDeleteDialog = {},
                    onDeleteAccountPasswordChange = {},
                    onConfirmDeleteAccount = {},
                    onShowEmailToggleChange = {},
                    onShowOnlineStatusChange = {},
                    onShowLastSeenToggleChange = {},
                    onProfileVisibilityChange = {},
                    onSavePrivacySettings = {},
                    onNavigateToBlockedUsers = {}
                )
            }
        }

        // Click the log out button
        compose.onNodeWithText("Log out").performClick()

        // Verify view model method is called
        compose.runOnIdle {
            coVerify { viewModel.onLogout(any()) }
        }
    }
}
