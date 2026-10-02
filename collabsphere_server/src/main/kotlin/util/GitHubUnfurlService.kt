package com.collabsphere.util

import com.collabsphere.dto.GitHubPreviewItem
import com.collabsphere.model.GitHubCommitsTable
import com.collabsphere.model.GitHubConnectionsTable
import com.collabsphere.model.GitHubIssuesTable
import com.collabsphere.model.GitHubPullRequestsTable
import com.collabsphere.model.GitHubRepositoriesTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import kotlinx.coroutines.async

object GitHubUnfurlService {

    sealed class ParsedUrl {
        abstract val owner: String
        abstract val repo: String
        val repoFullName get() = "$owner/$repo"

        data class Repository(override val owner: String, override val repo: String) : ParsedUrl()
        data class PullRequest(override val owner: String, override val repo: String, val number: Int) : ParsedUrl()
        data class Issue(override val owner: String, override val repo: String, val number: Int) : ParsedUrl()
        data class Commit(override val owner: String, override val repo: String, val sha: String) : ParsedUrl()
    }

    private val PULL_REQUEST_REGEX = Regex("^https://github\\.com/([^/]+)/([^/]+)/pull/(\\d+).*$")
    private val ISSUE_REGEX = Regex("^https://github\\.com/([^/]+)/([^/]+)/issues/(\\d+).*$")
    private val COMMIT_REGEX = Regex("^https://github\\.com/([^/]+)/([^/]+)/commit/([a-f0-9]+).*$")
    private val REPO_REGEX = Regex("^https://github\\.com/([^/]+)/([^/]+)/?$")

    fun parse(url: String): ParsedUrl? {
        val cleanUrl = url.substringBefore("#").substringBefore("?")
        
        PULL_REQUEST_REGEX.matchEntire(cleanUrl)?.let { match ->
            return ParsedUrl.PullRequest(match.groupValues[1], match.groupValues[2], match.groupValues[3].toInt())
        }
        ISSUE_REGEX.matchEntire(cleanUrl)?.let { match ->
            return ParsedUrl.Issue(match.groupValues[1], match.groupValues[2], match.groupValues[3].toInt())
        }
        COMMIT_REGEX.matchEntire(cleanUrl)?.let { match ->
            return ParsedUrl.Commit(match.groupValues[1], match.groupValues[2], match.groupValues[3])
        }
        REPO_REGEX.matchEntire(cleanUrl)?.let { match ->
            return ParsedUrl.Repository(match.groupValues[1], match.groupValues[2])
        }
        return null
    }

