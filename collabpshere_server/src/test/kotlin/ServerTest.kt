import com.collabsphere.module
import dto.LoginResponse
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.*

class ServerTest {
    @Test
    fun testLoginEndpointSuccess() = testApplication {
        application {
            module()
        }

        client.post("/api/register") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"test@example.com","password":"password123","userName":"Rohit"}""")
        }

        client.post("/api/login") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"test@example.com","password":"password123"}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val response = Json { ignoreUnknownKeys = true }.decodeFromString<LoginResponse>(bodyAsText())
            assertEquals("Rohit", response.userName)
            assertTrue(!response.token.isNullOrBlank(), "Expected a signed JWT to be issued on login")
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