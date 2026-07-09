package com.example.rohit_project_challlange.di

import androidx.room.Room
import androidx.work.WorkManager
import com.example.rohit_project_challlange.UserPreferences
import com.example.rohit_project_challlange.dataStore
import com.example.rohit_project_challlange.viewmodel.file.FileViewModel
import com.example.rohit_project_challlange.model.AppDatabase
import com.example.rohit_project_challlange.model.UserRepo
import com.example.rohit_project_challlange.model.workspace.WorkspaceRepo
import com.example.rohit_project_challlange.model.channels.ChannelRepo
import com.example.rohit_project_challlange.model.file.FileRepo
import com.example.rohit_project_challlange.model.notes.NotesRepo
import com.example.rohit_project_challlange.model.task.TaskRepo
import com.example.rohit_project_challlange.model.message.MessageRepo
import com.example.rohit_project_challlange.model.dm.DmRepo
import com.example.rohit_project_challlange.remote.login.LoginApiService
import com.example.rohit_project_challlange.remote.note.NoteApiService
import com.example.rohit_project_challlange.remote.channel.ChannelApiService
import com.example.rohit_project_challlange.remote.task.TaskApiService
import com.example.rohit_project_challlange.remote.message.MessageApiService
import com.example.rohit_project_challlange.remote.dm.DmApiService
import com.example.rohit_project_challlange.remote.worlspace.WorkspaceApiService
import com.example.rohit_project_challlange.viewmodel.LoginViewModel
import com.example.rohit_project_challlange.viewmodel.DashboardViewModel
import com.example.rohit_project_challlange.viewmodel.task.TaskViewModel
import com.example.rohit_project_challlange.viewmodel.dm.DmViewModel
import com.example.rohit_project_challlange.viewmodel.notes.NotesViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import com.example.rohit_project_challlange.NotificationHelper
import com.example.rohit_project_challlange.model.channels.ChannelSyncWorker
import com.example.rohit_project_challlange.model.dm.DmSyncWorker
import com.example.rohit_project_challlange.model.file.FileSyncWorker
import com.example.rohit_project_challlange.model.message.MessageSyncWorker
import com.example.rohit_project_challlange.model.notes.NotesSyncWorker
import com.example.rohit_project_challlange.model.task.TaskSyncWorker
import com.example.rohit_project_challlange.model.workspace.WorkspaceSyncWorker
import com.example.rohit_project_challlange.remote.file.FileApiService
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
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
        ).fallbackToDestructiveMigration().build()
    }

    single { get<AppDatabase>().userDao() }
    single { get<AppDatabase>().workspaceDao() }
    single { get<AppDatabase>().channelDao() }
    single { get<AppDatabase>().taskDao() }
    single { get<AppDatabase>().notesDao() }
    single { get<AppDatabase>().messageDao() }
    single { get<AppDatabase>().fileDao() }
    single { get<AppDatabase>().dmDao() }
}

val networkModule = module {
    single(named("RegularHttpClient")) {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                })
            }
        }
    }

    single(named("WebSocketHttpClient")) {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(WebSockets) {
                pingIntervalMillis = 15000
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
}

val repositoryModule = module {
    single { UserRepo(get(), get()) }
    single { WorkspaceRepo(get(), get(), get(), get()) }
    single { ChannelRepo(get(), get(), get(), get()) }
    single { TaskRepo(get(), get(), get(), get(), get(), get()) }
    single { NotesRepo(get(), get(), get(), get()) }
    single { MessageRepo(get(), get(), get(), get()) }
    single { FileRepo(get(), get(), get(), get()) }
    single { DmRepo(get(), get(), get()) }
}

val appModule = module {
    single { androidContext().dataStore }
    single { UserPreferences(get()) }
    single { NotificationHelper(androidContext()) }
    single { WorkManager.getInstance(androidContext()) }
}

val workerModule = module {
    worker { ChannelSyncWorker(get(), get(), get(), get(), get()) }
    worker { MessageSyncWorker(get(), get(), get(), get(), get()) }
    worker { NotesSyncWorker(get(), get(), get(), get(), get()) }
    worker { TaskSyncWorker(get(), get(), get(), get(), get()) }
    worker { WorkspaceSyncWorker(get(), get(), get(), get()) }
    worker { DmSyncWorker(get(), get(), get(), get()) }
    worker { FileSyncWorker(get(), get(), get(), get(), get()) }
}

val viewModelModule = module {
    viewModel { LoginViewModel(get(), get()) }

    viewModel { (loggedChannelId: Int) ->
        DashboardViewModel(
            repository = get(),
            loggedChannelId = loggedChannelId,
            userPreferences = get()
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
}

val appModules = listOf(databaseModule, networkModule, repositoryModule, appModule, workerModule, viewModelModule)