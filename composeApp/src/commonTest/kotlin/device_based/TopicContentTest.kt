package device_based

import androidx.compose.ui.test.*
import com.storyteller_f.a.app.AppInternal
import com.storyteller_f.a.app.compontents.TopicContentField
import com.storyteller_f.a.app.setupRequest
import com.storyteller_f.a.client_lib.getClient
import com.storyteller_f.shared.model.TopicInfo
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals


class AppTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun myTest() {
        test {
            runComposeUiTest {
                setContent {
                    AppInternal("http://10.0.2.2:$it", "ws://10.0.2.2:$it")
                }

                onNodeWithTag("me").performClick()
            }
        }

    }

    private fun test(block: (Int) -> Unit) {
        runBlocking {
            val testClient = getClient {
                expectSuccess = true
                setupRequest("http://10.0.2.2:8888")
            }
            assertEquals("pong", testClient.get("/ping").bodyAsText())
            val port = testClient.post("/start") {
                timeout {
                    requestTimeoutMillis = 10_000
                    socketTimeoutMillis = 20_000
                    connectTimeoutMillis = 10_000
                }
            }.bodyAsText().toIntOrNull() ?: return@runBlocking
            try {
                block(port)
            } finally {
                testClient.post("/stop") {
                    setBody(port.toString())
                }
            }
        }
    }
}

