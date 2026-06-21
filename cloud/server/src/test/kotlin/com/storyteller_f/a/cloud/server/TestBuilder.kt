package com.storyteller_f.a.cloud.server

import com.github.vertical_blank.sqlformatter.SqlFormatter
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.loadAvif
import com.storyteller_f.a.backend.core.readEnv
import com.storyteller_f.a.client.core.AuthKey
import com.storyteller_f.a.client.core.ConstPassHolder
import com.storyteller_f.a.client.core.PanelSessionManager
import com.storyteller_f.a.client.core.SimplePassHolder
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.UserSessionModel
import com.storyteller_f.a.client.core.createSimplePanelSessionManager
import com.storyteller_f.a.client.core.createSimpleUserSessionManager
import com.storyteller_f.a.client.core.defaultClientConfigure
import com.storyteller_f.a.client.core.defaultClientConfigureForPanel
import com.storyteller_f.a.client.core.getAuthKey
import com.storyteller_f.a.client.core.onBackgroundTask
import com.storyteller_f.a.client.core.panelSignIn
import com.storyteller_f.a.client.core.panelSignUp
import com.storyteller_f.a.client.core.signOut
import com.storyteller_f.a.client.core.userSignIn
import com.storyteller_f.a.client.core.userSignUp
import com.storyteller_f.a.cloud.worker.WorkerBackend
import com.storyteller_f.a.cloud.worker.buildBackendFromEnv
import com.storyteller_f.shared.commonJson
import com.storyteller_f.shared.loadCryptoLibIfNeed
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.PanelAccountInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.ExplainResult
import com.storyteller_f.shared.obj.ListResponse
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.setupKmpLogger
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.md5
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.elasticsearch.ElasticsearchContainer
import java.io.File
import java.net.ServerSocket
import java.net.SocketTimeoutException
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import com.storyteller_f.a.cloud.ws.module as wsModule

private const val TEST_WS_URL = "ws://localhost/link"
private const val TEST_SESSION_SECRET = "test-session-secret"

private typealias TestRoomReceiver = suspend (
    RoomFrame,
    UserSessionModel,
    DefaultClientWebSocketSession
) -> Unit

private val NOOP_ON_RECEIVE: TestRoomReceiver = { _, _, _ -> }

@OptIn(ExperimentalUuidApi::class)
class TestMate(
    val applicationTestBuilder: ApplicationTestBuilder,
    val workerBackend: WorkerBackend
) {

    fun createClient(block: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit = {}): HttpClient {
        return applicationTestBuilder.createClient(block)
    }
}

@OptIn(ExperimentalUuidApi::class)
fun test(
    overrideEnv: Map<String, String> = emptyMap(),
    block: suspend TestMate.() -> Unit
) {
    val uuid = Uuid.random().toHexString()
    val logPath = File("build/test/session/$uuid/logs").canonicalPath
    System.setProperty("LOG_PATH", logPath)
    setupKmpLogger()
    SnowflakeFactory.setMachine(0)
    loadCryptoLibIfNeed()
    loadAvif()
    val traceElements = Exception().stackTrace
    val methodNameIndex = traceElements.indexOfFirst {
        it.fileName != "TestBuilder.kt"
    }
    if (methodNameIndex < 0) {
        throw Exception("test not found")
    }
    val methodName = traceElements[methodNameIndex + 1].methodName
    Napier.i {
        "start test `$methodName` $uuid"
    }
    System.setProperty("api.version", "1.44")
    startTestContainerTest(
        uuid,
        mapOf(
            "SESSION_SECRET" to TEST_SESSION_SECRET,
            "METHOD_NAME" to methodName
        ) + overrideEnv,
        block
    )
    Napier.i {
        "test done `$methodName`"
    }
}

