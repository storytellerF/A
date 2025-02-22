package jvm_based

import startServer
import stopServer

fun jvmBasedTest(block: (String) -> Unit) {
    val serverProcess = startServer("..", 8080) ?: return
    try {
        block("http://localhost:8811")
    } finally {
        stopServer(serverProcess, 8080)
    }
}
