import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.a.client_lib.ClientSession
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.a.client_lib.getData
import com.storyteller_f.a.client_lib.signUp
import com.storyteller_f.buildBackendFromEnv
import com.storyteller_f.readEnv
import com.storyteller_f.shared.*
import com.storyteller_f.shared.finalData
import com.storyteller_f.shared.signature
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.Tuple4
import com.storyteller_f.shared.type.Tuple5
import io.ktor.client.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

@Suppress("unused")
fun Application.module() {
    routing {
    }
}

@OptIn(ExperimentalPathApi::class)
fun test(block: suspend (HttpClient) -> Unit) {
    SnowflakeFactory.setMachine(0)
    testApplication {
        addProvider()
        val path = Paths.get("../deploy/lucene_data/index")
        path.deleteRecursively()
        val backend = buildBackendFromEnv(readEnv())
        DatabaseFactory.clean(backend.config.databaseConnection)
        DatabaseFactory.init(backend.config.databaseConnection)
        environment {
            config = MapApplicationConfig(
                "ktor.application.modules.0" to "TestBuilderKt.module",
                "ktor.application.modules.1" to "com.storyteller_f.a.server.ApplicationKt.module",
                "ktor.application.modules.size" to "2"
            )
        }
        val client = createClient {
            defaultClientConfigure()
        }
        block(client)
        path.deleteRecursively()
        DatabaseFactory.clean()
    }
}

suspend fun<R> attachSession(
    client: HttpClient,
    block: suspend (Tuple4<String, String, String, PrimaryKey>) -> R
): Tuple5<String, String, String, PrimaryKey, R> {
    val priKey = generateKeyPair()
    val pubKey = getDerPublicKeyFromPrivateKey(priKey)
    val address = calcAddress(pubKey)
    val rawData = client.getData()
    val data = finalData(rawData)
    val sign = signature(priKey, data)
    val userInfo = client.signUp(pubKey, sign)
    LoginViewModel.updateState(ClientSession.SignUpSuccess(priKey, pubKey, address))
    LoginViewModel.updateUser(userInfo)
    LoginViewModel.updateSession(rawData, sign)
    val r = block(Tuple4(priKey, pubKey, address, userInfo.id))
    client.signOut()
    LoginViewModel.signOut()
    return Tuple5(priKey, pubKey, address, userInfo.id, r)
}

suspend fun<R1, R2> loginSession(
    client: HttpClient,
    session: Tuple5<String, String, String, PrimaryKey, R1>,
    block: suspend (Tuple4<String, String, String, PrimaryKey>) -> R2
): Tuple5<String, String, String, PrimaryKey, R2> {
    val rawData = client.getData()
    val data = finalData(rawData)
    val sign = signature(session.data1, data)
    val address = session.data3
    val pubKey = session.data2
    val priKey = session.data1
    val userInfo = client.signIn(address, sign)
    LoginViewModel.updateState(ClientSession.SignUpSuccess(priKey, pubKey, address))
    LoginViewModel.updateUser(userInfo)
    LoginViewModel.updateSession(rawData, sign)
    val r2 = block(Tuple4(priKey, pubKey, address, userInfo.id))
    return Tuple5(priKey, pubKey, address, userInfo.id, r2)
}
