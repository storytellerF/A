/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.WebDriver
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppiumTestReportTest {
    @Test
    fun isolateAttemptsAndUnsafeNames() =
        runAppiumBlockingTest {
        withReportRoot { root ->
            val names = listOf("../test", "..\\test", "CON", "", "<test> & \"quoted\"", "测试")
            val reports =
                names.flatMap { name ->
                    listOf(
                        AppiumTestReport("suite", "android", name, root),
                        AppiumTestReport("suite", "android", name, root),
                    )
                }
            assertEquals(reports.size, reports.map { it.directory.canonicalPath }.distinct().size)
            reports.forEach { report ->
                assertTrue(report.directory.toPath().startsWith(root.toPath()))
                report.run { }
                assertTrue(report.directory.resolve("index.html").isFile)
            }
            assertTrue(root.resolve("index.html").readText().contains("&lt;test&gt; &amp; &quot;quoted&quot;"))
        }
    }

    @Test
    fun preserveFailureAndExposeCollectionErrors() =
        runAppiumBlockingTest {
        withReportRoot { root ->
            val report = AppiumTestReport("suite", "desktop", "failed case", root)
            val original = AssertionError("original <failure>")
            val actual =
                assertFailsWith<AssertionError> {
                    report.run {
                        report.collect("Recording") { error("recording unavailable") }
                        report.collect("Application log") {
                            report.directory.resolve("application.log").writeText("log")
                        }
                        report.directory.resolve("server.error.txt").writeText("service export failed")
                        throw original
                    }
                }
            assertSame(original, actual)
            val html = report.directory.resolve("index.html").readText()
            assertTrue(html.contains("FAILED"))
            assertTrue(html.contains("original &lt;failure&gt;"))
            assertTrue(html.contains("recording unavailable"))
            assertTrue(html.contains("service export failed"))
            assertTrue(html.contains("href=\"artifact-views/application.log.html\""))
            assertTrue(report.directory.resolve("failure.txt").readText().contains("original <failure>"))
        }
    }

    @Test
    fun reportCancelledAndSuccessfulRuns() =
        runAppiumBlockingTest {
        withReportRoot { root ->
            val cancelled = AppiumTestReport("suite", "wasm-firefox", "cancelled", root)
            val cancellation = CancellationException("cancelled")
            assertSame(cancellation, assertFailsWith<CancellationException> { cancelled.run { throw cancellation } })
            assertTrue(cancelled.directory.resolve("index.html").readText().contains("CANCELLED"))
            val success = AppiumTestReport("suite", "wasm-chrome", "success", root)
            success.run { }
            assertTrue(success.directory.resolve("index.html").readText().contains("PASSED"))
            assertFalse(success.directory.resolve("failure.txt").exists())
        }
    }

    @Test
    fun allocateConcurrentAttemptsWithoutOverwrite() =
        runAppiumBlockingTest {
        withReportRoot { root ->
            val reports =
                coroutineScope {
                    (1..12).map {
                        async(Dispatchers.IO) {
                            AppiumTestReport("suite", "android", "same name", root).also { report -> report.run { } }
                        }
                    }.awaitAll()
                }
            assertEquals(12, reports.map { it.directory }.distinct().size)
            val index = root.resolve("index.html").readText()
            reports.forEach { assertTrue(index.contains(it.directory.relativeTo(root).invariantSeparatorsPath)) }
            assertTrue(
                AppiumTestReport("other suite", "android", "same name", root).directory.parentFile !=
                    reports.first().directory.parentFile,
            )
        }
    }

    @Test
    fun cleanPrivateStateAfterSetupFailure() =
        runAppiumBlockingTest {
        withReportRoot { root ->
            val report = AppiumTestReport("suite", "desktop", "setup failure", root)
            val privateSession = report.temporaryPath("session.json")
            val runtime = report.temporaryPath("runtime").also { it.mkdirs() }
            privateSession.writeText("synthetic private state")
            runtime.resolve("storage").writeText("synthetic storage")
            assertFalse(privateSession.toPath().startsWith(root.toPath()))
            assertFailsWith<IllegalStateException> { report.run { error("setup failed") } }
            assertFalse(privateSession.exists())
            assertFalse(runtime.exists())
            val html = report.directory.resolve("index.html").readText()
            assertFalse(html.contains("synthetic private state"))
            assertTrue(html.contains("setup failed"))
        }
    }

    @Test
    fun preserveFailureWhenReportCannotBeWritten() =
        runAppiumBlockingTest {
        withReportRoot { root ->
            val report = AppiumTestReport("suite", "desktop", "broken report", root)
            report.directory.resolve("index.html").mkdir()
            val original = AssertionError("test failure")
            val actual = assertFailsWith<AssertionError> { report.run { throw original } }
            assertSame(original, actual)
            assertEquals(1, actual.suppressed.size)
        }
    }

    @Test
    fun finishReportAfterJobCancellation() =
        runAppiumBlockingTest {
        withReportRoot { root ->
            val report = AppiumTestReport("suite", "android", "cancelled job", root)
            coroutineScope {
                val started = CompletableDeferred<Unit>()
                val job =
                    launch {
                        report.run {
                            started.complete(Unit)
                            awaitCancellation()
                        }
                    }
                started.await()
                job.cancel()
                job.join()
            }
            assertTrue(report.directory.resolve("index.html").readText().contains("CANCELLED"))
        }
    }

    @Test
    fun retainOtherArtifactsWhenDriverCaptureFails() =
        runAppiumBlockingTest {
        withReportRoot { root ->
            val report = AppiumTestReport("suite", "wasm-edge", "capture", root)
            val driver =
                Proxy.newProxyInstance(
                    WebDriver::class.java.classLoader,
                    arrayOf(WebDriver::class.java, TakesScreenshot::class.java),
                ) { _, method, _ ->
                    when (method.name) {
                        "getPageSource" -> "<html>page</html>"
                        "getScreenshotAs" -> error("screenshot unavailable")
                        else -> error("Unexpected driver call")
                    }
                } as WebDriver
            report.run {
                report.captureDriver(driver)
                report.directory.resolve("recording # &.mp4").writeBytes(byteArrayOf(1))
            }
            assertEquals("<html>page</html>", report.directory.resolve("page-source.xml").readText())
            val html = report.directory.resolve("index.html").readText()
            assertTrue(html.contains("screenshot unavailable"))
            assertTrue(html.contains("recording%20%23%20%26.mp4.html"))
            assertTrue(html.contains("PASSED"))
        }
    }

    private suspend fun withReportRoot(block: suspend (File) -> Unit) {
        val parent = File("build/test/appium-report-tests").also { it.mkdirs() }
        val root = Files.createTempDirectory(parent.toPath(), "report-").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
