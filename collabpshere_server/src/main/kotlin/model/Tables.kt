package com.collabsphere.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.ReferenceOption

object UsersTable : Table("users") {
    val id = integer("id").autoIncrement()
    val email = varchar("email", 255)
    val password = varchar("password", 255)
    val username = varchar("user_name", 255)

    override val primaryKey = PrimaryKey(id)
}

object WorkspacesTable : Table("workspace") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val workspaceName = varchar("workspace_name", 255)
    val workspaceOwner = varchar("workspace_owner", 255)
    val workspacePassword = varchar("workspace_password", 255)
    val isDeleted = bool("is_deleted").default(false)
    val updatedAt = long("updated_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(id)
}

object WorkspaceMembersTable : Table("workspace_members") {
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE)
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(workspaceId, userId)
}

object ChannelsTable : Table("channels") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val channelName = varchar("channel_name", 255)
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE)
    val description = text("description")
    val updatedAt = long("updated_at") 
    val isDeleted = bool("is_deleted").default(false)

    override val primaryKey = PrimaryKey(id)
}

object LocalFilesTable : Table("local_files") {
    val id = long("id").autoIncrement()
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE)
    val userName = varchar("user_name", 255)
    val url = varchar("url", 500)
    val mimeType = varchar("mime_type", 100)
    val localPath = varchar("localpath", 500).nullable()
    val fileName = varchar("file_name", 255)
    val sizeBytes = long("sizebytes")
    val fileLocation = varchar("file_location", 500)
    val updatedAt = long("updated_at").default(System.currentTimeMillis())
    val isDeleted = bool("is_deleted").default(false)

    override val primaryKey = PrimaryKey(id)
}

object MessageTable : Table("message") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE)
    val channelId = integer("channel_id").references(ChannelsTable.id, onDelete = ReferenceOption.CASCADE)
    val userName = varchar("user_name", 255)
    val content = text("content")
    val status = varchar("status", 50)
    val isDeleted = bool("is_deleted").default(false)
    val updatedAt = long("updated_at").default(0L)

    override val primaryKey = PrimaryKey(id)
}

object NotesTable : Table("notes") {
    val id = integer("id").autoIncrement()
    val userIdNotes = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE)
    val notesName = varchar("notes_name", 255)
    val notesDescription = text("description")
    val isDeleted = bool("is_deleted").default(false)
    val updatedAt = long("updated_at").default(0L)

    override val primaryKey = PrimaryKey(id)
}

object TasksTable : Table("task") {
    val id = integer("id").autoIncrement()
    val createdByUserId = integer("created_by_user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val assignedToUserId = integer("assigned_to_user_id").references(UsersTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE)
    val taskName = varchar("task_name", 255)
    val taskDescription = text("task_description")
    val status = varchar("status", 50)
    val isDeleted = bool("is_deleted").default(false)
    val updatedAt = long("updated_at").default(0L)

    override val primaryKey = PrimaryKey(id)
}

object DirectMessagesTable : Table("direct_messages") {
    val id = integer("id").autoIncrement()
    val workspaceId = integer("workspace_id").references(WorkspacesTable.id, onDelete = ReferenceOption.CASCADE)
    val senderId = integer("sender_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val receiverId = integer("receiver_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val content = text("content")
    val timestamp = long("timestamp")

    override val primaryKey = PrimaryKey(id)
}