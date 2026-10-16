package com.collabsphere.app
 
import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.collabsphere.app.di.appModules
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.android.ext.android.get
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class MyApplication : Application(), ImageLoaderFactory {

    companion object {
        private val _isAppForegroundFlow = MutableStateFlow(false)
        // Reactive: sync loops suspend on this via `.first { it }` while backgrounded — a true
        // park (no polling/wakeups at all) that resumes the instant the app comes back, instead
        // of a cheap-but-still-periodic flag check.
        val isAppForegroundFlow: StateFlow<Boolean> = _isAppForegroundFlow.asStateFlow()

        var isAppForeground: Boolean
            get() = _isAppForegroundFlow.value
            private set(value) { _isAppForegroundFlow.value = value }

        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100 MB hard cap
                    .build()
            }
            .components {
                add(com.collabsphere.app.model.media.CloudinarySizingInterceptor())
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        com.collabsphere.app.model.media.VoiceNoteCache.init(this)
        com.collabsphere.app.model.SyncSignals.isSocketConnected = { com.collabsphere.app.remote.dm.DmWebSocketService.isWebSocketConnected }

        // ProcessLifecycleOwner already solves "is the app in the foreground" correctly (including
        // debouncing the brief onStop/onStart blip between activities during a screen rotation or
        // navigation) — no need to hand-roll a started-activity counter.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isAppForeground = true
                com.collabsphere.app.model.SyncSignals.onAppForeground()
                // Pick up Sphere Points earned elsewhere (e.g. a note a teammate pinned).
                com.collabsphere.app.model.progression.ProgressionEvents.requestRefresh()
            }

            override fun onStop(owner: LifecycleOwner) {
                isAppForeground = false
            }
        })

        startKoin {
            androidLogger()
            androidContext(this@MyApplication)
            workManagerFactory()
            modules(appModules)
        }

        get<com.collabsphere.app.model.progression.ProgressionRepo>().startAutoRefresh()

        val userPreferences: UserPreferences = get()
        // On startup: run the one-time migration from plaintext DataStore → EncryptedSharedPreferences,
        // then hydrate the in-memory holder so the Ktor interceptor can attach the token to the very
        // first request (before any ViewModel has had a chance to call saveAuthToken again).
        val authTokenSyncScope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
                Log.e("MyApplication", "Auth token migration failed", e)
            }
        )
        authTokenSyncScope.launch {
            val token = userPreferences.migrateTokenToEncryptedStore()
            AuthTokenHolder.token = token
        }
    }
}