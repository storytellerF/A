import androidx.compose.ui.test.ExperimentalTestApi
import com.storyteller_f.a.api.PaginationQuery
import com.storyteller_f.a.app.compose_app.buildHttpClient
import com.storyteller_f.a.client.core.buildWebSocketUrl
import com.storyteller_f.a.client.core.createUserSessionManager
import com.storyteller_f.a.client.core.getRecommendTopics
import com.storyteller_f.a.client.core.startBackgroundTask
import kotlinx.coroutines.Job
import kotlin.test.Test

class TopicContentTest : UsingContextTest() {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testApp() = remoteServerTest(8080) {
        val webSocketUrl = buildWebSocketUrl(it.replace("http", "ws"))
        val manager = createUserSessionManager(webSocketUrl, { model, cookie ->
            buildHttpClient(it, cookie, model)
        }, { _, _, _ -> })
        manager.getRecommendTopics(PaginationQuery()).getOrThrow()
        manager.startBackgroundTask().forEach(Job::cancel)
    }
}
