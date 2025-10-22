package device_based

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.storyteller_f.a.client.core.getClient
import com.storyteller_f.shared.getPlatform
import com.storyteller_f.shared.setupKmpLogger
import io.github.aakira.napier.Napier
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
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
                    Scaffold {
                        Column(Modifier.padding(it)) {
                            var text by remember { mutableStateOf("Hello") }
                            Text(
                                text = text,
                                modifier = Modifier.testTag("text")
                            )
                            Button(
                                onClick = { text = "Compose" },
                                modifier = Modifier.testTag("button")
                            ) {
                                Text("Click me")
                            }
                        }
                    }
                }

                // Tests the declared UI with assertions and actions of the Compose Multiplatform testing API
                onNodeWithTag("text").assertTextEquals("Hello")
                onNodeWithTag("button").performClick()
                onNodeWithTag("text").assertTextEquals("Compose")
            }
        }

    }

    private fun runServer(block: (String) -> Unit) {
        setupKmpLogger()
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
            Napier.i {
                "${platform.id} ${platform.name}"
            }
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