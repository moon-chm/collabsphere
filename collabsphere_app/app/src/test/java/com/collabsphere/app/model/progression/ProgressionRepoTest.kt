package com.collabsphere.app.model.progression

import android.util.Log
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.collabsphere.app.UserPreferences
import com.collabsphere.app.dto.progression.BadgeProgressResponse
import com.collabsphere.app.dto.progression.BadgeUnlockResponse
import com.collabsphere.app.dto.progression.CareerLevelDto
import com.collabsphere.app.dto.progression.ProgressionCatalogDto
import com.collabsphere.app.dto.progression.ProgressionSummaryResponse
import com.collabsphere.app.remote.ApiStatusException
import com.collabsphere.app.remote.progression.ProgressionApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProgressionRepoTest {

    private lateinit var tempDir: File
    private lateinit var scope: CoroutineScope
    private val dao = mockk<ProgressionDao>(relaxed = true)
    private val api = mockk<ProgressionApiService>()
    private val userPreferences = mockk<UserPreferences>(relaxed = true)
    private lateinit var repo: ProgressionRepo

    private val catalog = ProgressionCatalogDto(
        version = 1,
        levels = listOf(CareerLevelDto(1, "Graduate Trainee", 0), CareerLevelDto(2, "Junior Developer", 100)),
        badges = emptyList()
    )

    private fun summary(userId: Int = 7, level: Int = 2) = ProgressionSummaryResponse(
        userId = userId, totalSp = 120, level = level, maxLevel = 20, title = "Junior Developer", levelRequiredSp = 100,
        nextLevel = 3, nextTitle = "Associate Software Engineer", nextLevelRequiredSp = 250,
        spIntoLevel = 20, spSpanToNext = 150, isMaxLevel = false, currentStreakDays = 2, bestStreakDays = 4,
        catalogVersion = 1, lastLedgerId = 9
    )

    private val badges = listOf(
        BadgeProgressResponse("TASK_TITAN", true, 12, listOf(BadgeUnlockResponse("BRONZE", 1_000)), "SILVER", 50)
    )

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        tempDir = Files.createTempDirectory("progression-test").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tempDir, "prefs.preferences_pb") }
        repo = ProgressionRepo(dao, api, dataStore, userPreferences)
        coEvery { dao.getCatalog() } returns null
        coEvery { dao.getSummary(any()) } returns null
        coEvery { api.updateTimeZone(any()) } returns Unit
        coEvery { api.getCatalog(any()) } returns (catalog to "\"v1\"")
    }

    @After
    fun tearDown() {
        scope.cancel()
        tempDir.deleteRecursively()
        unmockkStatic(Log::class)
    }

    @Test
    fun `a successful refresh stores the server state together`() = runBlocking {
        coEvery { api.getSummary() } returns summary()
        coEvery { api.getBadges() } returns badges
        val stored = slot<ProgressionSummaryEntity>()
        val storedBadges = slot<List<BadgeProgressEntity>>()
        coEvery { dao.replaceProgress(capture(stored), 7, capture(storedBadges)) } returns Unit

        assertTrue(repo.refresh(7, force = true).isSuccess)

        assertEquals(120L, stored.captured.totalSp)
        assertEquals("Junior Developer", stored.captured.title)
        assertEquals(1_000L, storedBadges.captured.single().bronzeUnlockedAt)
        assertNull(storedBadges.captured.single().silverUnlockedAt)
        coVerify { dao.upsertCatalog(match { it.etag == "\"v1\"" && it.version == 1 }) }
    }

    @Test
    fun `a failed refresh keeps the cached state untouched`() = runBlocking {
        coEvery { api.getSummary() } throws ApiStatusException(503)
        coEvery { api.getBadges() } returns badges

        assertTrue(repo.refresh(7, force = true).isFailure)
        coVerify(exactly = 0) { dao.replaceProgress(any(), any(), any()) }
    }

    @Test
    fun `another account's summary is never cached under this user`() = runBlocking {
        coEvery { api.getSummary() } returns summary(userId = 99)
        coEvery { api.getBadges() } returns badges

        assertTrue(repo.refresh(7, force = true).isFailure)
        coVerify(exactly = 0) { dao.replaceProgress(any(), any(), any()) }
    }

    @Test
    fun `an unchanged catalog is reused from the cache`() = runBlocking {
        coEvery { dao.getCatalog() } returns ProgressionCatalogEntity(version = 1, etag = "\"v1\"", json = """{"version":1,"levels":[],"badges":[]}""")
        coEvery { api.getCatalog("\"v1\"") } returns null
        coEvery { api.getSummary() } returns summary()
        coEvery { api.getBadges() } returns badges

        assertTrue(repo.refresh(7, force = true).isSuccess)
        coVerify(exactly = 0) { dao.upsertCatalog(any()) }
    }

    @Test
    fun `the first sync sets the seen baseline and later syncs leave it for the ui to advance`() = runBlocking {
        coEvery { api.getSummary() } returnsMany listOf(summary(level = 2), summary(level = 3))
        coEvery { api.getBadges() } returns badges

        repo.refresh(7, force = true)
        assertEquals(SeenProgress(2, setOf("TASK_TITAN:BRONZE")), repo.observeSeen(7).first())

        repo.refresh(7, force = true)
        assertEquals("a later promotion stays pending until shown", 2, repo.observeSeen(7).first()!!.level)

        repo.acknowledge(7, AchievementFeedback("Promoted", level = 3))
        assertEquals(3, repo.observeSeen(7).first()!!.level)
    }
}
