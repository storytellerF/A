package com.storyteller_f.a.server

import com.maxmind.geoip2.DatabaseReader
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.MergedEnv
import com.storyteller_f.a.server.auth.UserSession
import com.storyteller_f.a.server.auth.configureAuth
import com.storyteller_f.a.server.auth.getRateLimitKey
import com.storyteller_f.buildBackendFromEnv
import com.storyteller_f.media.loadAvif
import com.storyteller_f.readEnv
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.request.*
import io.ktor.server.resources.Resources
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import java.io.File
import java.net.InetAddress
import kotlin.jvm.optionals.getOrNull
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    loadAvif()
    Napier.base(DebugAntilog())
    SnowflakeFactory.setMachine(0)

    val map = readEnv()

    processPreSetData(map)

    val serverPort = map["SERVER_PORT"].takeIf { it.isNotEmpty() }?.toInt() ?: 80
    val extraArgs = arrayOf("-port=$serverPort")

    EngineMain.main(args + extraArgs)
}

private fun processPreSetData(env: MergedEnv) {
    val preSetEnable = env["PRESET_ENABLE"].toBoolean()
    if (preSetEnable) {
        val preSetScript = env["PRESET_SCRIPT"]
        val workingDir = env["PRESET_WORKING_DIR"]
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
                    println("preset failed. code: $code")
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
            println("preset config failure")
            exitProcess(1)
        }
    }
}

@Suppress("unused")
fun Application.module() {
    val associate = engine.environment.config.toMap().mapNotNull {
        (it.value as? String)?.let { v ->
            it.key to v
        }
    }.associate { it }
    val map = readEnv(associate)
    val backend = buildBackendFromEnv(map)
    val reader = DatabaseReader.Builder(
        ClassLoader.getSystemClassLoader().getResourceAsStream("GeoLite2-Country.mmdb")
    ).build()
    DatabaseFactory.init(backend.config.databaseConnection)

    install(ContentNegotiation) {
        json()
    }
    install(CallLogging) {
        level = Level.INFO
        format { call ->
            buildLog(call, reader)
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
        pingPeriod = 15.seconds
        timeout = 15.seconds
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
    if (backend.config.isProd) {
        install(RateLimit) {
            global {
                rateLimiter(limit = 10, refillPeriod = 1.seconds)
                requestKey { call ->
                    call.getRateLimitKey(reader)
                }
                requestWeight { applicationCall, key ->
                    when (applicationCall.request.httpMethod) {
                        HttpMethod.Post -> 10
                        else -> 1
                    }
                }
            }
        }
    }
    install(Resources)
    configureAuth(backend, reader)
}

private fun buildLog(
    call: ApplicationCall,
    reader: DatabaseReader
): String {
    val status = call.response.status()
    val httpMethod = call.request.httpMethod.value
    val ipList = call.remoteIp(reader).joinToString(",")
    return """Status: $status, HTTP method: $httpMethod, 
                |Url: ${call.request.uri},
                |Query: ${call.request.queryString()}
                |Ip：$ipList}""".trimMargin()
}

fun ApplicationCall.remoteIp(
    reader: DatabaseReader
): List<Pair<String, String?>> {
    val remoteAddress = request.origin.remoteAddress
    val country = reader.tryCountry(InetAddress.getByName(remoteAddress)).getOrNull()
    return if (country == null) {
        request.header("X-Forwarded-For")?.split(", ").orEmpty().mapNotNull {
            val c = reader.tryCountry(InetAddress.getByName(it)).getOrNull()
            if (c != null) {
                it to c.country.isoCode
            } else {
                null
            }
        }.ifEmpty {
            listOf("127.0.0.1" to null)
        }
    } else {
        listOf(remoteAddress to country.country.isoCode)
    }
}
