import com.storyteller_f.a.app.dev.forceStop
import com.storyteller_f.a.app.dev.startServerByRun
import com.storyteller_f.shared.setupKmpLogger
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.minutes

expect abstract class PlatformHeadlessTest() {
    val portOffset: Int
}
fun PlatformHeadlessTest.remoteServerTest(
    suggestPort: Int,
    block: suspend CoroutineScope.(String) -> Unit,
) = runTest(timeout = 10.minutes) {
    setupKmpLogger()
    val traceElements = Exception().stackTrace
    val methodNameIndex = traceElements.indexOfFirst {
        it.methodName == "remoteServerTest"
    }
    if (methodNameIndex < 0) {
        throw Exception("remoteServerTest not found")
    }
    val methodName = traceElements[methodNameIndex + 1].methodName
    val port = suggestPort + portOffset
    println("port: $port, methodName: $methodName")
    forceStop(port)
    val processMate = startServerByRun(
        "../..",
        port,
        "./build/test/headless/sessions/${Uuid.random().toHexString()}"
    ) ?: throw Exception("start server failed")
    try {
        block("http://localhost:$port")
        Napier.i {
            "test done $methodName"
        }
    } finally {
        processMate.stop()
    }
}
