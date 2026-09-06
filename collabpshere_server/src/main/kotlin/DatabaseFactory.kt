package com.collabsphere

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
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
                    val host = uri.host ?: "localhost"
                    val port = if (uri.port != -1) uri.port else 5432
                    val dbName = uri.path?.removePrefix("/") ?: "Collabsphere"
                    val query = if (!uri.query.isNullOrBlank()) "?${uri.query}" else "?sslmode=require"

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
            validate()
        }

        val dataSource = HikariDataSource(config)
        val database = Database.connect(dataSource)

        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(
                UsersTable,
                WorkspacesTable,
                ChannelsTable,
                LocalFilesTable,
                MessageTable,
                NotesTable,
                TasksTable,
                DirectMessagesTable,
                WorkspaceMembersTable
            )
        }
    }
}