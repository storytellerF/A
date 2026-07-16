import io.appium.java_client.AppiumDriver
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.openqa.selenium.remote.DesiredCapabilities
import org.testcontainers.containers.Network
import java.io.File
import java.net.URI
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class DesktopAppiumRuntimeConfig(
    val suiteName: String,
    val appLabel: String,
    val mainClassName: String,
    val runtimeClasspathCandidates: List<File>,
    val runtimeClasspathErrorMessage: String,
    val scriptPrefix: String,
    val includeWsUrl: Boolean,
    val windowWaitSeconds: Long = 30L,
)

val appDesktopRuntimeConfig = DesktopAppiumRuntimeConfig(
    suiteName = "DesktopAppiumTest",
    appLabel = "Desktop app",
    mainClassName = "com.storyteller_f.a.app.JvmMainKt",
    runtimeClasspathCandidates = listOf(
        File("../../app/desktopApp/build/appium/runtimeClasspath.txt"),
        File("app/desktopApp/build/appium/runtimeClasspath.txt"),
    ),
    runtimeClasspathErrorMessage = "Desktop runtime classpath not found. " +
        "Run :app:desktopApp:writeAppiumRuntimeClasspath first.",
    scriptPrefix = "desktop-appium-",
    includeWsUrl = true,
)

val panelDesktopRuntimeConfig = DesktopAppiumRuntimeConfig(
    suiteName = "DesktopPanelAppiumTest",
    appLabel = "Desktop panel app",
    mainClassName = "com.storyteller_f.a.panel.PanelMainKt",
    runtimeClasspathCandidates = listOf(
        File("../../panel/desktopApp/build/appium/runtimeClasspath.txt"),
        File("panel/desktopApp/build/appium/runtimeClasspath.txt"),
    ),
    runtimeClasspathErrorMessage = "Panel desktop runtime classpath not found. " +
        "Run :panel:desktopApp:writeAppiumRuntimeClasspath first.",
    scriptPrefix = "desktop-panel-appium-",
    includeWsUrl = false,
)

fun runDesktopAppiumBlockingTest(block: suspend () -> Unit) = runBlocking {
    withTimeout(10.minutes) { block() }
}

suspend fun <T> runConfiguredDesktopAppiumTestWithSetup(
    testName: String,
    config: DesktopAppiumRuntimeConfig,
    beforeLaunch: suspend (ports: AppiumPorts, sessionFilePath: String) -> T,
    block: suspend (AppiumDriver, T) -> Unit,
) {
    runDesktopContainerTest { ports ->
        val sessionFile = File("build/test/appium/tmp/desktop-session-$testName.json")
        val runtimeDir = File("build/test/appium/tmp/desktop-runtime-$testName")
        val logDir = File("build/test/appium-logs/${config.suiteName}").also { it.mkdirs() }
        val appLogFile = File(logDir, "${safeDesktopAppiumName(testName)}.desktop.log")
        runtimeDir.deleteRecursively()
        runtimeDir.mkdirs()
        sessionFile.parentFile?.mkdirs()
        appLogFile.delete()

        val setup = beforeLaunch(ports, sessionFile.canonicalPath)
        val launchScript = buildDesktopAppiumLaunchScript(
            ports = ports,
            sessionFile = sessionFile,
            runtimeDir = runtimeDir,
            appLogFile = appLogFile,
            runtimeClasspath = resolveDesktopRuntimeClasspath(config),
            config = config,
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

fun writeDesktopSessionFile(path: String, sessionJson: String) {
    File(path).also { it.parentFile?.mkdirs() }.writeText(sessionJson)
}

@OptIn(ExperimentalUuidApi::class)
private suspend fun runDesktopContainerTest(block: suspend (AppiumPorts) -> Unit) {
    val sessionId = Uuid.random().toHexString()
    val hostSessionPath = File("build/test/appium/sessions", sessionId).canonicalPath
    prepareSessionDirectories(hostSessionPath)
    val containerDataPath = "/appium-session"
    System.setProperty("api.version", "1.44")
    Network.newNetwork().use { network ->
        useDatabaseContainer(network) { db ->
            val env = buildContainerEnv(containerDataPath, db)
            useCliInitContainer(network, env, hostSessionPath, containerDataPath) {
                useWsContainer(network, env, hostSessionPath, containerDataPath) { ws ->
                    val wsPort = ws.getMappedPort(8813)
                    useServerContainer(network, env, hostSessionPath, containerDataPath) { server ->
                        val serverPort = server.getMappedPort(8811)
                        useWorkerContainer(network, env, hostSessionPath, containerDataPath) {
                            block(AppiumPorts(server = serverPort, ws = wsPort))
                        }
                    }
                }
            }
        }
    }
}

private fun resolveDesktopRuntimeClasspath(config: DesktopAppiumRuntimeConfig): String =
    config.runtimeClasspathCandidates.firstOrNull { it.isFile }
        ?.readText()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: error(config.runtimeClasspathErrorMessage)

private fun buildDesktopAppiumLaunchScript(
    ports: AppiumPorts,
    sessionFile: File,
    runtimeDir: File,
    appLogFile: File,
    runtimeClasspath: String,
    config: DesktopAppiumRuntimeConfig,
): File {
    val javaExec = System.getenv("APP_DESKTOP_TEST_JAVA") ?: "java"
    val atspiClasspath = listOf(runtimeClasspath, "/usr/share/java/java-atk-wrapper.jar")
        .joinToString(File.pathSeparator)
    val prefsDir = runtimeDir.resolve("prefs").also { it.mkdirs() }
    val tmpDir = runtimeDir.resolve("tmp").also { it.mkdirs() }
    val scriptDir = File("build/test/appium/tmp").also { it.mkdirs() }
    val script = File.createTempFile(config.scriptPrefix, ".sh", scriptDir)
    val arguments = buildList {
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
    val argumentLines = arguments.joinToString(" \\\n") {
        "              \"${it.escapeForDoubleQuotedShell()}\""
    }
    script.writeText(
        """
        #!/bin/bash
        mkdir -p "${appLogFile.parentFile.canonicalPath}"
        exec "$javaExec" \
$argumentLines \
          >> "${appLogFile.canonicalPath}" 2>&1
        """.trimIndent()
    )
    script.setExecutable(true)
    return script
}

fun safeDesktopAppiumName(value: String): String =
    value.replace(Regex("[^a-zA-Z0-9._-]"), "_")

private fun String.escapeForDoubleQuotedShell(): String =
    replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("${'$'}", "\\${'$'}")
        .replace("`", "\\`")
