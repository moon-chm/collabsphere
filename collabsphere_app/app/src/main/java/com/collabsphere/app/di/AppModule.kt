package com.collabsphere.app.di

import androidx.room.Room
import androidx.work.WorkManager
import com.collabsphere.app.SessionManager
import com.collabsphere.app.UserPreferences
import com.collabsphere.app.dataStore
import com.collabsphere.app.viewmodel.file.FileViewModel
import com.collabsphere.app.model.AppDatabase
import com.collabsphere.app.model.UserRepo
import com.collabsphere.app.model.workspace.WorkspaceRepo
import com.collabsphere.app.model.channels.ChannelRepo
import com.collabsphere.app.model.file.FileRepo
import com.collabsphere.app.model.notes.NotesRepo
import com.collabsphere.app.model.task.TaskRepo
import com.collabsphere.app.model.message.MessageRepo
import com.collabsphere.app.model.dm.DmRepo
import com.collabsphere.app.remote.login.LoginApiService
import com.collabsphere.app.remote.note.NoteApiService
import com.collabsphere.app.remote.channel.ChannelApiService
import com.collabsphere.app.remote.task.TaskApiService
import com.collabsphere.app.remote.message.MessageApiService
import com.collabsphere.app.remote.dm.DmApiService
import com.collabsphere.app.remote.workspace.WorkspaceApiService
import com.collabsphere.app.viewmodel.LoginViewModel
import com.collabsphere.app.viewmodel.DashboardViewModel
import com.collabsphere.app.viewmodel.task.TaskViewModel
import com.collabsphere.app.viewmodel.dm.DmViewModel
import com.collabsphere.app.viewmodel.workspace.WorkspaceViewModel
import com.collabsphere.app.viewmodel.channel.ChannelViewModel
import com.collabsphere.app.viewmodel.message.MessageViewModel
import com.collabsphere.app.viewmodel.notes.NotesViewModel
import com.collabsphere.app.viewmodel.profile.ProfileViewModel
import com.collabsphere.app.viewmodel.GitHubViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.http.HttpStatusCode
import com.collabsphere.app.SessionEvents
import com.collabsphere.app.NotificationHelper
import com.collabsphere.app.model.channels.ChannelSyncWorker
import com.collabsphere.app.model.dm.DmSyncWorker
import com.collabsphere.app.model.file.FileSyncWorker
import com.collabsphere.app.model.message.MessageSyncWorker
import com.collabsphere.app.model.notes.NotesSyncWorker
import com.collabsphere.app.model.task.TaskSyncWorker
import com.collabsphere.app.model.workspace.WorkspaceSyncWorker
import com.collabsphere.app.remote.file.FileApiService
import com.collabsphere.app.remote.notification.NotificationApiService
import com.collabsphere.app.model.NotificationRepo
import com.collabsphere.app.viewmodel.NotificationsViewModel
import com.collabsphere.app.AuthTokenHolder
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.qualifier.named
import org.koin.dsl.module

val databaseModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "app_database"
        )
            // WAL mode: concurrent readers are never blocked by a writer.
            // Without this, every incoming WebSocket message write fully locks the
            // database and causes the UI Flow queries to stall — the main source of lag.
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .addMigrations(AppDatabase.MIGRATION_35_36, AppDatabase.MIGRATION_36_37, AppDatabase.MIGRATION_37_38, AppDatabase.MIGRATION_38_39, AppDatabase.MIGRATION_39_40, AppDatabase.MIGRATION_40_41)
            .build()
    }

    single { get<AppDatabase>().userDao() }
    single { get<AppDatabase>().workspaceDao() }
    single { get<AppDatabase>().channelDao() }
    single { get<AppDatabase>().taskDao() }
    single { get<AppDatabase>().notesDao() }
    single { get<AppDatabase>().messageDao() }
    single { get<AppDatabase>().fileDao() }
    single { get<AppDatabase>().dmDao() }
    single { get<AppDatabase>().dmReactionDao() }
}

val networkModule = module {
    single(named("RegularHttpClient")) {
        HttpClient(OkHttp) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                })
            }
            install(HttpTimeout) {
                // Render free tier cold-starts in 30–60 s — give it enough room
                requestTimeoutMillis = 60000
                connectTimeoutMillis = 60000
                socketTimeoutMillis  = 60000
            }
            install(DefaultRequest) {
                // Read lazily per-request — if captured at construction time the token
                // is null (client is built before login completes) and never refreshes.
                AuthTokenHolder.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            }
            HttpResponseValidator {
                validateResponse { response ->
                    if (response.status == HttpStatusCode.Unauthorized) {
                        val request = response.call.request
                        if (!request.url.encodedPath.endsWith("/api/login")) {
                            val sentToken = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
                            SessionEvents.onUnauthorized(sentToken)
                        }
                    }
                }
            }
        }
    }

    single(named("WebSocketHttpClient")) {
        HttpClient(OkHttp) {
            engine {
                config {
                    pingInterval(15, java.util.concurrent.TimeUnit.SECONDS)
                    retryOnConnectionFailure(true)
                }
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(WebSockets) {
                pingIntervalMillis = 15000
            }
            install(DefaultRequest) {
                AuthTokenHolder.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            }
        }
    }

    single { LoginApiService(get(named("RegularHttpClient"))) }
    single { WorkspaceApiService(get(named("RegularHttpClient"))) }
    single { TaskApiService(get(named("RegularHttpClient"))) }
    single { ChannelApiService(get(named("RegularHttpClient"))) }
    single { NoteApiService(get(named("RegularHttpClient"))) }
    single { MessageApiService(get(named("RegularHttpClient"))) }
    single { DmApiService(get(named("WebSocketHttpClient"))) }
    single { FileApiService(get(named("RegularHttpClient"))) }
    single { NotificationApiService(get(named("RegularHttpClient"))) }
}

