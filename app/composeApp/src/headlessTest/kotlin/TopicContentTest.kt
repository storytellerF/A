import androidx.compose.ui.test.ExperimentalTestApi
import com.storyteller_f.a.app.compose_app.buildHttpClient
import com.storyteller_f.a.client.core.buildWebSocketUrl
import com.storyteller_f.a.client.core.createUserSessionManager
import com.storyteller_f.a.client.core.start
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlin.test.Ignore
import kotlin.test.Test


class TopicContentTest : UsingContextTest() {

    @OptIn(ExperimentalTestApi::class)
    @Test
    @Ignore
    fun testApp() = remoteServerTest {
        coroutineScope {
            val webSocketUrl = buildWebSocketUrl(it.replace("http", "ws"))
            val manager = createUserSessionManager(webSocketUrl, { model, cookie ->
                buildHttpClient(it, cookie, model)
            }, { _, _ -> })
            manager.start().forEach(Job::cancel)
        }

    }
}