private fun startTestContainerTest(
    uuid: String,
    overrideEnv: Map<String, String>,
    block: suspend TestMate.() -> Unit
) {
    runBlocking {
        val env = mutableMapOf<String, String>()
        useElasticTestContainer(env) {
            useMinioTestContainer(env) {
                useDatabaseContainer(env) {
                    doTest(uuid, env + overrideEnv, block)
                }
            }
        }
    }
}

private suspend fun useDatabaseContainer(
    env: MutableMap<String, String>,
    block: suspend () -> Unit
) {
    PostgreSQLContainer(
        "pgvector/pgvector:pg16"
    ).use { postgreSQLContainer ->
        postgreSQLContainer.start()
        Napier.i("jdbc: ${postgreSQLContainer.jdbcUrl}")
        env["DATABASE_URI"] = postgreSQLContainer.jdbcUrl.replace("jdbc", "r2dbc")
        env["DATABASE_DRIVER"] = "postgresql"
        env["DATABASE_USER"] = postgreSQLContainer.username
        env["DATABASE_PASS"] = postgreSQLContainer.password
        env["DATABASE_DB"] = postgreSQLContainer.databaseName
        block()
    }
}

private suspend fun useMinioTestContainer(
    env: MutableMap<String, String>,
    block: suspend () -> Unit
) {
    MinIOContainer("minio/minio:RELEASE.2024-12-18T13-15-44Z")
        .use { minioContainer ->
            minioContainer.start()
            env["MEDIA_SERVICE"] = "minio"
            env["MINIO_URL"] = minioContainer.s3URL
            env["MINIO_NAME"] = minioContainer.userName
            env["MINIO_PASS"] = minioContainer.password
            block()
        }
}

private suspend fun useElasticTestContainer(
    env: MutableMap<String, String>,
    block: suspend () -> Unit
) {
    ElasticsearchContainer(
        "docker.elastic.co/elasticsearch/elasticsearch:8.17.0"
    ).withEnv("xpack.security.transport.ssl.enabled", "false")
        .withEnv("xpack.security.http.ssl.enabled", "false").use { elasticClient ->
            elasticClient.start()
            env["SEARCH_SERVICE"] = "elastic"
            env["ELASTIC_NAME"] = "elastic"
            env["ELASTIC_PASSWORD"] = "changeme"
            env["ELASTIC_URL"] = "http://${elasticClient.httpHostAddress}"
            block()
        }
}

private fun doTest(
    uuid: String,
    env: Map<String, String>,
    block: suspend TestMate.() -> Unit
) {
    testApplication {
        environment {
            config = MapApplicationConfig().apply {
                env.forEach {
                    put(it.key, it.value)
                }
            }
        }
        application {
            module()
        }
        externalServices {
            hosts("ws://localhost") {
                wsModule()
            }
        }
        val port = env["port"]?.toIntOrNull()

        val backend = buildBackendFromEnv(readEnv(env))
        val workerBackend = backend as WorkerBackend
        val testMate = TestMate(this@testApplication, workerBackend)
        if (port != null) {
            coroutineScope {
                val task = CompletableDeferred<Unit>()
                val job = launch(Dispatchers.IO) {
                    receiveExplainResult(task, port, uuid)
                }
                task.await()
                testMate.block()
                job.cancel()
            }
        } else {
            testMate.block()
        }
    }
}

private suspend fun receiveExplainResult(
    task: CompletableDeferred<Unit>,
    port: Int,
    uuid: String,
) {
    withContext(Dispatchers.IO) {
        ServerSocket(port).apply {
            soTimeout = 1000
        }.use { serverSocket ->
            task.complete(Unit)
            while (true) {
                try {
                    yield()
                    serverSocket.accept().use { socket ->
                        socket.getInputStream().bufferedReader().use {
                            val explainResult =
                                commonJson.decodeFromString<ExplainResult>(it.readText())
                            saveDatabaseExplainResult(explainResult, uuid)
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    break
                } catch (_: CancellationException) {
                    break
                } catch (e: Exception) {
                    Napier.e(throwable = e) {
                        "server socket error"
                    }
                    break
                }
            }
            Napier.i {
                "server socket done"
            }
        }
    }
}

fun saveDatabaseExplainResult(explainResult: ExplainResult, uuid: String) {
    val (dialect, statements, result, stackTraceString) = explainResult
    val file = File(
        "./build/test/session/$uuid/$dialect/${extractTableNames(
            statements
        ).joinToString("/")}/${md5(statements)}.explain"
    )
    file.parentFile!!.let {
        if (!it.exists() && !it.mkdirs()) {
            throw Exception("mkdirs failed ${it.canonicalPath}")
        }
    }
    val newText = "${SqlFormatter.format(statements)}\n\n$result\n\n$stackTraceString"
    if (!file.exists() || file.readText() != newText) {
        file.writeText(newText)
    }
}

