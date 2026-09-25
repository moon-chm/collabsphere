package com.collabsphere.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.ReferenceOption

object UsersTable : Table("users") {
    val id = integer("id").autoIncrement()
    val email = varchar("email", 255)
    val password = varchar("password", 255)
    val username = varchar("user_name", 255)

    // Profile enrichment fields
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val bio = text("bio").nullable()
    val statusMessage = varchar("status_message", 255).nullable()
    val isEmailVerified = bool("is_email_verified").default(false)
    val lastSeen = long("last_seen").nullable()

    // Privacy settings
    val showEmail = bool("show_email").default(true)
    val showOnlineStatus = bool("show_online_status").default(true)
    val showLastSeen = bool("show_last_seen").default(true)
    val profileVisibility = varchar("profile_visibility", 20).default("public") // "public" | "members_only"
    val fcmToken = varchar("fcm_token", 500).nullable()

    override val primaryKey = PrimaryKey(id)
}

object WorkspacesTable : Table("workspace") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE).index()
    val workspaceName = varchar("workspace_name", 255)
    val workspaceOwner = varchar("workspace_owner", 255)
    val workspacePassword = varchar("workspace_password", 255)
    val isDeleted = bool("is_deleted").default(false)
    val updatedAt = long("updated_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey = PrimaryKey(id)
}

object WorkspaceMembersTable : Table("workspace_members") {
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE)
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE).index()
    val role = varchar("role", 10).default("MEMBER")

    override val primaryKey = PrimaryKey(workspaceId, userId)
}

object ChannelsTable : Table("channels") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val channelName = varchar("channel_name", 255)
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE).index()
    val description = text("description")
    val updatedAt = long("updated_at").clientDefault { System.currentTimeMillis() }
    val isDeleted = bool("is_deleted").default(false)

    override val primaryKey = PrimaryKey(id)
}

object LocalFilesTable : Table("local_files") {
    val id = long("id").autoIncrement()
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE).index()
    val userName = varchar("user_name", 255)
    val url = varchar("url", 500)
    val mimeType = varchar("mime_type", 100)
    val localPath = varchar("localpath", 500).nullable()
    val fileName = varchar("file_name", 255)
    val sizeBytes = long("sizebytes")
    val fileLocation = varchar("file_location", 500)
    val updatedAt = long("updated_at").clientDefault { System.currentTimeMillis() }
    val isDeleted = bool("is_deleted").default(false)

    override val primaryKey = PrimaryKey(id)
}

object MessageTable : Table("message") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE)
    val channelId = integer("channel_id").references(ChannelsTable.id, onDelete = ReferenceOption.CASCADE).index()
    val userName = varchar("user_name", 255)
    val content = text("content")
    val replyToId = integer("reply_to_id").nullable()
    val mediaUrl = varchar("media_url", 500).nullable()
    val pinnedAt = long("pinned_at").nullable()
    val pinnedByUserId = integer("pinned_by_user_id").nullable()
    val status = varchar("status", 50)
    val isDeleted = bool("is_deleted").default(false)
    val updatedAt = long("updated_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey = PrimaryKey(id)
}

object NotesTable : Table("notes") {
    val id = integer("id").autoIncrement()
    val userIdNotes = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE).index()
    val notesName = varchar("notes_name", 255)
    val notesDescription = text("description")
    val isPinned = bool("is_pinned").default(false)
    val isDeleted = bool("is_deleted").default(false)
    val updatedAt = long("updated_at").clientDefault { System.currentTimeMillis() }
    // Set by the client on an offline-created note so a WorkManager retry after a lost (but
    // successful) create response returns the existing row instead of inserting a duplicate.
    val idempotencyKey = varchar("idempotency_key", 64).nullable().uniqueIndex()

    override val primaryKey = PrimaryKey(id)
}

