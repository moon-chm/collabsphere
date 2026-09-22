package com.collabsphere.app.view

import android.widget.Toast
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import com.collabsphere.app.NotificationDeepLink
import com.collabsphere.app.NotificationHelper
import com.collabsphere.app.model.UserRepo
import com.collabsphere.app.model.channels.ChannelRepo
import com.collabsphere.app.model.file.FileRepo
import com.collabsphere.app.model.notes.NotesRepo
import com.collabsphere.app.model.task.TaskRepo
import com.collabsphere.app.model.workspace.WorkspaceRepo
import com.collabsphere.app.model.message.MessageRepo
import com.collabsphere.app.model.dm.DmRepo
import com.collabsphere.app.view.DashboardScreen
import com.collabsphere.app.view.ProfileUI.ProfileRoute
import com.collabsphere.app.view.ProfileUI.BlockedUsersScreen
import com.collabsphere.app.view.UserUI.PublicProfileScreen
import com.collabsphere.app.view.UserUI.UserSearchScreen
import com.collabsphere.app.viewmodel.BlockedUsersViewModel
import com.collabsphere.app.viewmodel.PublicProfileViewModel
import com.collabsphere.app.viewmodel.UserSearchViewModel
import com.collabsphere.app.viewmodel.NotificationsViewModel
import com.collabsphere.app.view.NotificationUI.NotificationsScreen
import com.collabsphere.app.view.WorkspaceUI.CreateWorkspaceScreen
import com.collabsphere.app.view.WorkspaceUI.DeleteWorkspaceScreen
import com.collabsphere.app.view.WorkspaceUI.WorkspaceDetailedScreen
import com.collabsphere.app.view.WorkspaceUI.WorkspaceAction
import com.collabsphere.app.view.MessageUI.MessageScreen
import com.collabsphere.app.view.dmUI.DMScreen
import com.collabsphere.app.view.OnboardingScreen
import com.collabsphere.app.viewmodel.DashboardViewModel
import com.collabsphere.app.viewmodel.LoginViewModel
import com.collabsphere.app.viewmodel.channel.ChannelViewModel
import com.collabsphere.app.viewmodel.file.FileViewModel
import com.collabsphere.app.viewmodel.notes.NotesViewModel
import com.collabsphere.app.viewmodel.profile.ProfileViewModel
import com.collabsphere.app.viewmodel.task.TaskViewModel
import com.collabsphere.app.viewmodel.workspace.WorkspaceViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.collabsphere.app.viewmodel.message.MessageViewModel
import com.collabsphere.app.viewmodel.dm.DmViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.compose.KoinContext
import org.koin.compose.koinInject

