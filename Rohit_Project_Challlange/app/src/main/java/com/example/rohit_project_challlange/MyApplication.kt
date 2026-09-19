package com.example.rohit_project_challlange
 
import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.rohit_project_challlange.di.appModules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.android.ext.android.get
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class MyApplication : Application() {

    companion object {
        var isAppForeground: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()

        // ProcessLifecycleOwner already solves "is the app in the foreground" correctly (including
        // debouncing the brief onStop/onStart blip between activities during a screen rotation or
        // navigation) — no need to hand-roll a started-activity counter.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isAppForeground = true
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

        val userPreferences: UserPreferences = get()
        userPreferences.authTokenFlow
            .onEach { AuthTokenHolder.token = it }
            .launchIn(CoroutineScope(Dispatchers.IO))
    }
}