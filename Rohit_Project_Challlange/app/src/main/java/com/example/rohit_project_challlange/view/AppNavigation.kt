package com.example.rohit_project_challlange.view

import android.widget.Toast
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.rohit_project_challlange.NotificationHelper
import com.example.rohit_project_challlange.model.UserRepo
import com.example.rohit_project_challlange.model.channels.ChannelRepo
import com.example.rohit_project_challlange.model.file.FileRepo
import com.example.rohit_project_challlange.model.notes.NotesRepo
import com.example.rohit_project_challlange.model.task.TaskRepo
import com.example.rohit_project_challlange.model.workspace.WorkspaceRepo
import com.example.rohit_project_challlange.model.message.MessageRepo
import com.example.rohit_project_challlange.model.dm.DmRepo
import com.example.rohit_project_challlange.view.DashboardScreen
import com.example.rohit_project_challlange.view.ProfileUI.ProfileRoute
import com.example.rohit_project_challlange.view.WorkspaceUI.CreateWorkspaceScreen
import com.example.rohit_project_challlange.view.WorkspaceUI.DeleteWorkspaceScreen
import com.example.rohit_project_challlange.view.WorkspaceUI.WorkspaceDetailedScreen
import com.example.rohit_project_challlange.view.WorkspaceUI.WorkspaceAction
import com.example.rohit_project_challlange.view.MessageUI.MessageScreen
import com.example.rohit_project_challlange.view.dmUI.DMScreen
import com.example.rohit_project_challlange.viewmodel.DashboardViewModel
import com.example.rohit_project_challlange.viewmodel.LoginViewModel
import com.example.rohit_project_challlange.viewmodel.channel.ChannelViewModel
import com.example.rohit_project_challlange.viewmodel.file.FileViewModel
import com.example.rohit_project_challlange.viewmodel.notes.NotesViewModel
import com.example.rohit_project_challlange.viewmodel.profile.ProfileViewModel
import com.example.rohit_project_challlange.viewmodel.task.TaskViewModel
import com.example.rohit_project_challlange.viewmodel.workspace.WorkspaceViewModel
import com.example.rohit_project_challlange.viewmodel.workspace.WorkspaceViewModelFactory
import com.example.rohit_project_challlange.viewmodel.message.MessageViewModel
import com.example.rohit_project_challlange.viewmodel.dm.DmViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.compose.KoinContext
import org.koin.compose.koinInject

