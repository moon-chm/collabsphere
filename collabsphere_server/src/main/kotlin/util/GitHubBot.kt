package com.collabsphere.util

import com.collabsphere.model.UsersTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

object GitHubBot {

    const val EMAIL = "github-bot@collabsphere.invalid"
    const val USERNAME = "GitHub"

    @Volatile
    private var cachedId: Int? = null

    fun ensureExists(): Int {
        cachedId?.let { return it }
        val existing = UsersTable.selectAll()
            .where { UsersTable.email eq EMAIL }
            .singleOrNull()
            ?.get(UsersTable.id)
        val id = existing ?: UsersTable.insert {
            it[UsersTable.email] = EMAIL
            it[UsersTable.username] = USERNAME
            it[UsersTable.password] = PasswordHasher.hash(UUID.randomUUID().toString())
            it[UsersTable.isEmailVerified] = false
            it[UsersTable.showEmail] = false
            it[UsersTable.showOnlineStatus] = false
            it[UsersTable.showLastSeen] = false
        }[UsersTable.id]
        cachedId = id
        return id
    }
}
