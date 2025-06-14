package com.storyteller_f.a.server

import com.maxmind.geoip2.DatabaseReader
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.server.auth.UserSession
import com.storyteller_f.a.server.auth.configureAuth
import com.storyteller_f.a.server.auth.getRateLimitKey
import com.storyteller_f.a.server.route.configureRoute
import com.storyteller_f.backend.service.Backend
import com.storyteller_f.backend.service.DatabaseFactory
import com.storyteller_f.backend.service.MergedEnv
import com.storyteller_f.backend.service.buildBackendFromEnv
import com.storyteller_f.backend.service.media.loadAvif
import com.storyteller_f.backend.service.readEnv
import com.storyteller_f.shared.kmpLogger
import io.github.aakira.napier.Napier
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import java.io.File
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.security.SecureRandom
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.jvm.optionals.getOrNull
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    loadAvif()
    Napier.base(kmpLogger)
    Napier.i {
        "encoding ${OutputStreamWriter(System.out).encoding}"
    }
    SnowflakeFactory.setMachine(0)

    val map = readEnv()
    processPresetDataIfNeed(map)
    val serverPort = map["SERVER_PORT"].takeIf { it.isNotEmpty() }?.toInt() ?: 80
    val extraArgs = arrayOf("-port=$serverPort")

    EngineMain.main(args + extraArgs)
}

fun Application.module() {
    val reader = buildDatabaseReader()
    val backend = buildBackend()
    DatabaseFactory.init(backend)
    val serverJob = launch {
        backend.sendTopicToRoomMembers()
        Napier.i {
            "server topic task finished"
        }
    }
    monitor.subscribe(ApplicationStopping) {
        monitor.unsubscribe(ApplicationStopping) {}
        serverJob.cancel()
    }
    configurePlugin(reader, backend)
    configureAuth(reader, backend)
    configureRoute(reader, backend)
}

private fun Application.configurePlugin(
    reader: DatabaseReader,
    backend: Backend
) {
    install(ContentNegotiation) {
        json()
    }
    install(CallLogging) {
        level = Level.INFO
        callIdMdc("call-id")
        format { call ->
            buildLog(call, reader)
        }
    }
    install(CallId) {
        header(HttpHeaders.XRequestId)
        generate()
    }
    install(WebSockets) {
        setupWebSockets()
    }
    install(Sessions) {
        setupSessions()
    }
    if (backend.config.buildType == "prod") {
        setupRateLimit(reader)
    }
    install(PartialContent)
    install(Resources)
    configureMonitor()
}

private fun WebSockets.WebSocketOptions.setupWebSockets() {
    pingPeriod = 15.seconds
    timeout = 15.seconds
    maxFrameSize = Long.MAX_VALUE
    masking = false
    contentConverter = KotlinxWebsocketSerializationConverter(Json)
}

private fun Application.setupRateLimit(reader: DatabaseReader) {
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

private fun SessionsConfig.setupSessions() {
    val secureRandom = SecureRandom()
    val secretEncryptKey = ByteArray(16).apply {
        secureRandom.nextBytes(this)
    }
    val secretSignKey = ByteArray(14).apply {
        secureRandom.nextBytes(this)
    }

    cookie<UserSession>("user_session") {
        cookie.path = "/"
        cookie.maxAgeInSeconds = 3600

        transform(SessionTransportTransformerEncrypt(secretEncryptKey, secretSignKey))
    }
}

private fun Application.buildBackend(): Backend {
    val associate = engine.environment.config.toMap().mapNotNull {
        (it.value as? String)?.let { v ->
            it.key to v
        }
    }.associate { it }
    val env = readEnv(associate)
    Napier.i {
        "start server at ${env["SERVER_PORT"]}"
    }
    return buildBackendFromEnv(env)
}

private fun buildDatabaseReader() = DatabaseReader.Builder(
    ClassLoader.getSystemClassLoader().getResourceAsStream("GeoLite2-Country.mmdb")
).build()

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

private fun processPresetDataIfNeed(env: MergedEnv) {
    if (!env["PRESET_ENABLE"].toBoolean()) return
    val preSetScript = env["PRESET_SCRIPT"]
    val workingDir = env["PRESET_WORKING_DIR"]
    if (preSetScript.isBlank() || workingDir.isBlank()) {
        println("preset config failure")
        exitProcess(1)
    }
    val scriptArray = preSetScript.trim('\'').split(" ").map {
        if (it.startsWith("~")) {
            val home = System.getProperty("user.home")
            home + it.substring(1)
        } else {
            it
        }
    }
    val file = File(workingDir.trim('\''))
    println("exec preset scripts: ${scriptArray.joinToString(" ")}. working dir: ${file.canonicalPath}")
    runBlocking {
        suspendCancellableCoroutine {
            thread {
                processPresetData(scriptArray, file, it)
            }
        }
    }
}

private fun CoroutineScope.processPresetData(
    scriptArray: List<String>,
    file: File,
    continuation: CancellableContinuation<Int>
) {
    val process = ProcessBuilder(scriptArray).directory(file).start()
    val reader = process.inputStream.bufferedReader()
    val errorReader = process.errorStream.bufferedReader()
    try {
        launch {
            while (process.isAlive) {
                val line = reader.readLine() ?: break
                Napier.i(tag = "preset") {
                    line
                }
            }
        }
        launch {
            while (process.isAlive) {
                val line = errorReader.readLine() ?: break
                Napier.e(tag = "preset") {
                    line
                }
            }
        }
        Napier.i(tag = "preset") {
            "preset started"
        }
        val code = process.waitFor()
        Napier.i(tag = "preset") {
            "preset finished. code: $code"
        }
        continuation.resume(code)
    } catch (e: Exception) {
        Napier.e(tag = "preset", throwable = e) {
            "preset failed"
        }
        continuation.resumeWithException(e)
        exitProcess(1)
    } finally {
        reader.close()
        errorReader.close()
        process.destroy()
    }
}
