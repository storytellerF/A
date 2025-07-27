import androidx.compose.ui.test.ExperimentalTestApi
import com.caoccao.javet.interop.NodeRuntime
import com.caoccao.javet.interop.V8Host
import com.caoccao.javet.node.modules.NodeModuleModule
import com.storyteller_f.a.app.compose_app.buildHttpClient
import com.storyteller_f.a.app.compose_app.buildWebSocketUrl
import com.storyteller_f.a.client.core.createUserSessionManager
import com.storyteller_f.a.client.core.start
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import java.io.File
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals


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

    @Test
    fun `test parse ascii doc`() {
        val scriptDir = File("src/headlessTest/resources")
        if (!File(scriptDir, "node_modules").exists()) {
            return
        }
        val scripts = File(scriptDir, "parse-ascii.js").bufferedReader().use {
            it.readText()
        }
        (V8Host.getNodeInstance().createV8Runtime() as NodeRuntime).use {
            it.getNodeModule(NodeModuleModule::class.java)
                .setRequireRootDirectory(scriptDir)

            val result = it.getExecutor(scripts)
                .executeString()
            assertEquals(
                """
                <div class="paragraph">
                <p>Hello, <em>Asciidoctor</em></p>
                </div>
            """.trimIndent(), result
            )
        }
    }
}