@Composable
fun AppNavigation(
    loginViewModel: LoginViewModel,
    userRepo: UserRepo,
    workspaceRepo: WorkspaceRepo,
    channelRepo: ChannelRepo,
    taskRepo: TaskRepo,
    notesRepo: NotesRepo,
    messageRepo: MessageRepo,
    fileRepo: FileRepo,
    dmRepo: DmRepo,
    notificationHelper: NotificationHelper,
    dashboardViewModel: DashboardViewModel,
    startDestination: String = "login"
) {
    KoinContext {
        val navController = rememberNavController()
        val isLoggedIn by loginViewModel.isLoggedIn.collectAsStateWithLifecycle()
        val loggedInUserId by loginViewModel.loggedInUserId.collectAsStateWithLifecycle()
        val loggedInUsername by loginViewModel.loggedInUserName.collectAsStateWithLifecycle()
        val loggedInUserEmail by loginViewModel.loggedInUserEmail.collectAsStateWithLifecycle()
        val context = LocalContext.current

        LaunchedEffect(isLoggedIn) {
            val currentRoute = navController.currentDestination?.route
            if (isLoggedIn) {
                if (loggedInUserId > 0L) {
                    dashboardViewModel.updateUserId(loggedInUserId.toInt())
                }

                if (currentRoute == "login" || currentRoute == "register" || currentRoute == null) {
                    navController.navigate("dashboard") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            } else {
                if (currentRoute != "login" && currentRoute != "register" && currentRoute != null) {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }

        val workspaceViewModelFactory = remember(loggedInUserId) {
            WorkspaceViewModelFactory(workspaceRepo, loggedInUserId.toInt())
        }

        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable("login") {
                LoginScreen(
                    viewModel = loginViewModel,
                    onNavigateToRegister = {
                        if (navController.currentDestination?.route == "login") {
                            navController.navigate("register")
                        }
                    }
                )
            }

            composable("register") {
                RegistrationScreen(
                    viewModel = loginViewModel,
                    onNavigateToLogin = { navController.popBackStack() }
                )
            }

            composable("dashboard") {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToWorkspace = {
                        if (navController.currentDestination?.route == "dashboard") {
                            navController.navigate("workspace_main")
                        }
                    },
                    onWorkspaceClick = { workspace ->
                        if (navController.currentDestination?.route == "dashboard") {
                            val encodedName = URLEncoder.encode(workspace.workspaceName, StandardCharsets.UTF_8.toString())
                            navController.navigate("workspace_detailed/${workspace.id}/$encodedName")
                        }
                    },
                    onDeleteWorkspaceClick = { workspace ->
                        if (navController.currentDestination?.route == "dashboard") {
                            val encodedName = URLEncoder.encode(workspace.workspaceName, StandardCharsets.UTF_8.toString())
                            navController.navigate("workspace_delete/$encodedName")
                        }
                    },
                    onProfileClick = {
                        if (navController.currentDestination?.route == "dashboard") {
                            navController.navigate("profile")
                        }
                    }
                )
            }

            composable("workspace_main") {
                val workspaceViewModel: WorkspaceViewModel =
                    viewModel(factory = workspaceViewModelFactory)
                WorkspaceAction(
                    viewModel = workspaceViewModel,
                    onNavigateToCreate = {
                        if (navController.currentDestination?.route == "workspace_main") {
                            navController.navigate("workspace_create")
                        }
                    }
                )
            }

            composable("profile") {
                val userPreferences = koinInject<com.example.rohit_project_challlange.UserPreferences>()

                val profileViewModel: ProfileViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return ProfileViewModel(
                                loggedInUserId = loggedInUserId.toInt(),
                                userEmail = loggedInUserEmail,
                                repo = userRepo,
                                userPreferences = userPreferences
                            ) as T
                        }
                    }
                )

                ProfileRoute(
                    viewModel = profileViewModel,
                    initialUserName = loggedInUsername,
                    onBack = { navController.popBackStack() },
                    onProfileUpdated = { newName ->
                        loginViewModel.updateLoggedInUserName(newName)
                    },
                    onLogoutComplete = {
                        dashboardViewModel.logout()
                        loginViewModel.logout()
                    }
                )
            }

            composable(
                route = "workspace_detailed/{workspaceId}/{workspaceName}",
                arguments = listOf(
                    navArgument("workspaceId") { type = NavType.IntType },
                    navArgument("workspaceName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val workspaceId = backStackEntry.arguments?.getInt("workspaceId") ?: 0
                val rawWorkspaceName = backStackEntry.arguments?.getString("workspaceName") ?: ""
                val workspaceName = try {
                    URLDecoder.decode(rawWorkspaceName, StandardCharsets.UTF_8.toString())
                } catch (e: Exception) {
                    rawWorkspaceName
                }

                val workspaceViewModel: WorkspaceViewModel =
                    viewModel(factory = workspaceViewModelFactory)
                val workspaceStatus by workspaceViewModel.workspaceStatus.collectAsStateWithLifecycle()
                val workspaceMembers by workspaceViewModel.workspaceMembers.collectAsStateWithLifecycle()

                val channelViewModel: ChannelViewModel = viewModel(
                    viewModelStoreOwner = backStackEntry,
                    key = "channel_vm_ws_$workspaceId",
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return ChannelViewModel(channelRepo, loggedInUserId.toInt(), workspaceId) as T
                        }
                    }
                )

                val notesViewModel: NotesViewModel = viewModel(
                    viewModelStoreOwner = backStackEntry,
                    key = "notes_vm_ws_$workspaceId",
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return NotesViewModel(notesRepo, loggedInUserId.toInt(), workspaceId) as T
                        }
                    }
                )

                val taskViewModel: TaskViewModel = viewModel(
                    viewModelStoreOwner = backStackEntry,
                    key = "task_vm_ws_$workspaceId",
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return TaskViewModel(taskRepo, loggedInUserId.toInt(), workspaceId) as T
                        }
                    }
                )

                val fileViewModel: FileViewModel = viewModel(
                    viewModelStoreOwner = backStackEntry,
                    key = "file_vm_ws_$workspaceId",
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return FileViewModel(
                                fileRepo,
                                loggedInUserId.toInt(),
                                workspaceId,
                                loggedInUsername
                            ) as T
                        }
                    }
                )

                val dmViewModel: DmViewModel = viewModel(
                    viewModelStoreOwner = backStackEntry,
                    key = "dm_vm_ws_$workspaceId",
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return DmViewModel(
                                repo = dmRepo,
                                workspaceRepo = workspaceRepo,
                                notificationHelper = notificationHelper,
                                context = context.applicationContext
                            ) as T
                        }
                    }
                )

                LaunchedEffect(workspaceStatus) {
                    workspaceStatus?.let {
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                        workspaceViewModel.clearWorkspaceStatus()
                    }
                }

                WorkspaceDetailedScreen(
                    workspaceName = workspaceName,
                    userId = loggedInUserId,
                    workspaceId = workspaceId,
                    channelViewModel = channelViewModel,
                    taskViewModel = taskViewModel,
                    notesViewModel = notesViewModel,
                    fileViewModel = fileViewModel,
                    dmViewModel = dmViewModel,
                    workspaceMembers = workspaceMembers,
                    onBack = {
                        navController.popBackStack()
                    },
                    onChannelClick = { channel ->
                        val encodedChannelName = URLEncoder.encode(channel.channelName, StandardCharsets.UTF_8.toString())
                        val encodedUserName = URLEncoder.encode(loggedInUsername, StandardCharsets.UTF_8.toString())
                        navController.navigate("channel_chat/${workspaceId}/${channel.id}/$encodedChannelName?userName=$encodedUserName")
                    },
                    onAddMemberSubmit = { email ->
                        workspaceViewModel.onJoinWorkspace(workspaceId, email)
                    }
                )
            }

            composable(
                route = "channel_chat/{workspaceId}/{channelId}/{channelName}?userName={userName}",
                arguments = listOf(
                    navArgument("workspaceId") { type = NavType.IntType },
                    navArgument("channelId") { type = NavType.IntType },
                    navArgument("channelName") { type = NavType.StringType },
                    navArgument("userName") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val workspaceId = backStackEntry.arguments?.getInt("workspaceId") ?: 0
                val channelId = backStackEntry.arguments?.getInt("channelId") ?: 0

                val rawChannelName = backStackEntry.arguments?.getString("channelName") ?: ""
                val channelName = try {
                    URLDecoder.decode(rawChannelName, StandardCharsets.UTF_8.toString())
                } catch (e: Exception) {
                    rawChannelName
                }

                val rawUserName = backStackEntry.arguments?.getString("userName") ?: loggedInUsername
                val userName = try {
                    URLDecoder.decode(rawUserName, StandardCharsets.UTF_8.toString())
                } catch (e: Exception) {
                    rawUserName
                }

                val workspaceParentEntry = remember(backStackEntry) {
                    try {
                        navController.getBackStackEntry("workspace_detailed/{workspaceId}/{workspaceName}")
                    } catch (e: Exception) {
                        backStackEntry
                    }
                }

                val messageViewModel: MessageViewModel = viewModel(
                    viewModelStoreOwner = workspaceParentEntry,
                    key = "message_vm_ch_$channelId",
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return MessageViewModel(
                                messageRepo,
                                loggedInUserId.toInt(),
                                workspaceId,
                                channelId,
                                userName
                            ) as T
                        }
                    }
                )

                MessageScreen(
                    viewModel = messageViewModel,
                    channelName = channelName,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("workspace_create") {
                val workspaceViewModel: WorkspaceViewModel =
                    viewModel(factory = workspaceViewModelFactory)
                CreateWorkspaceScreen(
                    viewModel = workspaceViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "workspace_delete/{workspaceName}",
                arguments = listOf(
                    navArgument("workspaceName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val rawWorkspaceName = backStackEntry.arguments?.getString("workspaceName") ?: ""
                val workspaceName = try {
                    URLDecoder.decode(rawWorkspaceName, StandardCharsets.UTF_8.toString())
                } catch (e: Exception) {
                    rawWorkspaceName
                }

                val workspaceViewModel: WorkspaceViewModel =
                    viewModel(factory = workspaceViewModelFactory)
                DeleteWorkspaceScreen(
                    viewModel = workspaceViewModel,
                    workspaceNameToDelete = workspaceName,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}