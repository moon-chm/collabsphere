package com.collabsphere.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.collabsphere.app.ui.theme.CollabSphereTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.collabsphere.app.view.AppNavigation
import com.collabsphere.app.viewmodel.LoginViewModel
import com.collabsphere.app.viewmodel.DashboardViewModel
import com.collabsphere.app.viewmodel.dm.DmViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.koin.core.parameter.parametersOf

data class NotificationDeepLink(
    val workspaceId: Int,
    val partnerId: Int = -1,
    val senderName: String = "Member",
    val targetTab: Int = 4,
    val notificationId: Int = 0
)

class MainActivity : ComponentActivity() {

    private val userPreferences: UserPreferences by inject()
    private val notificationHelper: NotificationHelper by inject()

    private val loginViewModel: LoginViewModel by viewModel()
    private val dmViewModel: DmViewModel by viewModel()
    private val notificationsViewModel: com.collabsphere.app.viewmodel.NotificationsViewModel by viewModel()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d("MainActivity", "POST_NOTIFICATIONS permission granted: $isGranted")
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private var notificationDeepLink by mutableStateOf<NotificationDeepLink?>(null)

    private fun extractNotificationDeepLink(targetIntent: Intent?) {
        if (targetIntent?.getBooleanExtra("from_notification", false) == true) {
            val workspaceId = targetIntent.getIntExtra("workspace_id", -1)
            val partnerId = targetIntent.getIntExtra("partner_id", -1)
            val senderName = targetIntent.getStringExtra("partner_name") ?: "Member"
            val targetTab = targetIntent.getIntExtra("target_tab", if (partnerId != -1) 4 else 0)
            val notificationId = targetIntent.getIntExtra("notification_id", 0)

            if (workspaceId != -1) {
                notificationDeepLink = NotificationDeepLink(
                    workspaceId = workspaceId,
                    partnerId = partnerId,
                    senderName = senderName,
                    targetTab = targetTab,
                    notificationId = notificationId
                )
            }
            // Mark this intent as consumed so a rotation (which re-delivers the same intent to a
            // fresh onCreate) doesn't silently re-navigate to the same DM/workspace again.
            targetIntent.putExtra("from_notification", false)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractNotificationDeepLink(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        extractNotificationDeepLink(intent)
        checkAndRequestNotificationPermission()

        setContent {
            CollabSphereTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                ) {
                    val savedUserId by userPreferences.userIdFlow.collectAsStateWithLifecycle(initialValue = -1)
                    val loggedInUserId by loginViewModel.loggedInUserId.collectAsStateWithLifecycle(initialValue = 0L)

                    val currentUserId = if (loggedInUserId != 0L) loggedInUserId.toInt() else savedUserId

                    val dashboardViewModel: DashboardViewModel = koinViewModel {
                        parametersOf(currentUserId)
                    }

                    val widgetDestination = intent?.getStringExtra("widget_destination")

                    val startDestination = when {
                        // Widget deep-link overrides — map to the workspace detail route
                        // (user must already be logged in for widgets to send these)
                        widgetDestination == "tasks"  && (savedUserId != -1 || loggedInUserId != 0L) -> "dashboard"
                        widgetDestination == "notes"  && (savedUserId != -1 || loggedInUserId != 0L) -> "dashboard"
                        widgetDestination == "dm"     && (savedUserId != -1 || loggedInUserId != 0L) -> "dashboard"
                        widgetDestination == "files"  && (savedUserId != -1 || loggedInUserId != 0L) -> "dashboard"
                        widgetDestination == "workspaces" && (savedUserId != -1 || loggedInUserId != 0L) -> "dashboard"
                        savedUserId != -1 || loggedInUserId != 0L -> "dashboard"
                        else -> "onboarding"
                    }

                    LaunchedEffect(savedUserId) {
                        if (savedUserId != -1) {
                            dashboardViewModel.updateUserId(savedUserId)
                            dmViewModel.initWebSocketConnection(AppConfig.BASE_URL, savedUserId.toLong())
                            com.collabsphere.app.remote.fcm.FcmTokenRegistrar.syncCurrentToken(savedUserId)
                        }
                    }

                    LaunchedEffect(loggedInUserId) {
                        if (loggedInUserId != 0L && loggedInUserId.toInt() != savedUserId) {
                            userPreferences.saveUserId(loggedInUserId.toInt())
                            dashboardViewModel.updateUserId(loggedInUserId.toInt())
                            dmViewModel.initWebSocketConnection(AppConfig.BASE_URL, loggedInUserId)
                            com.collabsphere.app.remote.fcm.FcmTokenRegistrar.syncCurrentToken(loggedInUserId.toInt())
                        }
                    }

                    AppNavigation(
                        loginViewModel = loginViewModel,
                        dashboardViewModel = dashboardViewModel,
                        notificationsViewModel = notificationsViewModel,
                        notificationHelper = notificationHelper,
                        startDestination = startDestination,
                        notificationDeepLink = notificationDeepLink,
                        onDeepLinkConsumed = { notificationDeepLink = null }
                    )
                }
            }
        }
    }
}