data class SessionTuple(
    val authKey: AuthKey,
    val uid: PrimaryKey
)

data class SessionOuterTuple<T>(
    val authKey: AuthKey,
    val uid: PrimaryKey,
    val custom: T
)

suspend fun <R> TestMate.attachSession(
    algo: AlgoType = AlgoType.P256,
    onReceive: suspend (RoomFrame, UserSessionModel, DefaultClientWebSocketSession) -> Unit = NOOP_ON_RECEIVE,
    block: suspend UserSessionManager.(SessionTuple) -> R
): SessionOuterTuple<R> {
    val authKey = getAuthKey(algo)
    return getAppSignUpSession(authKey, onReceive, block)
}

suspend fun TestMate.attachSession(): SessionOuterTuple<Unit> {
    return attachSession(onReceive = NOOP_ON_RECEIVE, block = {})
}

suspend fun <R> TestMate.getAppSession(
    authKey: AuthKey,
    onReceive: suspend (RoomFrame, UserSessionModel, DefaultClientWebSocketSession) -> Unit,
    block: suspend UserSessionManager.(SessionTuple) -> R,
    getUserInfo: suspend UserSessionManager.(SimplePassHolder) -> UserInfo
): SessionOuterTuple<R> {
    return coroutineScope {
        val passHolder = SimplePassHolder()
        val sessionManager = createSimpleUserSessionManager(
            TEST_WS_URL,
            AcceptAllCookiesStorage(),
            passHolder,
            { model, cookiesStorage ->
                createClient {
                    defaultClientConfigure(cookiesStorage, model, passHolder)
                }
            },
            onReceive
        )
        sessionManager.onBackgroundTask {
            try {
                val sessionModel = sessionManager.model
                val userInfo = sessionManager.getUserInfo(passHolder)
                val custom = sessionManager.block(SessionTuple(authKey, userInfo.id))
                sessionManager.signOut().getOrThrow()
                sessionModel.clear()
                SessionOuterTuple(authKey, userInfo.id, custom)
            } finally {
                sessionManager.client.coroutineContext[Job]?.cancelAndJoin()
            }
        }
    }
}

suspend fun <R> TestMate.getAppSignUpSession(
    authKey: AuthKey,
    onReceive: suspend (RoomFrame, UserSessionModel, DefaultClientWebSocketSession) -> Unit,
    block: suspend UserSessionManager.(SessionTuple) -> R,
): SessionOuterTuple<R> {
    return getAppSession(authKey, onReceive, block) {
        this.userSignUp(authKey, it)
    }
}

suspend fun <R> TestMate.getAppSignInSession(
    authKey: AuthKey,
    onReceive: suspend (RoomFrame, UserSessionModel, DefaultClientWebSocketSession) -> Unit,
    block: suspend UserSessionManager.(SessionTuple) -> R,
): SessionOuterTuple<R> {
    return getAppSession(authKey, onReceive, block) {
        this.userSignIn(authKey, it)
    }
}

