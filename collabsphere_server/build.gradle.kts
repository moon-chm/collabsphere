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