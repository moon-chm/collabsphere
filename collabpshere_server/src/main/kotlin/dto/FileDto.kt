package com.collabsphere.dto

import kotlinx.serialization.Serializable

@Serializable
data class FileRequest(
    val userId: Int,
    val workspaceId: Int,
    val userName: String,
    val url: String,
    val mimeType: String,
    val localpath: String?,
    val fileName: String,
    val sizebytes: Long,
    val fileLocation: String
)

@Serializable
data class FileResponse(
    val id: Long,
    val userId: Int,
    val workspaceId: Int,
    val userName: String,
    val url: String,
    val mimeType: String,
    val localpath: String?,
    val fileName: String,
    val sizebytes: Long,
    val fileLocation: String
)