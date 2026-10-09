package com.collabsphere

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.transactions.transaction
import com.collabsphere.model.*
import java.net.URI
import org.slf4j.LoggerFactory

object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    @Volatile
    private var initialized = false

    lateinit var writeDatabase: Database
        private set
    
    lateinit var readDatabase: Database
        private set

    /**
     * Idempotent: a second call (tests start the application module many times in one JVM) reuses the
     * existing pool instead of opening another one that is never closed.
     */
    @Synchronized
    fun init() {
        if (initialized) return
        val writeConfig = createHikariConfig(
            envUrlKey = "DATABASE_URL",
            envJdbcKey = "JDBC_DATABASE_URL",
            envPoolSizeKey = "DB_MAX_POOL_SIZE",
            defaultPoolSize = 25
        )
        val writeDataSource = HikariDataSource(writeConfig).apply {
            metricRegistry = io.micrometer.core.instrument.Metrics.globalRegistry
        }
        writeDatabase = Database.connect(writeDataSource)

        val readConfig = createHikariConfig(
            envUrlKey = "DATABASE_READ_URL",
            envJdbcKey = "JDBC_DATABASE_READ_URL",
            envPoolSizeKey = "DB_READ_POOL_SIZE",
            defaultPoolSize = 15
        ).apply {
            isReadOnly = true
        }
        
        // If no read replica is configured, we fall back to the primary write database
        if (readConfig.jdbcUrl.isNullOrBlank()) {
            readDatabase = writeDatabase
        } else {
            val readDataSource = HikariDataSource(readConfig)
            // Optionally could bind metrics for read pool here as well, omitting to avoid naming collision in micrometer unless tagged
            readDatabase = Database.connect(readDataSource)
        }

        transaction(writeDatabase) {
            if (GitHubCommitsTable.exists()) {
                exec(
                    "DELETE FROM github_commits a USING github_commits b " +
                        "WHERE a.id > b.id AND a.repository_id = b.repository_id AND a.sha = b.sha"
                )
            }
            if (GitHubPullRequestsTable.exists()) {
                exec(
                    "DELETE FROM github_pull_requests a USING github_pull_requests b " +
                        "WHERE a.id < b.id AND a.repository_id = b.repository_id AND a.github_pr_id = b.github_pr_id"
                )
            }
            SchemaUtils.createMissingTablesAndColumns(
                UsersTable,
                UserFcmTokensTable,
                WorkspacesTable,
                ChannelsTable,
                LocalFilesTable,
                MessageTable,
                NotesTable,
                TasksTable,
                DirectMessagesTable,
                DmReactionsTable,
                ChannelReactionsTable,
                NotificationMutesTable,
                ChannelReadStateTable,
                WorkspaceMembersTable,
                WorkspaceMembershipStateTable,
                UserBlocksTable,
                UserVerificationTable,
                NotificationsTable,
                PasswordResetTable,
                WorkspaceInvitationsTable,
                GitHubConnectionsTable,
                GitHubRepositoriesTable,
                GitHubCommitsTable,
                GitHubPullRequestsTable,
                GitHubContributorsTable,
                GitHubTaskLinksTable,
                GitHubIssuesTable,
                GitHubCheckSuitesTable,
                GitHubReleasesTable,
                GitHubWebhookEventsTable,
                GitHubActionIdempotencyTable,
                GitHubIdentityMappingTable,
                GitHubAssigneeSyncTable,
                GitHubCodeReferencesTable,
                GitHubCodeCacheTable
            )
            SyncSchema.install(this)
            com.collabsphere.util.GitHubBot.ensureExists()
        }
        // Separate transaction: if legacy rows already contain case-variant duplicates the CREATE
        // fails, and in Postgres a failed statement poisons the whole transaction it runs in.
        try {
            transaction(writeDatabase) {
                exec("CREATE UNIQUE INDEX IF NOT EXISTS users_email_lower_unique ON users (lower(email))")
            }
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            logger.error(
                "[DatabaseFactory] Could not create unique index on lower(users.email) — duplicate emails exist " +
                    "and must be merged manually. Registration still checks for duplicates, but concurrent sign-ups can race.",
                e
            )
        }
        initialized = true
    }

    private fun createHikariConfig(
        envUrlKey: String,
        envJdbcKey: String,
        envPoolSizeKey: String,
        defaultPoolSize: Int
    ): HikariConfig {
        val rawDatabaseUrl = System.getenv(envUrlKey)
        val rawJdbcUrl = System.getenv(envJdbcKey)
        
        return HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"

            if (!rawJdbcUrl.isNullOrBlank()) {
                jdbcUrl = rawJdbcUrl
            } else if (!rawDatabaseUrl.isNullOrBlank()) {
                try {
                    val uri = URI(rawDatabaseUrl.replace("postgresql://", "postgres://"))
                    val userInfo = uri.userInfo?.split(":")
                    val dbUser = userInfo?.getOrNull(0) ?: "postgres"
                    val dbPass = userInfo?.getOrNull(1) ?: ""
                    // Neon pooler (-pooler) causes session search_path to be empty; strip to use direct endpoint with HikariCP
                    val host = (uri.host ?: "localhost").replace("-pooler", "")
                    val port = if (uri.port != -1) uri.port else 5432
                    val dbName = uri.path?.removePrefix("/") ?: "Collabsphere"

                    val queryParams = (uri.query ?: "")
                        .split("&")
                        .filter { it.isNotBlank() && !it.startsWith("currentSchema=") }
                        .toMutableList()
                    if (queryParams.none { it.startsWith("sslmode=") }) {
                        queryParams.add("sslmode=require")
                    }
                    queryParams.add("currentSchema=public")
                    val query = "?" + queryParams.joinToString("&")

                    jdbcUrl = "jdbc:postgresql://$host:$port/$dbName$query"
                    username = dbUser
                    password = dbPass
                } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                    logger.error("Failed to parse $envUrlKey, falling back to raw string: ${e.message}")
                    jdbcUrl = rawDatabaseUrl
                }
            } else {
                if (envUrlKey == "DATABASE_URL") {
                    // Local development fallback only for primary
                    jdbcUrl = "jdbc:postgresql://localhost:5432/Collabsphere"
                    username = System.getenv("DB_USER") ?: "postgres"
                    password = System.getenv("DB_PASSWORD") ?: "root"
                } else {
                    jdbcUrl = ""
                }
            }

            if (!jdbcUrl.isNullOrBlank()) {
                maximumPoolSize = System.getenv(envPoolSizeKey)?.toIntOrNull() ?: defaultPoolSize
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                connectionTimeout = 30000
                schema = "public"
                connectionInitSql = "SET search_path TO public;"
                maxLifetime = System.getenv("DB_MAX_LIFETIME_MS")?.toLongOrNull() ?: 1800000L // 30 mins
                idleTimeout = System.getenv("DB_IDLE_TIMEOUT_MS")?.toLongOrNull() ?: 600000L // 10 mins
            }
        }
    }
}
