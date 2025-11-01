package com.storyteller_f.a.cloud.server

import com.maxmind.geoip2.DatabaseReader
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CombinedDatabase
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.CustomConfig
import com.storyteller_f.a.backend.core.CustomKeyStore
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.buildCommunitySearchService
import com.storyteller_f.a.backend.core.buildNameService
import com.storyteller_f.a.backend.core.buildRoomSearchService
import com.storyteller_f.a.backend.core.buildTopicSearchService
import com.storyteller_f.a.backend.core.buildUserSearchService
import com.storyteller_f.a.backend.core.databaseConnection
import com.storyteller_f.a.backend.core.loadAvif
import com.storyteller_f.a.backend.core.mediaService
import com.storyteller_f.a.backend.core.readEnv
import com.storyteller_f.a.backend.core.service.CommunitySearchService
import com.storyteller_f.a.backend.core.service.NameService
import com.storyteller_f.a.backend.core.service.ObjectStorageService
import com.storyteller_f.a.backend.core.service.RoomSearchService
import com.storyteller_f.a.backend.core.service.TopicSearchService
import com.storyteller_f.a.backend.core.service.UserSearchService
import com.storyteller_f.a.backend.core.setLogPath
import com.storyteller_f.a.backend.exposed.buildExposedDatabase
import com.storyteller_f.a.cloud.server.auth.UserSession
import com.storyteller_f.a.cloud.server.auth.configureAuth
import com.storyteller_f.a.cloud.server.auth.getRateLimitKey
import com.storyteller_f.a.cloud.server.route.bindAccountRoute
import com.storyteller_f.a.cloud.server.route.bindCommunityRoute
import com.storyteller_f.a.cloud.server.route.bindProtectedAccountRoute
import com.storyteller_f.a.cloud.server.route.bindProtectedAdminRoute
import com.storyteller_f.a.cloud.server.route.bindProtectedCommunityRoute
import com.storyteller_f.a.cloud.server.route.bindProtectedMediaRoute
import com.storyteller_f.a.cloud.server.route.bindProtectedRoomRoute
import com.storyteller_f.a.cloud.server.route.bindProtectedTitleRoute
import com.storyteller_f.a.cloud.server.route.bindProtectedTopicRoute
import com.storyteller_f.a.cloud.server.route.bindProtectedUserRoute
import com.storyteller_f.a.cloud.server.route.bindProtectedUserSubscriptionRoute
import com.storyteller_f.a.cloud.server.route.bindRoomRoute
import com.storyteller_f.a.cloud.server.route.bindTopicRoute
import com.storyteller_f.a.cloud.server.route.bindUnauthenticatedPanelRoute
import com.storyteller_f.a.cloud.server.route.bindUnauthenticatedRoute
import com.storyteller_f.a.cloud.server.route.bindUnprotectedAccountRoute
import com.storyteller_f.a.cloud.server.route.bindUserRoute
import com.storyteller_f.shared.CryptoJvm
import com.storyteller_f.shared.loadCryptoLibIfNeed
import com.storyteller_f.shared.setupKmpLogger
import io.github.aakira.napier.Napier
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callid.generate
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.queryString
import io.ktor.server.request.uri
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionTransportTransformerEncrypt
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.SessionsConfig
import io.ktor.server.sessions.cookie
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.util.toMap
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.close
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import java.io.File
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.security.SecureRandom
import kotlin.jvm.optionals.getOrNull
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    setLogPath("A")
    setupKmpLogger()
    loadCryptoLibIfNeed()
    loadAvif()
    Napier.i {
        "encoding ${OutputStreamWriter(System.out).encoding}"
    }
    SnowflakeFactory.setMachine(0)

    val map = readEnv(flavorFilePath = getFlavorFilePath())
    processInitTaskIfNeed(map)
    val serverPort = map["SERVER_PORT"]?.toInt() ?: 80
    val extraArgs = arrayOf("-port=$serverPort")
    if (map["BUILD_TYPE"] == "prod") {
        Sentry.init { options ->
            options.dsn = map["SENTRY_DSN"]
            options.isDebug = false
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        Napier.i("程序即将退出，执行清理操作...")
    })
    EngineMain.main(args + extraArgs)
}

