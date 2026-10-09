import com.collabsphere.model.UsersTable
import com.collabsphere.model.WorkspacesTable
import com.collabsphere.module
import com.collabsphere.util.JwtConfig
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.*

/** Runs only against the disposable database configured for integrationTest. */
class SecurityRoutesTest {

    private val createdUserIds = mutableListOf<Int>()

    private fun createUser(): Int = transaction {
        val email = "sec-${java.util.UUID.randomUUID()}@example.com"
        UsersTable.insert {
            it[UsersTable.email] = email
            it[UsersTable.password] = "unused"
            it[UsersTable.username] = "sec-test"
            it[UsersTable.isEmailVerified] = true
        }[UsersTable.id]
    }.also { createdUserIds += it }

    private fun fcmTokenOf(userId: Int): String? = transaction {
        UsersTable.selectAll().where { UsersTable.id eq userId }.single()[UsersTable.fcmToken]
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
        transaction { createdUserIds.forEach { id -> UsersTable.deleteWhere { UsersTable.id eq id } } }
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
