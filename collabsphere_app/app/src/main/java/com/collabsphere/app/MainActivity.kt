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
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.collabsphere.app.view.AppNavigation
import com.collabsphere.app.viewmodel.LoginViewModel
import com.collabsphere.app.viewmodel.GitHubAuthEvents
import com.collabsphere.app.viewmodel.DashboardViewModel
import com.collabsphere.app.viewmodel.dm.DmViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.koin.core.parameter.parametersOf
import kotlinx.coroutines.flow.map

private const val GITHUB_TAB_INDEX = 5

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
        GitHubAuthEvents.publish(intent.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        extractNotificationDeepLink(intent)
        if (savedInstanceState == null && GitHubAuthEvents.publish(intent?.data)) {
            GitHubAuthEvents.result.value?.workspaceId?.let { workspaceId ->
                notificationDeepLink = NotificationDeepLink(
                    workspaceId = workspaceId,
                    senderName = "",
                    targetTab = GITHUB_TAB_INDEX
                )
            }
        }
        checkAndRequestNotificationPermission()

        setContent {
            CollabSphereTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                ) {
                    // null = DataStore not yet loaded. Avoids the one-frame onboarding
                    // flash for logged-in users on cold start (#5).
                    val savedUserId: Int? by userPreferences.userIdFlow
                        .map<Int, Int?> { it }
                        .collectAsStateWithLifecycle(initialValue = null)
                    val onboardingDone: Boolean? by userPreferences.onboardingDoneFlow
                        .map<Boolean, Boolean?> { it }
                        .collectAsStateWithLifecycle(initialValue = null)
                    val loggedInUserId by loginViewModel.loggedInUserId.collectAsStateWithLifecycle(initialValue = 0L)

                    var showSplash by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }

                    // Show splash while DataStore is loading
                    if (savedUserId == null || onboardingDone == null) {
                        if (showSplash) {
                            com.collabsphere.app.view.SplashScreen(
                                onSplashFinished = { showSplash = false }
                            )
                        }
                        return@Surface
                    }

                    val resolvedUserId: Int = savedUserId!!
                    val currentUserId = if (loggedInUserId != 0L) loggedInUserId.toInt() else resolvedUserId

                    val dashboardViewModel: DashboardViewModel = koinViewModel {
                        parametersOf(currentUserId)
                    }

                    // Map widget_destination extras to tab indices (#7).
                    // Tab: 0=Spaces, 1=Tasks, 2=Files, 3=Docs, 4=Chat, 5=GitHub
                    val widgetDestination = intent?.getStringExtra("widget_destination")
                    val widgetTab: Int? = when (widgetDestination) {
                        "tasks"      -> 1
                        "files"      -> 2
                        "notes"      -> 3
                        "dm"         -> 4
                        "workspaces" -> 0
                        else         -> null
                    }
                    val isLoggedInForWidget = resolvedUserId != -1 || loggedInUserId != 0L

                    val startDestination = when {
                        widgetTab != null && isLoggedInForWidget -> "dashboard"
                        resolvedUserId != -1 || loggedInUserId != 0L -> "dashboard"
                        // Show onboarding only the first time; subsequent logged-out
                        // launches go straight to login (#5).
                        onboardingDone!! -> "login"
                        else -> "onboarding"
                    }

                    LaunchedEffect(Unit) {
                        SessionEvents.expiredToken.collect { token ->
                            if (!SessionEvents.claim(token)) return@collect
                            loginViewModel.logout()
                            Toast.makeText(
                                this@MainActivity,
                                "Your session expired. Please log in again.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    LaunchedEffect(resolvedUserId) {
                        if (resolvedUserId != -1) {
                            dashboardViewModel.updateUserId(resolvedUserId)
                            dmViewModel.initWebSocketConnection(AppConfig.BASE_URL, resolvedUserId.toLong())
                            com.collabsphere.app.remote.fcm.FcmTokenRegistrar.syncCurrentToken(resolvedUserId)
                        }
                    }

                    LaunchedEffect(loggedInUserId) {
                        if (loggedInUserId != 0L && loggedInUserId.toInt() != resolvedUserId) {
                            userPreferences.saveUserId(loggedInUserId.toInt())
                            dashboardViewModel.updateUserId(loggedInUserId.toInt())
                            dmViewModel.initWebSocketConnection(AppConfig.BASE_URL, loggedInUserId)
                            com.collabsphere.app.remote.fcm.FcmTokenRegistrar.syncCurrentToken(loggedInUserId.toInt())
                        }
                    }

                    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                        AppNavigation(
                            loginViewModel = loginViewModel,
                            dashboardViewModel = dashboardViewModel,
                            notificationsViewModel = notificationsViewModel,
                            notificationHelper = notificationHelper,
                            startDestination = startDestination,
                            notificationDeepLink = notificationDeepLink,
                            onDeepLinkConsumed = { notificationDeepLink = null },
                            widgetTab = if (isLoggedInForWidget) widgetTab else null
                        )

                        androidx.compose.animation.AnimatedVisibility(
                            visible = showSplash,
                            exit = androidx.compose.animation.fadeOut(
                                animationSpec = androidx.compose.animation.core.tween(500)
                            )
                        ) {
                            com.collabsphere.app.view.SplashScreen(
                                onSplashFinished = { showSplash = false }
                            )
                        }
                    }
                }
            }
        }
    }
}