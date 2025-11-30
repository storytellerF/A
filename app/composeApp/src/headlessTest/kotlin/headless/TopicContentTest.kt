package headless

import UsingContextTest
import androidx.compose.ui.test.ExperimentalTestApi
import com.storyteller_f.a.api.PaginationQuery
import com.storyteller_f.a.app.buildHttpClient
import com.storyteller_f.a.client.core.buildWebSocketUrl
import com.storyteller_f.a.client.core.createUserSessionManager
import com.storyteller_f.a.client.core.getRecommendTopics
import com.storyteller_f.a.client.core.startBackgroundTask
import kotlinx.coroutines.cancelAndJoin
import remoteServerTest
import kotlin.test.Test

class TopicContentTest : UsingContextTest() {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testApp() = remoteServerTest(8080) { url ->
        val webSocketUrl = buildWebSocketUrl(url.replace("http", "ws"))
        val manager = createUserSessionManager(webSocketUrl, { model, cookie ->
            buildHttpClient(url, cookie, model)
        }, { _, _, _ -> })
        val jobs = manager.startBackgroundTask()
        manager.getRecommendTopics(PaginationQuery()).getOrThrow()
        jobs.forEach {
            it.cancelAndJoin()
        }
    }
}
