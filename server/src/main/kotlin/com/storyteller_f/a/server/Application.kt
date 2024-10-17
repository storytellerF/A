package com.storyteller_f.a.server

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.auth.UserSession
import com.storyteller_f.a.server.auth.configureAuth
import com.storyteller_f.buildBackendFromEnv
import com.storyteller_f.readEnv
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
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import java.io.File
import java.time.Duration
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    SnowflakeFactory.setMachine(0)

    val map = readEnv()

    processPreSetData(map)

    val backend = buildBackendFromEnv(map)
    val serverPort = (map["SERVER_PORT"] as String).toInt()
    val extraArgs = arrayOf("-port=$serverPort")
    DatabaseFactory.init(backend.config.databaseConnection)

    EngineMain.main(args + extraArgs)
}

private fun processPreSetData(map: MutableMap<out Any, out Any>) {
    val preSetEnable = (map["PRE_SET_ENABLE"] as String).toBoolean()
    if (preSetEnable) {
        val preSetScript = map["PRE_SET_SCRIPT"] as String
        val workingDir = map["PRE_SET_WORKING_DIR"] as String
        if (preSetScript.isNotBlank() && workingDir.isNotBlank()) {
            val scriptArray = preSetScript.trim('\'').split(" ").map {
                if (it.startsWith("~")) {
                    val home = System.getProperty("user.home")
                    home + it.substring(1)
                } else {
                    it
                }
            }
            val file = File(workingDir.trim('\''))
            println("exec pre set: ${scriptArray.joinToString(" ")}. working dir: ${file.canonicalPath}")

            val start = ProcessBuilder(scriptArray).directory(file).start()
            try {
                val code = start.waitFor()
                val input = start.inputStream.bufferedReader().readText()
                if (code != 0) {
                    val error = start.errorStream.bufferedReader().readText()
                    println("pre set failed. code: $code")
                    println("input: $input")
                    println("error: $error")
                } else {
                    val error = start.errorStream.bufferedReader().readText()
                    println("flush success.")
                    println("input: $input")
                    println("error: $error")
                }
            } finally {
                start.destroy()
            }
        } else {
            println("pre set config failure")
            exitProcess(1)
        }
    }
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
