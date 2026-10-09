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

private fun Query.applyDeltaPage(
    limit: Int?,
    after: SyncPosition?,
    syncXid: Column<Long>,
    id: Column<Int>
): Query {
    val positioned = if (after == null) this else andWhere {
        if (after.id > Int.MAX_VALUE.toLong()) Op.FALSE
        else (syncXid greater after.syncXid) or ((syncXid eq after.syncXid) and (id greater after.id.toInt()))
    }
    return if (limit == null) positioned else positioned.limit(limit)
}

private fun Query.applyDeltaPageByLongId(
    limit: Int?,
    after: SyncPosition?,
    syncXid: Column<Long>,
    id: Column<Long>
): Query {
    val positioned = if (after == null) this else andWhere {
        (syncXid greater after.syncXid) or ((syncXid eq after.syncXid) and (id greater after.id))
    }
    return if (limit == null) positioned else positioned.limit(limit)
}

private fun <T> ResultRow.withSyncPosition(syncXid: Column<Long>, id: Column<Int>, value: T) =
    DeltaSyncRow(value, SyncPosition(this[syncXid], this[id].toLong()))

private fun <T> ResultRow.withLongIdSyncPosition(syncXid: Column<Long>, id: Column<Long>, value: T) =
    DeltaSyncRow(value, SyncPosition(this[syncXid], this[id]))

/*
 * The delta-sync query for each synced entity. All of these must run inside a transaction (dbQuery)
 * and assume the caller has already checked the acting user may see the scope (workspace membership).
 */

/** Current workspaces changed plus durable removal tombstones for this user. */
internal fun workspaceDeltaSync(userId: Int, request: SyncRequest): SyncPage<WorkspaceSyncDto> {
    val snapshot = currentSyncSnapshot()
    val cursor = request.cursor
    val pageToken = request.pageToken?.let(::decodePageToken)
    if (cursor != null && cursor > snapshot.xmax) {
        return SyncPage(emptyList(), snapshot.xmin, reset = true)
    }
    if (request.pageToken != null && (pageToken == null || request.pageSize == null)) {
        return SyncPage(emptyList(), snapshot.xmin, reset = true)
    }
    if (pageToken != null &&
        (pageToken.snapshotCursor > snapshot.xmax || pageToken.position.syncXid > snapshot.xmax)
    ) {
        return SyncPage(emptyList(), snapshot.xmin, reset = true)
    }

    val currentChanges = if (cursor != null) {
        (WorkspacesTable.syncXid greaterEq cursor) or (WorkspaceMembersTable.syncXid greaterEq cursor)
    } else if (request.pageSize != null) {
        (WorkspacesTable.syncXid greaterEq 0L) or (WorkspaceMembersTable.syncXid greaterEq 0L)
    } else {
        WorkspacesTable.updatedAt greater request.since
    }
    val removalChanges = if (cursor != null) {
        WorkspaceMembershipStateTable.syncXid greaterEq cursor
    } else if (request.pageSize != null) {
        WorkspaceMembershipStateTable.syncXid greaterEq 0L
    } else {
        WorkspaceMembershipStateTable.updatedAt greater request.since
    }

    // Workspace changes combine active rows and membership tombstones. Continue after the last
    // workspace id and fetch at most one page plus one row from each ordered source.
    val afterId = pageToken?.position?.id
    val pageSize = request.pageSize

    val activeQuery = (WorkspacesTable innerJoin WorkspaceMembersTable)
        .selectAll()
        .where {
            (WorkspaceMembersTable.userId eq userId) and currentChanges and
                (afterId?.let { if (it > Int.MAX_VALUE.toLong()) Op.FALSE else WorkspacesTable.id greater it.toInt() } ?: Op.TRUE)
        }
        .orderBy(WorkspacesTable.id to SortOrder.ASC)
    val activeRows = (if (pageSize == null) activeQuery else activeQuery.limit(pageSize + 1)).map { row ->
            val dto = WorkspaceSyncDto(
                id = row[WorkspacesTable.id],
                userId = row[WorkspacesTable.userId],
                workspaceName = row[WorkspacesTable.workspaceName],
                workspaceOwner = row[WorkspacesTable.workspaceOwner],
                isDeleted = row[WorkspacesTable.isDeleted],
                updatedAt = row[WorkspacesTable.updatedAt]
            )
            DeltaSyncRow(dto, SyncPosition(0, dto.id.toLong()))
        }

    val removedQuery = WorkspaceMembershipStateTable.selectAll()
        .where {
            (WorkspaceMembershipStateTable.userId eq userId) and
                (WorkspaceMembershipStateTable.isMember eq false) and removalChanges and
                (afterId?.let { if (it > Int.MAX_VALUE.toLong()) Op.FALSE else WorkspaceMembershipStateTable.workspaceId greater it.toInt() } ?: Op.TRUE)
        }
        .orderBy(WorkspaceMembershipStateTable.workspaceId to SortOrder.ASC)
    val removedRows = (if (pageSize == null) removedQuery else removedQuery.limit(pageSize + 1)).map { row ->
            val dto = WorkspaceSyncDto(
                id = row[WorkspaceMembershipStateTable.workspaceId],
                userId = userId,
                workspaceName = "",
                workspaceOwner = "",
                isDeleted = true,
                updatedAt = row[WorkspaceMembershipStateTable.updatedAt]
            )
            DeltaSyncRow(dto, SyncPosition(0, dto.id.toLong()))
        }

    val orderedRows = (activeRows + removedRows).distinctBy { it.value.id }.sortedBy { it.value.id }
    val snapshotCursor = pageToken?.snapshotCursor ?: snapshot.xmin
    return createDeltaSyncPage(orderedRows, pageSize, snapshotCursor)
}

