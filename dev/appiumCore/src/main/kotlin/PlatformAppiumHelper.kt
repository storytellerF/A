import io.appium.java_client.AppiumDriver
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import org.openqa.selenium.remote.DesiredCapabilities
import org.testcontainers.containers.Network
import java.io.File
import java.net.URI
import java.util.Base64
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class AppiumTestSetup<T>(
    val data: T,
    val injectedSession: InjectedSession? = null,
)

interface AppiumTestScope {
    val driver: AppTestDriver

    suspend fun assertAsciidocPreviewOpened(source: String)
}

abstract class PlatformAppiumHelper {
    abstract val capturesExternalAsciidocPreview: Boolean

    abstract suspend fun <T> runTest(
        testName: String,
        target: TargetAppiumHelper,
        captureBrowserOpen: Boolean = false,
        setup: suspend (AppiumPorts) -> AppiumTestSetup<T>,
        block: suspend (AppiumTestScope, T) -> Unit,
    )
}

class AndroidAppiumHelper : PlatformAppiumHelper() {
    override val capturesExternalAsciidocPreview = false

    override suspend fun <T> runTest(
        testName: String,
        target: TargetAppiumHelper,
        captureBrowserOpen: Boolean,
        setup: suspend (AppiumPorts) -> AppiumTestSetup<T>,
        block: suspend (AppiumTestScope, T) -> Unit,
    ) {
        runAndroidTestEnvironment { ports ->
            var driver: AndroidDriver? = null
            var testFailed = false
            try {
                clearAppData(target.androidApp.packageName)
                val prepared = setup(ports)
                prepared.injectedSession?.let { session ->
                    pushInjectedSessionToPrivateDir(
                        packageName = target.androidApp.packageName,
                        testName = testName,
                        content = buildInjectedSessionJson(session),
                    )
                }
                val options = UiAutomator2Options()
                    .setAppPackage(target.androidApp.packageName)
                    .setAppActivity(target.androidApp.mainActivityClassName)
                    .setNoReset(true)
                driver = AndroidDriver(URI("http://127.0.0.1:4723").toURL(), options)
                driver.startRecordingScreen()
                block(
                    AndroidAppiumTestScope(
                        driver = AndroidAppTestDriver(driver, ::runAdbCommand),
                        packageName = target.androidApp.packageName,
                    ),
                    prepared.data,
                )
            } catch (throwable: Throwable) {
                testFailed = true
                throw throwable
            } finally {
                driver?.let { activeDriver ->
                    saveRecording(activeDriver, target.suiteName, testName)
                    copyAppLogToBuild(target.suiteName, testName, target.androidApp.packageName)
                    if (testFailed) {
                        collectBugreport(target.suiteName, testName)
                    }
                    runCatching { activeDriver.quit() }
                }
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun runAndroidTestEnvironment(block: suspend (AppiumPorts) -> Unit) {
        val sessionId = Uuid.random().toHexString()
        val hostSessionPath = File("build/test/appium/sessions", sessionId).canonicalPath
        prepareSessionDirectories(hostSessionPath)
        val containerDataPath = "/appium-session"
        System.setProperty("api.version", "1.44")
        Network.newNetwork().use { network ->
            useDatabaseContainer(network) { databaseContainer ->
                val commonEnv = buildContainerEnv(containerDataPath, databaseContainer)
                useCliInitContainer(network, commonEnv, hostSessionPath, containerDataPath) {
                    useWsContainer(network, commonEnv, hostSessionPath, containerDataPath) { wsContainer ->
                        val hostWsPort = wsContainer.getMappedPort(8813)
                        bindAndroidReverse(hostPort = hostWsPort, devicePort = 8813)
                        useServerContainer(network, commonEnv, hostSessionPath, containerDataPath) { serverContainer ->
                            val hostServerPort = serverContainer.getMappedPort(8811)
                            bindAndroidReverse(hostPort = hostServerPort, devicePort = 8811)
                            useWorkerContainer(network, commonEnv, hostSessionPath, containerDataPath) {
                                block(AppiumPorts(server = hostServerPort, ws = hostWsPort))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun clearAppData(packageName: String) {
        runAdbCommand(listOf("shell", "pm", "clear", packageName))
    }

    private fun pushInjectedSessionToPrivateDir(packageName: String, testName: String, content: String) {
        val file = File("build/test/appium/tmp/injected-session-$testName.json")
        file.parentFile?.mkdirs()
        file.writeText(content)
        runAdbCommand(listOf("push", file.canonicalPath, INJECTED_SESSION_TEMP_PATH))
        runAdbCommand(listOf("shell", "run-as", packageName, "mkdir", "-p", INJECTED_SESSION_DIR))
        runAdbCommand(
            listOf(
                "shell",
                "run-as",
                packageName,
                "cp",
                INJECTED_SESSION_TEMP_PATH,
                INJECTED_SESSION_FILE,
            ),
        )
        runAdbCommand(listOf("shell", "run-as", packageName, "cat", INJECTED_SESSION_FILE))
    }

    private fun bindAndroidReverse(hostPort: Int, devicePort: Int) {
        val devices = runAdbCommandAllowFailure(listOf("devices")).connectedDeviceSerials()
        check(devices.isNotEmpty()) { "No Android device available for adb reverse" }
        devices.forEach { device ->
            val result = runAdbCommandAllowFailure(
                listOf("-s", device, "reverse", "tcp:$devicePort", "tcp:$hostPort"),
            )
            check(result.exitCode == 0) {
                "Failed to bind android reverse for $device tcp:$devicePort -> tcp:$hostPort: " +
                    result.output.ifBlank { "exitCode=${result.exitCode}" }
            }
            waitForAndroidReverse(device, devicePort)
        }
    }

    private fun waitForAndroidReverse(device: String, devicePort: Int) {
        val result = runAdbCommandAllowFailure(
            listOf("-s", device, "shell", "nc", "-z", "127.0.0.1", devicePort.toString()),
        )
        if (result.exitCode == 0) return

        val reverseList = runAdbCommandAllowFailure(listOf("-s", device, "reverse", "--list")).output
        error(
            "android reverse $device tcp:$devicePort is not reachable. " +
                "adb reverse --list: ${reverseList.ifBlank { "<empty>" }}",
        )
    }

    private fun copyAppLogToBuild(suiteName: String, testName: String, packageName: String) {
        val outputDir = File("build/test/appium-logs/$suiteName")
        outputDir.mkdirs()
        val outputFile = File(outputDir, "$testName.log")
        val logResult = runAdbCommandAllowFailure(
            listOf("exec-out", "run-as", packageName, "cat", "files/logs/$APP_LOG_FILE_NAME"),
        )
        if (logResult.exitCode == 0 && logResult.output.isNotBlank()) {
            outputFile.writeText(logResult.output)
        } else {
            outputFile.writeText(
                "Failed to export app log: ${logResult.output.ifBlank { "exitCode=${logResult.exitCode}" }}",
            )
        }
        copyAnrTracesToBuild(outputDir, testName, packageName)
    }

    private fun collectBugreport(suiteName: String, testName: String) {
        val outputDir = File("build/test/appium-logs/$suiteName")
        outputDir.mkdirs()
        val outputFile = File(outputDir, "$testName.bugreport.zip")
        runAdbCommandAllowFailure(listOf("bugreport", outputFile.canonicalPath))
    }

    private fun copyAnrTracesToBuild(outputDir: File, testName: String, packageName: String) {
        val anrResult = runAdbCommandAllowFailure(
            listOf("shell", "dumpsys", "activity", "exit-info", packageName),
        )
        if (anrResult.exitCode == 0 && anrResult.output.isNotBlank()) {
            File(outputDir, "$testName.exit-info.txt").writeText(anrResult.output)
        }
    }

    private fun saveRecording(driver: AndroidDriver, suiteName: String, testName: String) {
        runCatching {
            val content = driver.stopRecordingScreen()
            val decoded = Base64.getDecoder().decode(content)
            val directory = File("build/test/appium-records/$suiteName").also { it.mkdirs() }
            File(directory, "$testName.mp4").writeBytes(decoded)
        }
    }

    private fun runAdbCommand(args: List<String>): String {
        val result = runAdbCommandAllowFailure(args)
        check(result.exitCode == 0) {
            val arguments = args.joinToString(" ")
            if (result.output.isNotEmpty()) {
                "adb command failed ($arguments): ${result.output}"
            } else {
                "adb command failed ($arguments)"
            }
        }
        return result.output
    }

    private fun runAdbCommandAllowFailure(args: List<String>): AdbCommandResult {
        val home = System.getProperty("user.home")
        val process = ProcessBuilder(listOf("$home/Android/Sdk/platform-tools/adb") + args)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        return AdbCommandResult(exitCode = process.waitFor(), output = output)
    }

    private data class AdbCommandResult(
        val exitCode: Int,
        val output: String,
    ) {
        fun connectedDeviceSerials(): List<String> = output.lineSequence()
            .drop(1)
            .map { it.trim().split(Regex("\\s+")) }
            .filter { it.size >= 2 && it[1] == "device" }
            .map { it[0] }
            .toList()
    }

    private companion object {
        const val APP_LOG_FILE_NAME = "appium-app.log"
        const val INJECTED_SESSION_TEMP_PATH = "/data/local/tmp/appium-session-session.json"
        const val INJECTED_SESSION_DIR = "files/appium-session"
        const val INJECTED_SESSION_FILE = "files/appium-session/session.json"
    }
}

class DesktopAppiumHelper : PlatformAppiumHelper() {
    override val capturesExternalAsciidocPreview = false

    override suspend fun <T> runTest(
        testName: String,
        target: TargetAppiumHelper,
        captureBrowserOpen: Boolean,
        setup: suspend (AppiumPorts) -> AppiumTestSetup<T>,
        block: suspend (AppiumTestScope, T) -> Unit,
    ) {
        val browserCapture = if (captureBrowserOpen) DesktopBrowserCapture.create(testName) else null
        runDesktopTest(
            testName = testName,
            config = target.desktopRuntimeConfig,
            beforeLaunch = { ports, sessionFilePath ->
                val prepared = setup(ports)
                prepared.injectedSession?.let { session ->
                    writeSessionFile(sessionFilePath, buildInjectedSessionJson(session))
                }
                prepared
            },
            browserCapture = browserCapture,
        ) { driver, prepared ->
            block(DesktopAppiumTestScope(DesktopAppTestDriver(driver), browserCapture), prepared.data)
        }
    }

    private suspend fun <T> runDesktopTest(
        testName: String,
        config: DesktopAppiumRuntimeConfig,
        beforeLaunch: suspend (ports: AppiumPorts, sessionFilePath: String) -> T,
        browserCapture: DesktopBrowserCapture?,
        block: suspend (AppiumDriver, T) -> Unit,
    ) {
        runAppiumTestEnvironment { ports ->
            val sessionFile = File("build/test/appium/tmp/desktop-session-$testName.json")
            val runtimeDir = File("build/test/appium/tmp/desktop-runtime-$testName")
            val logDir = File("build/test/appium-logs/${config.suiteName}").also { it.mkdirs() }
            val appLogFile = File(logDir, "${safeName(testName)}.desktop.log")
            runtimeDir.deleteRecursively()
            runtimeDir.mkdirs()
            sessionFile.parentFile?.mkdirs()
            appLogFile.delete()

            val setup = beforeLaunch(ports, sessionFile.canonicalPath)
            val launchScript = buildLaunchScript(
                ports = ports,
                sessionFile = sessionFile,
                runtimeDir = runtimeDir,
                appLogFile = appLogFile,
                runtimeClasspath = resolveRuntimeClasspath(config),
                config = config,
                browserCapture = browserCapture,
            )

            var driver: AppiumDriver? = null
            try {
                val caps = DesiredCapabilities().apply {
                    setCapability("platformName", "linux")
                    setCapability("appium:automationName", "linux")
                    setCapability("appium:app", launchScript.canonicalPath)
                    setCapability("appium:newCommandTimeout", config.windowWaitSeconds)
                }
                driver = AppiumDriver(URI("http://127.0.0.1:4723").toURL(), caps)
                block(driver, setup)
            } catch (throwable: Throwable) {
                DesktopAppiumFailureDumper.dumpOnFailure(
                    suiteName = config.suiteName,
                    appLabel = config.appLabel,
                    testName = testName,
                    sessionFile = sessionFile,
                    driver = driver,
                    throwable = throwable,
                    logDir = logDir,
                    appLogFile = appLogFile,
                )
                throw throwable
            } finally {
                driver?.quit()
                launchScript.delete()
                sessionFile.delete()
                runtimeDir.deleteRecursively()
            }
        }
    }

    private fun writeSessionFile(path: String, sessionJson: String) {
        File(path).also { it.parentFile?.mkdirs() }.writeText(sessionJson)
    }

    private fun resolveRuntimeClasspath(config: DesktopAppiumRuntimeConfig): String =
        config.runtimeClasspathCandidates.firstOrNull { it.isFile }
            ?.readText()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: error(config.runtimeClasspathErrorMessage)

    private fun buildLaunchScript(
        ports: AppiumPorts,
        sessionFile: File,
        runtimeDir: File,
        appLogFile: File,
        runtimeClasspath: String,
        config: DesktopAppiumRuntimeConfig,
        browserCapture: DesktopBrowserCapture?,
    ): File {
        val javaExec = System.getenv("APP_DESKTOP_TEST_JAVA") ?: "java"
        val atspiClasspath = listOf(runtimeClasspath, "/usr/share/java/java-atk-wrapper.jar")
            .joinToString(File.pathSeparator)
        val prefsDir = runtimeDir.resolve("prefs").also { it.mkdirs() }
        val tmpDir = runtimeDir.resolve("tmp").also { it.mkdirs() }
        val scriptDir = File("build/test/appium/tmp").also { it.mkdirs() }
        val script = File.createTempFile(config.scriptPrefix, ".sh", scriptDir)
        val arguments = buildList {
            add("--add-opens=java.desktop/sun.awt=ALL-UNNAMED")
            add("--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED")
            add("-Dappium.server.url=http://127.0.0.1:${ports.server}")
            if (config.includeWsUrl) {
                add("-Dappium.ws.url=ws://127.0.0.1:${ports.ws}")
            }
            add("-Dappium.session.file=${sessionFile.canonicalPath}")
            add("-Djava.util.prefs.userRoot=${prefsDir.canonicalPath}")
            add("-Djava.io.tmpdir=${tmpDir.canonicalPath}")
            add("-XX:ErrorFile=${appLogFile.parentFile.canonicalPath}/hs_err_pid%p.log")
            add("-Djavax.accessibility.assistive_technologies=org.GNOME.Accessibility.AtkWrapper")
            add("-cp")
            add(atspiClasspath)
            add(config.mainClassName)
        }
        val browserEnvironment = browserCapture?.let {
            """
            export BROWSER="${it.command.canonicalPath.escapeForDoubleQuotedShell()}"
            export PATH="${it.command.parentFile.canonicalPath.escapeForDoubleQuotedShell()}:${'$'}PATH"
            """.trimIndent()
        }.orEmpty()
        script.writeText(
            buildDesktopLaunchScriptContent(
                javaExec = javaExec,
                arguments = arguments,
                appLogFile = appLogFile,
                browserEnvironment = browserEnvironment,
            ),
        )
        script.setExecutable(true)
        return script
    }

    companion object {
        fun safeName(value: String): String = value.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}

internal fun buildDesktopLaunchScriptContent(
    javaExec: String,
    arguments: List<String>,
    appLogFile: File,
    browserEnvironment: String,
): String {
    val argumentLines =
        arguments.joinToString(" \\\n") {
            "              \"${it.escapeForDoubleQuotedShell()}\""
        }
    return buildString {
        appendLine("#!/bin/bash")
        appendLine("""mkdir -p "${appLogFile.parentFile.canonicalPath}"""")
        if (browserEnvironment.isNotEmpty()) {
            appendLine(browserEnvironment)
        }
        append("exec \"")
        append(javaExec.escapeForDoubleQuotedShell())
        appendLine("\" \\")
        append(argumentLines)
        appendLine(" \\")
        append("""  >> "${appLogFile.canonicalPath}" 2>&1""")
    }
}

private fun String.escapeForDoubleQuotedShell(): String {
    val escapedBackslashes = replace("\\", "\\\\")
    return escapedBackslashes
        .replace("\"", "\\\"")
        .replace("${'$'}", "\\${'$'}")
        .replace("`", "\\`")
}

private class AndroidAppiumTestScope(
    override val driver: AndroidAppTestDriver,
    private val packageName: String,
) : AppiumTestScope {
    override suspend fun assertAsciidocPreviewOpened(source: String) {
        driver.assertAsciidocPreviewOpened(packageName, source)
    }
}

private class DesktopAppiumTestScope(
    override val driver: DesktopAppTestDriver,
    private val browserCapture: DesktopBrowserCapture?,
) : AppiumTestScope {
    override suspend fun assertAsciidocPreviewOpened(source: String) {
        browserCapture?.assertOpenedAsciidocPreview(source)
            ?: driver.assertAsciidocPreviewOpened()
    }
}
