package plugins

import com.collabsphere.DatabaseFactory
import com.collabsphere.SyncSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.postgresql.PGConnection
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties

internal data class SyncSignal(val workspaceId: Int, val scope: String, val affectedUserId: Int? = null) {
    companion object {
        private val allowedScopes = SyncSchema.NOTIFY_TARGETS.map { it.scope }.toSet()

        fun parse(payload: String): SyncSignal? {
            val parts = payload.split(':')
            if (parts.size !in 2..3) return null
            val workspaceId = parts[0].toIntOrNull()?.takeIf { it > 0 } ?: return null
            val scope = parts[1].takeIf { it in allowedScopes } ?: return null
            val affected = if (parts.size == 3) parts[2].toIntOrNull() ?: return null else null
            return SyncSignal(workspaceId, scope, affected)
        }
    }
}

@Serializable
internal data class WorkspaceSyncEvent(
    val action: String = "WORKSPACE_SYNC",
    val workspaceId: Int,
    val scopes: List<String>
)

@Serializable
internal data class SyncReadyEvent(val action: String = "SYNC_READY")

internal data class SyncDispatch(val workspaceId: Int, val scopes: List<String>, val extraRecipients: Set<Int>)

internal object WorkspaceSyncNotifier {
    private val logger = LoggerFactory.getLogger("WorkspaceSync")
    private const val COALESCE_MS = 250L
    private const val POLL_TIMEOUT_MS = 5_000
    private const val KEEPALIVE_MS = 60_000L
    private const val RECONNECT_DELAY_MS = 5_000L

    private val json = Json { encodeDefaults = true }
    private val signals = Channel<SyncSignal>(capacity = 2_048)

    val readyFrame: String = json.encodeToString(SyncReadyEvent())

    fun group(batch: Collection<SyncSignal>): List<SyncDispatch> =
        batch.groupBy { it.workspaceId }.map { (workspaceId, items) ->
            SyncDispatch(
                workspaceId = workspaceId,
                scopes = items.map { it.scope }.distinct().sorted(),
                extraRecipients = items.mapNotNull { it.affectedUserId }.toSet()
            )
        }.sortedBy { it.workspaceId }

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) { listen() }
        scope.launch(Dispatchers.IO) { dispatchLoop() }
    }

    private suspend fun CoroutineScope.listen() {
        while (isActive) {
            var connection: Connection? = null
            try {
                connection = openListenerConnection()
                connection.createStatement().use { it.execute("LISTEN ${SyncSchema.NOTIFY_CHANNEL}") }
                val pg = connection.unwrap(PGConnection::class.java)
                logger.info("[WorkspaceSync] Listening for workspace changes")
                var lastKeepAlive = System.currentTimeMillis()
                while (isActive && !connection.isClosed) {
                    pg.getNotifications(POLL_TIMEOUT_MS)?.forEach { notification ->
                        SyncSignal.parse(notification.parameter)?.let { signals.trySend(it) }
                    }
                    if (System.currentTimeMillis() - lastKeepAlive >= KEEPALIVE_MS) {
                        connection.createStatement().use { it.execute("SELECT 1") }
                        lastKeepAlive = System.currentTimeMillis()
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.warn("[WorkspaceSync] Listener connection lost; clients fall back to polling until it reconnects: ${e.message}")
            } finally {
                runCatching { connection?.close() }
            }
            if (isActive) delay(RECONNECT_DELAY_MS)
        }
    }

    private fun openListenerConnection(): Connection {
        val source = DatabaseFactory.writeDataSource
        val properties = Properties().apply {
            source.username?.let { setProperty("user", it) }
            source.password?.let { setProperty("password", it) }
            setProperty("ApplicationName", "collabsphere-sync-listener")
        }
        return DriverManager.getConnection(source.jdbcUrl, properties).apply { autoCommit = true }
    }

    private suspend fun dispatchLoop() {
        for (first in signals) {
            val batch = mutableSetOf(first)
            delay(COALESCE_MS)
            while (true) batch += signals.tryReceive().getOrNull() ?: break
            group(batch).forEach { dispatch ->
                runCatching { deliver(dispatch) }
                    .onFailure { logger.warn("[WorkspaceSync] Delivery failed for workspace ${dispatch.workspaceId}: ${it.message}") }
            }
        }
    }

    private suspend fun deliver(dispatch: SyncDispatch) {
        if ("workspace" in dispatch.scopes) WorkspaceMemberCache.invalidate(dispatch.workspaceId)
        val members = dbReadQuery { workspaceMemberIds(dispatch.workspaceId) }
        val frame = json.encodeToString(WorkspaceSyncEvent(workspaceId = dispatch.workspaceId, scopes = dispatch.scopes))
        (members + dispatch.extraRecipients).distinct().forEach { userId ->
            WebSocketBroker.deliverToSyncCapable(userId.toLong(), frame)
        }
    }
}