internal fun channelDeltaSync(workspaceId: Int, request: SyncRequest): SyncPage<ChannelSyncResponse> =
    deltaSync(
        request,
        changedSince = { cursor -> ChannelsTable.syncXid greaterEq cursor },
        legacyChangedSince = { since -> ChannelsTable.updatedAt greater since }
    ) { changed, limit, after ->
        ChannelsTable.selectAll()
            .where { (ChannelsTable.workspaceId eq workspaceId) and changed }
            .orderBy(ChannelsTable.syncXid to SortOrder.ASC, ChannelsTable.id to SortOrder.ASC)
            .applyDeltaPage(limit, after, ChannelsTable.syncXid, ChannelsTable.id)
            .map { row ->
                row.withSyncPosition(ChannelsTable.syncXid, ChannelsTable.id, ChannelSyncResponse(
                    id = row[ChannelsTable.id],
                    userId = row[ChannelsTable.userId],
                    channelName = row[ChannelsTable.channelName],
                    workspaceId = row[ChannelsTable.workspaceId],
                    description = row[ChannelsTable.description],
                    isDeleted = row[ChannelsTable.isDeleted],
                    updatedAt = row[ChannelsTable.updatedAt]
                ))
            }
    }

internal fun taskDeltaSync(workspaceId: Int, request: SyncRequest): SyncPage<TaskSyncResponse> =
    deltaSync(
        request,
        changedSince = { cursor -> TasksTable.syncXid greaterEq cursor },
        legacyChangedSince = { since -> TasksTable.updatedAt greater since }
    ) { changed, limit, after ->
        TasksTable.selectAll()
            .where { (TasksTable.workspaceId eq workspaceId) and changed }
            .orderBy(TasksTable.syncXid to SortOrder.ASC, TasksTable.id to SortOrder.ASC)
            .applyDeltaPage(limit, after, TasksTable.syncXid, TasksTable.id)
            .map { row ->
                row.withSyncPosition(TasksTable.syncXid, TasksTable.id, TaskSyncResponse(
                    id = row[TasksTable.id],
                    createdByUserId = row[TasksTable.createdByUserId],
                    assignedToUserId = row[TasksTable.assignedToUserId],
                    workspaceId = row[TasksTable.workspaceId],
                    taskName = row[TasksTable.taskName],
                    taskDescription = row[TasksTable.taskDescription],
                    status = row[TasksTable.status],
                    isDeleted = row[TasksTable.isDeleted],
                    updatedAt = row[TasksTable.updatedAt],
                    dueDate = row[TasksTable.dueDate],
                    priority = row[TasksTable.priority],
                    checklist = TaskExtras.decodeChecklist(row[TasksTable.checklist]),
                    labels = TaskExtras.decodeLabels(row[TasksTable.labels])
                ))
            }
    }

