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
    // Bumped on password change/reset — every JWT carries the version it was minted with, so this
    // revokes all previously issued tokens (see plugins/AuthSupport.kt TokenVersions).
    val tokenVersion = integer("token_version").default(0)
    // Requested-but-unverified new email. `email` only changes once the user proves they own this one.
    val pendingEmail = varchar("pending_email", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * Per-device FCM token registry — supports multiple simultaneous devices per user.
 *
 * Primary key is (userId, deviceId) — each physical device has exactly one row.
 * Registering the same deviceId again is an upsert (update the token, e.g. after a token refresh).
 *
 * The legacy [UsersTable.fcmToken] single-token column is kept for backward compatibility
 * with older Android clients that don't send a deviceId. [com.collabsphere.util.FcmService]
 * fans out to BOTH so no device is silently dropped during the migration window.
 */
object UserFcmTokensTable : Table("user_fcm_tokens") {
    val userId = integer("user_id")
        .references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
        .index()
    // Stable client-side identifier — e.g. Settings.Secure.ANDROID_ID or a UUID persisted to DataStore.
    // Max 128 chars to cover Android IDs, UUIDs, and vendor device IDs with room to spare.
    val deviceId = varchar("device_id", 128)
    val token = varchar("token", 500)
    // Unix millis — lets the server prune tokens that haven't refreshed in >90 days (stale installs)
    val updatedAt = long("updated_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey = PrimaryKey(userId, deviceId)
}

object WorkspacesTable : Table("workspace") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE).index()
    val workspaceName = varchar("workspace_name", 255)
    val workspaceOwner = varchar("workspace_owner", 255)
    val workspacePassword = varchar("workspace_password", 255)
    val isDeleted = bool("is_deleted").default(false)
    val updatedAt = long("updated_at").clientDefault { System.currentTimeMillis() }
    // Stamped by a Postgres trigger with the id of the last transaction that wrote the row — the
    // delta-sync cursor (see plugins/DeltaSync.kt). Never set from application code.
    val syncXid = long("sync_xid").default(0L)

    override val primaryKey = PrimaryKey(id)
}

object WorkspaceMembersTable : Table("workspace_members") {
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE)
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE).index()
    val role = varchar("role", 10).default("MEMBER")
    // Stamped by a Postgres trigger with the id of the last transaction that wrote the row — the
    // delta-sync cursor (see plugins/DeltaSync.kt). Never set from application code.
    val syncXid = long("sync_xid").default(0L)

    override val primaryKey = PrimaryKey(workspaceId, userId)
}

/** Current per-user workspace access state; inactive rows act as durable sync tombstones. */
object WorkspaceMembershipStateTable : Table("workspace_membership_state") {
    val workspaceId = integer("workspace_id")
    val userId = integer("user_id")
    val isMember = bool("is_member").default(true)
    val updatedAt = long("updated_at").clientDefault { System.currentTimeMillis() }
    val syncXid = long("sync_xid").default(0L)

    override val primaryKey = PrimaryKey(workspaceId, userId)

    init {
        index(false, userId, syncXid)
    }
}

object ChannelsTable : Table("channels") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val channelName = varchar("channel_name", 255)
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE).index()
    val description = text("description")
    val updatedAt = long("updated_at").clientDefault { System.currentTimeMillis() }
    val isDeleted = bool("is_deleted").default(false)
    // Stamped by a Postgres trigger with the id of the last transaction that wrote the row — the
    // delta-sync cursor (see plugins/DeltaSync.kt). Never set from application code.
    val syncXid = long("sync_xid").default(0L)

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, workspaceId, syncXid)
    }
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
    // The "<uuid>_<name>" segment that /api/file/download/{name} is addressed by — looked up by
    // equality instead of a `url LIKE '%/name'` scan. Backfilled from `url` for older rows.
    val storageKey = varchar("storage_key", 300).nullable().index()
    val updatedAt = long("updated_at").clientDefault { System.currentTimeMillis() }
    val isDeleted = bool("is_deleted").default(false)
    // Stamped by a Postgres trigger with the id of the last transaction that wrote the row — the
    // delta-sync cursor (see plugins/DeltaSync.kt). Never set from application code.
    val syncXid = long("sync_xid").default(0L)

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, workspaceId, syncXid)
    }
}

object MessageTable : Table("message") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE).index()
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
    // Stamped by a Postgres trigger with the id of the last transaction that wrote the row — the
    // delta-sync cursor (see plugins/DeltaSync.kt). Never set from application code.
    val syncXid = long("sync_xid").default(0L)

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, channelId, syncXid)
    }
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
    // Stamped by a Postgres trigger with the id of the last transaction that wrote the row — the
    // delta-sync cursor (see plugins/DeltaSync.kt). Never set from application code.
    val syncXid = long("sync_xid").default(0L)

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, workspaceId, syncXid)
    }
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
    // Stamped by a Postgres trigger with the id of the last transaction that wrote the row — the
    // delta-sync cursor (see plugins/DeltaSync.kt). Never set from application code.
    val syncXid = long("sync_xid").default(0L)

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, workspaceId, syncXid)
    }
}

