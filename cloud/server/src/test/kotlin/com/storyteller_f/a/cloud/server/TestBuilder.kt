/*
 * This is a private project. All rights reserved.
 */

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
import com.storyteller_f.a.test.createTestArtifactDirectory
import com.storyteller_f.a.test.recordTestArtifacts
import com.storyteller_f.a.test.testArtifactRoot
import com.storyteller_f.a.test.useBaseServiceFixture
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
import java.io.File
import java.net.ServerSocket
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import com.storyteller_f.a.cloud.ws.module as wsModule

private const val TEST_WS_URL = "ws://localhost/ws"
private const val TEST_SESSION_SECRET = "test-session-secret"

private typealias TestRoomReceiver = suspend (
    RoomFrame,
    UserSessionModel,
    DefaultClientWebSocketSession,
) -> Unit

private val NOOP_ON_RECEIVE: TestRoomReceiver = { _, _, _ -> }

@OptIn(ExperimentalUuidApi::class)
class TestMate(val applicationTestBuilder: ApplicationTestBuilder, val workerBackend: WorkerBackend) {
    fun createClient(
        block: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit = {
        },
    ): HttpClient = applicationTestBuilder.createClient(block)
}

@OptIn(ExperimentalUuidApi::class)
fun test(overrideEnv: Map<String, String> = emptyMap(), block: suspend TestMate.() -> Unit) {
    val testFrame =
        Exception().stackTrace.first { frame ->
            frame.className.startsWith("com.storyteller_f.a.cloud.server.") &&
                Class.forName(frame.className).declaredMethods.any { method ->
                    method.name == frame.methodName && method.isAnnotationPresent(Test::class.java)
                }
        }
    val methodName = testFrame.methodName
    val reportDirectory =
        createTestArtifactDirectory(
            testArtifactRoot(),
            testFrame.className.substringAfterLast('.'),
            methodName,
        )
    System.setProperty("LOG_PATH", reportDirectory.resolve("logs").canonicalPath)
    setupKmpLogger()
    SnowflakeFactory.setMachine(0)
    loadCryptoLibIfNeed()
    loadAvif()
    Napier.i { "start test `$methodName`" }
    runBlocking {
        recordTestArtifacts(reportDirectory, testFrame.className.substringAfterLast('.'), methodName) {
            startTestContainerTest(
                reportDirectory,
                mapOf(
                    "SESSION_SECRET" to TEST_SESSION_SECRET,
                    "METHOD_NAME" to methodName,
                ) + overrideEnv,
                block,
            )
        }
    }
    Napier.i {
        "test done `$methodName`"
    }
}

private suspend fun startTestContainerTest(
    reportDirectory: File,
    overrideEnv: Map<String, String>,
    block: suspend TestMate.() -> Unit,
) {
    useBaseServiceFixture(reportDirectory.resolve("logs/containers")) { services ->
        doTest(reportDirectory, services.hostEnvironment + overrideEnv, block)
    }
}

