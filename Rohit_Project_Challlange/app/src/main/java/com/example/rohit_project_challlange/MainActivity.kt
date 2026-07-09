package com.example.rohit_project_challlange

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import org.koin.core.parameter.parametersOf

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val savedUserId by userPreferences.userIdFlow.collectAsStateWithLifecycle(initialValue = -1)
                    val loggedInUserId by loginViewModel.loggedInUserId.collectAsStateWithLifecycle(initialValue = 0L)

                    val currentUserId = if (loggedInUserId != 0L) loggedInUserId.toInt() else savedUserId

                    val dashboardViewModel: DashboardViewModel = koinViewModel {
                        parametersOf(currentUserId)
                    }

                    val startDestination = if (savedUserId != -1 || loggedInUserId != 0L) {
                        "dashboard"
                    } else {
                        "login"
                    }

                    LaunchedEffect(savedUserId) {
                        if (savedUserId != -1) {
                            dashboardViewModel.updateUserId(savedUserId)
                            dmViewModel.initWebSocketConnection("http://10.238.3.93:8080", savedUserId.toLong())
                        }
                    }

                    LaunchedEffect(loggedInUserId) {
                        if (loggedInUserId != 0L && loggedInUserId.toInt() != savedUserId) {
                            userPreferences.saveUserId(loggedInUserId.toInt())
                            dashboardViewModel.updateUserId(loggedInUserId.toInt())
                            dmViewModel.initWebSocketConnection("http://10.238.3.93:8080", loggedInUserId)
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
                        startDestination = startDestination
                    )
                }
            }
        }
    }
}