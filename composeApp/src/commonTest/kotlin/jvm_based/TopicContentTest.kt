package jvm_based

import androidx.compose.ui.test.ExperimentalTestApi
import com.storyteller_f.a.app.buildHttpClient
import com.storyteller_f.a.app.buildWebSocketUrl
import com.storyteller_f.a.client_lib.createUserSessionManager
import com.storyteller_f.a.client_lib.start
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlin.test.Ignore
import kotlin.test.Test


class TopicContentTest : UsingContextTest() {

    @OptIn(ExperimentalTestApi::class)
    @Test
    @Ignore
    fun testApp() = jvmBasedTest {
        coroutineScope {
            val webSocketUrl = buildWebSocketUrl(it.replace("http", "ws"))
            val manager = createUserSessionManager(webSocketUrl, { model, cookie ->
                buildHttpClient(it, cookie, model)
            }, { _, _ -> })
            manager.start().forEach(Job::cancel)
        }

    }
}

