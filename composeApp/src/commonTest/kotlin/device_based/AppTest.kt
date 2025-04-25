package device_based

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import coil3.compose.LocalPlatformContext
import com.storyteller_f.a.app.AppInternal
import com.storyteller_f.a.app.utils.initEnvironment
import com.storyteller_f.a.client_lib.getClient
import com.storyteller_f.shared.getPlatform
import io.ktor.client.plugins.*
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals


class AppTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun myTest() {
        runServer {
            runComposeUiTest {
                setContent {
                    val current = LocalPlatformContext.current
                    var initDone by remember {
                        mutableStateOf(false)
                    }
                    LaunchedEffect(null) {
                        initEnvironment(current)
                        initDone = true
                    }
                    if (initDone) {
                        AppInternal(it, it.replace("http", "ws"))
                    }
                }

                onNodeWithTag("me").performClick()
            }
        }

    }

    private fun runServer(block: (String) -> Unit) {
        val ip = "localhost"
        runBlocking {
            val testClient = getClient {
                expectSuccess = true
                defaultRequest {
                    url("http://$ip:8888")
                }
            }
            assertEquals("pong", testClient.get("/ping").bodyAsText())
            val platform = getPlatform()
            val port = testClient.post("/start") {
                setBody("${platform.name}\n${platform.id}")
                timeout {
                    socketTimeoutMillis = 20_000
                }
            }.bodyAsText().toIntOrNull() ?: return@runBlocking
            try {
                block("http://$ip:$port")
            } finally {
                testClient.post("/stop") {
                    setBody(port.toString())
                }
            }
        }
    }
}

