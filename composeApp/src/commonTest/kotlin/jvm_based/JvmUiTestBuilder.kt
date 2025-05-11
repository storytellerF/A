package jvm_based

import kotlinx.coroutines.test.runTest
import startServer
import stopServer
import kotlin.time.Duration.Companion.hours

fun jvmBasedTest(block: (String) -> Unit) {
    runTest(timeout = 1.hours) {
        val serverProcess = startServer("..", 8080) ?: throw Exception("start server failed")
        try {
            block("http://localhost:8811")
        } finally {
            stopServer(serverProcess, 8080)
        }
    }
}