object TasksTable : Table("task") {
    val id = integer("id").autoIncrement()
    val createdByUserId = integer("created_by_user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val assignedToUserId = integer("assigned_to_user_id").references(UsersTable.id, onDelete = ReferenceOption.SET_NULL).nullable().index()
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE).index()
    val taskName = varchar("task_name", 255)
    val taskDescription = text("task_description")
    val status = varchar("status", 50)
    val dueDate = long("due_date").nullable()
    val priority = varchar("priority", 10).default("MEDIUM")
    val checklist = text("checklist").nullable()
    val labels = text("labels").nullable()
    val reminderSentAt = long("reminder_sent_at").nullable()
    val isDeleted = bool("is_deleted").default(false)
    val updatedAt = long("updated_at").clientDefault { System.currentTimeMillis() }
    // Set by the client on an offline-created task so a WorkManager retry after a lost (but
    // successful) create response returns the existing row instead of inserting a duplicate.
    val idempotencyKey = varchar("idempotency_key", 64).nullable().uniqueIndex()

    override val primaryKey = PrimaryKey(id)
}

object DirectMessagesTable : Table("direct_messages") {
    val id = integer("id").autoIncrement()
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE)
    val senderId = integer("sender_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE).index()
    val receiverId = integer("receiver_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE).index()
    val content = text("content")
    val mediaUrl = varchar("media_url", 500).nullable()
    val replyToId = integer("reply_to_id").nullable()
    val isRead = bool("is_read").default(false)
    val timestamp = long("timestamp")

    override val primaryKey = PrimaryKey(id)
}

object ChannelReadStateTable : Table("channel_read_state") {
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val channelId = integer("channel_id").references(ChannelsTable.id, onDelete = ReferenceOption.CASCADE).index()
    val lastReadMessageId = integer("last_read_message_id")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(userId, channelId)
}

object NotificationMutesTable : Table("notification_mutes") {
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE).index()
    val channelId = integer("channel_id").default(0)

    override val primaryKey = PrimaryKey(userId, workspaceId, channelId)
}

object ChannelReactionsTable : Table("channel_reactions") {
    val messageId = integer("message_id").references(MessageTable.id, onDelete = ReferenceOption.CASCADE).index()
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val emoji = varchar("emoji", 16)

    override val primaryKey = PrimaryKey(messageId, userId, emoji)
}

object DmReactionsTable : Table("dm_reactions") {
    val messageId = integer("message_id").references(DirectMessagesTable.id, onDelete = ReferenceOption.CASCADE).index()
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val emoji = varchar("emoji", 10)

    override val primaryKey = PrimaryKey(messageId, userId, emoji)
}

object UserBlocksTable : Table("user_blocks") {
    val blockerId = integer("blocker_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val blockedId = integer("blocked_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(blockerId, blockedId)
}

object UserVerificationTable : Table("user_verification") {
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val token = varchar("token", 255)
    val expiresAt = long("expires_at")

