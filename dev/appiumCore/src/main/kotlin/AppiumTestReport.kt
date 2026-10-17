/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import com.storyteller_f.a.test.prepareTestReportDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.openqa.selenium.OutputType
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.WebDriver
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

/** Keeps only the latest artifacts for each suite, platform and test name. */
internal class AppiumTestReport(
    private val suite: String,
    private val platform: String,
    private val testName: String,
    private val root: File = File("build/reports/test-artifacts"),
) {
    val directory: File = prepareTestReportDirectory(root, suite, testName, platform)
    private val started = Instant.now()
    private val collectionErrors = mutableListOf<String>()
    private val temporaryPaths = mutableListOf<File>()
    private val privateDirectory by lazy {
        val parent = File("build/test/appium/private").also { it.mkdirs() }
        Files.createTempDirectory(parent.toPath(), "runtime-").toFile().also { temporaryPaths.add(it) }
    }

    /** Private runtime state lives outside the report and is removed even when setup fails. */
    fun temporaryPath(name: String): File = privateDirectory.resolve(name)

    /** Collection must never replace a test failure or prevent subsequent collectors. */
    fun collect(label: String, block: () -> Unit) {
        try {
            block()
        } catch (failure: Exception) {
            collectionErrors.add("$label: ${failure.stackTraceToString()}")
        }
    }

    fun captureDriver(driver: WebDriver) {
        collect("Page source") { directory.resolve("page-source.xml").writeText(requireNotNull(driver.pageSource)) }
        collect("Screenshot") {
            val screenshot = driver as TakesScreenshot
            directory.resolve("screenshot.png").writeBytes(screenshot.getScreenshotAs(OutputType.BYTES))
        }
    }

    suspend fun run(block: suspend () -> Unit) {
        var outcome: AppiumOutcome = AppiumOutcome.Running
        try {
            block()
            outcome = AppiumOutcome.Passed
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (cause: Throwable) {
            outcome = AppiumOutcome.Failed(cause)
            throw cause
        } finally {
            withContext(NonCancellable) { finish(outcome) }
        }
    }

    private suspend fun finish(outcome: AppiumOutcome) {
        try {
            temporaryPaths.forEach { path ->
                collect("Temporary state cleanup") {
                    check(!path.exists() || path.deleteRecursively()) { "Unable to remove temporary state" }
                }
            }
            writeReport(outcome)
            publishReportIndex(root)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (reportFailure: Exception) {
            when (outcome) {
                is AppiumOutcome.Failed -> outcome.cause.addSuppressed(reportFailure)

                AppiumOutcome.Passed -> throw reportFailure

                AppiumOutcome.Running ->
                    org.slf4j.LoggerFactory.getLogger("appium-report")
                        .warn("Failed to write cancelled test report", reportFailure)
            }
        }
    }

    private fun writeReport(outcome: AppiumOutcome) {
        val failure = (outcome as? AppiumOutcome.Failed)?.cause
        if (failure != null) directory.resolve("failure.txt").writeText(failure.stackTraceToString())
        if (collectionErrors.isNotEmpty()) {
            directory.resolve("collection-errors.txt").writeText(collectionErrors.joinToString("\n"))
        }
        val serviceErrors =
            directory.walkTopDown().filter { it.isFile && it.name.endsWith(".error.txt") }
                .map { "${it.relativeTo(directory)}: ${it.readText()}" }.toList()
        val errors = collectionErrors + serviceErrors
        val status =
            when (outcome) {
                AppiumOutcome.Running -> "CANCELLED"
                AppiumOutcome.Passed -> "PASSED"
                is AppiumOutcome.Failed -> "FAILED"
            }
        val artifacts = renderAppiumArtifacts(directory)
        directory.resolve("index.html").writeText(
            """
            <!doctype html><html lang="en"><meta charset="utf-8">
            <title>${suite.escapeHtml()} / ${platform.escapeHtml()} / ${testName.escapeHtml()} — $status</title>
            <h1>${testName.escapeHtml()}</h1><p>Suite: ${suite.escapeHtml()}</p>
            <p>Platform: ${platform.escapeHtml()} | Result: $status</p>
            <p>Started: $started | Finished: ${Instant.now()}</p>
            <h2>Failure</h2><pre>${failure?.stackTraceToString().orEmpty().escapeHtml()}</pre>
            <h2>Collection errors</h2><pre>${errors.joinToString("\n").escapeHtml()}</pre>
            <h2>Artifacts</h2><ul>$artifacts</ul></html>
            """.trimIndent(),
        )
    }
}

internal fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

private val reportIndexMutex = Mutex()

private fun writeIndex(root: File) {
    val entries =
        root.walkTopDown().filter { it.isFile && it.name == "index.html" && it.parentFile != root }
            .sortedBy { it.path }.joinToString("\n") { report ->
                val relative = report.relativeTo(root)
                val path = relative.invariantSeparatorsPath
                val link = encodeArtifactPath(relative.toPath())
                val title = report.readText().substringAfter("<title>").substringBefore("</title>")
                "<li><a href=\"${link.escapeHtml()}\">$title</a> (${path.escapeHtml()})</li>"
            }
    root.resolve("index.html").writeText(
        """<!doctype html><html lang="en"><meta charset="utf-8"><title>Appium reports</title>""" +
            "<h1>Appium reports</h1><ul>$entries</ul></html>",
    )
}

private suspend fun publishReportIndex(root: File) =
    withContext(Dispatchers.IO) {
    reportIndexMutex.withLock {
        // The OS file lock coordinates independent Gradle forks; the coroutine mutex handles this JVM.
        FileChannel.open(root.resolve(".index.lock").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            .use { channel -> channel.lock().use { writeIndex(root) } }
    }
}

private sealed interface AppiumOutcome {
    data object Running : AppiumOutcome
    data object Passed : AppiumOutcome
    data class Failed(val cause: Throwable) : AppiumOutcome
}
