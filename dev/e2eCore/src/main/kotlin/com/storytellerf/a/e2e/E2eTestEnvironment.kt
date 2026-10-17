/*
 * This is a private project. All rights reserved.
 */

package com.storytellerf.a.e2e

import com.storyteller_f.a.test.useBackendFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.time.Duration.Companion.minutes

/** Mapped host ports of the HTTP and WebSocket services started for a CLI E2E test. */
class E2ePorts(server: Int, ws: Int) {
    /** HTTP server port on the test host. */
    val server: Int = server

    /** WebSocket server port on the test host. */
    val ws: Int = ws
}

/** Run a suspending CLI E2E test from JUnit's synchronous test boundary. */
fun runE2eBlockingTest(block: suspend CoroutineScope.() -> Unit) {
    runBlocking {
        withTimeout(10.minutes) {
            block()
        }
    }
}

/** Start an isolated full backend and retain service logs under the module build directory. */
suspend fun runE2eTestEnvironment(block: suspend (E2ePorts) -> Unit) {
    val root = File("build/reports/e2e").also { it.mkdirs() }
    val directory = Files.createTempDirectory(root.toPath(), "backend-").toFile()
    useBackendFixture(directory.resolve("logs")) { backend ->
        block(E2ePorts(backend.serverPort, backend.wsPort))
    }
}
