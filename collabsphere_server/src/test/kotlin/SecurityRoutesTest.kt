import com.collabsphere.model.UsersTable
import com.collabsphere.model.WorkspacesTable
import com.collabsphere.model.PasswordResetTable
import com.collabsphere.model.WorkspaceMembersTable
import com.collabsphere.model.UserVerificationTable
import com.collabsphere.module
import com.collabsphere.DatabaseFactory
import com.collabsphere.util.JwtConfig
import com.collabsphere.util.PasswordHasher
import plugins.AvatarStorage
import plugins.AvatarStorageAttribute
import plugins.lockUserPair
import io.ktor.client.request.*
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*
import java.io.ByteArrayOutputStream
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/** Runs only against the disposable database configured for integrationTest. */
class SecurityRoutesTest {

    private val createdUserIds = mutableListOf<Int>()

    private fun createUser(username: String = "sec-test"): Int = transaction {
        val email = "sec-${java.util.UUID.randomUUID()}@example.com"
        UsersTable.insert {
            it[UsersTable.email] = email
            it[UsersTable.password] = "unused"
            it[UsersTable.username] = username
            it[UsersTable.isEmailVerified] = true
        }[UsersTable.id]
    }.also { createdUserIds += it }

    private fun fcmTokenOf(userId: Int): String? = transaction {
        UsersTable.selectAll().where { UsersTable.id eq userId }.single()[UsersTable.fcmToken]
    }

    private fun passwordOf(userId: Int): String = transaction {
        UsersTable.selectAll().where { UsersTable.id eq userId }.single()[UsersTable.password]
    }

    private suspend fun ApplicationTestBuilder.registerFcmToken(bodyUserId: Int, token: String, jwt: String?) =
        client.post("/api/user/fcm-token") {
            contentType(ContentType.Application.Json)
            if (jwt != null) bearerAuth(jwt)
            setBody("""{"userId":"$bodyUserId","fcmToken":"$token"}""")
        }

    @AfterTest
    fun cleanUp() {
        if (createdUserIds.isEmpty()) return
        transaction {
            createdUserIds.forEach { id ->
                val email = UsersTable.selectAll().where { UsersTable.id eq id }.singleOrNull()?.get(UsersTable.email)
                if (email != null) PasswordResetTable.deleteWhere { PasswordResetTable.email eq email }
                UsersTable.deleteWhere { UsersTable.id eq id }
            }
        }
    }

    @Test
    fun `fcm token registration requires a login`() = testApplication {
        application { module() }
        startApplication()
        val victim = createUser()

        val response = registerFcmToken(bodyUserId = victim, token = "attacker-device", jwt = null)

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertNull(fcmTokenOf(victim))
    }

    @Test
    fun `fcm token is stored for the logged-in user, not the userId in the body`() = testApplication {
        application { module() }
        startApplication()
        val caller = createUser()
        val victim = createUser()

        val response = registerFcmToken(bodyUserId = victim, token = "caller-device", jwt = JwtConfig.generateToken(caller))

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("caller-device", fcmTokenOf(caller))
        assertNull(fcmTokenOf(victim))
    }

    @Test
    fun `a device token moves to whoever logs in on it last`() = testApplication {
        application { module() }
        startApplication()
        val firstUser = createUser()
        val secondUser = createUser()

        registerFcmToken(firstUser, "shared-device", JwtConfig.generateToken(firstUser))
        registerFcmToken(secondUser, "shared-device", JwtConfig.generateToken(secondUser))

        assertNull(fcmTokenOf(firstUser), "The previous user must stop receiving pushes on a handed-over device")
        assertEquals("shared-device", fcmTokenOf(secondUser))
    }

    @Test
    fun `logging out detaches the device from the account`() = testApplication {
        application { module() }
        startApplication()
        val user = createUser()
        val jwt = JwtConfig.generateToken(user)
        registerFcmToken(user, "my-device", jwt)

        val response = client.delete("/api/user/fcm-token") { bearerAuth(jwt) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertNull(fcmTokenOf(user))
    }

    @Test
    fun `password reset requires the issued code and consumes it after success`() = testApplication {
        application { module() }
        startApplication()
        val userId = createUser()
        val email = transaction { UsersTable.selectAll().where { UsersTable.id eq userId }.single()[UsersTable.email] }
        val originalPassword = passwordOf(userId)
        val oldJwt = JwtConfig.generateToken(userId)
        val otp = "123456"
        transaction {
            PasswordResetTable.insert {
                it[PasswordResetTable.email] = email
                it[PasswordResetTable.otp] = otp
                it[PasswordResetTable.expiresAt] = System.currentTimeMillis() + 60_000L
            }
        }

        val bypassAttempt = client.post("/api/auth/reset-password") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","otp":"000000","newPassword":"new-secret-123"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, bypassAttempt.status)
        assertEquals(originalPassword, passwordOf(userId), "A wrong or missing OTP must not change the password")

        val successfulReset = client.post("/api/auth/reset-password") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","otp":"$otp","newPassword":"new-secret-123"}""")
        }
        assertEquals(HttpStatusCode.OK, successfulReset.status)
        assertTrue(PasswordHasher.matches("new-secret-123", passwordOf(userId)))
        val staleSession = client.get("/api/user/profile") { bearerAuth(oldJwt) }
        assertEquals(HttpStatusCode.Unauthorized, staleSession.status)

