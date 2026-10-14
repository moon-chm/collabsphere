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
