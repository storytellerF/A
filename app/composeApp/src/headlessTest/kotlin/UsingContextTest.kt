import com.storyteller_f.a.app.dev.startServerByRun
import com.storyteller_f.a.app.dev.stopServer
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.hours

expect abstract class UsingContextTest() {
    fun onActivity(block: () -> Unit)

    fun executeIfNeed()
}

fun remoteServerTest(block: suspend (String) -> Unit) = runTest(timeout = 1.hours) {
    val serverProcess = startServerByRun("..", 8080) ?: throw Exception("start server failed")
    try {
        block("http://localhost:8080")
    } finally {
        stopServer(serverProcess, 8080)
    }
}
