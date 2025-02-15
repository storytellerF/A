import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.MergedEnv
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.a.server.module
import com.storyteller_f.buildBackendFromEnv
import com.storyteller_f.crypto_jvm.addProviderForJvm
import com.storyteller_f.readResourceEnv
import com.storyteller_f.shared.*
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.elasticsearch.ElasticsearchContainer
import kotlin.collections.Map
import kotlin.collections.forEach
import kotlin.collections.listOf
import kotlin.collections.set
import kotlin.collections.toMutableMap
import kotlin.test.assertEquals

fun test(receivedFrame: (RoomFrame) -> Unit = {}, block: suspend (HttpClient, ClientWebSocket) -> Unit) {
    Napier.base(DebugAntilog())
    val freeMemory = Runtime.getRuntime().freeMemory() / (1024 * 1024)
    Napier.i {
        "free ${freeMemory}MiB"
    }
    SnowflakeFactory.setMachine(0)
    addProviderForJvm()

    run {
        val env = readResourceEnv(".env")!!
        doTest(env, receivedFrame, block)
    }

    if (freeMemory >= 1024) {
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
                    ).waitingFor(Wait.forSuccessfulCommand("curl -I http://localhost:9000/minio/health/live"))
                        .use { minioContainer ->
                            minioContainer.start()
                            env["MEDIA_SERVICE"] = "minio"
                            env["MINIO_URL"] = minioContainer.s3URL
                            env["MINIO_NAME"] = minioContainer.userName
                            env["MINIO_PASS"] = minioContainer.password
                            PostgreSQLContainer(
                                "pgvector/pgvector:pg16"
                            ).waitingFor(Wait.forSuccessfulCommand("pg_isready")).use { postgreSQLContainer ->
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
    val backend = buildBackendFromEnv(MergedEnv(listOf(env)))
    runBlocking {
        backend.topicSearchService.clean()
    }
    DatabaseFactory.clean(backend.config.databaseConnection)
    DatabaseFactory.init(backend.config.databaseConnection)
    DatabaseFactory.enableExplain()
    println("prepared resource")
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
            defaultClientConfigure()
        }
        val wsClient = ClientWebSocket({
            client.webSocketSession("/link") {
                addRequestHeaders(LoginViewModel.session?.first)
            }
        }) {
            receivedFrame(it)
        }

        block(client, wsClient)
    }

    println("clean resource")
    runBlocking {
        backend.topicSearchService.clean()
    }
    DatabaseFactory.clean()
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
    val id: PrimaryKey,
    val custom: T
)

suspend fun <R> attachSession(
    client: HttpClient,
    block: suspend (SessionTuple) -> R
): SessionOuterTuple<R> {
    val priKey = generateKeyPair()
    val pubKey = getDerPublicKeyFromPrivateKey(priKey)
    val address = calcAddress(pubKey)
    val rawData = client.getData().getOrThrow()
    val data = finalData(rawData)
    val sign = signature(priKey, data)
    val userInfo = client.signUp(pubKey, sign).getOrThrow()
    val session = DefaultLoginUserSession(LoginUser(priKey, pubKey, address))
    LoginViewModel.updateState(ClientSession.SignUpSuccess(session))
    LoginViewModel.updateUser(userInfo)
    LoginViewModel.updateSession(rawData, sign)
    val r = block(SessionTuple(priKey, pubKey, address, userInfo.id))
    client.signOut().getOrThrow()
    LoginViewModel.signOut()
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
    val sign = signature(privateKey, data)
    val userInfo = client.signIn(address, sign).getOrThrow()
    val session1 = DefaultLoginUserSession(LoginUser(privateKey, publicKey, address))
    LoginViewModel.updateState(ClientSession.SignUpSuccess(session1))
    LoginViewModel.updateUser(userInfo)
    LoginViewModel.updateSession(rawData, sign)
    val r2 = block(SessionTuple(session.privateKey, session.publicKey, session.address, session.id))
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