@Composable
fun AppNavigation(
    loginViewModel: LoginViewModel,
    notificationHelper: NotificationHelper,
    dashboardViewModel: DashboardViewModel,
    notificationsViewModel: NotificationsViewModel,
    startDestination: String = "login",
    notificationDeepLink: NotificationDeepLink? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    KoinContext {
        val navController = rememberNavController()
        val isLoggedIn by loginViewModel.isLoggedIn.collectAsStateWithLifecycle()
        val loggedInUserId by loginViewModel.loggedInUserId.collectAsStateWithLifecycle()
        val loggedInUsername by loginViewModel.loggedInUserName.collectAsStateWithLifecycle()
        val loggedInUserEmail by loginViewModel.loggedInUserEmail.collectAsStateWithLifecycle()
        val loggedInAvatarUrl by loginViewModel.loggedInAvatarUrl.collectAsStateWithLifecycle()
        val unreadNotificationCount by notificationsViewModel.unreadCount.collectAsStateWithLifecycle()
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
                if (currentRoute != "login" && currentRoute != "register"
                    && currentRoute != "onboarding" && currentRoute != null
                ) {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }

        LaunchedEffect(notificationDeepLink, isLoggedIn) {
            val deepLink = notificationDeepLink
            if (deepLink != null && isLoggedIn) {
                if (deepLink.notificationId > 0) {
                    notificationsViewModel.onMarkRead(listOf(deepLink.notificationId))
                }
                val workspaces = dashboardViewModel.workspaces.value
                val matchedWs = workspaces?.find { it.id == deepLink.workspaceId }
                val wsName = matchedWs?.workspaceName ?: deepLink.senderName.ifEmpty { "Workspace" }
                val encodedWsName = URLEncoder.encode(wsName, StandardCharsets.UTF_8.toString())

                if (deepLink.partnerId != -1) {
                    navController.navigate("workspace_detailed/${deepLink.workspaceId}/$encodedWsName?initialTab=4&initialPartnerId=${deepLink.partnerId}")
                } else {
                    navController.navigate("workspace_detailed/${deepLink.workspaceId}/$encodedWsName?initialTab=${deepLink.targetTab}")
                }
                onDeepLinkConsumed()
            }
        }

        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(220))
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    targetOffset = { it / 4 }
                ) + fadeOut(animationSpec = tween(220))
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    initialOffset = { -it / 4 }
                ) + fadeIn(animationSpec = tween(200))
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(200))
            }
        ) {
            composable("onboarding") {
                OnboardingScreen(
                    onFinish = {
                        navController.navigate("login") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }

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

            composable(
                route = "register",
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(200))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(200))
                }
            ) {
                RegistrationScreen(
                    viewModel = loginViewModel,
                    onNavigateToLogin = { navController.popBackStack() }
                )
            }

            composable(
                route = "dashboard",
                enterTransition = {
                    fadeIn(animationSpec = tween(350)) + scaleIn(
                        initialScale = 0.95f,
                        animationSpec = tween(350, easing = FastOutSlowInEasing)
                    )
                }
            ) {
                val workspaceViewModel: WorkspaceViewModel =
                    koinViewModel { parametersOf(loggedInUserId.toInt()) }
                val joinByCodeStatus by workspaceViewModel.joinByCodeStatus.collectAsStateWithLifecycle()

                DashboardScreen(
                    viewModel = dashboardViewModel,
                    currentUserName = loggedInUsername,
                    avatarUrl = loggedInAvatarUrl,
                    onNavigateToWorkspace = {
                        if (navController.currentDestination?.route == "dashboard") {
                            navController.navigate("workspace_create")
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
                    },
                    onSearchClick = {
                        if (navController.currentDestination?.route == "dashboard") {
                            navController.navigate("user_search")
                        }
                    },
                    onNotificationsClick = {
                        if (navController.currentDestination?.route == "dashboard") {
                            navController.navigate("notifications")
                        }
                    },
                    unreadNotificationCount = unreadNotificationCount,
                    onJoinByCode = { code ->
                        workspaceViewModel.joinByCode(code)
                    },
                    joinByCodeStatus = joinByCodeStatus,
                    onClearJoinByCodeStatus = {
                        workspaceViewModel.clearJoinByCodeStatus()
                    }
                )
            }

            composable("notifications") {
                val workspaceViewModel: WorkspaceViewModel =
                    koinViewModel { parametersOf(loggedInUserId.toInt()) }

                NotificationsScreen(
                    viewModel = notificationsViewModel,
                    onBack = { navController.popBackStack() },
                    onNotificationClick = { notification ->
                        val workspaceId = notification.workspaceId
                        if (workspaceId != null) {
                            val wsName = dashboardViewModel.workspaces.value
                                ?.find { it.id == workspaceId }?.workspaceName ?: "Workspace"
                            val encodedWsName = URLEncoder.encode(wsName, StandardCharsets.UTF_8.toString())
                            val tab = if (notification.type == "TASK_ASSIGNED" || notification.type == "TASK_UPDATED") 1 else 0
                            navController.navigate("workspace_detailed/$workspaceId/$encodedWsName?initialTab=$tab")
                        }
                    },
                    onAcceptInvitation = { invitationId, notificationId ->
                        workspaceViewModel.acceptInvitation(invitationId) {
                            notificationsViewModel.onDelete(notificationId)
                        }
                    },
                    onDeclineInvitation = { invitationId, notificationId ->
                        workspaceViewModel.declineInvitation(invitationId) {
                            notificationsViewModel.onDelete(notificationId)
                        }
                    }
                )
            }

            composable("workspace_main") {
                val workspaceViewModel: WorkspaceViewModel =
                    koinViewModel { parametersOf(loggedInUserId.toInt()) }
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
                val userPreferences = koinInject<com.collabsphere.app.UserPreferences>()
                val sessionManager = koinInject<com.collabsphere.app.SessionManager>()
                val userRepo = koinInject<UserRepo>()

                val profileViewModel: ProfileViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return ProfileViewModel(
                                loggedInUserId = loggedInUserId.toInt(),
                                userEmail = loggedInUserEmail,
                                repo = userRepo,
                                userPreferences = userPreferences,
                                sessionManager = sessionManager
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
                    onAvatarUpdated = { newAvatarUrl ->
                        loginViewModel.updateLoggedInAvatarUrl(newAvatarUrl)
                    },
                    onNavigateToBlockedUsers = {
                        navController.navigate("blocked_users")
                    },
                    onLogoutComplete = {
                        dashboardViewModel.logout()
                        loginViewModel.logout()
                    }
                )
            }

            composable("user_search") {
                val userRepo = koinInject<UserRepo>()
                val searchViewModel: UserSearchViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return UserSearchViewModel(userRepo) as T
                        }
                    }
                )
                UserSearchScreen(
                    viewModel = searchViewModel,
                    onBack = { navController.popBackStack() },
                    onUserClick = { userId ->
                        navController.navigate("public_profile/$userId")
                    }
                )
            }

            composable("blocked_users") {
                val userRepo = koinInject<UserRepo>()
                val blockedUsersViewModel: BlockedUsersViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return BlockedUsersViewModel(userRepo) as T
                        }
                    }
                )
                BlockedUsersScreen(
                    viewModel = blockedUsersViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "public_profile/{userId}",
                arguments = listOf(navArgument("userId") { type = NavType.IntType })
            ) { backStackEntry ->
                val targetUserId = backStackEntry.arguments?.getInt("userId") ?: 0
                val userRepo = koinInject<UserRepo>()
                val publicProfileViewModel: PublicProfileViewModel = viewModel(
                    key = "public_profile_$targetUserId",
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return PublicProfileViewModel(targetUserId, userRepo) as T
                        }
                    }
                )
                PublicProfileScreen(
                    viewModel = publicProfileViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "workspace_detailed/{workspaceId}/{workspaceName}?initialTab={initialTab}&initialPartnerId={initialPartnerId}",
                arguments = listOf(
                    navArgument("workspaceId") { type = NavType.IntType },
                    navArgument("workspaceName") { type = NavType.StringType },
                    navArgument("initialTab") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                    navArgument("initialPartnerId") {
                        type = NavType.IntType
                        defaultValue = -1
                    }
                )
            ) { backStackEntry ->
                val workspaceId = backStackEntry.arguments?.getInt("workspaceId") ?: 0
                val rawWorkspaceName = backStackEntry.arguments?.getString("workspaceName") ?: ""
                val initialTab = backStackEntry.arguments?.getInt("initialTab") ?: 0
                val rawPartnerId = backStackEntry.arguments?.getInt("initialPartnerId") ?: -1
                val initialPartnerId = if (rawPartnerId != -1) rawPartnerId else null
                val workspaceName = try {
                    URLDecoder.decode(rawWorkspaceName, StandardCharsets.UTF_8.toString())
                } catch (e: Exception) {
                    rawWorkspaceName
                }

                val workspaceViewModel: WorkspaceViewModel =
                    koinViewModel { parametersOf(loggedInUserId.toInt()) }
                val workspaceStatus by workspaceViewModel.workspaceStatus.collectAsStateWithLifecycle()
                val workspaceMembers by workspaceViewModel.workspaceMembers.collectAsStateWithLifecycle()

                val workspaceRepo = koinInject<WorkspaceRepo>()
                val channelRepo = koinInject<ChannelRepo>()
                val notesRepo = koinInject<NotesRepo>()
                val taskRepo = koinInject<TaskRepo>()
                val fileRepo = koinInject<FileRepo>()
                val dmRepo = koinInject<DmRepo>()

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

                val invitationStatus by workspaceViewModel.invitationStatus.collectAsStateWithLifecycle()

                LaunchedEffect(workspaceStatus) {
                    workspaceStatus?.let {
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                        workspaceViewModel.clearWorkspaceStatus()
                    }
                }

                LaunchedEffect(invitationStatus) {
                    invitationStatus?.let {
                        Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                        workspaceViewModel.clearInvitationStatus()
                    }
                }

                WorkspaceDetailedScreen(
                    workspaceName = workspaceName,
                    userId = loggedInUserId,
                    workspaceId = workspaceId,
                    initialTab = initialTab,
                    initialPartnerId = initialPartnerId,
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

                val messageRepo = koinInject<MessageRepo>()
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
                    koinViewModel { parametersOf(loggedInUserId.toInt()) }
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
                    koinViewModel { parametersOf(loggedInUserId.toInt()) }
                DeleteWorkspaceScreen(
                    viewModel = workspaceViewModel,
                    workspaceNameToDelete = workspaceName,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}