fun Application.module() {
    monitor.subscribe(ApplicationStarted) { application ->
        application.environment.log.info("Server is started")
    }
    monitor.subscribe(ApplicationStopped) { application ->
        application.environment.log.info("Server is stopped")
        // Release resources and unsubscribe from events
        monitor.unsubscribe(ApplicationStarted) {}
        monitor.unsubscribe(ApplicationStopped) {}
    }
    val backend = try {
        buildBackend()
    } catch (e: Exception) {
        Napier.e(e, tag = "module") {
            "buildBackend failed"
        }
        throw e
    }
    val reader = try {
        buildDatabaseReader()
    } catch (e: Exception) {
        Napier.i(e, tag = "module") {
            "buildDatabaseReader failed"
        }
        throw e
    }
    runBlocking {
        if (backend.customConfig.buildType == "test") {
            backend.database.init()
        }
        backend.database.init()
    }
    startNewMessageTask(backend)
    configurePlugin(reader, backend)
    configureAuth(backend)
    configureRoute(reader, backend)
}

private fun Application.configurePlugin(
    reader: DatabaseReader,
    backend: Backend,
) {
    install(ContentNegotiation) {
        json()
    }
    install(CallLogging) {
        level = Level.INFO
        callIdMdc("call-id")
        filter {
            it.request.uri != "/metrics"
        }
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
    if (backend.customConfig.buildType == "prod") {
        setupRateLimit()
    }
    install(PartialContent)
    configureMonitor()
}

private fun WebSockets.WebSocketOptions.setupWebSockets() {
    pingPeriod = 15.seconds
    timeout = 15.seconds
    maxFrameSize = Long.MAX_VALUE
    masking = false
    contentConverter = KotlinxWebsocketSerializationConverter(Json)
}

private fun Application.setupRateLimit() {
    install(RateLimit) {
        global {
            rateLimiter(limit = 50, refillPeriod = 10.seconds)
            requestKey { call ->
                call.getRateLimitKey()
            }
            requestWeight { applicationCall, key ->
                when (applicationCall.request.httpMethod) {
                    HttpMethod.Post -> 2
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
    val injectedEnv = engine.environment.config.toMap().mapNotNull {
        (it.value as? String)?.let { v ->
            it.key to v
        }
    }.associate { it }
    val env = readEnv(injectedEnv, getFlavorFilePath())
    Napier.i {
        if (env["BUILD_TYPE"] == "test") {
            "start server in `${env["METHOD_NAME"]}`"
        } else {
            "start server at ${env["SERVER_PORT"]}"
        }
    }
    return buildBackendFromEnv(env)
}

private fun getFlavorFilePath() = File("../../${ServerConfig.FLAVOR}.env").canonicalPath

private fun buildDatabaseReader() = DatabaseReader.Builder(
    ClassLoader.getSystemResourceAsStream("GeoLite2-Country.mmdb")
).build()

private fun buildLog(
    call: ApplicationCall,
    reader: DatabaseReader,
): String {
    val status = call.response.status()
    val httpMethod = call.request.httpMethod.value
    val ipList = call.remoteIp(reader).joinToString(",")
    return """Url: ${call.request.uri}, 
                |    Status: $status, HTTP method: $httpMethod,
                |    Query: ${call.request.queryString()},
                |    Headers: ${call.request.headers.toMap()},
                |    Ip：$ipList""".trimMargin()
}

fun ApplicationCall.remoteIp(
    reader: DatabaseReader,
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

private fun processInitTaskIfNeed(env: MergedEnv) {
    if (!env["INIT_ENABLE"].toBoolean()) return
    val initScriptContent = env["INIT_SCRIPT"]
    val workingDir = env["INIT_WORKING_DIR"]
    if (initScriptContent.isNullOrBlank() || workingDir.isNullOrBlank()) {
        println("init failure")
        exitProcess(1)
    }
    val scriptArray = initScriptContent.trim('\'').split(" ").map {
        if (it.startsWith("~")) {
            val home = System.getProperty("user.home")
            home + it.substring(1)
        } else {
            it
        }
    }
    val file = File(workingDir.trim('\''))
    Napier.i(tag = "init") {
        "scripts: ${scriptArray.joinToString(" ")}. working dir: ${file.canonicalPath}"
    }
    runBlocking {
        executeScriptInThread(scriptArray, file)
    }
}

private suspend fun CoroutineScope.executeScriptInThread(
    scriptArray: List<String>,
    file: File,
) {
    val process = ProcessBuilder(scriptArray).directory(file).start()
    launch(Dispatchers.IO) {
        process.inputStream.bufferedReader().use {
            while (process.isAlive) {
                val line = it.readLine() ?: break
                Napier.i(tag = "init") {
                    line
                }
            }
        }
    }
    launch(Dispatchers.IO) {
        process.errorStream.bufferedReader().use {
            while (process.isAlive) {
                val line = it.readLine() ?: break
                Napier.e(tag = "init") {
                    line
                }
            }
        }
    }
    Napier.i(tag = "init") {
        "started"
    }
    val exitValue = withContext(Dispatchers.IO) {
        process.waitFor()
    }
    check(exitValue == 0) {
        "init failed $exitValue"
    }
}

class ServerBackend(
    override val customConfig: CustomConfig,
    override val topicSearchService: TopicSearchService,
    override val roomSearchService: RoomSearchService,
    override val communitySearchService: CommunitySearchService,
    override val userSearchService: UserSearchService,
    override val objectStorageService: ObjectStorageService,
    override val nameService: NameService,
    override val database: CombinedDatabase
) : Backend

fun buildBackendFromEnv(env: MergedEnv): Backend {
    Napier.i("load env: ${env["COMPOSE_PROJECT_NAME"]}")

    val databaseConnection = databaseConnection(env)

    val buildType = env["BUILD_TYPE"] ?: "prod"
    val flavor = env["FLAVOR"] ?: throw Exception("FLAVOR is empty")

    val topicSearchService = buildTopicSearchService(env)
    val userSearchService = buildUserSearchService(env)
    val roomSearchService = buildRoomSearchService(env)
    val communitySearchService = buildCommunitySearchService(env)
    val mediaService = mediaService(env)

    val snapshotKeyStorePath = env["SNAPSHOT_KEYSTORE_PATH"]
    val snapshotKeyStorePassword = env["SNAPSHOT_KEYSTORE_PASS"]
    val snapshotKeyStore =
        if (!snapshotKeyStorePath.isNullOrBlank() && !snapshotKeyStorePassword.isNullOrBlank()) {
            if (!File(snapshotKeyStorePath).exists()) {
                CryptoJvm.createKeystore(
                    snapshotKeyStorePassword.toCharArray(),
                    snapshotKeyStorePath
                )
            }
            CustomKeyStore(snapshotKeyStorePath, snapshotKeyStorePassword)
        } else {
            null
        }
    val customConfig = CustomConfig(buildType, flavor, snapshotKeyStore)

    return ServerBackend(
        customConfig,
        topicSearchService,
        roomSearchService,
        communitySearchService,
        userSearchService,
        mediaService,
        buildNameService(env),
        buildExposedDatabase(databaseConnection)
    )
}

@Suppress("ThrowsCount", "unused")
@OptIn(InternalAPI::class)
suspend fun ByteReadChannel.copyWithLimitAndClose(channel: ByteWriteChannel, limit: Long): Long {
    var result = 0L
    try {
        while (!isClosedForRead) {
            result += readBuffer.transferTo(channel.writeBuffer)
            if (result > limit) {
                throw CustomBadRequestException("exceed content length")
            }
            channel.flush()
            awaitContent()
        }

        closedCause?.let { throw it }
    } catch (cause: Throwable) {
        cancel(cause)
        channel.close(cause)
        throw cause
    } finally {
        channel.flushAndClose()
    }

    return result
}

fun Application.configureRoute(reader: DatabaseReader, backend: Backend) {
    routing {
        authenticate("user") {
            bindProtectedRoomRoute(backend)
            bindProtectedTopicRoute(backend)
            bindProtectedCommunityRoute(backend)
            bindProtectedUserRoute(backend)
            bindProtectedUserSubscriptionRoute(backend)
            webSocket("/link") {
                webSocketContent(reader, backend)
            }
            bindProtectedMediaRoute(backend)
            bindProtectedTitleRoute(backend)
            bindProtectedAccountRoute(backend)
        }
        authenticate("user", optional = true) {
            bindAccountRoute()
            bindRoomRoute(backend)
            bindTopicRoute(backend)
            bindCommunityRoute(backend)
            bindUserRoute(backend)
        }
        bindUnprotectedAccountRoute(backend)
        bindUnauthenticatedRoute(backend)
        bindProtectedAdminRoute(backend)
        bindUnauthenticatedPanelRoute(backend)
    }
}