    override val primaryKey = PrimaryKey(userId)
}

object NotificationsTable : Table("notifications") {
    val id = integer("id").autoIncrement()
    val recipientId = integer("recipient_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE).index()
    val actorId = integer("actor_id").references(UsersTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val type = varchar("type", 30)          // DM | CHANNEL_MESSAGE | MENTION | TASK_ASSIGNED | TASK_UPDATED | WORKSPACE_INVITE
    val title = varchar("title", 255)
    val body = text("body")
    val workspaceId = integer("workspace_id").nullable()
    val referenceId = integer("reference_id").nullable()  // message id, task id, invitation id, etc.
    val isRead = bool("is_read").default(false)
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey = PrimaryKey(id)
}

object PasswordResetTable : Table("password_resets") {
    val email = varchar("email", 255)
    val otp = varchar("otp", 10)
    val expiresAt = long("expires_at")

    override val primaryKey = PrimaryKey(email)
}

object WorkspaceInvitationsTable : Table("workspace_invitations") {
    val id = integer("id").autoIncrement()
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE).index()
    val inviterUserId = integer("inviter_user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val inviteeEmail = varchar("invitee_email", 255).index()
    val inviteCode = varchar("invite_code", 16).index()
    val status = varchar("status", 20).default("PENDING") // PENDING | ACCEPTED | DECLINED
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
    val expiresAt = long("expires_at")

    override val primaryKey = PrimaryKey(id)
}

object GitHubConnectionsTable : Table("github_connections") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val githubUserId = long("github_user_id")
    val githubUsername = varchar("github_username", 255)
    val installationId = long("installation_id")
    val refreshTokenEncrypted = text("refresh_token_encrypted").nullable()
    val refreshTokenExpiresAt = long("refresh_token_expires_at").nullable()
    val accessTokenEncrypted = text("access_token_encrypted").nullable()
    val accessTokenExpiresAt = long("access_token_expires_at").nullable()
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
    val updatedAt = long("updated_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey = PrimaryKey(id)
}

object GitHubRepositoriesTable : Table("github_repositories") {
    val id = integer("id").autoIncrement()
    val connectionId = integer("connection_id").references(GitHubConnectionsTable.id, onDelete = ReferenceOption.CASCADE)
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE).index()
    val githubRepoId = long("github_repo_id")
    val owner = varchar("owner", 255)
    val name = varchar("name", 255)
    val fullName = varchar("full_name", 255)
    val isPrivate = bool("is_private")
    val htmlUrl = varchar("html_url", 500)
    val defaultBranch = varchar("default_branch", 255)
    val lastSyncedAt = long("last_synced_at").clientDefault { System.currentTimeMillis() }
    val notifyChannelId = integer("notify_channel_id").references(ChannelsTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val lastDigestAt = long("last_digest_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object GitHubCheckSuitesTable : Table("github_check_suites") {
    val id = integer("id").autoIncrement()
    val repositoryId = integer("repository_id").references(GitHubRepositoriesTable.id, onDelete = ReferenceOption.CASCADE).index()
    val githubSuiteId = long("github_suite_id")
    val headSha = varchar("head_sha", 40)
    val status = varchar("status", 20)
    val conclusion = varchar("conclusion", 30).nullable()
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("github_check_suites_unique", repositoryId, githubSuiteId)
        index(false, repositoryId, headSha)
    }
}

object GitHubIssuesTable : Table("github_issues") {
    val id = integer("id").autoIncrement()
    val repositoryId = integer("repository_id").references(GitHubRepositoriesTable.id, onDelete = ReferenceOption.CASCADE).index()
    val githubIssueId = long("github_issue_id")
    val number = integer("number")
    val title = varchar("title", 500)
    val state = varchar("state", 20)
    val authorUsername = varchar("author_username", 255)
    val url = varchar("url", 500)
    val createdAt = long("created_at")
    val closedAt = long("closed_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("github_issues_repo_issue_unique", repositoryId, githubIssueId)
    }
}

object GitHubTaskLinksTable : Table("github_task_links") {
    val id = integer("id").autoIncrement()
    val taskId = integer("task_id").references(TasksTable.id, onDelete = ReferenceOption.CASCADE).index()
    val repositoryId = integer("repository_id").references(GitHubRepositoriesTable.id, onDelete = ReferenceOption.CASCADE)
    val kind = varchar("kind", 16)
    val ref = varchar("ref", 64)
    val title = varchar("title", 500)
    val url = varchar("url", 500)
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("github_task_links_unique", taskId, kind, ref)
    }
}

object GitHubCommitsTable : Table("github_commits") {
    val id = integer("id").autoIncrement()
    val repositoryId = integer("repository_id").references(GitHubRepositoriesTable.id, onDelete = ReferenceOption.CASCADE).index()
    val sha = varchar("sha", 40)
    val message = text("message")
    val authorName = varchar("author_name", 255).nullable()
    val authorEmail = varchar("author_email", 255).nullable()
    val commitDate = long("commit_date")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("github_commits_repo_sha_unique", repositoryId, sha)
    }
}

object GitHubPullRequestsTable : Table("github_pull_requests") {
    val id = integer("id").autoIncrement()
    val repositoryId = integer("repository_id").references(GitHubRepositoriesTable.id, onDelete = ReferenceOption.CASCADE).index()
    val githubPrId = long("github_pr_id")
    val number = integer("number")
    val title = varchar("title", 500)
    val state = varchar("state", 50) // open, closed
    val authorUsername = varchar("author_username", 255)
    val createdAt = long("created_at")
    val closedAt = long("closed_at").nullable()
    val mergedAt = long("merged_at").nullable()
    val headSha = varchar("head_sha", 40).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("github_prs_repo_pr_unique", repositoryId, githubPrId)
    }
}

object GitHubContributorsTable : Table("github_contributors") {
    val id = integer("id").autoIncrement()
    val repositoryId = integer("repository_id").references(GitHubRepositoriesTable.id, onDelete = ReferenceOption.CASCADE).index()
    val githubUsername = varchar("github_username", 255)
    val commitCount = integer("commit_count").default(0)

    override val primaryKey = PrimaryKey(id)
}