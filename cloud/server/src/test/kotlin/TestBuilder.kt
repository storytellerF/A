import com.github.vertical_blank.sqlformatter.SqlFormatter
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.a.server.module
import com.storyteller_f.backend.service.readResourceEnv
import com.storyteller_f.shared.generateECDSAPemPrivateKey
import com.storyteller_f.shared.kmpLogger
import com.storyteller_f.shared.loadCryptoLibIfNeed
import com.storyteller_f.shared.obj.ExplainResult
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.md5
import io.github.aakira.napier.Napier
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.elasticsearch.ElasticsearchContainer
import java.io.File
import java.net.ServerSocket
import java.net.SocketTimeoutException
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun test(overrideEnv: Map<String, String> = emptyMap(), block: suspend ApplicationTestBuilder.() -> Unit) {
    Napier.base(kmpLogger)
    val freeMemory = Runtime.getRuntime().freeMemory() / (1024 * 1024)
    Napier.i {
        "free ${freeMemory}MiB"
    }
    SnowflakeFactory.setMachine(0)
    loadCryptoLibIfNeed()

    startInMemoryTest(overrideEnv, block)

    if (freeMemory >= 100) {
//        startTestContainerTest(receivedFrame, true, block)
        startTestContainerTest(false, block)
    }
    Napier.i {
        "test done"
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun startInMemoryTest(
    overrideEnv: Map<String, String>,
    block: suspend (ApplicationTestBuilder) -> Unit
) {
    val env = readResourceEnv(".env")!! + Pair(
        "DATABASE_URI",
        "jdbc:h2:mem:${Uuid.random().toHexString()};DB_CLOSE_DELAY=-1;"
    ) + overrideEnv
    doTest(env, block)
}

private fun startTestContainerTest(
    @Suppress("SameParameterValue") databaseTypeIsMysql: Boolean,
    block: suspend ApplicationTestBuilder.() -> Unit
) {
    runBlocking {
        val env = readResourceEnv(".env")!!.toMutableMap()
        ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:8.17.0"
        )
            // disable SSL
            .withEnv("xpack.security.transport.ssl.enabled", "false")
            .withEnv("xpack.security.http.ssl.enabled", "false").use { elasticClient ->
                elasticClient.start()
                env["SEARCH_SERVICE"] = "elastic"
                env["ELASTIC_NAME"] = "elastic"
                env["ELASTIC_PASSWORD"] = "changeme"
                env["ELASTIC_URL"] = "http://${elasticClient.httpHostAddress}"
                MinIOContainer(
                    "minio/minio:RELEASE.2024-12-18T13-15-44Z"
                )
                    .use { minioContainer ->
                        minioContainer.start()
                        env["MEDIA_SERVICE"] = "minio"
                        env["MINIO_URL"] = minioContainer.s3URL
                        env["MINIO_NAME"] = minioContainer.userName
                        env["MINIO_PASS"] = minioContainer.password
                        if (databaseTypeIsMysql) {
                            MySQLContainer(
                                "mysql:8.0"
                            ).withUrlParam("characterEncoding", "utf8")
                                .withUrlParam("useUnicode", "true")
                                .withUrlParam("connectionCollation", "utf8mb4_unicode_ci").use { mySQLContainer ->
                                    mySQLContainer.start()
                                    println("jdbc: ${mySQLContainer.jdbcUrl}")
                                    env["DATABASE_URI"] = mySQLContainer.jdbcUrl
                                    env["DATABASE_DRIVER"] = mySQLContainer.driverClassName
                                    env["DATABASE_USER"] = mySQLContainer.username
                                    env["DATABASE_PASS"] = mySQLContainer.password
                                    env["DATABASE_DB"] = mySQLContainer.databaseName
                                    doTest(env, block)
                                }
                        } else {
                            PostgreSQLContainer(
                                "pgvector/pgvector:pg16"
                            ).use { postgreSQLContainer ->
                                postgreSQLContainer.start()
                                println("jdbc: ${postgreSQLContainer.jdbcUrl}")
                                env["DATABASE_URI"] = postgreSQLContainer.jdbcUrl
                                env["DATABASE_DRIVER"] = postgreSQLContainer.driverClassName
                                env["DATABASE_USER"] = postgreSQLContainer.username
                                env["DATABASE_PASS"] = postgreSQLContainer.password
                                env["DATABASE_DB"] = postgreSQLContainer.databaseName
                                doTest(env, block)
                            }
                        }
                    }
            }
    }
}

private fun doTest(
    env: Map<String, String>,
    block: suspend (ApplicationTestBuilder) -> Unit
) {
    val json = Json {
        ignoreUnknownKeys = true
    }
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
            val job = launch {
                suspendCoroutine { continuation ->
                    thread {
                        ServerSocket(8888).apply {
                            soTimeout = 1000 // 5秒超时
                        }.use { serverSocket ->
                            task.complete(Unit)
                            while (isActive) {
                                try {
                                    serverSocket.accept().use { socket ->
                                        socket.getInputStream().bufferedReader().use {
                                            val explainResult = json.decodeFromString<ExplainResult>(it.readText())
                                            saveDatabaseExplainResult(explainResult)
                                        }
                                    }
                                } catch (_: SocketTimeoutException) {
                                } catch (e: Exception) {
                                    Napier.e(throwable = e) {
                                        "server socket error"
                                    }
                                }
                            }
                            Napier.i {
                                "server socket closed"
                            }
                            continuation.resume(Unit)
                        }
                    }
                }
                Napier.i {
                    "server socket job finished"
                }
            }
            task.await()
            block(this@testApplication)
            job.cancel()
        }
    }
}

