package com.collabsphere

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.transactions.transaction
import com.collabsphere.model.*
import java.net.URI

object DatabaseFactory {
    fun init() {
        val rawDatabaseUrl = System.getenv("DATABASE_URL")
        val rawJdbcUrl = System.getenv("JDBC_DATABASE_URL")

        val config = HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"

            if (!rawJdbcUrl.isNullOrBlank()) {
                jdbcUrl = rawJdbcUrl
            } else if (!rawDatabaseUrl.isNullOrBlank()) {
                // Parse standard cloud postgres:// or postgresql:// URI
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
                    println("Failed to parse DATABASE_URL, falling back to raw string: ${e.message}")
                    jdbcUrl = rawDatabaseUrl
                }
            } else {
                // Local development fallback
                jdbcUrl = "jdbc:postgresql://localhost:5432/Collabsphere"
                username = System.getenv("DB_USER") ?: "postgres"
                password = System.getenv("DB_PASSWORD") ?: "root"
            }

            maximumPoolSize = System.getenv("DB_MAX_POOL_SIZE")?.toIntOrNull() ?: 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            connectionTimeout = 30000
            schema = "public"
            connectionInitSql = "SET search_path TO public;"
            validate()
        }

        val dataSource = HikariDataSource(config)
        val database = Database.connect(dataSource)

        transaction(database) {
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
                WorkspacesTable,
                ChannelsTable,
                LocalFilesTable,
                MessageTable,
                NotesTable,
                TasksTable,
                DirectMessagesTable,
                DmReactionsTable,
                WorkspaceMembersTable,
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
                GitHubCheckSuitesTable
            )
            com.collabsphere.util.GitHubBot.ensureExists()
        }
    }
}
