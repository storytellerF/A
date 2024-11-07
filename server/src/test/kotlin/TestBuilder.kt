import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.buildBackendFromEnv
import com.storyteller_f.readEnv
import com.storyteller_f.shared.*
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
        val backend = buildBackendFromEnv(readEnv())
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

suspend fun session(client: HttpClient, block: suspend () -> Unit) {
    val priKey = generateKeyPair()
    val pubKey = getDerPublicKeyFromPrivateKey(priKey)
    val address = calcAddress(pubKey)
    val data = finalData(client.getData())
    val sign = signature(priKey, data)
    val userInfo = client.sign(true, pubKey, sign, address)
    LoginViewModel.updateState(ClientSession.LoginSuccess(priKey, pubKey, address))
    LoginViewModel.updateUser(userInfo)
    block()
}