private fun doTest(reportDirectory: File, env: Map<String, String>, block: suspend TestMate.() -> Unit) {
    testApplication {
        environment {
            config =
                MapApplicationConfig().apply {
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
                val job =
                    launch(Dispatchers.IO) {
                        receiveExplainResult(task, port, reportDirectory)
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

private suspend fun receiveExplainResult(task: CompletableDeferred<Unit>, port: Int, reportDirectory: File) {
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
                            saveDatabaseExplainResult(explainResult, reportDirectory)
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

fun saveDatabaseExplainResult(explainResult: ExplainResult, reportDirectory: File) {
    val dialect = explainResult.dialect
    val statements = explainResult.sql
    val result = explainResult.result
    val stackTraceString = explainResult.stackTraceString
    val file =
        File(
            reportDirectory,
            "explain/$dialect/${extractTableNames(
                statements,
            ).joinToString("/")}/${md5(statements)}.explain",
        )
    file.parentFile!!.let {
        if (!it.exists() && !it.mkdirs()) {
            error("mkdirs failed ${it.canonicalPath}")
        }
    }
    val newText =
        """
        ${SqlFormatter.format(statements)}

        $result

        $stackTraceString
        """.trimIndent()
    if (!file.exists() || file.readText() != newText) {
        file.writeText(newText)
    }
}

data class SessionTuple(val authKey: AuthKey, val uid: PrimaryKey)

data class SessionOuterTuple<T>(val authKey: AuthKey, val uid: PrimaryKey, val custom: T)

suspend fun <R> TestMate.attachSession(
    algo: AlgoType = AlgoType.P256,
    onReceive: suspend (RoomFrame, UserSessionModel, DefaultClientWebSocketSession) -> Unit = NOOP_ON_RECEIVE,
    block: suspend UserSessionManager.(SessionTuple) -> R,
): SessionOuterTuple<R> {
    val authKey = getAuthKey(algo)
    return getAppSignUpSession(authKey, onReceive, block)
}

suspend fun TestMate.attachSession(): SessionOuterTuple<Unit> = attachSession(onReceive = NOOP_ON_RECEIVE, block = {})

suspend fun <R> TestMate.getAppSession(
    authKey: AuthKey,
    onReceive: suspend (RoomFrame, UserSessionModel, DefaultClientWebSocketSession) -> Unit,
    block: suspend UserSessionManager.(SessionTuple) -> R,
    getUserInfo: suspend UserSessionManager.(SimplePassHolder) -> UserInfo,
): SessionOuterTuple<R> =
    coroutineScope {
    val passHolder = SimplePassHolder()
    val sessionManager =
        createSimpleUserSessionManager(
            TEST_WS_URL,
            AcceptAllCookiesStorage(),
            passHolder,
            { model, cookiesStorage ->
                createClient {
                    defaultClientConfigure(cookiesStorage, model)
                }
            },
            onReceive,
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

suspend fun <R> TestMate.getAppSignUpSession(
    authKey: AuthKey,
    onReceive: suspend (RoomFrame, UserSessionModel, DefaultClientWebSocketSession) -> Unit,
    block: suspend UserSessionManager.(SessionTuple) -> R,
): SessionOuterTuple<R> =
    getAppSession(authKey, onReceive, block) {
    this.userSignUp(authKey, it)
}

suspend fun <R> TestMate.getAppSignInSession(
    authKey: AuthKey,
    onReceive: suspend (RoomFrame, UserSessionModel, DefaultClientWebSocketSession) -> Unit,
    block: suspend UserSessionManager.(SessionTuple) -> R,
): SessionOuterTuple<R> =
    getAppSession(authKey, onReceive, block) {
    this.userSignIn(authKey, it)
}

suspend fun <R1, R2> TestMate.loginSession(
    tuple: SessionOuterTuple<R1>,
    onReceive: suspend (RoomFrame, UserSessionModel, DefaultClientWebSocketSession) -> Unit = NOOP_ON_RECEIVE,
    block: suspend UserSessionManager.(SessionTuple) -> R2,
): SessionOuterTuple<R2> = getAppSignInSession(tuple.authKey, onReceive = onReceive, block = block)

suspend fun <R2> TestMate.noneSession(block: suspend UserSessionManager.() -> R2): R2 =
    coroutineScope {
    val passHolder = ConstPassHolder(null)
    val sessionManager =
        createSimpleUserSessionManager(
            TEST_WS_URL,
            AcceptAllCookiesStorage(),
            passHolder,
            { model, cookiesStorage ->
                createClient {
                    defaultClientConfigure(cookiesStorage, model)
                }
            },
        ) { _, _, _ -> }
    sessionManager.onBackgroundTask {
        block()
    }
}

fun <T> assertListSize(count: Int, result: Result<ListResponse<T>>) {
    assertEquals(count, result.getOrThrow().data.size)
}

fun <T> assertListTotalSize(count: Int, result: Result<ListResponse<T>>) {
    assertEquals(count.toLong(), result.getOrThrow().pagination?.total)
}

fun extractTableNames(query: String): List<String> {
    val regex = Regex("""(?i)\bFROM\s+([a-zA-Z0-9_.]+)|\bJOIN\s+([a-zA-Z0-9_.]+)""")
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
    block: suspend PanelSessionManager.(SessionTuple) -> R,
): SessionOuterTuple<R> {
    val authKey = getAuthKey(algo)
    return getPanelSession(authKey, block) {
        this.panelSignUp(authKey, it)
    }
}

suspend fun TestMate.attachPanelSession(): SessionOuterTuple<Unit> = attachPanelSession { }

private suspend fun <R> TestMate.getPanelSession(
    authKey: AuthKey,
    block: suspend PanelSessionManager.(SessionTuple) -> R,
    getUserInfo: suspend PanelSessionManager.(SimplePassHolder) -> PanelAccountInfo,
): SessionOuterTuple<R> =
    coroutineScope {
    val passHolder = SimplePassHolder()
    val sessionManager =
        createSimplePanelSessionManager(passHolder) { model, cookiesStorage ->
            createClient {
                defaultClientConfigureForPanel(cookiesStorage, model)
            }
        }
    val sessionModel = sessionManager.model
    val userInfo = getUserInfo(sessionManager, passHolder)
    val custom = sessionManager.block(SessionTuple(authKey, userInfo.id))
    sessionManager.signOut().getOrThrow()
    sessionModel.clear()
    SessionOuterTuple(authKey, userInfo.id, custom)
}

suspend fun <R1, R2> TestMate.loginPanelSession(
    tuple: SessionOuterTuple<R1>,
    block: suspend PanelSessionManager.(SessionTuple) -> R2,
): SessionOuterTuple<R2> =
    getPanelSession(tuple.authKey, block = block) {
    this.panelSignIn(tuple.authKey, it)
}

suspend fun <R> TestMate.withWorkerBackend(block: suspend (WorkerBackend) -> R): R = block(workerBackend)

suspend fun <R> TestMate.withCliBackend(block: suspend (WorkerBackend) -> R): R = block(workerBackend)
