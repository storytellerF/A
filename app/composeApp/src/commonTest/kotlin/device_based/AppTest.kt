package device_based

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import coil3.compose.LocalPlatformContext
import com.storyteller_f.a.app.App
import com.storyteller_f.a.app.LocalUiViewModel
import com.storyteller_f.a.app.UIViewModel
import com.storyteller_f.a.app.utils.initEnvironment
import com.storyteller_f.a.app.core.components.CenterBox
import com.storyteller_f.a.client.core.RawUserPass
import com.storyteller_f.a.client.core.getClient
import com.storyteller_f.a.client.core.getUserInfo
import com.storyteller_f.shared.getPlatform
import com.storyteller_f.shared.setupKmpLogger
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.util.decodeBase64String
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test

class AppTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    @Ignore
    fun testUserPage() {
        runServer { uiViewModel, map ->
            runComposeUiTest {
                setContent {
                    val current = LocalPlatformContext.current
                    var initDone by remember {
                        mutableStateOf(false)
                    }
                    val isSignIn by uiViewModel.mainInstance.sessionManager.isAlreadySignIn.collectAsState()
                    LaunchedEffect(null) {
                        initEnvironment(current)
                        val privateKeyContent = map["p-system"]!!
                        uiViewModel.mainInstance.sessionManager.getUserInfo(
                            privateKeyContent,
                            true
                        ) {
                            RawUserPass(it)
                        }
                        initDone = true
                    }
                    if (initDone && isSignIn) {
                        CompositionLocalProvider(LocalUiViewModel provides uiViewModel) {
                            App()
                        }
                    } else {
                        CenterBox {
                            Text("$initDone $isSignIn")
                        }
                    }
                }
                waitUntil(timeoutMillis = 5000) {
                    onNodeWithTag("me").isDisplayed()
                }
                onNodeWithTag("me").performClick()
                onNodeWithTag("user-dialog-cell").performClick()
                waitUntil(timeoutMillis = 5000) {
                    onNodeWithTag("user-page").isDisplayed()
                }
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun myTest() = runComposeUiTest {
        // Declares a mock UI to demonstrate API calls
        //
        // Replace with your own declarations to test the code of your project
        setContent {
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

        // Tests the declared UI with assertions and actions of the Compose Multiplatform testing API
        onNodeWithTag("text").assertTextEquals("Hello")
        onNodeWithTag("button").performClick()
        onNodeWithTag("text").assertTextEquals("Compose")
    }
}

private fun runServer(block: (UIViewModel, Map<String, String>) -> Unit) {
    setupKmpLogger()
    val ip = "localhost"
    runTest {
        val testClient = getClient {
            expectSuccess = true
            defaultRequest {
                url("http://$ip:8888")
            }
        }
        val ecdsaMap = testClient.get("/ecdsa").bodyAsText()
        val map = ecdsaMap.split("\n").map {
            it.split("\t")
        }.associate {
            it[0] to it[1].decodeBase64String()
        }
        val port = testClient.post("/start") {
            setBody(getPlatform().name)
            timeout {
                socketTimeoutMillis = 20_000
            }
        }.bodyAsText().toInt()
        try {
            val url = "http://$ip:$port"
            val wsServerUrl = url.replace("http", "ws")
            coroutineScope {
                val uIViewModel = UIViewModel(this, wsServerUrl, url)
                block(uIViewModel, map)
            }
        } finally {
            testClient.post("/stop") {
                setBody(port.toString())
            }
        }
    }
}
