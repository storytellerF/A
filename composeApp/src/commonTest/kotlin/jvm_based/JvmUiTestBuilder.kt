package jvm_based

import startServer
import stopServer

fun jvmBasedTest(block: () -> Unit) {
    val serverProcess = startServer(8080,"..") ?: return
    block()
    stopServer(serverProcess, 8080)
}
