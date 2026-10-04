package com.collabsphere

import plugins.WebSocketBroker
import plugins.configureRouting
import plugins.configureGitHubRoutes
import plugins.startTaskReminderScheduler
import com.collabsphere.util.JwtConfig
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.slf4j.event.Level
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // ── WebSockets ────────────────────────────────────────────────────────────
    install(WebSockets) {
        pingPeriod = 20.seconds    // more tolerant of mobile network flaps (was 15s)
        timeout = 60.seconds       // was 30s — slow mobile connections get more grace
        maxFrameSize = 4L * 1024 * 1024 // 4MB cap (was 8MB — reduce memory pressure)
        masking = false
    }

    // No CORS plugin: this API has no browser-based frontend, only the Android app, which isn't
    // subject to CORS at all (it's a browser-only mechanism) — so a permissive config here would be
    // pure attack surface with no functional benefit. Add a scoped CORS config if a web client ever exists.

    // ── Database ──────────────────────────────────────────────────────────────
    DatabaseFactory.init()

    // ── Optional Redis (no-op when REDIS_URL is absent) ───────────────────────
    RedisFactory.init()
    // Start cross-instance WebSocket subscriber — silent no-op on single instance without Redis
    WebSocketBroker.startRedisSubscriber(this)

    // ── FCM ───────────────────────────────────────────────────────────────────
    com.collabsphere.util.FcmService.init()

    // ── Observability: Prometheus metrics ────────────────────────────────────
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    io.micrometer.core.instrument.Metrics.addRegistry(appMicrometerRegistry)
    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
        // Track active WS sessions as a gauge
        registry.gauge("ws.sessions.local", WebSocketBroker) { it.localSessionCount.toDouble() }
    }

    // ── Observability: Correlation IDs ────────────────────────────────────────
    // Reads X-Request-Id from incoming request; generates UUID if absent; echoes back in response.
    // The requestId is also placed into MDC so logback includes it in every log line.
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        replyToHeader(HttpHeaders.XRequestId)
        // Populate MDC so logback prints requestId on every log line for this coroutine
        verify { callId -> callId.isNotEmpty() }
    }

    // ── Forwarded Headers (Proxy Support) ──────────────────────────────────
    install(XForwardedHeaders)
    install(ForwardedHeaders)

    // ── Observability: Structured access logging ──────────────────────────────
    install(CallLogging) {
        level = Level.INFO
        callIdMdc("requestId")
        // Suppress health/metrics endpoint noise from access log
        filter { call ->
            call.request.local.uri !in setOf("/health", "/metrics")
        }
        format { call ->
            val status = call.response.status()
            val method = call.request.local.method.value
            val uri = call.request.local.uri
            val duration = call.processingTimeMillis()
            "$method $uri -> ${status?.value} (${duration}ms)"
        }
    }

    // ── Content negotiation ───────────────────────────────────────────────────
    install(ContentNegotiation) {
        json()
    }

    // ── Rate limiting ─────────────────────────────────────────────────────────
    // Per-user for authenticated endpoints; per-IP for auth endpoints.
    // Limits are generous — primarily to prevent runaway bugs / bots, not normal users.
    install(RateLimit) {
        // Auth endpoints (login, register, password reset) — stricter
        register(RateLimitName("auth")) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
        // All authenticated API endpoints — generous limit per user
        register(RateLimitName("api")) {
            rateLimiter(limit = 300, refillPeriod = 1.minutes)
            requestKey { call ->
                call.principal<JWTPrincipal>()
                    ?.payload?.getClaim("userId")?.asString()
                    ?: call.request.origin.remoteHost
            }
        }
    }

    // ── Global error handler ──────────────────────────────────────────────────
    // Safety net for anything not already caught by a route's own try/catch (e.g. a malformed
    // body that fails during deserialization) — prevents a raw stack trace/framework error page
    // from ever reaching a client.
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception caught by StatusPages", cause)
            call.respond(HttpStatusCode.InternalServerError, "Internal server error")
        }
    }

    // ── JWT Authentication ────────────────────────────────────────────────────
    install(Authentication) {
        jwt("auth-jwt") {
            realm = JwtConfig.realm
            verifier(JwtConfig.verifier)
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asInt() ?: return@validate null
                val tokenVersion = credential.payload.getClaim(JwtConfig.TOKEN_VERSION_CLAIM).asInt() ?: 0
                // Deleted account (null) or a token minted before the last password change/reset.
                if (plugins.TokenVersions.current(userId) != tokenVersion) return@validate null
                JWTPrincipal(credential.payload)
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token is missing, invalid, or expired")
            }
        }
    }

    // ── Health & Metrics endpoints ─────────────────────────────────────────────
    // These are NOT behind auth — load balancers and monitoring need open access.
    // /metrics should be blocked externally (e.g. Render's ingress or a reverse proxy)
    // from public internet access — only internal scraping should reach it.
    routing {
        get("/health") {
            val dbOk = try {
                // Minimal DB round-trip — uses the same HikariCP pool as production traffic
                plugins.dbQuery { 1 }
                true
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                application.environment.log.error("[Health] DB check failed", e)
                false
            }
            val status = if (dbOk) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
            call.respond(status, mapOf(
                "status" to if (dbOk) "UP" else "DEGRADED",
                "db" to if (dbOk) "UP" else "DOWN",
                "redis" to if (RedisFactory.isAvailable) "UP" else "NOT_CONFIGURED",
                "wsSessions" to WebSocketBroker.localSessionCount,
                "ts" to System.currentTimeMillis()
            ))
        }

        get("/metrics") {
            call.respondText(
                appMicrometerRegistry.scrape(),
                ContentType.parse("text/plain; version=0.0.4; charset=utf-8")
            )
        }
    }

    // ── Feature routes ────────────────────────────────────────────────────────
    configureRouting()
    configureGitHubRoutes()
    startTaskReminderScheduler()
}