fun saveDatabaseExplainResult(explainResult: ExplainResult) {
    val (dialect, statements, result, stackTraceStrig) = explainResult
    val file = File(
        "./build/test/$dialect/${extractTableNames(statements).joinToString("/")}/${md5(statements)}.explain"
    )
    file.parentFile?.let {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
    val newText = "${SqlFormatter.format(statements)}\n\n$result\n\n$stackTraceStrig"
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
    onReceive: suspend (RoomFrame, SessionModel) -> Unit = { _, _ -> },
    block: suspend SessionManager.(SessionTuple) -> R
): SessionOuterTuple<R> {
    return coroutineScope {
        val sessionManager = createUserSessionManager("link", { model, client ->
            createClient {
                defaultClientConfigure(client, model)
            }
        }, onReceive)
        sessionManager.start {
            val sessionModel = sessionModel
            val priKey = generateECDSAPemPrivateKey().getOrThrow()
            val (rawUserPassInfo, userInfo) = signUpOrInFromPrivateKey(priKey, this, true)
            sessionModel.updateState(ClientSessionState.Success(RawUserPass(rawUserPassInfo)))
            val custom = block(SessionTuple(priKey, userInfo.id))
            signOut().getOrThrow()
            sessionModel.clear()
            SessionOuterTuple(priKey, userInfo.id, custom)
        }
    }
}

suspend fun <R1, R2> ApplicationTestBuilder.loginSession(
    session: SessionOuterTuple<R1>,
    onReceive: suspend (RoomFrame, SessionModel) -> Unit = { _, _ -> },
    block: suspend SessionManager.(SessionTuple) -> R2
): SessionOuterTuple<R2> {
    return coroutineScope {
        val sessionManager = createUserSessionManager("link", { model, client ->
            createClient {
                defaultClientConfigure(client, model)
            }
        }, onReceive)
        sessionManager.start {
            val (privateKey) = session
            val sessionModel = sessionModel
            val (rawUserPass, userInfo) = signUpOrInFromPrivateKey(privateKey, this, false)
            assertEquals(session.uid, userInfo.id)
            sessionModel.updateState(ClientSessionState.Success(RawUserPass(rawUserPass)))
            val custom = block(SessionTuple(session.privateKey, session.uid))
            signOut().getOrThrow()
            sessionModel.clear()
            SessionOuterTuple(privateKey, userInfo.id, custom)
        }
    }
}

suspend fun <R2> ApplicationTestBuilder.noneSession(
    block: suspend SessionManager.() -> R2
): R2 {
    return coroutineScope {
        val sessionManager = createUserSessionManager("link", { model, client ->
            createClient {
                defaultClientConfigure(client, model)
            }
        }) { _, _ -> }
        sessionManager.start {
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

inline fun <T> assertResponse(count: Int, result: Result<ServerResponse<T>>, block: (ServerResponse<T>) -> Unit) {
    assertListSize(count, result)
    block(result.getOrThrow())
}

fun extractTableNames(query: String): List<String> {
    val regex = Regex("(?i)\\bFROM\\s+([a-zA-Z0-9_.]+)|\\bJOIN\\s+([a-zA-Z0-9_.]+)")
    return regex.findAll(query).flatMap { it.groupValues.drop(1).filter { name -> name.isNotEmpty() } }.toList()
}
