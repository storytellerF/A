package device_based

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import coil3.compose.LocalPlatformContext
import com.storyteller_f.a.app.compose_app.App
import com.storyteller_f.a.app.compose_app.LocalUiViewModel
import com.storyteller_f.a.app.compose_app.UIViewModel
import com.storyteller_f.a.app.compose_app.utils.initEnvironment
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
import kotlinx.coroutines.test.runTest
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
                        CompositionLocalProvider(LocalUiViewModel provides it) {
                            App()
                        }
                    }
                }

                onNodeWithTag("me").performClick()
                onNodeWithTag("sign_in").performClick()
                onNodeWithTag("goto_sign_up").performClick()
                onNodeWithTag("private_key").performClick()
                onNodeWithTag("auto_generate").performClick()
                onNodeWithTag("start_sign").performClick()
                waitUntil(timeoutMillis = 15000) {
                    onNodeWithTag("home").isDisplayed()
                }

            }
        }

    }


}

private fun runServer(block: (UIViewModel) -> Unit) {
    setupKmpLogger()
    val ip = "localhost"
    runTest {
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
        }.bodyAsText().toInt()
        try {
            val url = "http://$ip:$port"
            val wsServerUrl = url.replace("http", "ws")
            val uIViewModel = UIViewModel(this, wsServerUrl, url)
            block(uIViewModel)
        } finally {
            testClient.post("/stop") {
                setBody(port.toString())
            }
        }
    }
}