val repositoryModule = module {
    single { UserRepo(get(), get(), get()) }
    single { WorkspaceRepo(get(), get(), get(), get()) }
    single { ChannelRepo(get(), get(), get(), get()) }
    single { TaskRepo(get(), get(), get(), get(), get(), get()) }
    single { NotesRepo(get(), get(), get(), get()) }
    single { MessageRepo(get(), get(), get(), get(), get()) }
    single { FileRepo(get(), get(), get(), get()) }
    single { DmRepo(get(), get(), get(), get()) }
    single { NotificationRepo(get()) }
}

val appModule = module {
    single { androidContext().dataStore }
    single { UserPreferences(get()) }
    single { SessionManager(androidContext(), get(), get()) }
    single { NotificationHelper(androidContext(), get()) }
    single { WorkManager.getInstance(androidContext()) }
    single { com.collabsphere.app.model.DraftStore(get()) }
}

val workerModule = module {
    worker { ChannelSyncWorker(get(), get(), get(), get(), get()) }
    worker { MessageSyncWorker(get(), get(), get(), get(), get()) }
    worker { NotesSyncWorker(get(), get(), get(), get(), get()) }
    worker { TaskSyncWorker(get(), get(), get(), get(), get()) }
    worker { WorkspaceSyncWorker(get(), get(), get(), get()) }
    worker { DmSyncWorker(get(), get(), get(), get(), get()) }
    worker { FileSyncWorker(get(), get(), get(), get(), get()) }
}

val viewModelModule = module {
    viewModel { LoginViewModel(get(), get(), get()) }

    viewModel { (initialUserId: Int) ->
        DashboardViewModel(
            repository = get(),
            initialUserId = initialUserId,
            userPreferences = get(),
            sessionManager = get()
        )
    }

    viewModel { (loggedInUserId: Int) ->
        WorkspaceViewModel(
            repo = get(),
            loggedInUserId = loggedInUserId
        )
    }

    viewModel { (loggedUserId: Int, loggedWorkspaceId: Int) ->
        ChannelViewModel(
            repo = get(),
            loggeduserID = loggedUserId,
            loggedWorkspaceId = loggedWorkspaceId
        )
    }

    viewModel { (loggedUserId: Int, loggedWorkspaceId: Int) ->
        TaskViewModel(
            repo = get(),
            loggedUserId = loggedUserId,
            loggedWorkspaceId = loggedWorkspaceId
        )
    }

    viewModel { (loggedUserId: Int, loggedWorkspaceId: Int) ->
        NotesViewModel(
            repo = get(),
            loggedUserId = loggedUserId,
            loggedWorkspaceId = loggedWorkspaceId
        )
    }

    viewModel { (loggedUserId: Int, loggedWorkspaceId: Int, loggedChannelId: Int, loggedUserName: String) ->
        MessageViewModel(
            repo = get(),
            loggedUserId = loggedUserId,
            loggedWorkspaceId = loggedWorkspaceId,
            loggedChannelId = loggedChannelId,
            loggedUserName = loggedUserName,
            draftStore = get()
        )
    }

    viewModel {
        DmViewModel(
            repo = get(),
            workspaceRepo = get(),
            notificationHelper = get(),
            context = androidContext()
        )
    }

    viewModel { (loggedUserId: Int, loggedWorkspaceId: Int, loggedUserName: String) ->
        FileViewModel(
            repo = get(),
            loggedUserId = loggedUserId,
            loggedWorkspaceId = loggedWorkspaceId,
            loggedUserName = loggedUserName
        )
    }

    viewModel { NotificationsViewModel(get()) }

    viewModel { (loggedInUserId: Int, userEmail: String) ->
        ProfileViewModel(
            loggedInUserId = loggedInUserId,
            userEmail = userEmail,
            repo = get(),
            userPreferences = get(),
            sessionManager = get()
        )
    }

    viewModel { GitHubViewModel(get(named("RegularHttpClient"))) }
}

val appModules = listOf(databaseModule, networkModule, repositoryModule, appModule, workerModule, viewModelModule)