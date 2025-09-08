package com.storyteller_f.a.cloud.server

import com.maxmind.geoip2.DatabaseReader
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.CustomConfig
import com.storyteller_f.a.backend.core.CustomKeyStore
import com.storyteller_f.a.backend.exposed.buildExposedDatabase
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.backend.service.MergedEnv
import com.storyteller_f.a.backend.service.buildCommunitySearchService
import com.storyteller_f.a.backend.service.buildRoomSearchService
import com.storyteller_f.a.backend.service.buildTopicSearchService
import com.storyteller_f.a.backend.service.buildUserSearchService
import com.storyteller_f.a.backend.service.databaseConnection
import com.storyteller_f.a.backend.service.mediaService
import com.storyteller_f.a.backend.service.naming.NameService
import com.storyteller_f.a.backend.service.object_storage.loadAvif
import com.storyteller_f.a.backend.service.readEnv
import com.storyteller_f.a.cloud.server.auth.UserSession
import com.storyteller_f.a.cloud.server.auth.configureAuth
import com.storyteller_f.a.cloud.server.auth.getRateLimitKey
import com.storyteller_f.a.cloud.server.route.configureRoute
import com.storyteller_f.shared.kmpLogger
import com.storyteller_f.shared.loadCryptoLibIfNeed
import io.github.aakira.napier.Napier
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
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
import io.ktor.server.sessions.SessionTransportTransformerEncrypt
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.SessionsConfig
import io.ktor.server.sessions.cookie
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.util.toMap
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.close
import io.sentry.Sentry
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.slf4j.event.Level
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.math.BigInteger
import java.net.InetAddress
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.jvm.optionals.getOrNull
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    Napier.base(kmpLogger)
    loadCryptoLibIfNeed()
    loadAvif()
    Napier.i {
        "encoding ${OutputStreamWriter(System.out).encoding}"
    }
    SnowflakeFactory.setMachine(0)

    val map = readEnv()
    processInitTaskIfNeed(map)
    val serverPort = map["SERVER_PORT"]?.toInt() ?: 80
    val extraArgs = arrayOf("-port=$serverPort")
    if (map["BUILD_TYPE"] == "prod") {
        Sentry.init { options ->
            options.dsn = map["SENTRY_DSN"]
            options.isDebug = false
        }
    }
    EngineMain.main(args + extraArgs)
}

fun Application.module() {
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
    if (backend.customConfig.buildType == "test") {
        runBlocking {
            backend.combinedDatabase.init()
        }
    }
    startNewMessageTask(backend)
    configurePlugin(reader, backend)
    configureAuth(reader, backend)
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
            rateLimiter(limit = 10, refillPeriod = 1.seconds)
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
    ClassLoader.getSystemResourceAsStream("GeoLite2-Country.mmdb")
).build()

private fun buildLog(
    call: ApplicationCall,
    reader: DatabaseReader,
): String {
    val status = call.response.status()
    val httpMethod = call.request.httpMethod.value
    val ipList = call.remoteIp(reader).joinToString(",")
    return """Status: $status, HTTP method: $httpMethod, 
                |    Url: ${call.request.uri},
                |    Query: ${call.request.queryString()}
                |    Headers: ${call.request.headers.toMap()}
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
        suspendCancellableCoroutine {
            thread {
                executeScriptInThread(scriptArray, file, it)
            }
        }
    }
}

private fun executeScriptInThread(
    scriptArray: List<String>,
    file: File,
    continuation: CancellableContinuation<Int>,
) {
    val process = try {
        ProcessBuilder(scriptArray).directory(file).start()
    } catch (e: Exception) {
        continuation.resumeWithException(e)
        return
    }
    val reader = process.inputStream.bufferedReader()
    val errorReader = process.errorStream.bufferedReader()
    thread {
        while (process.isAlive) {
            val line = reader.readLine() ?: break
            Napier.i(tag = "init") {
                line
            }
        }
    }
    thread {
        while (process.isAlive) {
            val line = errorReader.readLine() ?: break
            Napier.e(tag = "init") {
                line
            }
        }
    }
    Napier.i(tag = "init") {
        "started"
    }
    try {
        val code = process.waitFor()
        reader.readLine()?.let {
            Napier.i(tag = "init") {
                it
            }
        }
        errorReader.readLine()?.let {
            Napier.i(tag = "init") {
                it
            }
        }
        Napier.i(tag = "init") {
            "finished. code: $code"
        }
        continuation.resume(code)
    } catch (e: Exception) {
        Napier.e(tag = "init", throwable = e) {
            "failed"
        }
        continuation.resumeWithException(e)
    } finally {
        reader.close()
        errorReader.close()
        process.destroy()
    }
}

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
                createKeystore(snapshotKeyStorePassword.toCharArray(), snapshotKeyStorePath)
            }
            CustomKeyStore(snapshotKeyStorePath, snapshotKeyStorePassword)
        } else {
            null
        }
    val customConfig = CustomConfig(buildType, flavor, snapshotKeyStore)

    return Backend(
        customConfig,
        topicSearchService,
        roomSearchService,
        communitySearchService,
        userSearchService,
        mediaService,
        NameService(),
        buildExposedDatabase(databaseConnection)
    )
}

fun createKeystore(keystorePassword: CharArray, path: String) {
    val file = File(path)
    if (!file.parentFile.exists() && !file.parentFile.mkdirs()) {
        throw Exception("can not create parent file $path")
    }
    val alias = "snapshot"
    val validityDays = 365L

    // 注册 BouncyCastle 提供者（如未自动加载）
    Security.addProvider(BouncyCastleProvider())

    // 1. 生成密钥对
    val keyPairGen = KeyPairGenerator.getInstance("RSA", "BC")
    keyPairGen.initialize(2048, SecureRandom())
    val keyPair: KeyPair = keyPairGen.generateKeyPair()

    // 2. 证书信息
    val issuer = X500Name("CN=Example, OU=Org, O=Company, L=City, ST=State, C=US")
    val subject = issuer // 自签名
    val serial = BigInteger(160, SecureRandom())
    val notBefore = Date(System.currentTimeMillis())
    val notAfter = Date(System.currentTimeMillis() + validityDays * 24 * 60 * 60 * 1000)

    // 3. 构建 X509 证书
    val certBuilder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
        issuer,
        serial,
        notBefore,
        notAfter,
        subject,
        keyPair.public
    )

    // 4. 生成签名
    val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
    val certHolder = certBuilder.build(signer)
    val cert: X509Certificate = JcaX509CertificateConverter()
        .setProvider("BC")
        .getCertificate(certHolder)

    // 5. 创建 PKCS12 keystore 并存储证书和私钥
    val ks = KeyStore.getInstance("PKCS12")
    ks.load(null, null)
    ks.setKeyEntry(alias, keyPair.private, keystorePassword, arrayOf(cert))

    // 6. 保存 keystore 到文件
    FileOutputStream(file).use { fos ->
        ks.store(fos, keystorePassword)
    }
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
