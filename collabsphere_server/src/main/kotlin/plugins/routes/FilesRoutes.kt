package plugins

import com.collabsphere.dto.*
import dto.*
import com.collabsphere.model.*
import com.collabsphere.util.CloudinaryService
import com.collabsphere.util.StoragePolicy
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID

private val logger = LoggerFactory.getLogger("FilesRoutes")

private fun ResultRow.toFileResponse() = FileResponse(
    id = this[LocalFilesTable.id],
    userId = this[LocalFilesTable.userId],
    workspaceId = this[LocalFilesTable.workspaceId],
    userName = this[LocalFilesTable.userName],
    url = this[LocalFilesTable.url],
    mimeType = this[LocalFilesTable.mimeType],
    localpath = this[LocalFilesTable.localPath],
    fileName = this[LocalFilesTable.fileName],
    sizebytes = this[LocalFilesTable.sizeBytes],
    fileLocation = this[LocalFilesTable.fileLocation]
)

private fun File.sha256Hex(): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun Route.filesRoutes() {
    route("/api/file") {
        post {
            var staged: File? = null
            try {
                val actingUserId = call.authenticatedUserId()
                val durableStorageRequired = StoragePolicy.requiresDurableStorage()
                if (durableStorageRequired && !CloudinaryService.isConfigured) {
                    call.respond(HttpStatusCode.ServiceUnavailable, "Durable file storage is not configured")
                    return@post
                }
                if (call.declaredBodyExceeds(MAX_UPLOAD_BYTES)) throw UploadTooLargeException(MAX_UPLOAD_BYTES)
                val multipart = call.receiveMultipart()
                var workspaceId: Int? = null
                var userName: String? = null
                var localpath: String? = null
                var idempotencyKey: String? = null

                var fileName: String? = null
                var contentType: String? = null

                // The client always sends workspaceId before the file part (see FileApiService.uploadFile's
                // formData order) — checked as soon as it arrives so a non-member's file bytes are never
                // buffered at all, instead of paying that cost before finding out the request is rejected.
                //
                // Defence-in-depth: if a malicious client reorders parts and sends file bytes BEFORE
                // workspaceId, the file data is disposed immediately (not buffered) and the request is
                // rejected after all parts have been consumed, closing a potential memory-DoS window.
                var isForbidden = false
                var membershipVerified = false

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            when (part.name) {
                                "workspaceId" -> {
                                    workspaceId = part.value.toIntOrNull()
                                    workspaceId?.let { wsId ->
                                        if (!dbQuery { isMember(actingUserId, wsId) }) {
                                            isForbidden = true
                                        } else {
                                            membershipVerified = true
                                        }
                                    }
                                }
                                "userName" -> userName = part.value
                                "localpath" -> localpath = part.value
                                "idempotencyKey" -> idempotencyKey = part.value.takeIf { it.isNotBlank() }
                            }
                            part.dispose()
                        }
                        is PartData.FileItem -> {
                            if (isForbidden || !membershipVerified) {
                                // Either explicitly forbidden or workspaceId hasn't arrived yet —
                                // refuse to buffer potentially 25 MB of unauthorized file data.
                                part.dispose()
                            } else {
                                // File(...).name strips any directory components (e.g. "../../etc/passwd" -> "passwd"),
                                // so a malicious client-supplied filename can't escape uploadDir below.
                                fileName = part.originalFileName?.let { File(it).name }?.ifBlank { null }
                                contentType = part.contentType?.toString()
                                // Streamed to disk with a hard cap regardless of what Content-Length claims
                                // (chunked transfer has none) — heap use stays flat whatever the file size.
                                if (staged == null) staged = part.stageToTempFile(MAX_UPLOAD_BYTES)
                                part.dispose()
                            }
                        }
                        else -> part.dispose()
                    }
                }

                if (isForbidden) {
                    call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                    return@post
                }

                val stagedFile = staged
                if (workspaceId == null || userName == null || stagedFile == null || fileName == null) {
                    val missingFields = mutableListOf<String>()
                    if (workspaceId == null) missingFields.add("workspaceId")
                    if (userName == null) missingFields.add("userName")
                    if (stagedFile == null) missingFields.add("fileBytes")
                    if (fileName == null) missingFields.add("fileName")

                    call.respond(HttpStatusCode.BadRequest, "Missing multipart assets: ${missingFields.joinToString(", ")}")
                    return@post
                }

                val finalMimeType = contentType ?: "application/octet-stream"
                val fileSize = stagedFile.length()

                if (fileSize == 0L) {
                    call.respond(HttpStatusCode.BadRequest, "File cannot be empty")
                    return@post
                }
                
                val forbiddenMimes = listOf("application/x-executable", "application/x-msdownload", "application/x-sh")
                if (finalMimeType in forbiddenMimes || fileName!!.endsWith(".exe") || fileName!!.endsWith(".sh")) {
                    call.respond(HttpStatusCode.UnsupportedMediaType, "Unsupported file type")
                    return@post
                }

                val contentHash = stagedFile.sha256Hex()
                val matchingPrior = idempotencyKey?.let { key ->
                    dbQuery { LocalFilesTable.selectAll().where { LocalFilesTable.idempotencyKey eq key }.singleOrNull() }
                }
                if (matchingPrior != null) {
                    val sameOperation = matchingPrior[LocalFilesTable.userId] == actingUserId &&
                        matchingPrior[LocalFilesTable.workspaceId] == workspaceId &&
                        matchingPrior[LocalFilesTable.userName] == userName &&
                        matchingPrior[LocalFilesTable.fileName] == fileName &&
                        matchingPrior[LocalFilesTable.mimeType] == finalMimeType &&
                        matchingPrior[LocalFilesTable.sizeBytes] == fileSize &&
                        matchingPrior[LocalFilesTable.contentHash] == contentHash
                    if (!sameOperation) {
                        call.respond(HttpStatusCode.Conflict, "Idempotency key was already used for different file content")
                    } else {
                        call.respond(HttpStatusCode.Created, matchingPrior.toFileResponse())
                    }
                    return@post
                }

                val uniqueFileName = "${UUID.randomUUID()}_$fileName"

                val cloudLocation = if (CloudinaryService.isConfigured) {
                    try {
                        CloudinaryService.uploadRawFile(
                            file = stagedFile,
                            folder = "workspace_files/$workspaceId",
                            publicId = uniqueFileName.replace(Regex("[^A-Za-z0-9._-]"), "_"),
                            fileName = fileName!!,
                            contentType = finalMimeType
                        )
                    } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                        logger.warn("[FileUpload] Cloudinary upload failed", e)
                        if (durableStorageRequired) {
                            call.respond(HttpStatusCode.ServiceUnavailable, "Durable file storage is temporarily unavailable")
                            return@post
                        }
                        null
                    }
                } else {
                    null
                }

                val generatedFileLocation = cloudLocation ?: run {
                    check(!durableStorageRequired) { "Production uploads cannot fall back to local disk" }
                    val uploadDir = File(System.getenv("UPLOAD_DIR") ?: "local_files_upload")
                    if (!uploadDir.exists()) {
                        uploadDir.mkdirs()
                    }
                    val physicalFile = File(uploadDir, uniqueFileName)
                    // A rename when the temp dir shares the volume, a copy-then-delete otherwise.
                    java.nio.file.Files.move(
                        stagedFile.toPath(), physicalFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                    physicalFile.absolutePath
                }

                val scheme = call.request.headers["X-Forwarded-Proto"] ?: "http"
                val host = call.request.headers["Host"] ?: "127.0.0.1:8080"
                val generatedUrl = "$scheme://$host/api/file/download/$uniqueFileName"
                val currentTimeMil = System.currentTimeMillis()

                val insertedId = try {
                    dbQuery {
                        val insertResult = LocalFilesTable.insertIgnore {
                            it[LocalFilesTable.userId] = actingUserId
                            it[LocalFilesTable.workspaceId] = workspaceId!!
                            it[LocalFilesTable.userName] = userName!!
                            it[LocalFilesTable.url] = generatedUrl
                            it[LocalFilesTable.storageKey] = uniqueFileName
                            it[LocalFilesTable.mimeType] = finalMimeType
                            it[LocalFilesTable.localPath] = localpath
                            it[LocalFilesTable.fileName] = fileName!!
                            it[LocalFilesTable.sizeBytes] = fileSize
                            it[LocalFilesTable.fileLocation] = generatedFileLocation
                            it[LocalFilesTable.idempotencyKey] = idempotencyKey
                            it[LocalFilesTable.contentHash] = contentHash
                            it[LocalFilesTable.updatedAt] = currentTimeMil
                            it[LocalFilesTable.isDeleted] = false
                        }
                        insertResult.resultedValues?.singleOrNull()?.get(LocalFilesTable.id)
                    }
                } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                    // No row will ever point at the stored copy — remove it instead of leaking it.
                    if (CloudinaryService.isCloudinaryUrl(generatedFileLocation)) {
                        CloudinaryService.deleteRawFile(generatedFileLocation)
                    } else {
                        File(generatedFileLocation).delete()
                    }
                    throw e
                }

                if (insertedId == null) {
                    val prior = idempotencyKey?.let { key ->
                        dbQuery { LocalFilesTable.selectAll().where { LocalFilesTable.idempotencyKey eq key }.singleOrNull() }
                    }
                    val sameOperation = prior != null &&
                        prior[LocalFilesTable.userId] == actingUserId &&
                        prior[LocalFilesTable.workspaceId] == workspaceId &&
                        prior[LocalFilesTable.userName] == userName &&
                        prior[LocalFilesTable.fileName] == fileName &&
                        prior[LocalFilesTable.mimeType] == finalMimeType &&
                        prior[LocalFilesTable.sizeBytes] == fileSize &&
                        prior[LocalFilesTable.contentHash] == contentHash
                    if (CloudinaryService.isCloudinaryUrl(generatedFileLocation)) {
                        CloudinaryService.deleteRawFile(generatedFileLocation)
                    } else {
                        File(generatedFileLocation).delete()
                    }
                    if (!sameOperation) {
                        call.respond(HttpStatusCode.Conflict, "Idempotency key was already used for different file content")
                    } else {
                        call.respond(HttpStatusCode.Created, prior!!.toFileResponse())
                    }
                    return@post
                }

                val response = FileResponse(
                    id = insertedId,
                    userId = actingUserId,
                    workspaceId = workspaceId!!,
                    userName = userName!!,
                    url = generatedUrl,
                    mimeType = finalMimeType,
                    localpath = localpath,
                    fileName = fileName!!,
                    sizebytes = fileSize,
                    fileLocation = generatedFileLocation
                )

                call.respond(HttpStatusCode.Created, response)
            } catch (e: UploadTooLargeException) {
                call.respond(HttpStatusCode.PayloadTooLarge, e.message ?: "File too large")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.error("[FileUpload] Upload failed", e)
                call.respond(HttpStatusCode.InternalServerError, "File upload failed")
            } finally {
                // Already gone if it was moved into the upload dir; otherwise this is the temp copy.
                staged?.delete()
            }
        }

        get("/workspace/{workspaceId}") {
            try {
                val workspaceIdParam = call.parameters["workspaceId"]?.toIntOrNull()
                val actingUserId = call.authenticatedUserId()
                if (workspaceIdParam == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid workspaceId")
                    return@get
                }

                val filesList = dbQuery {
                    if (!isMember(actingUserId, workspaceIdParam)) {
                        return@dbQuery null
                    }
                    LocalFilesTable.selectAll().where {
                        (LocalFilesTable.workspaceId eq workspaceIdParam) and (LocalFilesTable.isDeleted eq false)
                    }.map {
                        FileResponse(
                            id = it[LocalFilesTable.id],
                            userId = it[LocalFilesTable.userId],
                            workspaceId = it[LocalFilesTable.workspaceId],
                            userName = it[LocalFilesTable.userName],
                            url = it[LocalFilesTable.url],
                            mimeType = it[LocalFilesTable.mimeType],
                            localpath = it[LocalFilesTable.localPath],
                            fileName = it[LocalFilesTable.fileName],
                            sizebytes = it[LocalFilesTable.sizeBytes],
                            fileLocation = it[LocalFilesTable.fileLocation]
                        )
                    }
                }
                if (filesList == null) {
                    call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                } else {
                    call.respond(HttpStatusCode.OK, filesList)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.InternalServerError, "Error retrieving files list")
            }
        }

        get("/updates") {
            try {
                val workspaceIdParam = call.request.queryParameters["workspaceId"]?.toIntOrNull()
                val syncRequest = call.syncRequest(sinceParam = "lastSyncTime")
                val hasLegacyWatermark = call.request.queryParameters["lastSyncTime"]?.toLongOrNull() != null
                val actingUserId = call.authenticatedUserId()

                if (workspaceIdParam == null || (syncRequest.cursor == null && !hasLegacyWatermark)) {
                    call.respond(HttpStatusCode.BadRequest, "Missing or invalid workspaceId or lastSyncTime tracking values.")
                    return@get
                }

                val page = dbQuery {
                    if (!isMember(actingUserId, workspaceIdParam)) {
                        return@dbQuery null
                    }
                    fileDeltaSync(workspaceIdParam, syncRequest)
                }
                if (page == null) {
                    call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                } else {
                    recordSyncPage("files", page.rows.size)
                    call.appendSyncHeaders(page.nextCursor, page.reset, page.nextPageToken)
                    call.respond(HttpStatusCode.OK, page.rows)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.error("[Sync] File sync failed", e)
                call.respond(HttpStatusCode.InternalServerError, "Error fetching delta updates loop context.")
            }
        }

        get("/download/{fileName}") {
            try {
                val rawFileNameParam = call.parameters["fileName"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing filename")
                // Strip any directory components a crafted path segment might smuggle in (e.g. "..%2F..%2Fetc%2Fpasswd").
                val fileNameParam = File(rawFileNameParam).name
                if (fileNameParam.isBlank()) {
                    return@get call.respond(HttpStatusCode.BadRequest, "Invalid filename")
                }
                val actingUserId = call.authenticatedUserId()

                val fileRow = dbQuery {
                    LocalFilesTable.selectAll()
                        .where {
                            (LocalFilesTable.storageKey eq fileNameParam) and (LocalFilesTable.isDeleted eq false)
                        }
                        .singleOrNull()
                }

                if (fileRow == null) {
                    call.respond(HttpStatusCode.NotFound, "File not found")
                    return@get
                }

                val allowed = dbQuery { isMember(actingUserId, fileRow[LocalFilesTable.workspaceId]) }
                if (!allowed) {
                    call.respond(HttpStatusCode.Forbidden, "Not a member of this workspace")
                    return@get
                }

                val storedLocation = fileRow[LocalFilesTable.fileLocation]
                if (CloudinaryService.isCloudinaryUrl(storedLocation)) {
                    // Generate a short-lived signed URL and redirect — avoids piping bytes
                    // through the server. The client (or CDN) fetches directly from Cloudinary.
                    // This cuts server egress bandwidth to ~0 for cloud-stored files.
                    val signedUrl = CloudinaryService.signedDownloadUrl(
                        originalUrl = storedLocation,
                        expiresInSeconds = 1800  // 30 minutes — ample for a download to start
                    )
                    call.respondRedirect(signedUrl, permanent = false)
                    return@get
                }

                val uploadDir = File(System.getenv("UPLOAD_DIR") ?: "local_files_upload").canonicalFile
                val file = File(uploadDir, fileNameParam).canonicalFile
                if (!file.path.startsWith(uploadDir.path + File.separator)) {
                    return@get call.respond(HttpStatusCode.BadRequest, "Invalid filename")
                }

                if (file.exists()) {
                    call.respondFile(file)
                } else {
                    call.respond(HttpStatusCode.NotFound, "File not found on server")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                call.respond(HttpStatusCode.InternalServerError, "Error downloading file")
            }
        }

        delete("/{fileId}") {
            try {
                val fileIdParam = call.parameters["fileId"]?.toLongOrNull()
                if (fileIdParam == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing or invalid fileId parameter")
                    return@delete
                }
                val actingUserId = call.authenticatedUserId()

                val (updatedRows, fileLocation) = dbQuery {
                    val fileRow = LocalFilesTable.selectAll().where { LocalFilesTable.id eq fileIdParam }.singleOrNull()
                        ?: return@dbQuery -1 to null
                    if (!isMember(actingUserId, fileRow[LocalFilesTable.workspaceId])) {
                        return@dbQuery -2 to null
                    }
                    val updated = LocalFilesTable.update({ LocalFilesTable.id eq fileIdParam }) {
                        it[LocalFilesTable.isDeleted] = true
                        it[LocalFilesTable.updatedAt] = System.currentTimeMillis()
                    }
                    updated to fileRow[LocalFilesTable.fileLocation]
                }

                when {
                    updatedRows == -2 -> call.respond(HttpStatusCode.Forbidden, false)
                    updatedRows > 0 -> {
                        // Best-effort physical cleanup — the row is already soft-deleted either way.
                        call.respond(HttpStatusCode.OK, true)
                        fileLocation?.let { location ->
                            if (CloudinaryService.isCloudinaryUrl(location)) {
                                CloudinaryService.deleteRawFile(location)
                            } else {
                                runCatching { File(location).delete() }
                            }
                        }
                    }
                    else -> call.respond(HttpStatusCode.NotFound, false)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, false)
            }
        }
    }
}
