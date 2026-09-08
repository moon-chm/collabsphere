package com.example.rohit_project_challlange

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
import com.example.rohit_project_challlange.ui.theme.CollabSphereTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.rohit_project_challlange.model.UserRepo
import com.example.rohit_project_challlange.model.workspace.WorkspaceRepo
import com.example.rohit_project_challlange.model.channels.ChannelRepo
import com.example.rohit_project_challlange.model.file.FileRepo
import com.example.rohit_project_challlange.model.notes.NotesRepo
import com.example.rohit_project_challlange.model.task.TaskRepo
import com.example.rohit_project_challlange.model.message.MessageRepo
import com.example.rohit_project_challlange.model.dm.DmRepo
import com.example.rohit_project_challlange.view.AppNavigation
import com.example.rohit_project_challlange.viewmodel.LoginViewModel
import com.example.rohit_project_challlange.viewmodel.DashboardViewModel
import com.example.rohit_project_challlange.viewmodel.dm.DmViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.koin.core.parameter.parametersOf

data class NotificationDeepLink(
    val workspaceId: Int,
    val partnerId: Int,
    val senderName: String = "Member"
)

class MainActivity : ComponentActivity() {

    private val userPreferences: UserPreferences by inject()
    private val notificationHelper: NotificationHelper by inject()

    private val userRepo: UserRepo by inject()
    private val workspaceRepo: WorkspaceRepo by inject()
    private val channelRepo: ChannelRepo by inject()
    private val taskRepo: TaskRepo by inject()
    private val notesRepo: NotesRepo by inject()
    private val messageRepo: MessageRepo by inject()
    private val fileRepo: FileRepo by inject()
    private val dmRepo: DmRepo by inject()

    private val loginViewModel: LoginViewModel by viewModel()
    private val dmViewModel: DmViewModel by viewModel()

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
            val senderName = targetIntent.getStringExtra("sender_name") ?: "Member"
            if (workspaceId != -1 && partnerId != -1) {
                notificationDeepLink = NotificationDeepLink(workspaceId, partnerId, senderName)
            }
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

                    val fromNotification = intent?.getBooleanExtra("from_notification", false) ?: false
                    val widgetDestination = intent?.getStringExtra("widget_destination")

                    val startDestination = when {
                        fromNotification && (savedUserId != -1 || loggedInUserId != 0L) -> "dashboard"
                        // Widget deep-link overrides — map to the workspace detail route
                        // (user must already be logged in for widgets to send these)
                        widgetDestination == "tasks"  && (savedUserId != -1 || loggedInUserId != 0L) -> "dashboard"
                        widgetDestination == "notes"  && (savedUserId != -1 || loggedInUserId != 0L) -> "dashboard"
                        widgetDestination == "dm"     && (savedUserId != -1 || loggedInUserId != 0L) -> "dashboard"
                        widgetDestination == "files"  && (savedUserId != -1 || loggedInUserId != 0L) -> "dashboard"
                        savedUserId != -1 || loggedInUserId != 0L -> "dashboard"
                        else -> "onboarding"
                    }

                    LaunchedEffect(savedUserId) {
                        if (savedUserId != -1) {
                            dashboardViewModel.updateUserId(savedUserId)
                            dmViewModel.initWebSocketConnection(AppConfig.BASE_URL, savedUserId.toLong())
                        }
                    }

                    LaunchedEffect(loggedInUserId) {
                        if (loggedInUserId != 0L && loggedInUserId.toInt() != savedUserId) {
                            userPreferences.saveUserId(loggedInUserId.toInt())
                            dashboardViewModel.updateUserId(loggedInUserId.toInt())
                            dmViewModel.initWebSocketConnection(AppConfig.BASE_URL, loggedInUserId)
                        }
                    }

                    AppNavigation(
                        loginViewModel = loginViewModel,
                        userRepo = userRepo,
                        workspaceRepo = workspaceRepo,
                        channelRepo = channelRepo,
                        taskRepo = taskRepo,
                        notesRepo = notesRepo,
                        messageRepo = messageRepo,
                        fileRepo = fileRepo,
                        dmRepo = dmRepo,
                        dashboardViewModel = dashboardViewModel,
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