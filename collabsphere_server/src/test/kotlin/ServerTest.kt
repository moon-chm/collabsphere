import com.collabsphere.model.UsersTable
import com.collabsphere.module
import dto.LoginResponse
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.test.*

class ServerTest {
    @Test
    fun testLoginEndpointSuccess() = testApplication {
        application {
            module()
        }

        // A fixed email collides with itself on a second run against the same (real) dev DB this
        // test relies on — unique per run so the test stays repeatable without needing a test DB.
        val email = "test-${java.util.UUID.randomUUID()}@example.com"

        client.post("/api/register") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"$email","password":"password123","userName":"Rohit"}""")
        }
        markEmailVerified(email)

        client.post("/api/login") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"$email","password":"password123"}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val response = Json { ignoreUnknownKeys = true }.decodeFromString<LoginResponse>(bodyAsText())
            assertEquals("Rohit", response.userName)
            assertTrue(!response.token.isNullOrBlank(), "Expected a signed JWT to be issued on login")
        }
    }

    @Test
    fun testLoginRejectsUnverifiedEmail() = testApplication {
        application {
            module()
        }

        val email = "test-${java.util.UUID.randomUUID()}@example.com"

        client.post("/api/register") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"$email","password":"password123","userName":"Rohit"}""")
        }

        client.post("/api/login") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"$email","password":"password123"}""")
        }.apply {
            assertEquals(HttpStatusCode.Forbidden, status)
        }
    }

    private fun markEmailVerified(email: String) {
        transaction {
            UsersTable.update({ UsersTable.email eq email }) {
                it[UsersTable.isEmailVerified] = true
            }
        }
    }

    @Test
    fun testLoginEndpointInvalidCredentials() = testApplication {
        application {
            module()
        }

        client.post("/api/login") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"wrong@example.com","password":"badpassword"}""")
        }.apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }
}