package plugins

import com.collabsphere.dto.FileSyncResponse
import com.collabsphere.dto.MessageSyncResponse
import com.collabsphere.dto.NotesSyncResponse
import com.collabsphere.model.*
import dto.ChannelSyncResponse
import dto.DmDto
import dto.TaskExtras
import dto.TaskSyncResponse
import dto.WorkspaceSyncDto
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.transactions.TransactionManager

/*
 * The delta-sync query for each synced entity. All of these must run inside a transaction (dbQuery)
 * and assume the caller has already checked the acting user may see the scope (workspace membership).
 */

/** Current workspaces changed plus durable removal tombstones for this user. */
internal fun workspaceDeltaSync(userId: Int, request: SyncRequest): SyncPage<WorkspaceSyncDto> {
    val snapshot = currentSyncSnapshot()
    val cursor = request.cursor
    if (cursor != null && cursor > snapshot.xmax) {
        return SyncPage(emptyList(), snapshot.xmin, reset = true)
    }

    val currentChanges = if (cursor != null) {
        (WorkspacesTable.syncXid greaterEq cursor) or (WorkspaceMembersTable.syncXid greaterEq cursor)
    } else {
        WorkspacesTable.updatedAt greater request.since
    }
    val removalChanges = if (cursor != null) {
        WorkspaceMembershipStateTable.syncXid greaterEq cursor
    } else {
        WorkspaceMembershipStateTable.updatedAt greater request.since
    }

    val activeWorkspaces = (WorkspacesTable innerJoin WorkspaceMembersTable)
        .selectAll()
        .where { (WorkspaceMembersTable.userId eq userId) and currentChanges }
        .map {
            WorkspaceSyncDto(
                id = it[WorkspacesTable.id],
                userId = it[WorkspacesTable.userId],
                workspaceName = it[WorkspacesTable.workspaceName],
                workspaceOwner = it[WorkspacesTable.workspaceOwner],
                isDeleted = it[WorkspacesTable.isDeleted],
                updatedAt = it[WorkspacesTable.updatedAt]
            )
        }

    val removedWorkspaces = WorkspaceMembershipStateTable.selectAll()
        .where {
            (WorkspaceMembershipStateTable.userId eq userId) and
                (WorkspaceMembershipStateTable.isMember eq false) and removalChanges
        }
        .map {
            WorkspaceSyncDto(
                id = it[WorkspaceMembershipStateTable.workspaceId],
                userId = userId,
                workspaceName = "",
                workspaceOwner = "",
                isDeleted = true,
                updatedAt = it[WorkspaceMembershipStateTable.updatedAt]
            )
        }

    return SyncPage(
        rows = (activeWorkspaces + removedWorkspaces).distinctBy { it.id }.sortedBy { it.id },
        nextCursor = snapshot.xmin
    )
}

internal fun channelDeltaSync(workspaceId: Int, request: SyncRequest): SyncPage<ChannelSyncResponse> =
    deltaSync(
        request,
        changedSince = { cursor -> ChannelsTable.syncXid greaterEq cursor },
        legacyChangedSince = { since -> ChannelsTable.updatedAt greater since }
    ) { changed ->
        ChannelsTable.selectAll()
            .where { (ChannelsTable.workspaceId eq workspaceId) and changed }
            .orderBy(ChannelsTable.id)
            .map {
                ChannelSyncResponse(
                    id = it[ChannelsTable.id],
                    userId = it[ChannelsTable.userId],
                    channelName = it[ChannelsTable.channelName],
                    workspaceId = it[ChannelsTable.workspaceId],
                    description = it[ChannelsTable.description],
                    isDeleted = it[ChannelsTable.isDeleted],
                    updatedAt = it[ChannelsTable.updatedAt]
                )
            }
    }

