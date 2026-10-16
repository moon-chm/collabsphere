package com.collabsphere.app.model

import com.collabsphere.app.AppConfig
import com.collabsphere.app.MyApplication
import com.collabsphere.app.UserPreferences
import com.collabsphere.app.model.workspace.MemberDirectory
import com.collabsphere.app.model.workspace.WorkspaceDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class StartupPrefetcher(
    private val userPreferences: UserPreferences,
    private val workspaceDao: WorkspaceDao,
    private val memberDirectory: MemberDirectory
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        MyApplication.appScope.launch { warmUpServer() }
        MyApplication.appScope.launch {
            val userId = userPreferences.userIdFlow.first()
            if (userId <= 0) return@launch
            val workspaces = runCatching { workspaceDao.getAllWorkspacesForUser(userId).first() }.getOrDefault(emptyList())
            workspaces.filter { it.id > 0 }.take(MAX_WORKSPACES).forEach { workspace ->
                memberDirectory.observe(workspace.id).catch { }.collect()
            }
        }
    }

    private suspend fun warmUpServer() = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL("${AppConfig.BASE_URL.trimEnd('/')}/health").openConnection() as HttpURLConnection
            connection.connectTimeout = WARM_UP_TIMEOUT_MS
            connection.readTimeout = WARM_UP_TIMEOUT_MS
            connection.requestMethod = "GET"
            try {
                connection.responseCode
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        const val MAX_WORKSPACES = 5
        const val WARM_UP_TIMEOUT_MS = 45_000
    }
}
