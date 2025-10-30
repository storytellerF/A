import com.storyteller_f.a.app.dev.forceStop
import com.storyteller_f.a.app.dev.startServerByRun
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest

expect abstract class UsingContextTest() {
    val portOffset: Int
}
fun UsingContextTest.remoteServerTest(
    port: Int,
    block: suspend CoroutineScope.(String) -> Unit,
) = runTest {
    val p = port + portOffset
    println("port: $p")
    forceStop(p)
    val serverProcess = startServerByRun("../..", p) ?: throw Exception("start server failed")
    try {
        block("http://localhost:$p")
    } finally {
        serverProcess.stop()
    }
}