object DirectMessagesTable : Table("direct_messages") {
    val id = integer("id").autoIncrement()
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE).index()
    val senderId = integer("sender_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE).index()
    val receiverId = integer("receiver_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE).index()
    val content = text("content")
    val mediaUrl = varchar("media_url", 500).nullable()
    val replyToId = integer("reply_to_id").nullable()
    val isRead = bool("is_read").default(false)
    val timestamp = long("timestamp")
    // Soft delete: the row stays as a tombstone (content/media cleared) so a device that was offline
    // when the message was deleted still learns about it through /api/dm/sync.
    val isDeleted = bool("is_deleted").default(false)
    // Stamped by a Postgres trigger with the id of the last transaction that wrote the row — the
    // delta-sync cursor (see plugins/DeltaSync.kt). Never set from application code.
    val syncXid = long("sync_xid").default(0L)

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, senderId, syncXid)
        index(false, receiverId, syncXid)
    }
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
    val attempts = integer("attempts").default(0)

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

    init {
        // Unread badge count and "mark all read" both filter on exactly this pair.
        index(false, recipientId, isRead)
    }
}

object PasswordResetTable : Table("password_resets") {
    val email = varchar("email", 255)
    val otp = varchar("otp", 10)
    val expiresAt = long("expires_at")
    val attempts = integer("attempts").default(0)

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
    val syncState = varchar("sync_state", 20).default("IDLE")
    val lastSuccessfulSyncAt = long("last_successful_sync_at").nullable()
    val lastSyncError = text("last_sync_error").nullable()
    val notifyChannelId = integer("notify_channel_id").references(ChannelsTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val lastDigestAt = long("last_digest_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object GitHubWebhookEventsTable : Table("github_webhook_events") {
    val deliveryId = varchar("delivery_id", 128)
    val eventType = varchar("event_type", 64)
    val payload = text("payload")
    val status = varchar("status", 20).default("QUEUED")
    val retryCount = integer("retry_count").default(0)
    val nextRetryAt = long("next_retry_at").clientDefault { System.currentTimeMillis() }
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
    val error = text("error").nullable()
    // When a worker claimed the row — a PROCESSING row older than the lease is presumed abandoned
    // (server crashed/redeployed mid-event) and is put back in the queue.
    val processingStartedAt = long("processing_started_at").nullable()

    override val primaryKey = PrimaryKey(deliveryId)

    init {
        index(false, status, nextRetryAt)
    }
}

object GitHubCheckSuitesTable : Table("github_check_suites") {
    val id = integer("id").autoIncrement()
    val repositoryId = integer("repository_id").references(GitHubRepositoriesTable.id, onDelete = ReferenceOption.CASCADE).index()
    val githubSuiteId = long("github_suite_id")
    val headSha = varchar("head_sha", 40)
    val status = varchar("status", 20)
    val conclusion = varchar("conclusion", 30).nullable()
    val updatedAt = long("updated_at")
    val headBranch = varchar("head_branch", 255).nullable()
    val appName = varchar("app_name", 255).nullable()
    val url = varchar("url", 500).nullable()
    val createdAt = long("created_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("github_check_suites_unique", repositoryId, githubSuiteId)
        index(false, repositoryId, headSha)
        index(false, repositoryId, updatedAt)
    }
}

object GitHubReleasesTable : Table("github_releases") {
    val id = integer("id").autoIncrement()
    val repositoryId = integer("repository_id").references(GitHubRepositoriesTable.id, onDelete = ReferenceOption.CASCADE).index()
    val githubReleaseId = long("github_release_id")
    val tagName = varchar("tag_name", 255)
    val name = varchar("name", 255).nullable()
    val body = text("body").nullable()
    val author = varchar("author", 255).nullable()
    val htmlUrl = varchar("html_url", 500)
    val draft = bool("draft").default(false)
    val prerelease = bool("prerelease").default(false)
    val publishedAt = long("published_at").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    init {
        uniqueIndex("github_releases_unique", repositoryId, githubReleaseId)
        index(false, repositoryId, publishedAt)
    }

    data class ReleaseRow(
        val id: Int,
        val githubReleaseId: Long,
        val tagName: String,
        val name: String?,
        val body: String?,
        val author: String?,
        val htmlUrl: String,
        val draft: Boolean,
        val prerelease: Boolean,
        val publishedAt: Long?,
        val createdAt: Long
    )
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

object GitHubActionIdempotencyTable : Table("github_action_idempotency") {
    val id = integer("id").autoIncrement()
    val idempotencyKey = varchar("idempotency_key", 128).uniqueIndex()
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE)
    val resourceIdentity = varchar("resource_identity", 500)
    val action = varchar("action", 50)
    val status = varchar("status", 50) // IN_PROGRESS, SUCCEEDED, FAILED_RETRYABLE, FAILED_FINAL
    val resultBody = text("result_body").nullable()
    val createdAt = long("created_at")
    val completedAt = long("completed_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * Feature E: Stable mapping from CollabSphere users to GitHub identities.
 * Derived from GitHubConnectionsTable but provides a denormalized, queryable
 * index for workspace-level assignee synchronization.
 * The githubUserId (numeric) is the long-term identity key.
 */
object GitHubIdentityMappingTable : Table("github_identity_mapping") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val githubUserId = long("github_user_id")
    val githubLogin = varchar("github_login", 255)
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
    val updatedAt = long("updated_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("github_identity_ws_user_unique", workspaceId, userId)
        uniqueIndex("github_identity_ws_ghid_unique", workspaceId, githubUserId)
        index(false, workspaceId, githubLogin)
    }
}

/**
 * Feature E: Tracks assignee sync state for linked task↔issue pairs.
 * Prevents infinite loops by recording the origin of the last sync and
 * comparing assignee set hashes before triggering writes.
 */
object GitHubAssigneeSyncTable : Table("github_assignee_sync") {
    val id = integer("id").autoIncrement()
    val taskId = integer("task_id").references(TasksTable.id, onDelete = ReferenceOption.CASCADE)
    val repositoryId = integer("repository_id").references(GitHubRepositoriesTable.id, onDelete = ReferenceOption.CASCADE)
    val issueNumber = integer("issue_number")
    val enabled = bool("enabled").default(true)
    /** "GITHUB", "COLLABSPHERE", or "INITIAL" — who triggered the last sync */
    val lastSyncSource = varchar("last_sync_source", 20).default("INITIAL")
    /** Hash of the assignee set at last sync (sorted github user IDs, joined) */
    val lastSyncHash = varchar("last_sync_hash", 128).default("")
    val lastSyncAt = long("last_sync_at").nullable()
    val syncStatus = varchar("sync_status", 20).default("SYNCED") // SYNCED, PARTIAL, FAILED, RETRYING
    val syncError = text("sync_error").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("github_assignee_sync_unique", taskId, repositoryId, issueNumber)
    }
}

/**
 * Feature G: Structured reference to a code snippet in a GitHub repository.
 * Stored as metadata only — source content is fetched on-demand and cached.
 * Can be attached to tasks, channel messages, or DMs via referenceType/referenceId.
 */
object GitHubCodeReferencesTable : Table("github_code_references") {
    val id = integer("id").autoIncrement()
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE)
    val repositoryId = integer("repository_id").references(GitHubRepositoriesTable.id, onDelete = ReferenceOption.CASCADE)
    val filePath = varchar("file_path", 1000)
    val ref = varchar("ref", 255)           // branch name or commit SHA
    val commitSha = varchar("commit_sha", 40).nullable()  // pinned commit for stability
    val startLine = integer("start_line").nullable()
    val endLine = integer("end_line").nullable()
    val canonicalUrl = varchar("canonical_url", 1000)
    /** "TASK", "CHANNEL_MESSAGE", "DM" */
    val referenceType = varchar("reference_type", 30)
    val referenceId = integer("reference_id")  // task ID, message ID, or DM ID
    val createdByUserId = integer("created_by_user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, workspaceId, referenceType, referenceId)
        index(false, repositoryId, commitSha, filePath)
    }
}

/**
 * Feature G: Bounded cache for fetched code snippet content.
 * Keyed by repository + commitSha + filePath for immutability.
 * Content is trimmed to the requested line range at fetch time.
 */
object GitHubCodeCacheTable : Table("github_code_cache") {
    val id = integer("id").autoIncrement()
    val repositoryId = integer("repository_id").references(GitHubRepositoriesTable.id, onDelete = ReferenceOption.CASCADE)
    val commitSha = varchar("commit_sha", 40)
    val filePath = varchar("file_path", 1000)
    val content = text("content")
    val language = varchar("language", 50).nullable()
    val totalLines = integer("total_lines").default(0)
    val sizeBytes = integer("size_bytes").default(0)
    val isBinary = bool("is_binary").default(false)
    val fetchedAt = long("fetched_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("github_code_cache_unique", repositoryId, commitSha, filePath)
    }
}
