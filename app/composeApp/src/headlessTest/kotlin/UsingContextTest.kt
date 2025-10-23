import com.storyteller_f.a.app.dev.forceStop
import com.storyteller_f.a.app.dev.startServerByRun
import com.storyteller_f.a.app.dev.stopServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.hours

expect abstract class UsingContextTest()

fun remoteServerTest(block: suspend CoroutineScope.(String) -> Unit) {
    val port = 8080
    runTest(timeout = 1.hours) {
        forceStop(port)
        val serverProcess = startServerByRun("../..", port) ?: throw Exception("start server failed")
        try {
            block("http://localhost:8080")
        } finally {
            stopServer(serverProcess)
        }
    }
}
