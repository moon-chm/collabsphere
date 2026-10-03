package com.collabsphere.util

import com.collabsphere.model.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

/**
 * Feature G: Code Snippet Embedding Service.
 *
 * Handles parsing GitHub code URLs, resolving file content securely,
 * caching, and managing code references attached to tasks/messages/DMs.
 *
 * Security model:
 * - All file resolution goes through the server (Android never gets tokens).
 * - Repository must be linked to the requesting workspace.
 * - User must be a member of the workspace.
 * - Cache is keyed by (repositoryId, commitSha, filePath) — workspace-scoped access control.
 */
object GitHubCodeSnippetService {

    private const val MAX_LINE_RANGE = 500
    private const val MAX_FILE_SIZE = 512 * 1024 // 512KB

    // ─── URL Parsing ──────────────────────────────────────────────────────

    @Serializable
    data class ParsedCodeUrl(
        val owner: String,
        val repo: String,
        val ref: String,          // branch or commit SHA
        val filePath: String,
        val startLine: Int? = null,
        val endLine: Int? = null
    ) {
        val repoFullName get() = "$owner/$repo"
        val isCommitRef get() = ref.matches(Regex("^[a-f0-9]{7,40}$"))
    }

    // Matches: https://github.com/owner/repo/blob/<ref>/path/to/file#L10-L20
    private val CODE_URL_REGEX = Regex(
        """^https://github\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.+?)(?:#L(\d+)(?:-L(\d+))?)?$"""
    )

    fun parseCodeUrl(url: String): ParsedCodeUrl? {
        val cleanUrl = url.trim()
        val match = CODE_URL_REGEX.matchEntire(cleanUrl) ?: return null
        val owner = match.groupValues[1]
        val repo = match.groupValues[2]
        val ref = match.groupValues[3]
        val filePath = java.net.URLDecoder.decode(match.groupValues[4], "UTF-8")
        val startLine = match.groupValues[5].toIntOrNull()
        val endLine = match.groupValues[6].toIntOrNull()

        // Validate line ranges
        if (startLine != null && startLine < 1) return null
        if (endLine != null && startLine != null && endLine < startLine) return null
        if (startLine != null && endLine != null && (endLine - startLine + 1) > MAX_LINE_RANGE) return null

        return ParsedCodeUrl(owner, repo, ref, filePath, startLine, endLine)
    }

