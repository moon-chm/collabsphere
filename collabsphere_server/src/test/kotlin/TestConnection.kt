import org.junit.Test
import java.sql.DriverManager

class TestConnection {
    @Test
    fun testDb() {
        val url = "jdbc:postgresql://ep-empty-sea-b4cfsaq3-pooler.c-6.us-east-2.aws.neon.tech:5432/neondb?sslmode=require&user=neondb_owner&password=npg_GIUWP2cQJdM5"
        try {
            val conn = DriverManager.getConnection(url)
            println("=== CONNECTION SUCCESSFUL ===")
            println("Server: " + conn.metaData.databaseProductName)
            println("Version: " + conn.metaData.databaseProductVersion)
            conn.close()
        } catch(e: Exception) {
            println("=== CONNECTION FAILED ===")
            println(e.message)
            throw e
        }
    }
}
