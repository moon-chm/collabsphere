import com.collabsphere.DatabaseFactory
import com.collabsphere.model.*
import com.collabsphere.module
import com.collabsphere.util.JwtConfig
import com.collabsphere.util.PasswordHasher
import dto.LedgerPageResponse
import dto.ProgressionSummaryResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import plugins.progression.*
import java.util.UUID
import kotlin.test.*

/** Career progression against real PostgreSQL: route wiring, idempotency, concurrency and badge maths. */
class ProgressionIntegrationTest {

    companion object {
        init {
            DatabaseFactory.init()
        }
        private const val DAY = 86_400_000L
        private const val FIXED_NOON = 1_800_000_000_000L - (1_800_000_000_000L % DAY) + DAY / 2
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val createdUserIds = mutableSetOf<Int>()
    private val createdWorkspaceIds = mutableSetOf<Int>()

    @AfterTest
    fun cleanUp() {
        transaction {
            if (createdWorkspaceIds.isNotEmpty()) {
                NotificationsTable.deleteWhere { NotificationsTable.workspaceId inList createdWorkspaceIds }
                WorkspacesTable.deleteWhere { WorkspacesTable.id inList createdWorkspaceIds }
            }
            if (createdUserIds.isNotEmpty()) {
                // Ledger rows reference events, so remove them before the cascade from users runs.
                SpLedgerTable.deleteWhere { SpLedgerTable.userId inList createdUserIds }
                UserBadgeUnlocksTable.deleteWhere { UserBadgeUnlocksTable.userId inList createdUserIds }
                UsersTable.deleteWhere { UsersTable.id inList createdUserIds }
            }
        }
    }

    private fun user(): Int = transaction {
        UsersTable.insert {
            it[email] = "prog-${UUID.randomUUID()}@example.com"
            it[username] = "prog"
            it[password] = PasswordHasher.hash("password123")
            it[isEmailVerified] = true
        }[UsersTable.id]
    }.also { createdUserIds += it }

    private fun workspace(vararg members: Int): Int = transaction {
        val ws = WorkspacesTable.insert {
            it[userId] = members.first()
            it[workspaceName] = "progression"
            it[workspaceOwner] = "progression"
            it[workspacePassword] = ""
        }[WorkspacesTable.id]
        members.forEach { m -> WorkspaceMembersTable.insert { it[workspaceId] = ws; it[userId] = m } }
        ws
    }.also { createdWorkspaceIds += it }

    private fun token(userId: Int) = JwtConfig.generateToken(userId)

    private suspend fun HttpClient.createTask(creator: Int, ws: Int, assignee: Int, extra: String = ""): Int {
        val response = post("/api/tasks") {
            bearerAuth(token(creator))
            contentType(ContentType.Application.Json)
            setBody("""{"taskName":"t-${UUID.randomUUID()}","taskDescription":"","workspaceId":$ws,"assignedToUserId":$assignee,"status":"TODO"$extra}""")
        }
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return json.parseToJsonElement(response.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content.toInt()
    }

    private suspend fun HttpClient.setStatus(actor: Int, taskId: Int, ws: Int, assignee: Int, status: String, extra: String = ""): HttpResponse {
        val name = transaction { TasksTable.selectAll().where { TasksTable.id eq taskId }.single()[TasksTable.taskName] }
        return put("/api/tasks/$taskId") {
            bearerAuth(token(actor))
            contentType(ContentType.Application.Json)
            setBody("""{"taskName":"$name","taskDescription":"","workspaceId":$ws,"assignedToUserId":$assignee,"status":"$status"$extra}""")
        }
    }

    private fun totalSp(userId: Int): Long = transaction {
        UserProgressionTable.selectAll().where { UserProgressionTable.userId eq userId }
            .singleOrNull()?.get(UserProgressionTable.totalSp) ?: 0L
    }

    private fun ledgerSum(userId: Int): Long = transaction {
        val sum = SpLedgerTable.amount.sum()
        SpLedgerTable.select(sum).where { SpLedgerTable.userId eq userId }.single()[sum]?.toLong() ?: 0L
    }

    private fun counter(userId: Int, key: String): Long = transaction {
        UserBadgeCountersTable.selectAll()
            .where { (UserBadgeCountersTable.userId eq userId) and (UserBadgeCountersTable.counter eq key) }
            .singleOrNull()?.get(UserBadgeCountersTable.value) ?: 0L
    }

    private fun unlockedTiers(userId: Int, family: String): List<String> = transaction {
        UserBadgeUnlocksTable.selectAll()
            .where { (UserBadgeUnlocksTable.userId eq userId) and (UserBadgeUnlocksTable.familyKey eq family) }
            .map { it[UserBadgeUnlocksTable.tier] }.sorted()
    }

    // ── route wiring ─────────────────────────────────────────────────────────────

    @Test
    fun `completing a teammate's task awards sp once even when toggled`() = testApplication {
        application { module() }
        val creator = user()
        val assignee = user()
        val ws = workspace(creator, assignee)
        val task = client.createTask(creator, ws, assignee)

        assertEquals(HttpStatusCode.OK, client.setStatus(assignee, task, ws, assignee, "DONE").status)
        assertEquals(20, totalSp(assignee))
        assertEquals(HttpStatusCode.OK, client.setStatus(assignee, task, ws, assignee, "TODO").status)
        assertEquals(HttpStatusCode.OK, client.setStatus(assignee, task, ws, assignee, "DONE").status)

        assertEquals(20, totalSp(assignee), "re-completing a task never re-earns")
        assertEquals(totalSp(assignee), ledgerSum(assignee))
        assertEquals(1, counter(assignee, BadgeCounters.COLLABORATIVE_ACTIVITIES))
        assertEquals(0, totalSp(creator), "the creator earns nothing for someone else's completion")
        assertNotNull(transaction { TasksTable.selectAll().where { TasksTable.id eq task }.single()[TasksTable.completedAt] })
    }

    @Test
    fun `older apps can move a task back to to-do with the legacy status spelling`() = testApplication {
        application { module() }
        val creator = user()
        val assignee = user()
        val ws = workspace(creator, assignee)
        val task = client.createTask(creator, ws, assignee)
        assertEquals(HttpStatusCode.OK, client.setStatus(assignee, task, ws, assignee, "IN_PROGRESS").status)

        val response = client.setStatus(assignee, task, ws, assignee, "TO_DO")
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertEquals("TODO", transaction { TasksTable.selectAll().where { TasksTable.id eq task }.single()[TasksTable.status] })
    }

    @Test
    fun `concurrent duplicate completions award once`() = testApplication {
        application { module() }
        val creator = user()
        val assignee = user()
        val ws = workspace(creator, assignee)
        val task = client.createTask(creator, ws, assignee)

        val statuses = coroutineScope {
            List(4) { async { client.setStatus(assignee, task, ws, assignee, "DONE").status } }.awaitAll()
        }
        assertTrue(statuses.all { it == HttpStatusCode.OK }, "statuses=$statuses")
        assertEquals(20, totalSp(assignee))
        assertEquals(1L, transaction { SpLedgerTable.selectAll().where { SpLedgerTable.userId eq assignee }.count() })
    }

    @Test
    fun `deleted tasks earn nothing`() = testApplication {
        application { module() }
        val creator = user()
        val assignee = user()
        val ws = workspace(creator, assignee)
        val task = client.createTask(creator, ws, assignee)
        transaction { TasksTable.update({ TasksTable.id eq task }) { it[isDeleted] = true } }

        client.setStatus(assignee, task, ws, assignee, "DONE")
        assertEquals(0, totalSp(assignee))
    }

    @Test
    fun `on-time credit ignores a deadline the assignee moves while completing`() = testApplication {
        application { module() }
        val creator = user()
        val assignee = user()
        val ws = workspace(creator, assignee)
        val farFuture = System.currentTimeMillis() + 10 * DAY

        val planned = client.createTask(creator, ws, assignee, ""","dueDate":$farFuture""")
        client.setStatus(assignee, planned, ws, assignee, "DONE")
        assertEquals(1, counter(assignee, BadgeCounters.ON_TIME_COMPLETIONS))

        val overdue = client.createTask(creator, ws, assignee, ""","dueDate":${System.currentTimeMillis() - DAY}""")
        client.setStatus(assignee, overdue, ws, assignee, "DONE", ""","dueDate":$farFuture""")
        assertEquals(1, counter(assignee, BadgeCounters.ON_TIME_COMPLETIONS), "pushing the deadline out while completing is not on time")
        assertEquals(2, counter(assignee, BadgeCounters.TASKS_COMPLETED))
    }

    @Test
    fun `a teammate pinning a substantial note rewards the author once`() = testApplication {
        application { module() }
        val author = user()
        val teammate = user()
        val ws = workspace(author, teammate)
        val created = client.post("/api/notes") {
            bearerAuth(token(author))
            contentType(ContentType.Application.Json)
            setBody("""{"userId":$author,"workspaceId":$ws,"notesName":"Runbook","description":"${"Deploy steps and rollback plan. ".repeat(3)}"}""")
        }
        val noteId = json.parseToJsonElement(created.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content.toInt()
        suspend fun pin(actor: Int, pinned: Boolean) = client.post("/api/notes/$noteId/pin") {
            bearerAuth(token(actor))
            contentType(ContentType.Application.Json)
            setBody("""{"pinned":$pinned}""")
        }.status

        assertEquals(HttpStatusCode.OK, pin(author, true))
        assertEquals(0, totalSp(author), "self-pinning is not approval")
        assertEquals(HttpStatusCode.OK, pin(teammate, true))
        assertEquals(5, totalSp(author))
        pin(teammate, false)
        pin(teammate, true)
        assertEquals(5, totalSp(author), "pin/unpin cycles cannot farm approvals")
        assertEquals(1, counter(author, BadgeCounters.KNOWLEDGE_CONTRIBUTIONS))
    }

    @Test
    fun `the progression api is read-only, authenticated and paginated`() = testApplication {
        application { module() }
        val creator = user()
        val assignee = user()
        val ws = workspace(creator, assignee)
        repeat(3) { client.setStatus(assignee, client.createTask(creator, ws, assignee), ws, assignee, "DONE") }
        val auth: HttpRequestBuilder.() -> Unit = { bearerAuth(token(assignee)) }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/progression/summary").status)

        val summary = json.decodeFromString<ProgressionSummaryResponse>(client.get("/api/progression/summary", auth).bodyAsText())
        assertEquals(60, summary.totalSp)
        assertEquals(1, summary.level)
        assertEquals("Junior Developer", summary.nextTitle)
        assertEquals(60, summary.spIntoLevel)
        assertEquals(100, summary.spSpanToNext)
        assertEquals(1, summary.currentStreakDays)

        val catalog = client.get("/api/progression/catalog", auth)
        val etag = catalog.headers[HttpHeaders.ETag]
        assertNotNull(etag)
        assertEquals(HttpStatusCode.NotModified, client.get("/api/progression/catalog") { auth(); header(HttpHeaders.IfNoneMatch, etag) }.status)

        val firstPage = json.decodeFromString<LedgerPageResponse>(client.get("/api/progression/history?limit=2", auth).bodyAsText())
        assertEquals(2, firstPage.entries.size)
        val secondPage = json.decodeFromString<LedgerPageResponse>(
            client.get("/api/progression/history?limit=2&beforeId=${firstPage.nextBeforeId}", auth).bodyAsText()
        )
        assertEquals(1, secondPage.entries.size)
        assertNull(secondPage.nextBeforeId)

        val badges = json.parseToJsonElement(client.get("/api/progression/badges", auth).bodyAsText())
        assertEquals(15, (badges as kotlinx.serialization.json.JsonArray).size)

        // No client endpoint can write SP, levels or badges.
        assertTrue(client.post("/api/progression/summary") { auth(); contentType(ContentType.Application.Json); setBody("""{"totalSp":99999}""") }
            .status in setOf(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed))
        assertEquals(60, totalSp(assignee))

        assertEquals(HttpStatusCode.BadRequest, client.put("/api/user/timezone") {
            auth(); contentType(ContentType.Application.Json); setBody("""{"timeZone":"Mars/Olympus"}""")
        }.status)
        assertEquals(HttpStatusCode.OK, client.put("/api/user/timezone") {
            auth(); contentType(ContentType.Application.Json); setBody("""{"timeZone":"Asia/Kolkata"}""")
        }.status)

        val profile = client.get("/api/user/$assignee") { bearerAuth(token(creator)) }.bodyAsText()
        assertEquals("Graduate Trainee", json.parseToJsonElement(profile).jsonObject["careerTitle"]!!.jsonPrimitive.content)
    }

    // ── service level (fixed timestamps, so day boundaries are deterministic) ──────

    private fun event(userId: Int, key: String, rule: String, counters: Set<String>, at: Long = FIXED_NOON, workspaceId: Int? = null) =
        ContributionEvent(
            idempotencyKey = "$key:$userId", userId = userId, eventType = "TEST", source = "TEST",
            referenceType = "TEST", referenceId = key, workspaceId = workspaceId, occurredAt = at,
            decision = RewardDecision(rule, counters)
        )

    @Test
    fun `self-made completions stop earning after the daily cap`() {
        val u = user()
        val results = (1..4).map { i ->
            transaction { ProgressionService.record(event(u, "self-$i", RuleKeys.TASK_SELF_COMPLETED, setOf(BadgeCounters.TASKS_COMPLETED))) }
        }
        assertEquals(listOf(RecordStatus.REWARDED, RecordStatus.REWARDED, RecordStatus.REWARDED, RecordStatus.CAPPED), results.map { it.status })
        assertEquals(60, totalSp(u))
        assertEquals(3, counter(u, BadgeCounters.TASKS_COMPLETED), "capped work does not count toward badges")

        val nextDay = transaction { ProgressionService.record(event(u, "self-5", RuleKeys.TASK_SELF_COMPLETED, setOf(BadgeCounters.TASKS_COMPLETED), FIXED_NOON + DAY)) }
        assertEquals(RecordStatus.REWARDED, nextDay.status, "the cap resets the next day")
    }

    @Test
    fun `one event crossing several levels sends one consolidated notification and replays are no-ops`() {
        val u = user()
        val base = ProgressionCatalogs.current
        val catalog = base.copy(
            levels = listOf(0L, 10L, 20L, 30L).mapIndexed { i, sp -> CareerLevel(i + 1, "L${i + 1}", sp) },
            rules = base.rules.map { if (it.key == RuleKeys.TASK_COMPLETED) it.copy(sp = 25) else it }
        )
        val first = transaction { ProgressionService.record(event(u, "big", RuleKeys.TASK_COMPLETED, emptySet()), catalog) }
        assertEquals(listOf(2, 3), first.levelsCrossed.map { it.level })
        assertEquals(1, first.notifications.count { it.type == ProgressionService.NOTIFICATION_LEVEL_UP })

        val replay = transaction { ProgressionService.record(event(u, "big", RuleKeys.TASK_COMPLETED, emptySet()), catalog) }
        assertEquals(RecordStatus.DUPLICATE, replay.status)
        assertEquals(25, totalSp(u))
        assertEquals(1L, transaction {
            NotificationsTable.selectAll().where {
                (NotificationsTable.recipientId eq u) and (NotificationsTable.type eq ProgressionService.NOTIFICATION_LEVEL_UP)
            }.count()
        })
    }

    @Test
    fun `one event can unlock several badges and sphere legend counts families not tiers`() {
        val u = user()
        val base = ProgressionCatalogs.current
        val low = setOf("TASK_TITAN", "SYNERGY_STAR", "DEADLINE_ACE")
        val catalog = base.copy(badges = base.badges.map {
            when (it.key) {
                in low -> it.copy(bronze = 1, silver = 2, gold = 3)
                "SPHERE_LEGEND" -> it.copy(bronze = 2, silver = 3, gold = 4)
                else -> it
            }
        })
        val counters = setOf(BadgeCounters.TASKS_COMPLETED, BadgeCounters.COLLABORATIVE_ACTIVITIES, BadgeCounters.ON_TIME_COMPLETIONS)
        val first = transaction { ProgressionService.record(event(u, "a", RuleKeys.TASK_COMPLETED, counters), catalog) }
        assertEquals(
            setOf("TASK_TITAN", "SYNERGY_STAR", "DEADLINE_ACE", "SPHERE_LEGEND"),
            first.badgesUnlocked.map { it.first.key }.toSet()
        )
        assertEquals(listOf("BRONZE", "SILVER"), unlockedTiers(u, "SPHERE_LEGEND"), "3 families earned")

        transaction { ProgressionService.record(event(u, "b", RuleKeys.TASK_COMPLETED, setOf(BadgeCounters.TASKS_COMPLETED)), catalog) }
        assertEquals(listOf("BRONZE", "SILVER"), unlockedTiers(u, "TASK_TITAN"), "silver implies bronze")
        assertEquals(listOf("BRONZE", "SILVER"), unlockedTiers(u, "SPHERE_LEGEND"), "a second tier of a family is still one family")
    }

    @Test
    fun `out-of-order days join into one streak`() {
        val u = user()
        listOf(0, 2, 1).forEach { offset ->
            transaction { ProgressionService.record(event(u, "day-$offset", RuleKeys.TASK_COMPLETED, emptySet(), FIXED_NOON + offset * DAY)) }
        }
        assertEquals(3, counter(u, BadgeCounters.STREAK_DAYS))
        assertEquals(listOf("BRONZE"), unlockedTiers(u, "MOMENTUM_KEEPER"))
    }

    @Test
    fun `sphere explorer counts distinct workspaces, not events`() {
        val u = user()
        val wsA = workspace(u)
        val wsB = workspace(u)
        listOf(wsA, wsA, wsB).forEachIndexed { i, ws ->
            transaction { ProgressionService.record(event(u, "ws-$i", RuleKeys.TASK_COMPLETED, emptySet(), workspaceId = ws)) }
        }
        assertEquals(2, counter(u, BadgeCounters.DISTINCT_WORKSPACES))
        assertEquals(listOf("BRONZE"), unlockedTiers(u, "SPHERE_EXPLORER"))
    }

    // ── GitHub rewards (phase 2) ──────────────────────────────────────────────────

    /** A workspace whose owner has a GitHub App connection and which links one repository. */
    private fun githubWorkspace(owner: Int, ownerGithubId: Long, vararg members: Int): Pair<Int, Long> {
        val ws = workspace(owner, *members)
        val repoId = 7_000_000_000L + (Math.random() * 1_000_000_000).toLong()
        transaction {
            val connection = GitHubConnectionsTable.insert {
                it[userId] = owner
                it[githubUserId] = ownerGithubId
                it[githubUsername] = "owner$ownerGithubId"
                it[installationId] = 1L
            }[GitHubConnectionsTable.id]
            GitHubRepositoriesTable.insert {
                it[connectionId] = connection
                it[workspaceId] = ws
                it[githubRepoId] = repoId
                it[GitHubRepositoriesTable.owner] = "acme"
                it[name] = "app"
                it[fullName] = "acme/app"
                it[isPrivate] = true
                it[htmlUrl] = "https://github.com/acme/app"
                it[defaultBranch] = "main"
            }
        }
        return ws to repoId
    }

    private fun linkGitHub(userId: Int, githubId: Long) = transaction {
        GitHubIdentityLinks.link(userId, githubId, "dev$githubId")
    }

    private fun ghUser(id: Long) = """{"id":$id,"login":"dev$id","type":"User"}"""

    private fun mergedPr(repoId: Long, number: Int, authorGithubId: Long, mergerGithubId: Long) =
        """{"action":"closed","repository":{"id":$repoId},"pull_request":{"number":$number,"merged":true,
        "user":${ghUser(authorGithubId)},"merged_by":${ghUser(mergerGithubId)},"additions":5,"deletions":1,"labels":[],
        "merged_at":"2026-10-01T10:00:00Z"}}"""

    private fun approval(repoId: Long, number: Int, reviewerGithubId: Long, authorGithubId: Long) =
        """{"action":"submitted","repository":{"id":$repoId},"review":{"state":"approved","user":${ghUser(reviewerGithubId)},
        "submitted_at":"2026-10-01T09:00:00Z"},"pull_request":{"number":$number,"user":${ghUser(authorGithubId)},"merged_at":null}}"""

    @Test
    fun `a merged pull request rewards its linked author once across webhook retries`() {
        val owner = user()
        val author = user()
        val (_, repoId) = githubWorkspace(owner, ownerGithubId = 51_000 + owner.toLong(), author)
        val authorGithubId = 61_000L + author
        assertEquals(IdentityLinkResult.LINKED, linkGitHub(author, authorGithubId))

        val payload = mergedPr(repoId, 1, authorGithubId, mergerGithubId = 51_000 + owner.toLong())
        repeat(3) { transaction { GitHubRewards.apply("pull_request", payload) } }

        assertEquals(30, totalSp(author))
        assertEquals(1, counter(author, BadgeCounters.ACCEPTED_CODE_CONTRIBUTIONS))
        assertEquals(1, counter(owner, BadgeCounters.PRS_MERGED_AS_MAINTAINER), "the owner merged a teammate's PR")
        assertEquals(0, totalSp(owner), "merging earns badge progress, not SP")
    }

    @Test
    fun `a repository linked in several workspaces notifies each person once per event`() {
        val owner = user()
        val wsA = workspace(owner)
        val wsB = workspace(owner)
        val githubRepoId = 8_000_000_000L + owner
        val repositoryRows = transaction {
            val connection = GitHubConnectionsTable.insert {
                it[userId] = owner
                it[githubUserId] = 81_000L + owner
                it[githubUsername] = "owner"
                it[installationId] = 1L
            }[GitHubConnectionsTable.id]
            listOf(wsA, wsB).map { ws ->
                GitHubRepositoriesTable.insert {
                    it[connectionId] = connection
                    it[workspaceId] = ws
                    it[GitHubRepositoriesTable.githubRepoId] = githubRepoId
                    it[GitHubRepositoriesTable.owner] = "acme"
                    it[name] = "app"
                    it[fullName] = "acme/app"
                    it[isPrivate] = true
                    it[htmlUrl] = "https://github.com/acme/app"
                    it[defaultBranch] = "main"
                }[GitHubRepositoriesTable.id]
            }
        }
        // What the webhook service produces for one check_suite delivery: one activity per linked row.
        val activities = repositoryRows.map { row ->
            com.collabsphere.util.GitHubCheckSuiteActivity(
                repositoryId = row, headSha = "abc1234def", status = "completed",
                conclusion = "failure", headBranch = "main", url = "https://github.com/acme/app/runs/1"
            )
        }
        kotlinx.coroutines.runBlocking { plugins.processGitHubActivities(activities) }

        assertEquals(1L, transaction {
            NotificationsTable.selectAll().where {
                (NotificationsTable.recipientId eq owner) and (NotificationsTable.type eq "GITHUB_CI")
            }.count()
        })
    }

    @Test
    fun `an approving review earns sp only once the pull request merges`() {
        val owner = user()
        val author = user()
        val reviewer = user()
        val (_, repoId) = githubWorkspace(owner, ownerGithubId = 52_000 + owner.toLong(), author, reviewer)
        val authorGithubId = 62_000L + author
        val reviewerGithubId = 63_000L + reviewer
        linkGitHub(author, authorGithubId)
        linkGitHub(reviewer, reviewerGithubId)

        transaction { GitHubRewards.apply("pull_request_review", approval(repoId, 2, reviewerGithubId, authorGithubId)) }
        assertEquals(0, totalSp(reviewer), "an approval of unmerged work earns nothing yet")

        transaction { GitHubRewards.apply("pull_request", mergedPr(repoId, 2, authorGithubId, authorGithubId)) }
        assertEquals(15, totalSp(reviewer))
        assertEquals(1, counter(reviewer, BadgeCounters.COLLABORATIVE_ACTIVITIES))
        assertEquals(30, totalSp(author))
        transaction { GitHubPrApprovalsTable.deleteWhere { GitHubPrApprovalsTable.githubRepoId eq repoId } }
    }

    @Test
    fun `unlinked accounts and non-members earn nothing from github`() {
        val owner = user()
        val outsider = user()
        val member = user()
        val (_, repoId) = githubWorkspace(owner, ownerGithubId = 53_000 + owner.toLong(), member)
        linkGitHub(outsider, 64_000L + outsider) // linked, but not a member of the workspace

        transaction { GitHubRewards.apply("pull_request", mergedPr(repoId, 3, 64_000L + outsider, 99_999_999)) }
        transaction { GitHubRewards.apply("pull_request", mergedPr(repoId, 4, 65_000L + member, 99_999_999)) } // member, unlinked
        assertEquals(0, totalSp(outsider))
        assertEquals(0, totalSp(member))
    }

    @Test
    fun `a github account can be linked to only one collabsphere user`() {
        val first = user()
        val second = user()
        val owner = user()
        githubWorkspace(owner, ownerGithubId = 66_000L + owner)
        assertEquals(IdentityLinkResult.LINKED, linkGitHub(first, 67_000L + first))
        assertEquals(IdentityLinkResult.ACCOUNT_IN_USE, linkGitHub(second, 67_000L + first), "linked by another member")
        assertEquals(IdentityLinkResult.ACCOUNT_IN_USE, linkGitHub(second, 66_000L + owner), "an owner's App connection")
    }

    @Test
    fun `the identity api reports and removes the link`() = testApplication {
        application { module() }
        val u = user()
        val auth: HttpRequestBuilder.() -> Unit = { bearerAuth(token(u)) }
        assertTrue(client.get("/api/user/github/identity", auth).bodyAsText().contains("\"linked\":false"))
        linkGitHub(u, 68_000L + u)
        val linked = client.get("/api/user/github/identity", auth).bodyAsText()
        assertTrue(linked.contains("\"linked\":true") && linked.contains("dev${68_000L + u}"), linked)
        assertEquals(HttpStatusCode.OK, client.delete("/api/user/github/link", auth).status)
        assertTrue(client.get("/api/user/github/identity", auth).bodyAsText().contains("\"linked\":false"))
    }

    // ── Helpful marks (phase 3) ───────────────────────────────────────────────────

    private fun message(ws: Int, author: Int): Int = transaction {
        val channel = ChannelsTable.selectAll().where { ChannelsTable.workspaceId eq ws }.firstOrNull()?.get(ChannelsTable.id)
            ?: ChannelsTable.insert {
                it[channelName] = "general"
                it[workspaceId] = ws
                it[description] = ""
            }[ChannelsTable.id]
        MessageTable.insert {
            it[userId] = author
            it[workspaceId] = ws
            it[channelId] = channel
            it[userName] = "author"
            it[content] = "Here's how to fix the flaky build"
            it[status] = "SENT"
        }[MessageTable.id]
    }

    private fun mark(messageId: Int, marker: Int) = transaction { HelpfulMarks.mark(messageId, marker) }.status

    @Test
    fun `helpful marks count messages once and recognisers once, and earn no sp`() {
        val author = user()
        val first = user()
        val second = user()
        val ws = workspace(author, first, second)
        val m1 = message(ws, author)
        val m2 = message(ws, author)

        assertEquals(HelpfulMarkStatus.MARKED, mark(m1, first))
        assertEquals(HelpfulMarkStatus.ALREADY_MARKED, mark(m1, first))
        assertEquals(HelpfulMarkStatus.MARKED, mark(m1, second))
        assertEquals(HelpfulMarkStatus.MARKED, mark(m2, first))

        assertEquals(2, counter(author, BadgeCounters.HELPFUL_MESSAGES), "two distinct messages")
        assertEquals(2, counter(author, BadgeCounters.HELPFUL_RECOGNIZERS), "two distinct teammates")
        assertEquals(0, totalSp(author), "recognition is badge progress only")
    }

    @Test
    fun `helpful marks reject self-marking, outsiders and over-marking`() {
        val author = user()
        val marker = user()
        val outsider = user()
        val ws = workspace(author, marker)
        assertEquals(HelpfulMarkStatus.OWN_MESSAGE, mark(message(ws, author), author))
        assertEquals(HelpfulMarkStatus.NOT_MEMBER, mark(message(ws, author), outsider))
        assertEquals(HelpfulMarkStatus.NOT_FOUND, mark(Int.MAX_VALUE, marker))

        repeat(HelpfulMarks.DAILY_LIMIT_PER_MARKER) { assertEquals(HelpfulMarkStatus.MARKED, mark(message(ws, author), marker)) }
        assertEquals(HelpfulMarkStatus.DAILY_LIMIT, mark(message(ws, author), marker))
    }

    @Test
    fun `the helpful route maps outcomes to http statuses`() = testApplication {
        application { module() }
        val author = user()
        val marker = user()
        val ws = workspace(author, marker)
        val m = message(ws, author)
        assertEquals(HttpStatusCode.OK, client.post("/api/message/$m/helpful") { bearerAuth(token(marker)) }.status)
        assertTrue(client.post("/api/message/$m/helpful") { bearerAuth(token(marker)) }.bodyAsText().contains("\"alreadyMarked\":true"))
        assertEquals(HttpStatusCode.BadRequest, client.post("/api/message/$m/helpful") { bearerAuth(token(author)) }.status)
    }

    @Test
    fun `corrections are audited, totals reconcile with the ledger and catalog changes recalculate levels`() {
        val u = user()
        transaction { ProgressionService.record(event(u, "c", RuleKeys.TASK_COMPLETED, emptySet())) }
        transaction { ProgressionService.applyCorrection(u, 90, "Restored work lost in an outage", actorId = null) }
        assertEquals(110, totalSp(u))
        val correction = transaction {
            SpLedgerTable.selectAll().where { (SpLedgerTable.userId eq u) and (SpLedgerTable.kind eq ProgressionService.KIND_CORRECTION) }.single()
        }
        assertEquals("Restored work lost in an outage", correction[SpLedgerTable.reason])
        assertEquals(2, transaction { UserProgressionTable.selectAll().where { UserProgressionTable.userId eq u }.single()[UserProgressionTable.level] })

        // A tampered running total and a stale catalog version are both repaired from the ledger.
        transaction {
            UserProgressionTable.update({ UserProgressionTable.userId eq u }) {
                it[totalSp] = 99_999
                it[level] = 20
                it[catalogVersion] = 0
            }
        }
        transaction { ProgressionService.reconcileAndMigrate() }
        val row = transaction { UserProgressionTable.selectAll().where { UserProgressionTable.userId eq u }.single() }
        assertEquals(110, row[UserProgressionTable.totalSp])
        assertEquals(2, row[UserProgressionTable.level])
        assertEquals(ProgressionCatalogs.current.version, row[UserProgressionTable.catalogVersion])
    }
}