        val replay = client.post("/api/auth/reset-password") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","otp":"$otp","newPassword":"another-secret-123"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, replay.status)
        assertTrue(PasswordHasher.matches("new-secret-123", passwordOf(userId)), "A consumed OTP must not be reusable")

        val cooldownRequest = client.post("/api/auth/forgot-password") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email"}""")
        }
        assertEquals(HttpStatusCode.OK, cooldownRequest.status)
        assertEquals(otp, transaction {
            PasswordResetTable.selectAll().where { PasswordResetTable.email eq email }
                .single()[PasswordResetTable.otp]
        }, "Consuming a code must not reset its resend cooldown")
    }

    @Test
    fun `password reset burns a code after the configured number of wrong guesses`() = testApplication {
        application { module() }
        startApplication()
        val userId = createUser()
        val email = transaction { UsersTable.selectAll().where { UsersTable.id eq userId }.single()[UsersTable.email] }
        val originalPassword = passwordOf(userId)
        transaction {
            PasswordResetTable.insert {
                it[PasswordResetTable.email] = email
                it[PasswordResetTable.otp] = "123456"
                it[PasswordResetTable.expiresAt] = System.currentTimeMillis() + 60_000L
            }
        }

        repeat(5) { index ->
            val response = client.post("/api/auth/reset-password") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"$email","otp":"00000$index","newPassword":"new-secret-123"}""")
            }
            assertEquals(if (index == 4) HttpStatusCode.TooManyRequests else HttpStatusCode.BadRequest, response.status)
        }
        val correctAfterLimit = client.post("/api/auth/reset-password") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","otp":"123456","newPassword":"new-secret-123"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, correctAfterLimit.status)
        assertEquals(originalPassword, passwordOf(userId))
    }

    @Test
    fun `members-only profile is hidden from nonmembers and visible to workspace members`() = testApplication {
        application { module() }
        startApplication()
        val profileOwner = createUser("hidden-search-target")
        val viewer = createUser()
        val ownerJwt = JwtConfig.generateToken(profileOwner)
        val viewerJwt = JwtConfig.generateToken(viewer)
        val privacyResponse = client.put("/api/user/privacy") {
            bearerAuth(ownerJwt)
            contentType(ContentType.Application.Json)
            setBody("""{"showEmail":false,"showOnlineStatus":false,"showLastSeen":false,"profileVisibility":"members_only"}""")
        }
        assertEquals(HttpStatusCode.OK, privacyResponse.status)

        val hiddenProfile = client.get("/api/user/$profileOwner") { bearerAuth(viewerJwt) }
        assertEquals(HttpStatusCode.NotFound, hiddenProfile.status)
        val hiddenSearch = client.get("/api/user/search?q=hidden-search-target") { bearerAuth(viewerJwt) }
        assertFalse(hiddenSearch.bodyAsText().contains("hidden-search-target"))

        val workspaceId = transaction {
            WorkspacesTable.insert {
                it[WorkspacesTable.userId] = profileOwner
                it[WorkspacesTable.workspaceName] = "private-profile-test"
                it[WorkspacesTable.workspaceOwner] = "sec-test"
                it[WorkspacesTable.workspacePassword] = "unused"
            }[WorkspacesTable.id]
        }
        transaction {
            WorkspaceMembersTable.insert {
                it[WorkspaceMembersTable.workspaceId] = workspaceId
                it[WorkspaceMembersTable.userId] = profileOwner
                it[WorkspaceMembersTable.role] = "OWNER"
            }
            WorkspaceMembersTable.insert {
                it[WorkspaceMembersTable.workspaceId] = workspaceId
                it[WorkspaceMembersTable.userId] = viewer
                it[WorkspaceMembersTable.role] = "MEMBER"
            }
        }
        val visibleProfile = client.get("/api/user/$profileOwner") { bearerAuth(viewerJwt) }
        assertEquals(HttpStatusCode.OK, visibleProfile.status)
        val visibleSearch = client.get("/api/user/search?q=hidden-search-target") { bearerAuth(viewerJwt) }
        assertTrue(visibleSearch.bodyAsText().contains("hidden-search-target"))
        assertFalse(visibleSearch.bodyAsText().contains("sec-"))
    }

    @Test
    fun `repeated block requests are idempotent and hide the profile both ways`() = testApplication {
        application { module() }
        startApplication()
        val blocker = createUser()
        val target = createUser()
        val jwt = JwtConfig.generateToken(blocker)

        repeat(2) {
            val response = client.post("/api/user/block/$target") { bearerAuth(jwt) }
            assertEquals(HttpStatusCode.OK, response.status)
        }
        val profile = client.get("/api/user/$target") { bearerAuth(JwtConfig.generateToken(target)) }
        assertEquals(HttpStatusCode.NotFound, profile.status)
    }

    @Test
    fun `user pair lock serializes competing block and message transactions regardless of pair order`() {
        val firstUser = createUser()
        val secondUser = createUser()
        val firstLockAcquired = CountDownLatch(1)
        val allowFirstToCommit = CountDownLatch(1)
        val secondLockAttempted = CountDownLatch(1)
        val secondLockAcquired = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit {
                transaction(DatabaseFactory.writeDatabase) {
                    lockUserPair(firstUser, secondUser)
                    firstLockAcquired.countDown()
                    check(allowFirstToCommit.await(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(firstLockAcquired.await(5, TimeUnit.SECONDS))

            val second = executor.submit {
                secondLockAttempted.countDown()
                transaction(DatabaseFactory.writeDatabase) {
                    lockUserPair(secondUser, firstUser)
                    secondLockAcquired.countDown()
                }
            }
            assertTrue(secondLockAttempted.await(5, TimeUnit.SECONDS))
            assertFalse(secondLockAcquired.await(150, TimeUnit.MILLISECONDS), "A competing transaction must wait for the pair lock")

            allowFirstToCommit.countDown()
            first.get(5, TimeUnit.SECONDS)
            second.get(5, TimeUnit.SECONDS)
            assertEquals(0L, secondLockAcquired.count)
        } finally {
            allowFirstToCommit.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `account deletion refuses to cascade-delete a shared owned workspace`() = testApplication {
        application { module() }
        startApplication()
        val owner = createUser()
        val member = createUser()
        val workspaceId = transaction {
            WorkspacesTable.insert {
                it[WorkspacesTable.userId] = owner
                it[WorkspacesTable.workspaceName] = "shared-delete-guard"
                it[WorkspacesTable.workspaceOwner] = "sec-test"
                it[WorkspacesTable.workspacePassword] = "unused"
            }[WorkspacesTable.id]
        }
        transaction {
            listOf(owner to "OWNER", member to "MEMBER").forEach { (userId, role) ->
                WorkspaceMembersTable.insert {
                    it[WorkspaceMembersTable.workspaceId] = workspaceId
                    it[WorkspaceMembersTable.userId] = userId
                    it[WorkspaceMembersTable.role] = role
                }
            }
        }

        val response = client.delete("/api/user/account") {
            bearerAuth(JwtConfig.generateToken(owner))
            contentType(ContentType.Application.Json)
            setBody("""{"password":"unused"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(1, transaction { UsersTable.selectAll().where { UsersTable.id eq owner }.count() })
        assertEquals(1, transaction { WorkspacesTable.selectAll().where { WorkspacesTable.id eq workspaceId }.count() })
    }

    @Test
    fun `successful account deletion revokes the session token`() = testApplication {
        application { module() }
        startApplication()
        val user = createUser()
        val jwt = JwtConfig.generateToken(user)

        val response = client.delete("/api/user/account") {
            bearerAuth(jwt)
            contentType(ContentType.Application.Json)
            setBody("""{"password":"unused"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val staleSession = client.get("/api/user/profile") { bearerAuth(jwt) }
        assertEquals(HttpStatusCode.Unauthorized, staleSession.status)
    }

    @Test
    fun `profile update requires a valid password change pair and current password`() = testApplication {
        application { module() }
        startApplication()
        val user = createUser()
        val originalPassword = passwordOf(user)
        val jwt = JwtConfig.generateToken(user)

        val missingCurrentPassword = client.post("/api/user/profile") {
            bearerAuth(jwt)
            contentType(ContentType.Application.Json)
            setBody("""{"userId":$user,"userName":"updated-user","newPassword":"changed-secret"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, missingCurrentPassword.status)
        assertEquals(originalPassword, passwordOf(user))

        val wrongCurrentPassword = client.post("/api/user/profile") {
            bearerAuth(jwt)
            contentType(ContentType.Application.Json)
            setBody("""{"userId":$user,"userName":"updated-user","currentPassword":"incorrect","newPassword":"changed-secret"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, wrongCurrentPassword.status)
        assertEquals(originalPassword, passwordOf(user))

        val oversizedProfile = client.post("/api/user/profile") {
            bearerAuth(jwt)
            contentType(ContentType.Application.Json)
            setBody("""{"userId":$user,"userName":"${"u".repeat(51)}"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, oversizedProfile.status)
    }

    @Test
    fun `expired email verification code is burned and cannot be replayed`() = testApplication {
        application { module() }
        startApplication()
        val user = createUser()
        val jwt = JwtConfig.generateToken(user)
        transaction {
            UserVerificationTable.insert {
                it[UserVerificationTable.userId] = user
                it[UserVerificationTable.token] = "654321"
                it[UserVerificationTable.targetEmail] = "sec-test@example.com"
                it[UserVerificationTable.expiresAt] = System.currentTimeMillis() - 1
            }
        }

        val expired = client.post("/api/user/verify-email/confirm") {
            bearerAuth(jwt)
            contentType(ContentType.Application.Json)
            setBody("""{"token":"654321"}""")
        }
        assertEquals(HttpStatusCode.Gone, expired.status)
        val replay = client.post("/api/user/verify-email/confirm") {
            bearerAuth(jwt)
            contentType(ContentType.Application.Json)
            setBody("""{"token":"654321"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, replay.status)
    }

    @Test
    fun `concurrent email changes cannot reserve the same address for two users`() = testApplication {
        application { module() }
        startApplication()
        val firstUser = createUser()
        val secondUser = createUser()
        val sharedEmail = "pending-${java.util.UUID.randomUUID()}@example.com"

        val responses = coroutineScope {
            listOf(firstUser, secondUser).map { userId ->
                async {
                    client.put("/api/user/email") {
                        bearerAuth(JwtConfig.generateToken(userId))
                        contentType(ContentType.Application.Json)
                        setBody("""{"newEmail":"$sharedEmail","currentPassword":"unused"}""")
                    }
                }
            }.awaitAll()
        }

        assertEquals(setOf(HttpStatusCode.OK, HttpStatusCode.Conflict), responses.map { it.status }.toSet())
        assertEquals(1, transaction {
            UsersTable.selectAll().where { UsersTable.pendingEmail.lowerCase() eq sharedEmail }.count()
        })
    }

    @Test
    fun `avatar replacement stores a fresh provider id then retires the old object`() = testApplication {
        val uploadedIds = mutableListOf<String>()
        val deletedIds = mutableListOf<String>()
        val fakeStorage = object : AvatarStorage {
            override suspend fun upload(file: java.io.File, publicId: String): String {
                uploadedIds += publicId
                return "https://avatars.example/$publicId.jpg"
            }
            override suspend fun delete(publicId: String) { deletedIds += publicId }
        }
        application {
            attributes.put(AvatarStorageAttribute, fakeStorage)
            module()
        }
        startApplication()
        val user = createUser()
        val jwt = JwtConfig.generateToken(user)
        val imageBytes = ByteArrayOutputStream().use { output ->
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output)
            output.toByteArray()
        }
        suspend fun upload() = client.post("/api/user/avatar") {
            bearerAuth(jwt)
            setBody(MultiPartFormDataContent(formData {
                append("avatar", imageBytes, io.ktor.http.Headers.build {
                    append(HttpHeaders.ContentType, "image/png")
                    append(HttpHeaders.ContentDisposition, "filename=\"avatar.png\"")
                })
            }))
        }

        assertEquals(HttpStatusCode.OK, upload().status)
        val firstPublicId = transaction {
            UsersTable.selectAll().where { UsersTable.id eq user }.single()[UsersTable.avatarPublicId]!!
        }
        assertEquals(HttpStatusCode.OK, upload().status)
        val secondPublicId = transaction {
            UsersTable.selectAll().where { UsersTable.id eq user }.single()[UsersTable.avatarPublicId]!!
        }
        assertTrue(firstPublicId != secondPublicId)
        assertEquals(listOf(firstPublicId), deletedIds)
        assertEquals(2, uploadedIds.size)
        repeat(2) {
            val removed = client.delete("/api/user/avatar") { bearerAuth(jwt) }
            assertEquals(HttpStatusCode.OK, removed.status)
        }
        assertEquals(listOf(firstPublicId, secondPublicId), deletedIds)
        transaction {
            val row = UsersTable.selectAll().where { UsersTable.id eq user }.single()
            assertNull(row[UsersTable.avatarUrl])
            assertNull(row[UsersTable.avatarPublicId])
        }
    }

    @Test
    fun `failed avatar provider response cleans the orphan and preserves the saved avatar`() = testApplication {
        val attemptedIds = mutableListOf<String>()
        val deletedIds = mutableListOf<String>()
        val fakeStorage = object : AvatarStorage {
            override suspend fun upload(file: java.io.File, publicId: String): String {
                attemptedIds += publicId // Simulate the provider storing it before a response timeout.
                throw java.io.IOException("provider response lost")
            }
            override suspend fun delete(publicId: String) { deletedIds += publicId }
        }
        application {
            attributes.put(AvatarStorageAttribute, fakeStorage)
            module()
        }
        startApplication()
        val user = createUser()
        val oldPublicId = "avatar_$user"
        transaction {
            UsersTable.update({ UsersTable.id eq user }) {
                it[UsersTable.avatarUrl] = "https://avatars.example/$oldPublicId.jpg"
                it[UsersTable.avatarPublicId] = oldPublicId
            }
        }
        val jwt = JwtConfig.generateToken(user)
        val imageBytes = ByteArrayOutputStream().use { output ->
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output)
            output.toByteArray()
        }

        val response = client.post("/api/user/avatar") {
            bearerAuth(jwt)
            setBody(MultiPartFormDataContent(formData {
                append("avatar", imageBytes, io.ktor.http.Headers.build {
                    append(HttpHeaders.ContentType, "image/png")
                    append(HttpHeaders.ContentDisposition, "filename=\"avatar.png\"")
                })
            }))
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(attemptedIds, deletedIds)
        transaction {
            val row = UsersTable.selectAll().where { UsersTable.id eq user }.single()
            assertEquals("https://avatars.example/$oldPublicId.jpg", row[UsersTable.avatarUrl])
            assertEquals(oldPublicId, row[UsersTable.avatarPublicId])
        }
    }

    @Test
    fun `changing a pending email invalidates a code sent to the previous address`() = testApplication {
        application { module() }
        startApplication()
        val user = createUser()
        val currentEmail = transaction { UsersTable.selectAll().where { UsersTable.id eq user }.single()[UsersTable.email] }
        val firstPending = "first-${java.util.UUID.randomUUID()}@example.com"
        val secondPending = "second-${java.util.UUID.randomUUID()}@example.com"
        val jwt = JwtConfig.generateToken(user)

        suspend fun requestEmailChange(address: String) = client.put("/api/user/email") {
            bearerAuth(jwt)
            contentType(ContentType.Application.Json)
            setBody("""{"newEmail":"$address","currentPassword":"unused"}""")
        }
        assertEquals(HttpStatusCode.OK, requestEmailChange(firstPending).status)
        transaction {
            UserVerificationTable.insert {
                it[UserVerificationTable.userId] = user
                it[UserVerificationTable.token] = "654321"
                it[UserVerificationTable.targetEmail] = firstPending
                it[UserVerificationTable.expiresAt] = System.currentTimeMillis() + 60_000L
            }
        }
        assertEquals(HttpStatusCode.OK, requestEmailChange(secondPending).status)

        val staleCode = client.post("/api/user/verify-email/confirm") {
            bearerAuth(jwt)
            contentType(ContentType.Application.Json)
            setBody("""{"token":"654321"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, staleCode.status)
        val emails = transaction {
            UsersTable.selectAll().where { UsersTable.id eq user }.single().let {
                it[UsersTable.email] to it[UsersTable.pendingEmail]
            }
        }
        assertEquals(currentEmail, emails.first)
        assertEquals(secondPending, emails.second)
    }

    @Test
    fun `presence is only visible to workspace members`() = testApplication {
        application { module() }
        startApplication()
        val owner = createUser()
        val outsider = createUser()
        val workspaceId = transaction {
            WorkspacesTable.insert {
                it[WorkspacesTable.userId] = owner
                it[WorkspacesTable.workspaceName] = "sec-test"
                it[WorkspacesTable.workspaceOwner] = "sec-test"
                it[WorkspacesTable.workspacePassword] = "unused"
            }[WorkspacesTable.id]
        }

        val response = client.get("/api/presence/$workspaceId") { bearerAuth(JwtConfig.generateToken(outsider)) }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}
