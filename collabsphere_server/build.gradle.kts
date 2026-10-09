plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    kotlin("plugin.serialization") version "2.3.21"
}

group = "com.collabsphere"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val ktor_version ="3.1.0"
    val exposed_version = "0.50.0"

    implementation(platform("io.ktor:ktor-bom:$ktor_version"))
    implementation("io.ktor:ktor-server-websockets-jvm:${ktor_version}")
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(libs.logback.classic)
    testImplementation("io.ktor:ktor-server-test-host:${ktor_version}")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")

    implementation("org.postgresql:postgresql:42.7.3")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("io.ktor:ktor-server-auth:${ktor_version}")
    implementation("io.ktor:ktor-server-auth-jwt:${ktor_version}")
    implementation("io.ktor:ktor-server-status-pages:${ktor_version}")
    implementation("at.favre.lib:bcrypt:0.10.2")
    implementation("com.google.firebase:firebase-admin:9.2.0")
    implementation("com.sun.mail:jakarta.mail:2.0.1")
    implementation("io.ktor:ktor-client-core:${ktor_version}")
    implementation("io.ktor:ktor-client-cio:${ktor_version}")
    implementation("io.ktor:ktor-client-content-negotiation:${ktor_version}")

    // ── Observability ─────────────────────────────────────────────────────────
    // Request correlation IDs (X-Request-Id header in/out)
    implementation("io.ktor:ktor-server-call-id:${ktor_version}")
    // Structured access logging (method, path, status, duration)
    implementation("io.ktor:ktor-server-call-logging:${ktor_version}")
    // Micrometer metrics exposed as Prometheus scrape endpoint
    implementation("io.ktor:ktor-server-metrics-micrometer:${ktor_version}")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.5")

    // ── Rate limiting ─────────────────────────────────────────────────────────
    implementation("io.ktor:ktor-server-rate-limit:${ktor_version}")
    implementation("io.ktor:ktor-server-forwarded-header:${ktor_version}")

    // ── Optional Redis (graceful degradation — disabled when REDIS_URL is absent) ──
    // Used for: WebSocket cross-instance fan-out, membership cache, workspace member cache
    implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}

tasks.withType<Test> {
    environment("JWT_SECRET", "test-secret")
}

// DB-backed route/locking tests mutate shared state and must never inherit a developer's
// DATABASE_URL or provider credentials. Keep them out of the default test task.
val databaseBackedTestPatterns = listOf(
    "**/*IntegrationTest.class",
    "**/RoutesEndToEndTest.class",
    "**/SecurityRoutesTest.class",
    "**/ServerTest.class"
)

tasks.named<Test>("test") {
    exclude(databaseBackedTestPatterns)
}

val testDatabaseUrl = providers.environmentVariable("TEST_DATABASE_URL")
val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs database-backed integration tests against an explicitly configured test database."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    shouldRunAfter(tasks.named("test"))

    filter {
        includeTestsMatching("RoutesEndToEndTest")
        includeTestsMatching("SecurityRoutesTest")
        includeTestsMatching("ServerTest")
        includeTestsMatching("plugins.DeltaSyncIntegrationTest")
        includeTestsMatching("plugins.DmHistoryIntegrationTest")
        includeTestsMatching("plugins.WebhookQueueIntegrationTest")
    }

    doFirst {
        require(testDatabaseUrl.isPresent && testDatabaseUrl.get().isNotBlank()) {
            "Set TEST_DATABASE_URL to a disposable PostgreSQL test database before running integrationTest."
        }
        val selectedUrl = testDatabaseUrl.get()
        val runtimeDatabaseUrl = System.getenv("DATABASE_URL")
        val runtimeJdbcUrl = System.getenv("JDBC_DATABASE_URL")
        require(selectedUrl != runtimeDatabaseUrl && selectedUrl != runtimeJdbcUrl) {
            "Integration tests refuse to use the configured runtime database URL."
        }
        require(System.getenv("COLLABSPHERE_ALLOW_TEST_DB") == "YES") {
            "Set COLLABSPHERE_ALLOW_TEST_DB=YES only after confirming TEST_DATABASE_URL is disposable."
        }
    }

    // The test process sees only the explicit test database. Provider configuration is blanked
    // so application tests can exercise their fallback behavior without sending real messages.
    environment("DATABASE_URL", testDatabaseUrl.orElse(""))
    environment("JDBC_DATABASE_URL", "")
    environment("DATABASE_READ_URL", "")
    environment("JDBC_DATABASE_READ_URL", "")
    environment("GMAIL_CLIENT_ID", "")
    environment("GMAIL_CLIENT_SECRET", "")
    environment("GMAIL_REFRESH_TOKEN", "")
    environment("SMTP_HOST", "")
    environment("SMTP_USER", "")
    environment("SMTP_PASSWORD", "")
    environment("SMTP_PASS", "")
    environment("CLOUDINARY_CLOUD_NAME", "")
    environment("CLOUDINARY_API_KEY", "")
    environment("CLOUDINARY_API_SECRET", "")
    environment("FIREBASE_SERVICE_ACCOUNT_JSON", "")
    environment("FIREBASE_CONFIG_PATH", "")
    environment("UPLOAD_DIR", layout.buildDirectory.dir("integration-test-uploads").get().asFile.absolutePath)
}

// Opt into DB-backed verification explicitly; ordinary `check` remains usable on a fresh
// checkout without requiring or risking access to any database.
tasks.register("checkWithIntegration") {
    description = "Runs the normal checks plus explicitly isolated PostgreSQL integration tests."
    group = "verification"
    dependsOn(tasks.named("check"), integrationTestTask)
}
