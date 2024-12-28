import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.MergedEnv
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.a.server.module
import com.storyteller_f.buildBackendFromEnv
import com.storyteller_f.readResourceEnv
import com.storyteller_f.shared.*
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.Tuple4
import com.storyteller_f.shared.type.Tuple5
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.elasticsearch.ElasticsearchContainer
import kotlin.collections.Map
import kotlin.collections.forEach
import kotlin.collections.listOf
import kotlin.collections.set
import kotlin.collections.toMutableMap

fun test(receivedFrame: (RoomFrame) -> Unit = {}, block: suspend (HttpClient, ClientWebSocket) -> Unit) {
    Napier.base(DebugAntilog())
    SnowflakeFactory.setMachine(0)
    addProvider()
    run {
        val env = readResourceEnv(".env")!!.toMutableMap()
        ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:8.1.2"
        )
            // disable SSL
            .withEnv("xpack.security.transport.ssl.enabled", "false")
            .withEnv("xpack.security.http.ssl.enabled", "false").use { elasticClient ->
                elasticClient.start()
                env["SEARCH_SERVICE"] = "elastic"
                env["ELASTIC_NAME"] = "elastic"
                env["ELASTIC_PASSWORD"] = "changeme"
                env["ELASTIC_URL"] = "http://${elasticClient.httpHostAddress}"
                MinIOContainer("minio/minio:RELEASE.2023-09-04T19-57-37Z").use { minioContainer ->
                    minioContainer.start()
                    env["MEDIA_SERVICE"] = "minio"
                    env["MINIO_URL"] = minioContainer.s3URL
                    env["MINIO_NAME"] = minioContainer.userName
                    env["MINIO_PASS"] = minioContainer.password
                    doTest(env, receivedFrame, block)
                    minioContainer.stop()
                }
                elasticClient.stop()
            }
    }

    run {
        val env = readResourceEnv(".env")!!
        doTest(env, receivedFrame, block)
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

    runBlocking {
        backend.topicSearchService.clean()
    }
    DatabaseFactory.clean()
}

suspend fun <R> attachSession(
    client: HttpClient,
    block: suspend (Tuple4<String, String, String, PrimaryKey>) -> R
): Tuple5<String, String, String, PrimaryKey, R> {
    val priKey = generateKeyPair()
    val pubKey = getDerPublicKeyFromPrivateKey(priKey)
    val address = calcAddress(pubKey)
    val rawData = client.getData().getOrThrow()
    val data = finalData(rawData)
    val sign = signature(priKey, data)
    val userInfo = client.signUp(pubKey, sign).getOrThrow()
    LoginViewModel.updateState(ClientSession.SignUpSuccess(priKey, pubKey, address))
    LoginViewModel.updateUser(userInfo)
    LoginViewModel.updateSession(rawData, sign)
    val r = block(Tuple4(priKey, pubKey, address, userInfo.id))
    client.signOut().getOrThrow()
    LoginViewModel.signOut()
    return Tuple5(priKey, pubKey, address, userInfo.id, r)
}

suspend fun <R1, R2> loginSession(
    client: HttpClient,
    session: Tuple5<String, String, String, PrimaryKey, R1>,
    block: suspend (Tuple4<String, String, String, PrimaryKey>) -> R2
): Tuple5<String, String, String, PrimaryKey, R2> {
    val rawData = client.getData().getOrThrow()
    val data = finalData(rawData)
    val sign = signature(session.data1, data)
    val address = session.data3
    val pubKey = session.data2
    val priKey = session.data1
    val userInfo = client.signIn(address, sign).getOrThrow()
    LoginViewModel.updateState(ClientSession.SignUpSuccess(priKey, pubKey, address))
    LoginViewModel.updateUser(userInfo)
    LoginViewModel.updateSession(rawData, sign)
    val r2 = block(Tuple4(priKey, pubKey, address, userInfo.id))
    return Tuple5(priKey, pubKey, address, userInfo.id, r2)
}
