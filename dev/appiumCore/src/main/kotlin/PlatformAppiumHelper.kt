/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import io.appium.java_client.AppiumDriver
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import org.testcontainers.containers.Network
import java.io.File
import java.net.URI
import java.util.Base64
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class AppiumTestSetup<T>(val data: T, val injectedSession: InjectedSession? = null)

interface AppiumTestScope {
    val driver: AppTestDriver

    suspend fun assertAsciidocPreviewOpened(source: String)

    /** Restarts the target process while retaining its isolated persistent storage. */
    suspend fun restart()
}

interface PlatformAppiumHelper {
    val capturesExternalAsciidocPreview: Boolean

    suspend fun <T> runTest(
        testName: String,
        target: TargetAppiumHelper,
        captureBrowserOpen: Boolean = false,
        setup: suspend (AppiumPorts) -> AppiumTestSetup<T>,
        block: suspend (AppiumTestScope, T) -> Unit,
    )
}

class AndroidAppiumHelper : PlatformAppiumHelper {
    override val capturesExternalAsciidocPreview = false

    override suspend fun <T> runTest(
        testName: String,
        target: TargetAppiumHelper,
        captureBrowserOpen: Boolean,
        setup: suspend (AppiumPorts) -> AppiumTestSetup<T>,
        block: suspend (AppiumTestScope, T) -> Unit,
    ) {
        // noReset keeps the previous process alive; stop it before replacing its reverse targets.
        withEnglishAndroidAppLocale(target.androidApp.packageName, ::runAdbCommand) {
            runAndroidTestEnvironment { ports ->
                var driver: AndroidDriver? = null
                var testFailed = false
                try {
                    val prepared = setup(ports)
                    prepared.injectedSession?.let { session ->
                        pushInjectedSessionToPrivateDir(
                            packageName = target.androidApp.packageName,
                            testName = testName,
                            content = buildInjectedSessionJson(session),
                        )
                    }
                    val options =
                        UiAutomator2Options()
                            .setAppPackage(target.androidApp.packageName)
                            .setAppActivity(target.androidApp.mainActivityClassName)
                            .setNoReset(true)
                    driver = AndroidDriver(URI("http://127.0.0.1:4723").toURL(), options)
                    driver.startRecordingScreen()
                    block(
                        AndroidAppiumTestScope(
                            driver = AndroidAppTestDriver(driver, ::runAdbCommand),
                            packageName = target.androidApp.packageName,
                            restartApp = { restartApp(target.androidApp) },
                        ),
                        prepared.data,
                    )
                } catch (cancellation: kotlin.coroutines.cancellation.CancellationException) {
                    throw cancellation
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
                    runAdbCommand(listOf("shell", "am", "force-stop", target.androidApp.packageName))
                }
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun runAndroidTestEnvironment(block: suspend (AppiumPorts) -> Unit) {
        val sessionId = Uuid.random().toHexString()
        val hostSessionPath = File("build/test/appium/sessions", sessionId).canonicalPath
        prepareSessionDirectories(hostSessionPath)
        System.setProperty("api.version", "1.44")
        Network.newNetwork().use { network ->
            useLightweightBackendContainers(network) { filesystem ->
                bindAndroidReverse(hostPort = filesystem.getMappedPort(8822), devicePort = 8822)
                val commonEnv = buildContainerEnv() + ("FILESYSTEM_PUBLIC_URL" to "http://127.0.0.1:8822")
                useCliInitContainer(network, commonEnv, hostSessionPath) {
                    useWsContainer(network, commonEnv, hostSessionPath) { wsContainer ->
                        val hostWsPort = wsContainer.getMappedPort(8813)
                        bindAndroidReverse(hostPort = hostWsPort, devicePort = 8813)
                        useServerContainer(network, commonEnv, hostSessionPath) { serverContainer ->
                            val hostServerPort = serverContainer.getMappedPort(8811)
                            bindAndroidReverse(hostPort = hostServerPort, devicePort = 8811)
                            useWorkerContainer(network, commonEnv, hostSessionPath) {
                                block(AppiumPorts(server = hostServerPort, ws = hostWsPort))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun restartApp(app: AppUnderTest) {
        runAdbCommand(listOf("shell", "am", "force-stop", app.packageName))
        runAdbCommand(listOf("shell", "run-as", app.packageName, "rm", "-f", INJECTED_SESSION_FILE))
        runAdbCommand(listOf("shell", "am", "start", "-n", "${app.packageName}/${app.mainActivityClassName}"))
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
            val result =
                runAdbCommandAllowFailure(
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
        val result =
            runAdbCommandAllowFailure(
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
        val logResult =
            runAdbCommandAllowFailure(
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
        val anrResult =
            runAdbCommandAllowFailure(
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
        val process =
            ProcessBuilder(listOf("adb") + args)
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        return AdbCommandResult(exitCode = process.waitFor(), output = output)
    }

    private data class AdbCommandResult(val exitCode: Int, val output: String) {
        fun connectedDeviceSerials(): List<String> =
            output.lineSequence()
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

class DesktopAppiumHelper : PlatformAppiumHelper {
    private val platform = DesktopTestPlatform.current()
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
        ) { driver, prepared, processId, restartApp ->
            block(
                DesktopAppiumTestScope(DesktopAppTestDriver(driver, processId), browserCapture, restartApp),
                prepared.data,
            )
        }
    }

    private suspend fun <T> runDesktopTest(
        testName: String,
        config: DesktopAppiumRuntimeConfig,
        beforeLaunch: suspend (ports: AppiumPorts, sessionFilePath: String) -> T,
        browserCapture: DesktopBrowserCapture?,
        block: suspend (AppiumDriver, T, Long?, suspend () -> DesktopAppTestDriver) -> Unit,
    ) {
        runAppiumTestEnvironment { ports ->
            val sessionFile = File("build/test/appium/tmp/desktop-session-$testName.json")
            val runtimeDir = File("build/test/appium/tmp/desktop-runtime-$testName")
            val logDir = File("build/test/appium-logs/${config.suiteName}").also { it.mkdirs() }
            val appLogFile = File(logDir, "${safeName(testName)}.desktop.log")
            runtimeDir.deleteRecursively()
            runtimeDir.mkdirs()
            appLogFile.delete()

            val setup = beforeLaunch(ports, sessionFile.canonicalPath)
            val launchScript =
                buildLaunchScript(
                    LaunchScriptContext(
                        ports = ports,
                        sessionFile = sessionFile,
                        runtimeDir = runtimeDir,
                        appLogFile = appLogFile,
                        runtimeClasspath = resolveRuntimeClasspath(config),
                        config = config,
                        browserCapture = browserCapture,
                    ),
                )

            var driver: AppiumDriver? = null
            try {
                val caps = platform.createCapabilities(launchScript, config.windowWaitSeconds)
                val serverUrl = System.getenv("APPIUM_DESKTOP_SERVER_URL") ?: "http://127.0.0.1:4723"
                driver = AppiumDriver(URI(serverUrl).toURL(), caps)
                block(driver, setup, readDesktopProcessId(sessionFile)) {
                    driver?.quit()
                    sessionFile.delete()
                    val restarted = AppiumDriver(URI(serverUrl).toURL(), caps)
                    driver = restarted
                    DesktopAppTestDriver(restarted, readDesktopProcessId(sessionFile))
                }
            } catch (cancellation: kotlin.coroutines.cancellation.CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                DesktopAppiumFailureDumper.dumpOnFailure(
                    context =
                    DesktopAppiumFailureDumper.Context(
                        suiteName = config.suiteName,
                        appLabel = config.appLabel,
                        testName = testName,
                        sessionFile = sessionFile,
                        logDir = logDir,
                        appLogFile = appLogFile,
                    ),
                    driver = driver,
                    throwable = throwable,
                )
                throw throwable
            } finally {
                driver?.quit()
                launchScript.delete()
                sessionFile.delete()
                File("${sessionFile.canonicalPath}.pid").delete()
                runtimeDir.deleteRecursively()
            }
        }
    }

    private fun writeSessionFile(path: String, sessionJson: String) {
        File(path).also { it.parentFile?.mkdirs() }.writeText(sessionJson)
    }

    private fun readDesktopProcessId(sessionFile: File): Long? =
        if (platform == DesktopTestPlatform.Windows) {
        File("${sessionFile.canonicalPath}.pid").readText().trim().toLong()
    } else {
        null
    }

    private fun resolveRuntimeClasspath(config: DesktopAppiumRuntimeConfig): String =
        config.runtimeClasspathCandidates.firstOrNull { it.isFile }
            ?.readText()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: error(config.runtimeClasspathErrorMessage)

    private data class LaunchScriptContext(
        val ports: AppiumPorts,
        val sessionFile: File,
        val runtimeDir: File,
        val appLogFile: File,
        val runtimeClasspath: String,
        val config: DesktopAppiumRuntimeConfig,
        val browserCapture: DesktopBrowserCapture?,
    )

    private fun buildLaunchScript(context: LaunchScriptContext): File {
        val runtimeDir = context.runtimeDir
        val appLogFile = context.appLogFile
        val runtimeClasspath = context.runtimeClasspath
        val config = context.config
        val browserCapture = context.browserCapture
        val javaExec = resolveDesktopJava(platform)
        val atspiClasspath =
            listOfNotNull(
                runtimeClasspath,
                "/usr/share/java/java-atk-wrapper.jar".takeIf { platform == DesktopTestPlatform.Linux },
                File(
                    DesktopTestLauncher::class.java
                        .protectionDomain.codeSource.location.toURI(),
                ).canonicalPath
                    .takeIf { platform == DesktopTestPlatform.Windows },
            )
                .joinToString(File.pathSeparator)
        val prefsDir = runtimeDir.resolve("prefs").also { it.mkdirs() }
        val tmpDir = runtimeDir.resolve("tmp").also { it.mkdirs() }
        val scriptDir = File("build/test/appium/tmp").also { it.mkdirs() }
        val script =
            File.createTempFile(
                config.scriptPrefix,
                if (platform == DesktopTestPlatform.Windows) ".args" else ".sh",
                scriptDir,
            )
        val arguments = buildLaunchArguments(context, atspiClasspath, prefsDir, tmpDir)
        if (platform == DesktopTestPlatform.Windows) {
            script.writeText(buildJavaArgumentFile(arguments))
            return script
        }
        val browserEnvironment =
            browserCapture?.let {
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

    private fun buildLaunchArguments(
        context: LaunchScriptContext,
        runtimeClasspath: String,
        prefsDir: File,
        tmpDir: File,
    ): List<String> {
        val ports = context.ports
        val sessionFile = context.sessionFile
        val appLogFile = context.appLogFile
        val config = context.config
        val arguments =
            buildList {
                add("-Duser.language=en")
                add("-Duser.country=US")
                add("--add-opens=java.desktop/sun.awt=ALL-UNNAMED")
                add("--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED")
                add("-Dappium.server.url=http://127.0.0.1:${ports.server}")
                if (config.includeWsUrl) {
                    add("-Dappium.ws.url=ws://127.0.0.1:${ports.ws}")
                }
                add("-Dappium.session.file=${sessionFile.canonicalPath}")
                add("-Djava.util.prefs.userRoot=${prefsDir.canonicalPath}")
                add("-Dapp.protected.storage.dir=${File(prefsDir, "protected-storage").canonicalPath}")
                if (platform == DesktopTestPlatform.Linux) {
                    add("-Djava.io.tmpdir=${tmpDir.canonicalPath}")
                }
                add("-XX:ErrorFile=${appLogFile.parentFile.canonicalPath}/hs_err_pid%p.log")
                if (platform == DesktopTestPlatform.Linux) {
                    add("-Djavax.accessibility.assistive_technologies=org.GNOME.Accessibility.AtkWrapper")
                }
                if (platform == DesktopTestPlatform.Windows) {
                    val bridgeAgent =
                        requireNotNull(System.getProperty("desktop.windows.bridge.agent")) {
                            "Desktop Windows bridge agent is not configured; run the desktop appiumTest task"
                        }
                    add("-javaagent:$bridgeAgent")
                    add("-Djava.util.prefs.PreferencesFactory=${DesktopTestPreferences::class.java.name}")
                    add("-Dappium.desktop.tmpdir=${tmpDir.canonicalPath}")
                    add("-Dappium.desktop.log=${appLogFile.canonicalPath}")
                    add("-Dappium.desktop.pid.file=${sessionFile.canonicalPath}.pid")
                }
                add("-cp")
                add(runtimeClasspath)
                if (platform == DesktopTestPlatform.Windows) add(DesktopTestLauncher::class.java.name)
                add(config.mainClassName)
            }
        return arguments
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
    val appLogPath = appLogFile.path.toBashPath()
    val appLogDirectory = requireNotNull(appLogFile.parentFile).path.toBashPath()
    val argumentLines =
        arguments.joinToString(" \\\n") {
            "              \"${it.escapeForDoubleQuotedShell()}\""
        }
    return buildString {
        appendLine("#!/bin/bash")
        appendLine("""mkdir -p "$appLogDirectory"""")
        if (browserEnvironment.isNotEmpty()) {
            appendLine(browserEnvironment)
        }
        append("exec \"")
        append(javaExec.escapeForDoubleQuotedShell())
        appendLine("\" \\")
        append(argumentLines)
        appendLine(" \\")
        append("""  >> "$appLogPath" 2>&1""")
    }
}

private fun String.toBashPath(): String = replace('\\', '/')

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
    private val restartApp: suspend () -> Unit,
) : AppiumTestScope {
    override suspend fun assertAsciidocPreviewOpened(source: String) {
        driver.assertAsciidocPreviewOpened(packageName, source)
    }

    override suspend fun restart() = restartApp()
}

private class DesktopAppiumTestScope(
    override var driver: DesktopAppTestDriver,
    private val browserCapture: DesktopBrowserCapture?,
    private val restartApp: suspend () -> DesktopAppTestDriver,
) : AppiumTestScope {
    override suspend fun assertAsciidocPreviewOpened(source: String) {
        browserCapture?.assertOpenedAsciidocPreview(source)
            ?: driver.assertAsciidocPreviewOpened()
    }

    override suspend fun restart() {
        driver = restartApp()
    }
}