suspend fun <R1, R2> TestMate.loginSession(
    tuple: SessionOuterTuple<R1>,
    onReceive: suspend (RoomFrame, UserSessionModel, DefaultClientWebSocketSession) -> Unit = NOOP_ON_RECEIVE,
    block: suspend UserSessionManager.(SessionTuple) -> R2
): SessionOuterTuple<R2> {
    return getAppSignInSession(tuple.authKey, onReceive = onReceive, block = block)
}

suspend fun <R2> TestMate.noneSession(
    block: suspend UserSessionManager.() -> R2
): R2 {
    return coroutineScope {
        val passHolder = ConstPassHolder(null)
        val sessionManager = createSimpleUserSessionManager(
            TEST_WS_URL,
            AcceptAllCookiesStorage(),
            passHolder,
            { model, cookiesStorage ->
                createClient {
                    defaultClientConfigure(cookiesStorage, model, passHolder)
                }
            }
        ) { _, _, _ -> }
        sessionManager.onBackgroundTask {
            block()
        }
    }
}

fun <T> assertListSize(count: Int, result: Result<ListResponse<T>>) {
    assertEquals(count, result.getOrThrow().data.size)
}

fun <T> assertListTotalSize(count: Int, result: Result<ListResponse<T>>) {
    assertEquals(count.toLong(), result.getOrThrow().pagination?.total)
}

fun extractTableNames(query: String): List<String> {
    val regex = Regex("(?i)\\bFROM\\s+([a-zA-Z0-9_.]+)|\\bJOIN\\s+([a-zA-Z0-9_.]+)")
    return regex.findAll(query)
        .flatMap { it.groupValues.drop(1).filter { name -> name.isNotEmpty() } }.toList()
}

suspend fun <T> UserSessionManager.waitAndSend(block: suspend DefaultClientWebSocketSession.() -> T): Result<T> {
    while (true) {
        if (webSocketClient.connectionHandler.data.value != null) {
            break
        }
        withContext(Dispatchers.IO) {
            delay(100.milliseconds)
        }
    }
    return webSocketClient.useWebSocket(block)
}

suspend fun <R> TestMate.attachPanelSession(
    algo: AlgoType = AlgoType.P256,
    block: suspend PanelSessionManager.(SessionTuple) -> R
): SessionOuterTuple<R> {
    val authKey = getAuthKey(algo)
    return getPanelSession(authKey, block) {
        this.panelSignUp(authKey, it)
    }
}

suspend fun TestMate.attachPanelSession(): SessionOuterTuple<Unit> {
    return attachPanelSession { }
}

private suspend fun <R> TestMate.getPanelSession(
    authKey: AuthKey,
    block: suspend PanelSessionManager.(SessionTuple) -> R,
    getUserInfo: suspend PanelSessionManager.(SimplePassHolder) -> PanelAccountInfo,
): SessionOuterTuple<R> {
    return coroutineScope {
        val passHolder = SimplePassHolder()
        val sessionManager = createSimplePanelSessionManager(passHolder) { model, cookiesStorage ->
            createClient {
                defaultClientConfigureForPanel(cookiesStorage, model, passHolder)
            }
        }
        val sessionModel = sessionManager.model
        val userInfo = getUserInfo(sessionManager, passHolder)
        val custom = sessionManager.block(SessionTuple(authKey, userInfo.id))
        sessionManager.signOut().getOrThrow()
        sessionModel.clear()
        SessionOuterTuple(authKey, userInfo.id, custom)
    }
}

suspend fun <R1, R2> TestMate.loginPanelSession(
    tuple: SessionOuterTuple<R1>,
    block: suspend PanelSessionManager.(SessionTuple) -> R2
): SessionOuterTuple<R2> {
    return getPanelSession(tuple.authKey, block = block) {
        this.panelSignIn(tuple.authKey, it)
    }
}

suspend fun <R> TestMate.withWorkerBackend(
    block: suspend (WorkerBackend) -> R
): R {
    return block(workerBackend)
}

suspend fun <R> TestMate.withCliBackend(
    block: suspend (WorkerBackend) -> R
): R {
    return block(workerBackend)
}
