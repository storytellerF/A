import io.appium.java_client.AppiumDriver
import java.io.File
import java.time.Instant

object DesktopAppiumFailureDumper {
    fun dumpOnFailure(
        suiteName: String,
        appLabel: String,
        testName: String,
        sessionFile: File,
        driver: AppiumDriver?,
        throwable: Throwable,
        logDir: File,
        appLogFile: File,
    ) {
        val outputDir = File("build/test/appium-debug/$suiteName", safeName(testName))
            .resolve(Instant.now().toString().replace(Regex("[^a-zA-Z0-9._-]"), "_"))
        outputDir.mkdirs()

        File(outputDir, "failure.txt").writeText(
            buildString {
                appendLine(throwable::class.qualifiedName ?: throwable::class.java.name)
                appendLine(throwable.message.orEmpty())
                appendLine()
                appendLine(throwable.stackTraceToString())
            }
        )

        dumpPageSource(driver, outputDir)
        dumpAwtAccessibilityTree(appLabel, sessionFile, outputDir)
        copyDesktopLogs(appLabel, logDir, appLogFile, outputDir)
    }

    private fun dumpPageSource(driver: AppiumDriver?, outputDir: File) {
        if (driver == null) return
        runCatching {
            File(outputDir, "appium-page-source.xml").writeText(driver.pageSource.orEmpty())
        }.onFailure {
            File(outputDir, "appium-page-source.error.txt").writeText(it.stackTraceToString())
        }
    }

    private fun dumpAwtAccessibilityTree(appLabel: String, sessionFile: File, outputDir: File) {
        val pid = findDesktopAppPid(sessionFile)
        if (pid == null) {
            File(outputDir, "awt-accessibility-tree.error.txt")
                .writeText("$appLabel process not found for session file: ${sessionFile.canonicalPath}\n")
            return
        }

        val agentPath = System.getProperty("desktop.accessibility.dump.agent")
        if (agentPath.isNullOrBlank()) {
            File(outputDir, "awt-accessibility-tree.error.txt")
                .writeText("System property desktop.accessibility.dump.agent is not configured\n")
            return
        }

        val output = File(outputDir, "awt-accessibility-tree.txt")
        runCatching {
            DesktopAccessibilityDumpAttacher.dump(pid.toString(), File(agentPath).canonicalPath, output.canonicalPath)
            waitForDump(output)
        }.onFailure {
            File(outputDir, "awt-accessibility-tree.error.txt").writeText(it.stackTraceToString())
        }
    }

    private fun findDesktopAppPid(sessionFile: File): Long? {
        val marker = "-Dappium.session.file=${sessionFile.canonicalPath}"
        return ProcessHandle.allProcesses()
            .filter { process ->
                process.info().commandLine().orElse("").contains(marker)
            }
            .mapToLong { it.pid() }
            .findFirst()
            .orElse(-1L)
            .takeIf { it > 0L }
    }

    private fun waitForDump(output: File) {
        repeat(50) {
            if (output.isFile && output.length() > 0) return
            Thread.sleep(100)
        }
        error("Timed out waiting for AWT accessibility dump: ${output.canonicalPath}")
    }

    private fun copyDesktopLogs(appLabel: String, logDir: File, appLogFile: File, outputDir: File) {
        if (appLogFile.isFile) {
            appLogFile.copyTo(File(outputDir, appLogFile.name), overwrite = true)
        } else {
            File(outputDir, "desktop-log.error.txt")
                .writeText("$appLabel log not found: ${appLogFile.canonicalPath}\n")
        }

        logDir.listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile && (
                    file.name.startsWith("hs_err_pid") ||
                        file.name.startsWith("replay_pid") ||
                        file.extension == "mdmp"
                    )
            }
            .forEach { file ->
                file.copyTo(File(outputDir, file.name), overwrite = true)
            }
    }

    private fun safeName(value: String): String =
        value.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}