    /**
     * Infer language from file extension for syntax highlighting hints.
     */
    fun inferLanguage(filePath: String): String? {
        val ext = filePath.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "js" -> "javascript"
            "ts" -> "typescript"
            "jsx" -> "jsx"
            "tsx" -> "tsx"
            "go" -> "go"
            "rs" -> "rust"
            "rb" -> "ruby"
            "cpp", "cc", "cxx" -> "cpp"
            "c", "h" -> "c"
            "cs" -> "csharp"
            "swift" -> "swift"
            "json" -> "json"
            "xml" -> "xml"
            "yaml", "yml" -> "yaml"
            "toml" -> "toml"
            "md", "markdown" -> "markdown"
            "html", "htm" -> "html"
            "css" -> "css"
            "scss", "sass" -> "scss"
            "sql" -> "sql"
            "sh", "bash" -> "bash"
            "dockerfile" -> "dockerfile"
            "gradle" -> "groovy"
            "proto" -> "protobuf"
            "dart" -> "dart"
            else -> null
        }
    }

    // ─── File Resolution ──────────────────────────────────────────────────

    @Serializable
    data class CodeSnippetResult(
        val content: String? = null,
        val language: String? = null,
        val startLine: Int? = null,
        val endLine: Int? = null,
        val totalLines: Int = 0,
        val filePath: String = "",
        val repoFullName: String = "",
        val commitSha: String? = null,
        val canonicalUrl: String = "",
        val isBinary: Boolean = false,
        val isTooLarge: Boolean = false,
        val sizeBytes: Int = 0,
        val error: String? = null
    )

    /**
     * Resolve a code snippet from a GitHub code URL.
     *
     * Authorization flow:
     * 1. Verify workspace membership (caller responsibility)
     * 2. Verify repository is linked to workspace
     * 3. Obtain GitHub token via installation
     * 4. Check cache first
     * 5. Fetch from GitHub API if cache miss
     * 6. Cache the result
     * 7. Extract requested line range
     */
    suspend fun resolveSnippet(
        workspaceId: Int,
        url: String
    ): CodeSnippetResult {
        val parsed = parseCodeUrl(url)
            ?: return CodeSnippetResult(error = "Invalid GitHub code URL")

        // Verify repository is linked to this workspace
        val repoRow = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            GitHubRepositoriesTable.selectAll()
                .where {
                    (GitHubRepositoriesTable.workspaceId eq workspaceId) and
                        (GitHubRepositoriesTable.fullName.lowerCase() eq parsed.repoFullName.lowercase())
                }
                .singleOrNull()
        } ?: return CodeSnippetResult(error = "Repository not linked to this workspace")

        val repositoryId = repoRow[GitHubRepositoriesTable.id]

        // Get token
        val connRow = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            GitHubConnectionsTable.selectAll()
                .where { GitHubConnectionsTable.id eq repoRow[GitHubRepositoriesTable.connectionId] }
                .singleOrNull()
        } ?: return CodeSnippetResult(error = "No GitHub connection found")

        val token = GitHubService.repoAccessToken(
            connRow[GitHubConnectionsTable.installationId],
            CryptoService.decrypt(connRow[GitHubConnectionsTable.accessTokenEncrypted])
        ) ?: return CodeSnippetResult(error = "Unable to obtain GitHub access token")

        // Resolve commit SHA if ref is a branch
        val commitSha = if (parsed.isCommitRef) {
            parsed.ref
        } else {
            GitHubService.resolveRef(token, parsed.repoFullName, parsed.ref) ?: parsed.ref
        }

        // Check cache
        val cached = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            GitHubCodeCacheTable.selectAll()
                .where {
                    (GitHubCodeCacheTable.repositoryId eq repositoryId) and
                        (GitHubCodeCacheTable.commitSha eq commitSha) and
                        (GitHubCodeCacheTable.filePath eq parsed.filePath)
                }
                .singleOrNull()
        }

        val content: String?
        val isBinary: Boolean
        val totalLines: Int
        val sizeBytes: Int
        val language: String?

        if (cached != null) {
            content = if (cached[GitHubCodeCacheTable.isBinary]) null else cached[GitHubCodeCacheTable.content]
            isBinary = cached[GitHubCodeCacheTable.isBinary]
            totalLines = cached[GitHubCodeCacheTable.totalLines]
            sizeBytes = cached[GitHubCodeCacheTable.sizeBytes]
            language = cached[GitHubCodeCacheTable.language]
        } else {
            // Fetch from GitHub
            val result = GitHubService.getFileContent(token, parsed.repoFullName, parsed.filePath, commitSha)
                ?: return CodeSnippetResult(error = "Failed to fetch file from GitHub")

            val (fetchedContent, fetchedIsBinary, fetchedSize) = result

            content = fetchedContent
            isBinary = fetchedIsBinary
            sizeBytes = fetchedSize
            totalLines = content?.lines()?.size ?: 0
            language = inferLanguage(parsed.filePath)

            // Cache the result
            if (content != null || isBinary) {
                newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
                    GitHubCodeCacheTable.insertIgnore {
                        it[GitHubCodeCacheTable.repositoryId] = repositoryId
                        it[GitHubCodeCacheTable.commitSha] = commitSha
                        it[GitHubCodeCacheTable.filePath] = parsed.filePath
                        it[GitHubCodeCacheTable.content] = content ?: ""
                        it[GitHubCodeCacheTable.language] = language
                        it[GitHubCodeCacheTable.totalLines] = totalLines
                        it[GitHubCodeCacheTable.sizeBytes] = sizeBytes
                        it[GitHubCodeCacheTable.isBinary] = isBinary
                    }
                }
            }
        }

        if (isBinary) {
            return CodeSnippetResult(
                isBinary = true,
                filePath = parsed.filePath,
                repoFullName = parsed.repoFullName,
                commitSha = commitSha,
                canonicalUrl = "https://github.com/${parsed.repoFullName}/blob/$commitSha/${parsed.filePath}",
                sizeBytes = sizeBytes
            )
        }

        if (content == null) {
            return CodeSnippetResult(
                error = "File not found or empty",
                filePath = parsed.filePath,
                repoFullName = parsed.repoFullName,
                commitSha = commitSha,
                canonicalUrl = url
            )
        }

        if (sizeBytes > MAX_FILE_SIZE) {
            return CodeSnippetResult(
                isTooLarge = true,
                filePath = parsed.filePath,
                repoFullName = parsed.repoFullName,
                commitSha = commitSha,
                canonicalUrl = "https://github.com/${parsed.repoFullName}/blob/$commitSha/${parsed.filePath}",
                sizeBytes = sizeBytes,
                totalLines = totalLines
            )
        }

        // Extract requested line range
        val lines = content.lines()
        val actualStart = parsed.startLine ?: 1
        val actualEnd = (parsed.endLine ?: lines.size).coerceAtMost(lines.size)
        val snippetLines = if (actualStart <= lines.size) {
            lines.subList(actualStart - 1, actualEnd.coerceAtMost(lines.size))
        } else {
            emptyList()
        }

        val lineFragment = if (parsed.startLine != null) {
            if (parsed.endLine != null) "#L${parsed.startLine}-L${parsed.endLine}" else "#L${parsed.startLine}"
        } else ""

        return CodeSnippetResult(
            content = snippetLines.joinToString("\n"),
            language = language,
            startLine = actualStart,
            endLine = actualEnd,
            totalLines = totalLines,
            filePath = parsed.filePath,
            repoFullName = parsed.repoFullName,
            commitSha = commitSha,
            canonicalUrl = "https://github.com/${parsed.repoFullName}/blob/$commitSha/${parsed.filePath}$lineFragment",
            sizeBytes = sizeBytes
        )
    }

    // ─── Code Reference Management ────────────────────────────────────────

    @Serializable
    data class CodeReferenceInfo(
        val id: Int,
        val repositoryId: Int,
        val filePath: String,
        val ref: String,
        val commitSha: String?,
        val startLine: Int?,
        val endLine: Int?,
        val canonicalUrl: String,
        val referenceType: String,
        val referenceId: Int,
        val createdByUserId: Int,
        val createdAt: Long
    )

    /** Attach a code reference to a task, message, or DM. */
    suspend fun attachCodeReference(
        workspaceId: Int,
        url: String,
        referenceType: String,
        referenceId: Int,
        userId: Int
    ): CodeReferenceInfo? {
        val parsed = parseCodeUrl(url) ?: return null

        val repoRow = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            GitHubRepositoriesTable.selectAll()
                .where {
                    (GitHubRepositoriesTable.workspaceId eq workspaceId) and
                        (GitHubRepositoriesTable.fullName.lowerCase() eq parsed.repoFullName.lowercase())
                }
                .singleOrNull()
        } ?: return null

        val repositoryId = repoRow[GitHubRepositoriesTable.id]

        // Resolve commit SHA for stability
        val connRow = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            GitHubConnectionsTable.selectAll()
                .where { GitHubConnectionsTable.id eq repoRow[GitHubRepositoriesTable.connectionId] }
                .singleOrNull()
        }

        val commitSha = if (parsed.isCommitRef) {
            parsed.ref
        } else if (connRow != null) {
            val token = GitHubService.repoAccessToken(
                connRow[GitHubConnectionsTable.installationId],
                CryptoService.decrypt(connRow[GitHubConnectionsTable.accessTokenEncrypted])
            )
            if (token != null) GitHubService.resolveRef(token, parsed.repoFullName, parsed.ref) else null
        } else null

        val lineFragment = if (parsed.startLine != null) {
            if (parsed.endLine != null) "#L${parsed.startLine}-L${parsed.endLine}" else "#L${parsed.startLine}"
        } else ""
        val canonicalUrl = "https://github.com/${parsed.repoFullName}/blob/${commitSha ?: parsed.ref}/${parsed.filePath}$lineFragment"

        val insertedId = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            GitHubCodeReferencesTable.insert {
                it[GitHubCodeReferencesTable.workspaceId] = workspaceId
                it[GitHubCodeReferencesTable.repositoryId] = repositoryId
                it[GitHubCodeReferencesTable.filePath] = parsed.filePath
                it[GitHubCodeReferencesTable.ref] = parsed.ref
                it[GitHubCodeReferencesTable.commitSha] = commitSha
                it[GitHubCodeReferencesTable.startLine] = parsed.startLine
                it[GitHubCodeReferencesTable.endLine] = parsed.endLine
                it[GitHubCodeReferencesTable.canonicalUrl] = canonicalUrl
                it[GitHubCodeReferencesTable.referenceType] = referenceType
                it[GitHubCodeReferencesTable.referenceId] = referenceId
                it[GitHubCodeReferencesTable.createdByUserId] = userId
            }[GitHubCodeReferencesTable.id]
        }

        return CodeReferenceInfo(
            id = insertedId,
            repositoryId = repositoryId,
            filePath = parsed.filePath,
            ref = parsed.ref,
            commitSha = commitSha,
            startLine = parsed.startLine,
            endLine = parsed.endLine,
            canonicalUrl = canonicalUrl,
            referenceType = referenceType,
            referenceId = referenceId,
            createdByUserId = userId,
            createdAt = System.currentTimeMillis()
        )
    }

    /** Get all code references for a given target (task, message, DM). */
    suspend fun getCodeReferences(
        workspaceId: Int,
        referenceType: String,
        referenceId: Int
    ): List<CodeReferenceInfo> = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
        GitHubCodeReferencesTable.selectAll()
            .where {
                (GitHubCodeReferencesTable.workspaceId eq workspaceId) and
                    (GitHubCodeReferencesTable.referenceType eq referenceType) and
                    (GitHubCodeReferencesTable.referenceId eq referenceId)
            }
            .map {
                CodeReferenceInfo(
                    id = it[GitHubCodeReferencesTable.id],
                    repositoryId = it[GitHubCodeReferencesTable.repositoryId],
                    filePath = it[GitHubCodeReferencesTable.filePath],
                    ref = it[GitHubCodeReferencesTable.ref],
                    commitSha = it[GitHubCodeReferencesTable.commitSha],
                    startLine = it[GitHubCodeReferencesTable.startLine],
                    endLine = it[GitHubCodeReferencesTable.endLine],
                    canonicalUrl = it[GitHubCodeReferencesTable.canonicalUrl],
                    referenceType = it[GitHubCodeReferencesTable.referenceType],
                    referenceId = it[GitHubCodeReferencesTable.referenceId],
                    createdByUserId = it[GitHubCodeReferencesTable.createdByUserId],
                    createdAt = it[GitHubCodeReferencesTable.createdAt]
                )
            }
    }

    /** Delete a code reference (only by the creator). */
    suspend fun deleteCodeReference(referenceId: Int, userId: Int): Boolean = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
        val deleted = GitHubCodeReferencesTable.deleteWhere {
            (GitHubCodeReferencesTable.id eq referenceId) and
                (GitHubCodeReferencesTable.createdByUserId eq userId)
        }
        deleted > 0
    }
}
