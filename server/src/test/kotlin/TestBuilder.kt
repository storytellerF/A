import com.github.vertical_blank.sqlformatter.SqlFormatter
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.a.server.module
import com.storyteller_f.readResourceEnv
import com.storyteller_f.shared.*
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.md5
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.websocket.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.elasticsearch.ElasticsearchContainer
import java.io.File
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun test(receivedFrame: (RoomFrame) -> Unit = {}, block: suspend (HttpClient, ClientWebSocket) -> Unit) {
    Napier.base(kmpLogger)
    val freeMemory = Runtime.getRuntime().freeMemory() / (1024 * 1024)
    Napier.i {
        "free ${freeMemory}MiB"
    }
    SnowflakeFactory.setMachine(0)
    loadIfNeed()

    startInMemoryTest(receivedFrame, block)

    if (freeMemory >= 100) {
//        startTestContainerTest(receivedFrame, true, block)
        startTestContainerTest(receivedFrame, false, block)
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun startInMemoryTest(
    receivedFrame: (RoomFrame) -> Unit,
    block: suspend (HttpClient, ClientWebSocket) -> Unit
) {
    val env = readResourceEnv(".env")!! + Pair(
        "DATABASE_URI",
        "jdbc:h2:mem:${Uuid.random().toHexString()};DB_CLOSE_DELAY=-1;"
    )
    doTest(env, receivedFrame, block)
}

private fun startTestContainerTest(
    receivedFrame: (RoomFrame) -> Unit,
    @Suppress("SameParameterValue") databaseTypeIsMysql: Boolean,
    block: suspend (HttpClient, ClientWebSocket) -> Unit
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
                                    doTest(env, receivedFrame, block)
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
                                doTest(env, receivedFrame, block)
                            }
                        }
                    }
            }
    }
}

private fun doTest(
    env: Map<String, String>,
    receivedFrame: (RoomFrame) -> Unit = {},
    block: suspend (HttpClient, ClientWebSocket) -> Unit
) {
    DatabaseFactory.enableExplain { dialect, statements, result, point ->
        val file = File(
            "./build/test/$dialect/${extractTableNames(statements).joinToString("/")}/${md5(statements)}.explain"
        )
        file.parentFile?.let {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
        val newText = "${SqlFormatter.format(statements)}\n\n$result\n\n$point"
        if (!file.exists() || file.readText() != newText) {
            file.writeText(newText)
        }
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
        val client = createClient {
            defaultClientConfigure(AcceptAllCookiesStorage())
        }
        val wsClient = ClientWebSocketImpl(client, { userInfo, sig ->
            client.webSocketSession("/link") {
                addRequestHeaders(userInfo, sig)
            }
        }) {
            receivedFrame(it)
        }

        block(client, wsClient)
    }

    DatabaseFactory.enableExplain(null)
}

data class SessionTuple(
    val privateKey: String,
    val publicKey: String,
    val address: String,
    val uid: PrimaryKey
)

data class SessionOuterTuple<T>(
    val privateKey: String,
    val publicKey: String,
    val address: String,
    val uid: PrimaryKey,
    val custom: T
)

suspend fun <R> attachSession(
    client: HttpClient,
    block: suspend (SessionTuple) -> R
): SessionOuterTuple<R> {
    val priKey = generateECDSAPemPrivateKey().getOrThrow()
    val pubKey = getDerPublicKeyFromPrivateKey(priKey).getOrThrow()
    val address = calcAddress(pubKey).getOrThrow()
    val rawData = client.getData().getOrThrow()
    val data = finalData(rawData)
    val sign = signature(priKey, data).getOrThrow()
    val userInfo = client.signUp(pubKey, sign).getOrThrow()
    val session = DefaultLoginUserSession(LoginUser(priKey, pubKey, address))
    SignInViewModel.updateState(ClientSession.SignInSuccess(session))
    SignInViewModel.updateUser(userInfo)
    SignInViewModel.updateSession(rawData, sign)
    val r = block(SessionTuple(priKey, pubKey, address, userInfo.id))
    client.signOut().getOrThrow()
    SignInViewModel.signOut()
    return SessionOuterTuple(priKey, pubKey, address, userInfo.id, r)
}

suspend fun <R1, R2> loginSession(
    client: HttpClient,
    session: SessionOuterTuple<R1>,
    block: suspend (SessionTuple) -> R2
): SessionOuterTuple<R2> {
    val rawData = client.getData().getOrThrow()
    val data = finalData(rawData)
    val (privateKey, publicKey, address) = session
    val sign = signature(privateKey, data).getOrThrow()
    val userInfo = client.signIn(address, sign).getOrThrow()
    assertEquals(session.uid, userInfo.id)
    val loginUserSession = DefaultLoginUserSession(LoginUser(privateKey, publicKey, address))
    SignInViewModel.updateState(ClientSession.SignInSuccess(loginUserSession))
    SignInViewModel.updateUser(userInfo)
    SignInViewModel.updateSession(rawData, sign)
    val r2 = block(SessionTuple(session.privateKey, session.publicKey, session.address, session.uid))
    client.signOut().getOrThrow()
    SignInViewModel.signOut()
    return SessionOuterTuple(privateKey, publicKey, address, userInfo.id, r2)
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
