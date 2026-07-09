import com.collabsphere.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ServerTest {
    @Test
    fun testLoginEndpointSuccess() = testApplication {
        application {
            module()
        }

        client.post("/api/login") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"test@example.com","password":"password123"}""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertContains(bodyAsText(), "mock-jwt-token-string")
            assertContains(bodyAsText(), "Rohit")
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