internal fun taskDeltaSync(workspaceId: Int, request: SyncRequest): SyncPage<TaskSyncResponse> =
    deltaSync(
        request,
        changedSince = { cursor -> TasksTable.syncXid greaterEq cursor },
        legacyChangedSince = { since -> TasksTable.updatedAt greater since }
    ) { changed ->
        TasksTable.selectAll()
            .where { (TasksTable.workspaceId eq workspaceId) and changed }
            .orderBy(TasksTable.id)
            .map {
                TaskSyncResponse(
                    id = it[TasksTable.id],
                    createdByUserId = it[TasksTable.createdByUserId],
                    assignedToUserId = it[TasksTable.assignedToUserId],
                    workspaceId = it[TasksTable.workspaceId],
                    taskName = it[TasksTable.taskName],
                    taskDescription = it[TasksTable.taskDescription],
                    status = it[TasksTable.status],
                    isDeleted = it[TasksTable.isDeleted],
                    updatedAt = it[TasksTable.updatedAt],
                    dueDate = it[TasksTable.dueDate],
                    priority = it[TasksTable.priority],
                    checklist = TaskExtras.decodeChecklist(it[TasksTable.checklist]),
                    labels = TaskExtras.decodeLabels(it[TasksTable.labels])
                )
            }
    }

internal fun noteDeltaSync(workspaceId: Int, request: SyncRequest): SyncPage<NotesSyncResponse> =
    deltaSync(
        request,
        changedSince = { cursor -> NotesTable.syncXid greaterEq cursor },
        legacyChangedSince = { since -> NotesTable.updatedAt greater since }
    ) { changed ->
        NotesTable.selectAll()
            .where { (NotesTable.workspaceId eq workspaceId) and changed }
            .orderBy(NotesTable.id)
            .map {
                NotesSyncResponse(
                    id = it[NotesTable.id],
                    userId = it[NotesTable.userIdNotes],
                    workspaceId = it[NotesTable.workspaceId],
                    notesName = it[NotesTable.notesName],
                    description = it[NotesTable.notesDescription],
                    isDeleted = it[NotesTable.isDeleted],
                    updatedAt = it[NotesTable.updatedAt],
                    isPinned = it[NotesTable.isPinned]
                )
            }
    }

internal fun ResultRow.toMessageSyncResponse() = MessageSyncResponse(
    id = this[MessageTable.id],
    userId = this[MessageTable.userId],
    workspaceId = this[MessageTable.workspaceId],
    channelId = this[MessageTable.channelId],
    userName = this[MessageTable.userName],
    content = this[MessageTable.content],
    status = this[MessageTable.status],
    isDeleted = this[MessageTable.isDeleted],
    updatedAt = this[MessageTable.updatedAt],
    replyToId = this[MessageTable.replyToId],
    mediaUrl = this[MessageTable.mediaUrl],
    pinnedAt = this[MessageTable.pinnedAt]
)

internal fun messageDeltaSync(workspaceId: Int, channelId: Int, request: SyncRequest): SyncPage<MessageSyncResponse> =
    deltaSync(
        request,
        changedSince = { cursor -> MessageTable.syncXid greaterEq cursor },
        legacyChangedSince = { since -> MessageTable.updatedAt greater since }
    ) { changed ->
        MessageTable.selectAll()
            .where { (MessageTable.workspaceId eq workspaceId) and (MessageTable.channelId eq channelId) and changed }
            .orderBy(MessageTable.id)
            .map { it.toMessageSyncResponse() }
    }

internal fun fileDeltaSync(workspaceId: Int, request: SyncRequest): SyncPage<FileSyncResponse> =
    deltaSync(
        request,
        changedSince = { cursor -> LocalFilesTable.syncXid greaterEq cursor },
        legacyChangedSince = { since -> LocalFilesTable.updatedAt greater since }
    ) { changed ->
        LocalFilesTable.selectAll()
            .where { (LocalFilesTable.workspaceId eq workspaceId) and changed }
            .orderBy(LocalFilesTable.id)
            .map {
                FileSyncResponse(
                    id = it[LocalFilesTable.id],
                    userId = it[LocalFilesTable.userId],
                    workspaceId = it[LocalFilesTable.workspaceId],
                    userName = it[LocalFilesTable.userName],
                    url = it[LocalFilesTable.url],
                    mimeType = it[LocalFilesTable.mimeType],
                    localpath = it[LocalFilesTable.localPath],
                    fileName = it[LocalFilesTable.fileName],
                    sizebytes = it[LocalFilesTable.sizeBytes],
                    fileLocation = it[LocalFilesTable.fileLocation],
                    isDeleted = it[LocalFilesTable.isDeleted],
                    updatedAt = it[LocalFilesTable.updatedAt]
                )
            }
    }

