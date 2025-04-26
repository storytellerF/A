package com.storyteller_f.a.server

import com.maxmind.geoip2.DatabaseReader
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.*
import com.storyteller_f.a.server.auth.UserSession
import com.storyteller_f.a.server.auth.configureAuth
import com.storyteller_f.a.server.auth.getRateLimitKey
import com.storyteller_f.media.loadAvif
import com.storyteller_f.shared.kmpLogger
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
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
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.update
import org.slf4j.event.Level
import java.io.File
import java.net.InetAddress
import java.security.SecureRandom
import kotlin.jvm.optionals.getOrNull
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    loadAvif()
    Napier.base(kmpLogger)
    SnowflakeFactory.setMachine(0)

    val map = readEnv()
    processPresetData(map)
    val serverPort = map["SERVER_PORT"].takeIf { it.isNotEmpty() }?.toInt() ?: 80
    val extraArgs = arrayOf("-port=$serverPort")

    EngineMain.main(args + extraArgs)
}

@Suppress("unused")
fun Application.module() {
    val reader = buildDatabaseReader()
    val backend = buildBackend()
    DatabaseFactory.init(backend)

    runTask(backend)

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
    if (backend.config.isProd) {
        setupRateLimit(reader)
    }
    install(Resources)
    configureAuth(reader, backend)
}

private fun Application.runTask(backend: Backend) {
    launch {
        while (true) {
            Napier.i(tag = "task") {
                "execute ${now()}"
            }
            getAcgTaskListFromTopics(backend).mapResultIfNotNull { (acgList, userAcgMap, list) ->
                DatabaseFactory.dbQuery(backend) {
                    acgList.forEach { (id, acg) ->
                        userAcgMap[id]?.let { oldAcgAmount ->
                            Users.update({
                                Users.id eq id
                            }) {
                                it[Users.acgAmount] = oldAcgAmount + acg
                            }
                        }
                    }

                    addTaskRecord(
                        TaskRecord(
                            SnowflakeFactory.nextId(),
                            now(),
                            TaskRecordType.TOPIC_ACG,
                            list.last().id
                        )
                    )
                }
            }.onSuccess {
                delay(1000)
                Napier.i(tag = "task") {
                    "task success $it"
                }
            }.onFailure {
                delay(1000)
                Napier.i(tag = "task", throwable = it) {
                    "task failed"
                }
            }
        }
    }
}

private suspend fun getAcgTaskListFromTopics(
    backend: Backend,
) =
    DatabaseFactory.getLatestTaskRecord(backend, TaskRecordType.TOPIC_ACG).mapResult {
        DatabaseFactory.getRawTopics(backend, it?.processedId ?: 0)
    }.mapResult { list ->
        if (list.isNotEmpty()) {
            val acgList = list.groupBy {
                it.author
            }.mapValues {
                it.value.count()
            }.toList()
            val uids = acgList.map {
                it.first
            }
            DatabaseFactory.getUserAcgByIds(backend, uids).map { list ->
                list.associate {
                    it.first to it.second
                }
            }.map { userAcgMap ->
                Triple(acgList, userAcgMap, list)
            }
        } else {
            Result.success(null)
        }
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

private fun processPresetData(env: MergedEnv) {
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
