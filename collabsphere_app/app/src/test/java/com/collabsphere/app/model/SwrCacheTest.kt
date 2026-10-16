package com.collabsphere.app.model

import android.content.Context
import com.collabsphere.app.UserPreferences
import com.collabsphere.app.dto.workspace.MemberResponse
import com.collabsphere.app.model.workspace.MemberDirectory
import com.collabsphere.app.remote.workspace.WorkspaceApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SwrCacheTest {

    private lateinit var dir: File
    private val userId = MutableStateFlow(7)
    private lateinit var cache: SwrCache

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("swr").toFile()
        val context = mockk<Context> { every { cacheDir } returns dir }
        val prefs = mockk<UserPreferences> { every { userIdFlow } returns userId }
        cache = SwrCache(context, prefs)
        SyncSignals.resetForTest()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
        SyncSignals.resetForTest()
    }

    @Test
    fun roundTripsValuesWithTheirSaveTime() = runTest {
        cache.write("greeting", String.serializer(), "hello", savedAt = 1_000L)
        val read = cache.read("greeting", String.serializer())!!
        assertEquals("hello", read.value)
        assertEquals(1_000L, read.savedAt)
    }

    @Test
    fun eachUserHasTheirOwnCache() = runTest {
        cache.write("k", String.serializer(), "alice")
        userId.value = 8
        assertNull(cache.read("k", String.serializer()))
        userId.value = 7
        assertEquals("alice", cache.read("k", String.serializer())!!.value)
    }

    @Test
    fun nothingIsStoredWhileLoggedOut() = runTest {
        userId.value = -1
        cache.write("k", String.serializer(), "x")
        assertNull(cache.read("k", String.serializer()))
    }

    @Test
    fun clearAllWipesEveryUser() = runTest {
        cache.write("k", String.serializer(), "a")
        userId.value = 8
        cache.write("k", String.serializer(), "b")
        cache.clearAll()
        assertNull(cache.read("k", String.serializer()))
        userId.value = 7
        assertNull(cache.read("k", String.serializer()))
    }

    @Test
    fun aCorruptFileIsDiscardedInsteadOfCrashing() = runTest {
        cache.write("k", String.serializer(), "a")
        File(dir, "swr/7/k.json").writeText("{not json")
        assertNull(cache.read("k", String.serializer()))
        assertTrue(!File(dir, "swr/7/k.json").exists())
    }

    @Test
    fun keysBecomeSafeFileNames() {
        assertEquals("members_12", SwrCache.fileName("members_12"))
        val urlKey = SwrCache.fileName("link_preview_https://example.com/a?b=c")
        assertTrue(urlKey.all { it.isLetterOrDigit() || it == '_' || it == '-' })
        assertTrue(SwrCache.fileName("link_preview_https://a.com/1") != SwrCache.fileName("link_preview_https://a.com/2"))
    }

    private fun member(id: Int, role: String = "MEMBER") = MemberResponse(5, id, "User $id", "u$id@x.com", role = role)

    @Test
    fun membersShowSavedListFirstThenRefresh() = runTest {
        cache.write("members_5", ListSerializer(MemberResponse.serializer()), listOf(member(1)))
        val api = mockk<WorkspaceApiService> { coEvery { getWorkspaceMembers(5) } returns listOf(member(1), member(2)) }
        val emissions = MemberDirectory(api, cache).observe(5).toList()
        assertEquals(listOf(1), emissions[0].map { it.userId })
        assertEquals(listOf(1, 2), emissions[1].map { it.userId })
    }

    @Test
    fun freshMembersAreServedWithoutTheNetworkUntilAMembershipSignal() = runTest {
        val api = mockk<WorkspaceApiService> { coEvery { getWorkspaceMembers(5) } returns listOf(member(1, "OWNER")) }
        val directory = MemberDirectory(api, cache)
        directory.observe(5).toList()
        assertEquals(1, directory.observe(5).toList().size)
        coVerify(exactly = 1) { api.getWorkspaceMembers(5) }

        SyncSignals.onEvent(WorkspaceSyncEvent(workspaceId = 5, scopes = listOf("workspace")))
        directory.observe(5).toList()
        coVerify(exactly = 2) { api.getWorkspaceMembers(5) }
    }

    @Test
    fun offlineWithASavedListStillShowsIt() = runTest {
        cache.write("members_5", ListSerializer(MemberResponse.serializer()), listOf(member(3)))
        val api = mockk<WorkspaceApiService> { coEvery { getWorkspaceMembers(5) } throws java.io.IOException("offline") }
        val emissions = MemberDirectory(api, cache).observe(5).toList()
        assertEquals(listOf(listOf(3)), emissions.map { list -> list.map { it.userId } })
    }

    @Test(expected = java.io.IOException::class)
    fun offlineWithNothingSavedReportsTheError() = runTest {
        val api = mockk<WorkspaceApiService> { coEvery { getWorkspaceMembers(5) } throws java.io.IOException("offline") }
        MemberDirectory(api, cache).observe(5).toList()
    }
}