internal fun ResultRow.toDmSyncDto() = DmDto(
    action = "SYNC",
    id = this[DirectMessagesTable.id],
    workspaceId = this[DirectMessagesTable.workspaceId],
    senderId = this[DirectMessagesTable.senderId],
    receiverId = this[DirectMessagesTable.receiverId],
    content = this[DirectMessagesTable.content],
    timestamp = this[DirectMessagesTable.timestamp],
    mediaUrl = this[DirectMessagesTable.mediaUrl],
    isRead = this[DirectMessagesTable.isRead],
    replyToId = this[DirectMessagesTable.replyToId],
    isDeleted = this[DirectMessagesTable.isDeleted]
)

/**
 * DMs to or from [userId] that were created, edited, read or deleted since [cursor] — deletions come
 * back as tombstones (`isDeleted = true`). New DMs also arrive in real time over the socket; this is
 * what a device that was offline uses to catch up on edits and deletes it missed.
 *
 * With no cursor yet there's nothing to diff against: a device that already holds DMs up to
 * [knownUpToId] gets every change made since this server started stamping DM rows to those messages
 * (sync_xid > 0), so a client upgraded after the fact still converges; a fresh install gets nothing,
 * since its initial history arrives over the socket.
 */
internal fun dmDeltaSync(userId: Int, cursor: Long?, knownUpToId: Int): SyncPage<DmDto> {
    val involvesUser = (DirectMessagesTable.senderId eq userId) or (DirectMessagesTable.receiverId eq userId)
    return deltaSync(
        SyncRequest(cursor = cursor, since = 0L),
        changedSince = { c -> DirectMessagesTable.syncXid greaterEq c },
        legacyChangedSince = { _ -> (DirectMessagesTable.syncXid greater 0L) and (DirectMessagesTable.id lessEq knownUpToId) }
    ) { changed ->
        if (cursor == null && knownUpToId <= 0) return@deltaSync emptyList()
        DirectMessagesTable.selectAll()
            .where { involvesUser and changed }
            .orderBy(DirectMessagesTable.id)
            .map { it.toDmSyncDto() }
    }
}

private fun java.sql.ResultSet.toDmHistoryDto() = DmDto(
    action = "HISTORY",
    id = getInt("id"),
    workspaceId = getInt("workspace_id"),
    senderId = getInt("sender_id"),
    receiverId = getInt("receiver_id"),
    content = getString("content"),
    timestamp = getLong("timestamp"),
    mediaUrl = getString("media_url"),
    isRead = getBoolean("is_read"),
    replyToId = getInt("reply_to_id").takeUnless { wasNull() }
)

private const val DM_COLUMNS = "id, workspace_id, sender_id, receiver_id, content, timestamp, media_url, is_read, reply_to_id"

/**
 * The newest [perConversation] live DMs of each of [userId]'s conversations, oldest first — what a
 * fresh device is sent on connect. The per-conversation cut happens in Postgres, so the server never
 * loads the user's full DM history to pick from it.
 */
internal fun initialDmHistory(userId: Int, perConversation: Int): List<DmDto> =
    TransactionManager.current().exec(
        """
        SELECT $DM_COLUMNS FROM (
            SELECT $DM_COLUMNS, row_number() OVER (
                PARTITION BY workspace_id, LEAST(sender_id, receiver_id), GREATEST(sender_id, receiver_id)
                ORDER BY id DESC
            ) AS rn
            FROM direct_messages
            WHERE (sender_id = ? OR receiver_id = ?) AND is_deleted = false
        ) ranked
        WHERE rn <= ?
        ORDER BY id ASC
        """.trimIndent(),
        listOf(IntegerColumnType() to userId, IntegerColumnType() to userId, IntegerColumnType() to perConversation)
    ) { rs ->
        buildList { while (rs.next()) add(rs.toDmHistoryDto()) }
    } ?: emptyList()

/** Up to [limit] live DMs of [userId] with id > [afterId], oldest first. */
internal fun dmCatchUpPage(userId: Int, afterId: Int, limit: Int): List<DmDto> =
    TransactionManager.current().exec(
        """
        SELECT $DM_COLUMNS FROM direct_messages
        WHERE (sender_id = ? OR receiver_id = ?) AND is_deleted = false AND id > ?
        ORDER BY id ASC
        LIMIT ?
        """.trimIndent(),
        listOf(
            IntegerColumnType() to userId,
            IntegerColumnType() to userId,
            IntegerColumnType() to afterId,
            IntegerColumnType() to limit
        )
    ) { rs ->
        buildList { while (rs.next()) add(rs.toDmHistoryDto()) }
    } ?: emptyList()
