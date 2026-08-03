/*
 * This is a private project. All rights reserved.
 */

package com.storytellerf.a.cliappium

import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import AppiumPorts as BackendPorts
import runAppiumBlockingTest as runBlockingE2eTest
import runAppiumTestEnvironment as runBackendTestEnvironment

private const val INSTALL_DIR_PROPERTY = "cli.app.install.dir"
private const val TRANSCRIPT_LIMIT = 24_000
private const val TERMINAL_COLUMNS = 120
private const val TERMINAL_ROWS = 40

internal class CliAppiumTest {
    private val terminalFactory = CliTerminalFactory()

    @Test
    internal fun `register and browse members through installed cli`() {
        runBlockingE2eTest {
            runBackendTestEnvironment { ports ->
                coroutineScope {
                    val terminal = terminalFactory.start(ports, this)
                    try {
                        terminal.awaitText("STORYTELLER CLI")
                        terminal.sendLine("1")
                        terminal.awaitText("Enter Private Key (leave empty for auto-generate):")
                        terminal.sendLine("")
                        terminal.awaitText("Registered and Logged in as:")

                        terminal.sendLine("6")
                        terminal.awaitText("Fetching Member List...")
                        terminal.awaitText("=== Member List ===")

                        terminal.sendLine("")
                        terminal.awaitText("--- Menu ---")
                        terminal.sendLine("0")
                        terminal.awaitExit()
                    } finally {
                        terminal.close()
                    }
                }
            }
        }
    }
}

private class CliTerminalFactory(private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    suspend fun start(ports: BackendPorts, scope: CoroutineScope): CliTerminal =
        CliTerminal.start(ports, scope, ioDispatcher)
}

private class CliTerminal private constructor(
    private val process: PtyProcess,
    private val input: BufferedInputStream,
    private val output: BufferedOutputStream,
    private val outputChunks: Channel<String>,
    private val readerJob: Job,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val transcript = StringBuilder()
    private var searchStart = 0

    suspend fun sendLine(line: String) {
        withContext(ioDispatcher) {
            output.write(line.encodeToByteArray())
            output.write(process.enterKeyCode.toInt())
            output.flush()
        }
    }

    suspend fun awaitText(expected: String) {
        val isFound =
            withTimeoutOrNull(90.seconds) {
                waitForText(expected)
            } == true

        assertTrue(isFound, "CLI did not render '$expected'.\n${recentTranscript()}")
    }

    private suspend fun waitForText(expected: String): Boolean {
        while (true) {
            val matchIndex = transcript.indexOf(expected, searchStart)
            if (matchIndex >= 0) {
                searchStart = matchIndex + expected.length
                return true
            }
            val chunk = outputChunks.receiveCatching().getOrNull()
            checkNotNull(chunk) {
                "CLI exited before rendering '$expected'.\n${recentTranscript()}"
            }
            transcript.append(chunk)
        }
    }

    suspend fun awaitExit() {
        val hasExited =
            withTimeoutOrNull(10.seconds) {
                waitForExit()
            } == true
        assertTrue(hasExited, "CLI did not exit after selecting 0.\n${recentTranscript()}")
    }

    private suspend fun waitForExit(): Boolean {
        while (process.isAlive) {
            delay(50)
        }
        return true
    }

    suspend fun close() {
        withContext(ioDispatcher) {
            if (process.isAlive) {
                process.destroyForcibly()
            }
            input.close()
            output.close()
        }
        readerJob.cancelAndJoin()
    }

    private fun recentTranscript(): String = transcript.takeLast(TRANSCRIPT_LIMIT).toString()

    companion object {
        suspend fun start(ports: BackendPorts, scope: CoroutineScope, ioDispatcher: CoroutineDispatcher): CliTerminal {
            val installDir = File(checkNotNull(System.getProperty(INSTALL_DIR_PROPERTY)))
            val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
            val executable = File(installDir, if (isWindows) "bin/cliApp.bat" else "bin/cliApp")
            check(executable.isFile) { "CLI executable not found: ${executable.absolutePath}" }

            val command =
                if (isWindows) {
                    arrayOf("cmd.exe", "/c", executable.absolutePath)
                } else {
                    arrayOf(executable.absolutePath)
                }
            val environment =
                System.getenv().toMutableMap().apply {
                    put("TERM", "xterm-256color")
                    put("SERVER_URL", "http://127.0.0.1:${ports.server}")
                    put("WS_SERVER_URL", "ws://127.0.0.1:${ports.ws}")
                }
            val process =
                withContext(ioDispatcher) {
                    PtyProcessBuilder(command)
                        .setDirectory(installDir.absolutePath)
                        .setEnvironment(environment)
                        .setInitialColumns(TERMINAL_COLUMNS)
                        .setInitialRows(TERMINAL_ROWS)
                        .setRedirectErrorStream(true)
                        .start()
                }
            val input = BufferedInputStream(process.inputStream)
            val outputChunks = Channel<String>(Channel.UNLIMITED)
            val readerJob =
                scope.launch(ioDispatcher) {
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    try {
                        while (true) {
                            val count = input.read(buffer)
                            if (count <= 0) break
                            outputChunks.send(buffer.decodeToString(endIndex = count))
                        }
                    } finally {
                        outputChunks.close()
                    }
                }
            return CliTerminal(
                process = process,
                input = input,
                output = BufferedOutputStream(process.outputStream),
                outputChunks = outputChunks,
                readerJob = readerJob,
                ioDispatcher = ioDispatcher,
            )
        }
    }
}
