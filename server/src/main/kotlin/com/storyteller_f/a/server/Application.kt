package com.storyteller_f.a.server

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.auth.UserSession
import com.storyteller_f.a.server.auth.configureAuth
import com.storyteller_f.buildBackendFromEnv
import com.storyteller_f.readEnv
import com.storyteller_f.shared.type.OKey
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import java.time.Duration



fun main(args: Array<String>) {
    SnowflakeFactory.setMachine(0)

    val map = readEnv()
    val backend = buildBackendFromEnv(map)
    val serverPort = (map["SERVER_PORT"] as String).toInt()
    val extraArgs = arrayOf("-port=$serverPort")
    DatabaseFactory.init(backend.config.databaseConnection)
    EngineMain.main(args + extraArgs)
}

@Suppress("unused")
fun Application.module() {
    val map = readEnv()
    val backend = buildBackendFromEnv(map)

    install(ContentNegotiation) {
        json()
    }
    install(CallLogging) {
        level = Level.INFO
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            "Status: $status, HTTP method: $httpMethod, Url: ${call.request.uri}"
        }
    }
    install(CallId) {
        header(HttpHeaders.XRequestId)
        generate {
            runBlocking {
                SnowflakeFactory.nextId().toString(36)
            }
        }
    }
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }
    install(Sessions) {
        cookie<UserSession>("user_session") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 3600
        }
    }
    configureAuth(backend)
}