internal fun noteDeltaSync(workspaceId: Int, request: SyncRequest): SyncPage<NotesSyncResponse> =
    deltaSync(
        request,
        changedSince = { cursor -> NotesTable.syncXid greaterEq cursor },
        legacyChangedSince = { since -> NotesTable.updatedAt greater since }
    ) { changed, limit, after ->
        NotesTable.selectAll()
            .where { (NotesTable.workspaceId eq workspaceId) and changed }
            .orderBy(NotesTable.syncXid to SortOrder.ASC, NotesTable.id to SortOrder.ASC)
            .applyDeltaPage(limit, after, NotesTable.syncXid, NotesTable.id)
            .map { row ->
                row.withSyncPosition(NotesTable.syncXid, NotesTable.id, NotesSyncResponse(
                    id = row[NotesTable.id],
                    userId = row[NotesTable.userIdNotes],
                    workspaceId = row[NotesTable.workspaceId],
                    notesName = row[NotesTable.notesName],
                    description = row[NotesTable.notesDescription],
                    isDeleted = row[NotesTable.isDeleted],
                    updatedAt = row[NotesTable.updatedAt],
                    isPinned = row[NotesTable.isPinned]
                ))
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
    ) { changed, limit, after ->
        MessageTable.selectAll()
            .where { (MessageTable.workspaceId eq workspaceId) and (MessageTable.channelId eq channelId) and changed }
            .orderBy(MessageTable.syncXid to SortOrder.ASC, MessageTable.id to SortOrder.ASC)
            .applyDeltaPage(limit, after, MessageTable.syncXid, MessageTable.id)
            .map { it.withSyncPosition(MessageTable.syncXid, MessageTable.id, it.toMessageSyncResponse()) }
    }

internal fun fileDeltaSync(workspaceId: Int, request: SyncRequest): SyncPage<FileSyncResponse> =
    deltaSync(
        request,
        changedSince = { cursor -> LocalFilesTable.syncXid greaterEq cursor },
        legacyChangedSince = { since -> LocalFilesTable.updatedAt greater since }
    ) { changed, limit, after ->
        LocalFilesTable.selectAll()
            .where { (LocalFilesTable.workspaceId eq workspaceId) and changed }
            .orderBy(LocalFilesTable.syncXid to SortOrder.ASC, LocalFilesTable.id to SortOrder.ASC)
            .applyDeltaPageByLongId(limit, after, LocalFilesTable.syncXid, LocalFilesTable.id)
            .map { row ->
                row.withLongIdSyncPosition(LocalFilesTable.syncXid, LocalFilesTable.id, FileSyncResponse(
                    id = row[LocalFilesTable.id],
                    userId = row[LocalFilesTable.userId],
                    workspaceId = row[LocalFilesTable.workspaceId],
                    userName = row[LocalFilesTable.userName],
                    url = row[LocalFilesTable.url],
                    mimeType = row[LocalFilesTable.mimeType],
                    localpath = row[LocalFilesTable.localPath],
                    fileName = row[LocalFilesTable.fileName],
                    sizebytes = row[LocalFilesTable.sizeBytes],
                    fileLocation = row[LocalFilesTable.fileLocation],
                    isDeleted = row[LocalFilesTable.isDeleted],
                    updatedAt = row[LocalFilesTable.updatedAt]
                ))
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
internal fun dmDeltaSync(
    userId: Int,
    cursor: Long?,
    knownUpToId: Int,
    pageSize: Int? = null,
    pageToken: String? = null
): SyncPage<DmDto> {
    val involvesUser = (DirectMessagesTable.senderId eq userId) or (DirectMessagesTable.receiverId eq userId)
    return deltaSync(
        SyncRequest(cursor = cursor, since = 0L, pageSize = pageSize, pageToken = pageToken),
        changedSince = { c -> DirectMessagesTable.syncXid greaterEq c },
        legacyChangedSince = { _ -> (DirectMessagesTable.syncXid greater 0L) and (DirectMessagesTable.id lessEq knownUpToId) },
        useCursorForFirstPage = false
    ) { changed, limit, after ->
        if (cursor == null && knownUpToId <= 0) return@deltaSync emptyList()
        DirectMessagesTable.selectAll()
            .where { involvesUser and changed }
            .orderBy(DirectMessagesTable.syncXid to SortOrder.ASC, DirectMessagesTable.id to SortOrder.ASC)
            .applyDeltaPage(limit, after, DirectMessagesTable.syncXid, DirectMessagesTable.id)
            .map { it.withSyncPosition(DirectMessagesTable.syncXid, DirectMessagesTable.id, it.toDmSyncDto()) }
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