    suspend fun unfurl(workspaceId: Int, urls: List<String>): Map<String, GitHubPreviewItem> {
        val previews = mutableMapOf<String, GitHubPreviewItem>()
        val parsedUrls = urls.associateWith { parse(it) }

        // Step 1: Look up in local DB and identify misses
        val misses = mutableListOf<Pair<String, ParsedUrl>>()

        newSuspendedTransaction {
            val workspaceRepos = GitHubRepositoriesTable.selectAll()
                .where { GitHubRepositoriesTable.workspaceId eq workspaceId }
                .associateBy { it[GitHubRepositoriesTable.fullName].lowercase() }

            for ((url, parsed) in parsedUrls) {
                if (parsed == null) continue

                val repoRow = workspaceRepos[parsed.repoFullName.lowercase()]
                if (repoRow == null) {
                    continue // Not linked, skip
                }

                val repoId = repoRow[GitHubRepositoriesTable.id]
                val repoFullName = repoRow[GitHubRepositoriesTable.fullName]

                when (parsed) {
                    is ParsedUrl.Repository -> {
                        previews[url] = GitHubPreviewItem(
                            type = "REPOSITORY",
                            url = url,
                            title = repoRow[GitHubRepositoriesTable.fullName],
                            repoFullName = repoFullName,
                            isPrivate = repoRow[GitHubRepositoriesTable.isPrivate]
                        )
                    }
                    is ParsedUrl.PullRequest -> {
                        val pr = GitHubPullRequestsTable.selectAll()
                            .where { (GitHubPullRequestsTable.repositoryId eq repoId) and (GitHubPullRequestsTable.number eq parsed.number) }
                            .singleOrNull()

                        if (pr != null) {
                            val headSha = pr[GitHubPullRequestsTable.headSha]
                            val ciStatus = if (headSha != null) {
                                com.collabsphere.util.GitHubDataStore.ciStatusFor(repoId, listOf(headSha))[headSha]
                            } else null
                            
                            previews[url] = GitHubPreviewItem(
                                type = "PULL_REQUEST",
                                url = url,
                                title = pr[GitHubPullRequestsTable.title],
                                repoFullName = repoFullName,
                                number = pr[GitHubPullRequestsTable.number],
                                state = pr[GitHubPullRequestsTable.state],
                                author = pr[GitHubPullRequestsTable.authorUsername],
                                merged = pr[GitHubPullRequestsTable.mergedAt] != null,
                                timestamp = pr[GitHubPullRequestsTable.createdAt],
                                ciStatus = ciStatus
                            )
                        } else {
                            misses.add(url to parsed)
                        }
                    }
                    is ParsedUrl.Issue -> {
                        val issue = GitHubIssuesTable.selectAll()
                            .where { (GitHubIssuesTable.repositoryId eq repoId) and (GitHubIssuesTable.number eq parsed.number) }
                            .singleOrNull()

                        if (issue != null) {
                            previews[url] = GitHubPreviewItem(
                                type = "ISSUE",
                                url = url,
                                title = issue[GitHubIssuesTable.title],
                                repoFullName = repoFullName,
                                number = issue[GitHubIssuesTable.number],
                                state = issue[GitHubIssuesTable.state],
                                author = issue[GitHubIssuesTable.authorUsername],
                                timestamp = issue[GitHubIssuesTable.createdAt]
                            )
                        } else {
                            misses.add(url to parsed)
                        }
                    }
                    is ParsedUrl.Commit -> {
                        val commit = GitHubCommitsTable.selectAll()
                            .where { (GitHubCommitsTable.repositoryId eq repoId) and (GitHubCommitsTable.sha eq parsed.sha) }
                            .singleOrNull()

                        if (commit != null) {
                            val sha = commit[GitHubCommitsTable.sha]
                            val ciStatus = com.collabsphere.util.GitHubDataStore.ciStatusFor(repoId, listOf(sha))[sha]
                            
                            previews[url] = GitHubPreviewItem(
                                type = "COMMIT",
                                url = url,
                                title = commit[GitHubCommitsTable.message].lines().firstOrNull() ?: parsed.sha,
                                repoFullName = repoFullName,
                                shortSha = parsed.sha.take(7),
                                author = commit[GitHubCommitsTable.authorName],
                                timestamp = commit[GitHubCommitsTable.commitDate],
                                ciStatus = ciStatus
                            )
                        } else {
                            misses.add(url to parsed)
                        }
                    }
                }
            }
        }

        // Step 2: Fetch misses from GitHub and save
        if (misses.isNotEmpty()) {
            val workspaceRepos = newSuspendedTransaction {
                GitHubRepositoriesTable.innerJoin(GitHubConnectionsTable)
                    .selectAll()
                    .where { GitHubRepositoriesTable.workspaceId eq workspaceId }
                    .associateBy({ it[GitHubRepositoriesTable.fullName].lowercase() }) { row ->
                        Triple(
                            row[GitHubRepositoriesTable.id],
                            row[GitHubRepositoriesTable.fullName],
                            row[GitHubConnectionsTable.installationId]
                        )
                    }
            }

            kotlinx.coroutines.withTimeoutOrNull(5000) {
                kotlinx.coroutines.coroutineScope {
                    val scope = this
                    val deferredFetches = misses.mapNotNull { (url, parsed) ->
                        val repoInfo = workspaceRepos[parsed.repoFullName.lowercase()] ?: return@mapNotNull null
                        val (repoId, repoFullName, installationId) = repoInfo
                        
                        val token = GitHubService.repoAccessToken(installationId, null) ?: return@mapNotNull null

                        // Deduplicate requests in-flight
                        val fetchKey = "${parsed.javaClass.simpleName}_${repoId}_${parsed.repoFullName}_${when(parsed) {
                            is ParsedUrl.PullRequest -> parsed.number
                            is ParsedUrl.Issue -> parsed.number
                            is ParsedUrl.Commit -> parsed.sha
                            else -> return@mapNotNull null
                        }}"

                        val deferred = inFlightFetches.getOrPut(fetchKey) {
                            scope.async {
                                try {
                                    fetchAndSave(parsed, url, repoId, repoFullName, token)
                                } finally {
                                    inFlightFetches.remove(fetchKey)
                                }
                            }
                        }
                        url to deferred
                    }

                    deferredFetches.forEach { (url, deferred) ->
                        val preview = deferred.await()
                        if (preview != null) {
                            previews[url] = preview
                        }
                    }
                }
            }
        }

        return previews
    }

    private val inFlightFetches = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<GitHubPreviewItem?>>()

    private suspend fun fetchAndSave(parsed: ParsedUrl, url: String, repoId: Int, repoFullName: String, token: String): GitHubPreviewItem? {
        when (parsed) {
            is ParsedUrl.PullRequest -> {
                val prInfo = GitHubService.getPullRequest(token, repoFullName, parsed.number)
                if (prInfo != null) {
                    val now = System.currentTimeMillis()
                    val prRecord = com.collabsphere.util.PullRequestRecord(
                        githubPrId = prInfo.id,
                        number = prInfo.number,
                        title = prInfo.title,
                        state = prInfo.state,
                        authorUsername = prInfo.user?.login ?: "",
                        createdAt = com.collabsphere.util.parseGitHubTime(prInfo.created_at) ?: now,
                        closedAt = com.collabsphere.util.parseGitHubTime(prInfo.closed_at),
                        mergedAt = com.collabsphere.util.parseGitHubTime(prInfo.merged_at),
                        body = prInfo.body.orEmpty(),
                        url = prInfo.html_url,
                        headSha = prInfo.head?.sha
                    )
                    newSuspendedTransaction { com.collabsphere.util.GitHubDataStore.savePullRequest(repoId, prRecord) }
                    val headSha = prInfo.head?.sha
                    val ciStatus = if (headSha != null) {
                        newSuspendedTransaction { com.collabsphere.util.GitHubDataStore.ciStatusFor(repoId, listOf(headSha))[headSha] }
                    } else null

                    return GitHubPreviewItem(
                        type = "PULL_REQUEST",
                        url = url,
                        title = prInfo.title,
                        repoFullName = repoFullName,
                        number = prInfo.number,
                        state = prInfo.state,
                        author = prInfo.user?.login ?: "",
                        merged = prInfo.merged_at != null,
                        timestamp = prRecord.createdAt,
                        ciStatus = ciStatus
                    )
                }
            }
            is ParsedUrl.Issue -> {
                val issueInfo = GitHubService.getIssue(token, repoFullName, parsed.number)
                if (issueInfo != null) {
                    val issueRecord = issueInfo.toRecord()
                    newSuspendedTransaction { com.collabsphere.util.GitHubDataStore.saveIssue(repoId, issueRecord) }
                    return GitHubPreviewItem(
                        type = "ISSUE",
                        url = url,
                        title = issueInfo.title,
                        repoFullName = repoFullName,
                        number = issueInfo.number,
                        state = issueInfo.state,
                        author = issueInfo.user?.login ?: "",
                        timestamp = issueRecord.createdAt
                    )
                }
            }
            is ParsedUrl.Commit -> {
                val commitInfo = GitHubService.getCommit(token, repoFullName, parsed.sha)
                if (commitInfo != null) {
                    val now = System.currentTimeMillis()
                    val commitRecord = com.collabsphere.util.CommitRecord(
                        sha = commitInfo.sha,
                        message = commitInfo.commit?.message ?: "",
                        authorName = commitInfo.commit?.author?.name,
                        authorEmail = commitInfo.commit?.author?.email,
                        commitDate = com.collabsphere.util.parseGitHubTime(commitInfo.commit?.author?.date) ?: now
                    )
                    newSuspendedTransaction { com.collabsphere.util.GitHubDataStore.saveCommits(repoId, listOf(commitRecord)) }
                    val ciStatus = newSuspendedTransaction { com.collabsphere.util.GitHubDataStore.ciStatusFor(repoId, listOf(parsed.sha))[parsed.sha] }

                    return GitHubPreviewItem(
                        type = "COMMIT",
                        url = url,
                        title = commitInfo.commit?.message?.lines()?.firstOrNull() ?: parsed.sha,
                        repoFullName = repoFullName,
                        shortSha = parsed.sha.take(7),
                        author = commitInfo.commit?.author?.name,
                        timestamp = commitRecord.commitDate,
                        ciStatus = ciStatus
                    )
                }
            }
            is ParsedUrl.Repository -> { /* Handled in step 1 */ }
        }
        return null
    }
}