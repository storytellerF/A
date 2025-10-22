package com.storyteller_f.a.cloud.server

import com.github.vertical_blank.sqlformatter.SqlFormatter
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.loadAvif
import com.storyteller_f.a.client.core.PanelSessionManager
import com.storyteller_f.a.client.core.RawUserPass
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.UserSessionModel
import com.storyteller_f.a.client.core.createPanelSessionManager
import com.storyteller_f.a.client.core.createUserSessionManager
import com.storyteller_f.a.client.core.defaultClientConfigure
import com.storyteller_f.a.client.core.defaultClientConfigureForPanel
import com.storyteller_f.a.client.core.getPanelAccountInfo
import com.storyteller_f.a.client.core.getUserInfo
import com.storyteller_f.a.client.core.signOut
import com.storyteller_f.a.client.core.startBackgroundTask
import com.storyteller_f.shared.commonJson
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.loadCryptoLibIfNeed
import com.storyteller_f.shared.obj.ExplainResult
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.setupKmpLogger
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.md5
import io.github.aakira.napier.Napier
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.elasticsearch.ElasticsearchContainer
import java.io.File
import java.net.ServerSocket
import java.net.SocketTimeoutException
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun test(
    overrideEnv: Map<String, String> = emptyMap(),
    block: suspend ApplicationTestBuilder.() -> Unit
) {
    val logPath = File("build/test/logs", Uuid.random().toHexString()).canonicalPath
    System.setProperty("LOG_PATH", logPath)
    setupKmpLogger()
    SnowflakeFactory.setMachine(0)
    loadCryptoLibIfNeed()
    loadAvif()
    val methodName = Exception().stackTrace[2].methodName
    Napier.i {
        "start test `$methodName`"
    }
    startMemoryTest(
        overrideEnv + mapOf(
            "SERVER_URL" to "http://localhost",
            "METHOD_NAME" to methodName
        ),
        block
    )
    if (System.getenv("ENABLE_TEST_CONTAINER") == "true") {
        startTestContainerTest(overrideEnv + mapOf("METHOD_NAME" to methodName), false, block)
    }
    Napier.i {
        "test done `$methodName`"
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun startMemoryTest(
    overrideEnv: Map<String, String>,
    block: suspend (ApplicationTestBuilder) -> Unit
) {
//    val h2File = File("./build/test/h2/${Uuid.random().toHexString()}")
//    h2File.parentFile!!.let {
//        if (!it.exists() && !it.mkdirs()) {
//            throw Exception("mkdirs failed ${it.canonicalPath}")
//        }
//    }
//    val url = "r2dbc:h2:file:///${h2File.path.replace("\\", "/")}"
    val url = "r2dbc:h2:mem:///${Uuid.random().toHexString()}"
    val env = mapOf(
        "DATABASE_URI" to url,
        "DATABASE_DRIVER" to "h2"
    ) + overrideEnv
    doTest(env, block)
}

private fun startTestContainerTest(
    overrideEnv: Map<String, String>,
    @Suppress("SameParameterValue") databaseTypeIsMysql: Boolean,
    block: suspend ApplicationTestBuilder.() -> Unit
) {
    runBlocking {
        val env = mutableMapOf<String, String>()
        useElasticTestContainer(env) {
            useMinioTestContainer(env) {
                useDatabaseContainer(databaseTypeIsMysql, env) {
                    doTest(env + overrideEnv, block)
                }
            }
        }
    }
}

private suspend fun useDatabaseContainer(
    databaseTypeIsMysql: Boolean,
    env: MutableMap<String, String>,
    block: suspend () -> Unit
) {
    if (databaseTypeIsMysql) {
        MySQLContainer(
            "mysql:8.0"
        ).withUrlParam("characterEncoding", "utf8")
            .withUrlParam("useUnicode", "true")
            .withUrlParam("connectionCollation", "utf8mb4_unicode_ci")
            .use { mySQLContainer ->
                mySQLContainer.start()
                println("jdbc: ${mySQLContainer.jdbcUrl}")
                env["DATABASE_URI"] = mySQLContainer.jdbcUrl
                env["DATABASE_DRIVER"] = mySQLContainer.driverClassName
                env["DATABASE_USER"] = mySQLContainer.username
                env["DATABASE_PASS"] = mySQLContainer.password
                env["DATABASE_DB"] = mySQLContainer.databaseName
                block()
            }
    } else {
        PostgreSQLContainer(
            "pgvector/pgvector:pg16"
        ).use { postgreSQLContainer ->
            postgreSQLContainer.start()
            Napier.i("jdbc: ${postgreSQLContainer.jdbcUrl}")
            env["DATABASE_URI"] =
                postgreSQLContainer.jdbcUrl.replace("jdbc", "r2dbc")
            env["DATABASE_DRIVER"] = "postgresql"
            env["DATABASE_USER"] = postgreSQLContainer.username
            env["DATABASE_PASS"] = postgreSQLContainer.password
            env["DATABASE_DB"] = postgreSQLContainer.databaseName
            block()
        }
    }
}

private suspend fun useMinioTestContainer(
    env: MutableMap<String, String>,
    block: suspend () -> Unit
) {
    MinIOContainer(
        "minio/minio:RELEASE.2024-12-18T13-15-44Z"
    )
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
    env: Map<String, String>,
    block: suspend ApplicationTestBuilder.() -> Unit
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
        coroutineScope {
            val task = CompletableDeferred<Unit>()
            val port = env["port"]?.toIntOrNull()
            if (port != null) {
                val job = launch(Dispatchers.IO) {
                    receiveExplainResult(task, port)
                }
                task.await()
                block()
                job.cancel()
            } else {
                block()
            }
        }
    }
}

private suspend fun receiveExplainResult(
    task: CompletableDeferred<Unit>,
    port: Int,
) {
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
                        saveDatabaseExplainResult(explainResult)
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

fun saveDatabaseExplainResult(explainResult: ExplainResult) {
    val (dialect, statements, result, stackTraceString) = explainResult
    val file = File(
        "./build/test/$dialect/${extractTableNames(statements).joinToString("/")}/${md5(statements)}.explain"
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
    val privateKey: String,
    val uid: PrimaryKey
)

data class SessionOuterTuple<T>(
    val privateKey: String,
    val uid: PrimaryKey,
    val custom: T
)

suspend fun <R> ApplicationTestBuilder.attachSession(
    onReceive: suspend (RoomFrame, UserSessionModel, DefaultClientWebSocketSession) -> Unit = { _, _, _ -> },
    block: suspend UserSessionManager.(SessionTuple) -> R
): SessionOuterTuple<R> {
    val priKey = getAlgo().generateECDSAPemPrivateKey().getOrThrow()
    return getAppSession(onReceive, priKey, block, true)
}

private suspend fun <R> ApplicationTestBuilder.getAppSession(
    onReceive: suspend (RoomFrame, UserSessionModel, DefaultClientWebSocketSession) -> Unit,
    priKey: String,
    block: suspend UserSessionManager.(SessionTuple) -> R,
    isSignUp: Boolean
): SessionOuterTuple<R> {
    return coroutineScope {
        val sessionManager = createUserSessionManager("link", { model, cookiesStorage ->
            createClient {
                defaultClientConfigure(cookiesStorage, model)
            }
        }, onReceive)
        sessionManager.startBackgroundTask {
            val sessionModel = model
            val userInfo = getUserInfo(priKey, isSignUp) {
                RawUserPass(it)
            }
            val custom = block(SessionTuple(priKey, userInfo.id))
            signOut().getOrThrow()
            sessionModel.clear()
            SessionOuterTuple(priKey, userInfo.id, custom)
        }
    }
}

suspend fun <R1, R2> ApplicationTestBuilder.loginSession(
    tuple: SessionOuterTuple<R1>,
    onReceive: suspend (RoomFrame, UserSessionModel, DefaultClientWebSocketSession) -> Unit = { _, _, _ -> },
    block: suspend UserSessionManager.(SessionTuple) -> R2
): SessionOuterTuple<R2> {
    return getAppSession(onReceive, tuple.privateKey, block, false)
}

suspend fun <R2> ApplicationTestBuilder.noneSession(
    block: suspend UserSessionManager.() -> R2
): R2 {
    return coroutineScope {
        val sessionManager = createUserSessionManager("link", { model, cookiesStorage ->
            createClient {
                defaultClientConfigure(cookiesStorage, model)
            }
        }) { _, _, _ -> }
        sessionManager.startBackgroundTask {
            block()
        }
    }
}

fun <T> assertListSize(count: Int, result: Result<ServerResponse<T>>) {
    assertEquals(count, result.getOrThrow().data.size)
}

fun <T> assertListTotalSize(count: Int, result: Result<ServerResponse<T>>) {
    assertEquals(count.toLong(), result.getOrThrow().pagination?.total)
}

fun extractTableNames(query: String): List<String> {
    val regex = Regex("(?i)\\bFROM\\s+([a-zA-Z0-9_.]+)|\\bJOIN\\s+([a-zA-Z0-9_.]+)")
    return regex.findAll(query)
        .flatMap { it.groupValues.drop(1).filter { name -> name.isNotEmpty() } }.toList()
}

suspend fun UserSessionManager.waitAndSend(block: suspend DefaultClientWebSocketSession.() -> Unit) {
    while (true) {
        if (webSocketClient.connectionHandler.data.value != null) {
            break
        }
        withContext(Dispatchers.IO) {
            delay(100)
        }
    }
    webSocketClient.useWebSocket(block)?.join()
}

suspend fun <R> ApplicationTestBuilder.attachPanelSession(
    block: suspend PanelSessionManager.(SessionTuple) -> R
): SessionOuterTuple<R> {
    val priKey = getAlgo().generateECDSAPemPrivateKey().getOrThrow()
    return getPanelSession(priKey, block, true)
}

private suspend fun <R> ApplicationTestBuilder.getPanelSession(
    priKey: String,
    block: suspend PanelSessionManager.(SessionTuple) -> R,
    isSignUp: Boolean
): SessionOuterTuple<R> {
    return coroutineScope {
        val sessionManager = createPanelSessionManager({ model, cookiesStorage ->
            createClient {
                defaultClientConfigureForPanel(cookiesStorage, model)
            }
        })
        val sessionModel = sessionManager.model
        val userInfo = sessionManager.getPanelAccountInfo(priKey, isSignUp) {
            RawUserPass(it)
        }
        val custom = sessionManager.block(SessionTuple(priKey, userInfo.id))
        sessionManager.signOut().getOrThrow()
        sessionModel.clear()
        SessionOuterTuple(priKey, userInfo.id, custom)
    }
}

suspend fun <R1, R2> ApplicationTestBuilder.loginPanelSession(
    tuple: SessionOuterTuple<R1>,
    block: suspend PanelSessionManager.(SessionTuple) -> R2
): SessionOuterTuple<R2> {
    return getPanelSession(tuple.privateKey, block, false)
}
