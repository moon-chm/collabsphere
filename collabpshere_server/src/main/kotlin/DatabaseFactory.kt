package com.collabsphere

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import com.collabsphere.model.*

object DatabaseFactory {
    fun init() {
        val config = HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            jdbcUrl = "jdbc:postgresql://localhost:5432/Collabsphere"
            username = "postgres"
            password = "root"

            maximumPoolSize = 3
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        val dataSource = HikariDataSource(config)
        val database = Database.connect(dataSource)

        transaction(database) {
            SchemaUtils.drop(
                WorkspaceMembersTable,
                DirectMessagesTable,
                TasksTable,
                NotesTable,
                MessageTable,
                LocalFilesTable,
                ChannelsTable,
                WorkspacesTable,
                UsersTable
            )
            SchemaUtils.create(
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