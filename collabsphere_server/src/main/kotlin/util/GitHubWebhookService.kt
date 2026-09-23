package com.collabsphere.util

import com.collabsphere.model.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object GitHubWebhookService {

    private val webhookSecret = System.getenv("GITHUB_WEBHOOK_SECRET")

    /**
     * Verifies the GitHub webhook payload signature to ensure it actually came from GitHub.
     */
    fun verifySignature(payload: String, signatureHeader: String?): Boolean {
        if (webhookSecret.isNullOrBlank() || signatureHeader == null) return false

        try {
            val algorithm = "HmacSHA256"
            val mac = Mac.getInstance(algorithm)
            val secretKeySpec = SecretKeySpec(webhookSecret.toByteArray(), algorithm)
            mac.init(secretKeySpec)

            val hashBytes = mac.doFinal(payload.toByteArray())
            val hexHash = hashBytes.joinToString("") { "%02x".format(it) }
            val expectedSignature = "sha256=$hexHash"

            return MessageDigest.isEqual(expectedSignature.toByteArray(), signatureHeader.toByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Parses the incoming JSON payload and inserts analytics data into the database.
     */
    fun handleWebhookEvent(eventType: String?, payloadJson: String) {
        val json = Json { ignoreUnknownKeys = true }
        val payload = try {
            json.parseToJsonElement(payloadJson).jsonObject
        } catch (e: Exception) {
            println("Failed to parse webhook JSON: ${e.message}")
            return
        }

        when (eventType) {
            "push" -> handlePushEvent(payload)
            "pull_request" -> handlePullRequestEvent(payload)
            else -> println("Unhandled GitHub event type: $eventType")
        }
    }

    private fun handlePushEvent(payload: JsonObject) {
        // Extract repository info
        val repo = payload["repository"]?.jsonObject ?: return
        val githubRepoId = repo["id"]?.jsonPrimitive?.longOrNull ?: return

        // Extract commits
        val commits = payload["commits"]?.jsonArray ?: return

        transaction {
            // Find our internal repository ID
            val internalRepoId = GitHubRepositoriesTable
                .select { GitHubRepositoriesTable.githubRepoId eq githubRepoId }
                .singleOrNull()?.get(GitHubRepositoriesTable.id) ?: return@transaction

            // Insert commits
            for (commitElement in commits) {
                val commit = commitElement.jsonObject
                val sha = commit["id"]?.jsonPrimitive?.content ?: continue
                val message = commit["message"]?.jsonPrimitive?.content ?: ""
                val author = commit["author"]?.jsonObject
                val authorName = author?.get("name")?.jsonPrimitive?.content
                val authorEmail = author?.get("email")?.jsonPrimitive?.content
                val timestampStr = commit["timestamp"]?.jsonPrimitive?.content
                val commitDate = timestampStr?.let { Instant.parse(it).toEpochMilli() } ?: System.currentTimeMillis()

                GitHubCommitsTable.insert {
                    it[repositoryId] = internalRepoId
                    it[this.sha] = sha
                    it[this.message] = message
                    it[this.authorName] = authorName
                    it[this.authorEmail] = authorEmail
                    it[this.commitDate] = commitDate
                }
            }
        }
    }

    private fun handlePullRequestEvent(payload: JsonObject) {
        val action = payload["action"]?.jsonPrimitive?.content ?: return
        val pr = payload["pull_request"]?.jsonObject ?: return
        val repo = payload["repository"]?.jsonObject ?: return
        
        val githubRepoId = repo["id"]?.jsonPrimitive?.longOrNull ?: return
        val prId = pr["id"]?.jsonPrimitive?.longOrNull ?: return
        val number = pr["number"]?.jsonPrimitive?.intOrNull ?: return
        val title = pr["title"]?.jsonPrimitive?.content ?: ""
        val state = pr["state"]?.jsonPrimitive?.content ?: "open"
        val authorUsername = pr["user"]?.jsonObject?.get("login")?.jsonPrimitive?.content ?: ""
        
        val createdAtStr = pr["created_at"]?.jsonPrimitive?.content
        val closedAtStr = pr["closed_at"]?.jsonPrimitive?.contentOrNull
        val mergedAtStr = pr["merged_at"]?.jsonPrimitive?.contentOrNull

        val createdAt = createdAtStr?.let { try { Instant.parse(it).toEpochMilli() } catch (e:Exception) { null } } ?: System.currentTimeMillis()
        val closedAt = closedAtStr?.let { try { Instant.parse(it).toEpochMilli() } catch (e:Exception) { null } }
        val mergedAt = mergedAtStr?.let { try { Instant.parse(it).toEpochMilli() } catch (e:Exception) { null } }

        transaction {
            val internalRepoId = GitHubRepositoriesTable
                .select { GitHubRepositoriesTable.githubRepoId eq githubRepoId }
                .singleOrNull()?.get(GitHubRepositoriesTable.id) ?: return@transaction

            // Simple insert (In a full production scenario, we'd upsert based on githubPrId to handle 'opened', 'closed', 'reopened' actions)
            GitHubPullRequestsTable.insert {
                it[repositoryId] = internalRepoId
                it[githubPrId] = prId
                it[this.number] = number
                it[this.title] = title
                it[this.state] = state
                it[this.authorUsername] = authorUsername
                it[this.createdAt] = createdAt
                it[this.closedAt] = closedAt
                it[this.mergedAt] = mergedAt
            }
        }
    }
}
