package com.collabsphere

import plugins.configureRouting
import plugins.configureGitHubRoutes
import plugins.startTaskReminderScheduler
import com.collabsphere.util.JwtConfig
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.websocket.*
import io.ktor.http.*
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    // No CORS plugin: this API has no browser-based frontend, only the Android app, which isn't
    // subject to CORS at all (it's a browser-only mechanism) — so a permissive config here would be
    // pure attack surface with no functional benefit. Add a scoped CORS config if a web client ever exists.

    DatabaseFactory.init()
    com.collabsphere.util.FcmService.init()

    install(ContentNegotiation) {
        json()
    }

    // Safety net for anything not already caught by a route's own try/catch (e.g. a malformed
    // body that fails during deserialization) — prevents a raw stack trace/framework error page
    // from ever reaching a client.
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            cause.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, "Internal server error")
        }
    }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = JwtConfig.realm
            verifier(JwtConfig.verifier)
            validate { credential ->
                if (credential.payload.getClaim("userId").asInt() != null) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token is missing, invalid, or expired")
            }
        }
    }

    configureRouting()
    configureGitHubRoutes()
    startTaskReminderScheduler()
}