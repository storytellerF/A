import com.storyteller_f.a.client.core.AuthKey
import com.storyteller_f.a.client.core.SimplePassHolder
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.buildWebSocketUrl
import com.storyteller_f.a.client.core.createSimpleUserSessionManager
import com.storyteller_f.a.client.core.defaultClientConfigure
import com.storyteller_f.a.client.core.getClient
import com.storyteller_f.a.client.core.userSignIn
import com.storyteller_f.a.client.core.userSignUp
import io.appium.java_client.AppiumDriver
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.rules.TestName
import org.openqa.selenium.remote.DesiredCapabilities
import org.testcontainers.containers.Network
import java.io.File
import java.net.URI
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal const val DESKTOP_APP_WINDOW_WAIT_SECONDS = 30L

abstract class DesktopAppiumTestBase {

    @get:Rule
    val name = TestName()

    protected fun runAppiumBlockingTest(block: suspend () -> Unit) = runBlocking {
        withTimeout(10.minutes) { block() }
    }

    protected suspend fun runDesktopType2Test(block: suspend (AppiumDriver) -> Unit) {
        runDesktopType1Test(beforeLaunch = { _, _ -> }) { driver, _ -> block(driver) }
    }

    protected suspend fun <T> runDesktopType1Test(
        beforeLaunch: suspend (ports: AppiumPorts, sessionFilePath: String) -> T,
        block: suspend (AppiumDriver, T) -> Unit,
    ) {
        runDesktopContainerTest { ports ->
            val sessionFile = File("build/test/appium/tmp/desktop-session-${name.methodName}.json")
            val runtimeDir = File("build/test/appium/tmp/desktop-runtime-${name.methodName}")
            val logDir = File("build/test/appium-logs/DesktopAppiumTest").also { it.mkdirs() }
            val appLogFile = File(logDir, "${safeName(name.methodName)}.desktop.log")
            runtimeDir.deleteRecursively()
            runtimeDir.mkdirs()
            sessionFile.parentFile?.mkdirs()
            appLogFile.delete()

            val setup = beforeLaunch(ports, sessionFile.canonicalPath)
            val launchScript = buildLaunchScript(ports, sessionFile, runtimeDir, appLogFile, resolveDesktopRuntimeClasspath())

            var driver: AppiumDriver? = null
            try {
                val caps = DesiredCapabilities().apply {
                    setCapability("platformName", "linux")
                    setCapability("appium:automationName", "linux")
                    setCapability("appium:app", launchScript.canonicalPath)
                    setCapability("appium:newCommandTimeout", DESKTOP_APP_WINDOW_WAIT_SECONDS)
                }
                driver = AppiumDriver(URI("http://127.0.0.1:4723").toURL(), caps)
                block(driver, setup)
            } catch (throwable: Throwable) {
                DesktopAppiumFailureDumper.dumpOnFailure(
                    suiteName = "DesktopAppiumTest",
                    appLabel = "Desktop app",
                    testName = name.methodName,
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

    protected fun writeSessionFile(path: String, sessionJson: String) {
        File(path).also { it.parentFile?.mkdirs() }.writeText(sessionJson)
    }

    protected suspend fun createPreRegisteredSession(ports: AppiumPorts): InjectedSession {
        return createPreRegisteredInjectedSession { authKey, passHolder ->
            val manager = createDesktopApiSessionManager(ports, passHolder)
            try {
                manager.userSignUp(authKey, passHolder)
            } finally {
                manager.client.close()
            }
        }
    }

    protected suspend fun createAuthenticatedSession(ports: AppiumPorts): AuthenticatedSession {
        val session = createPreRegisteredSession(ports)
        val passHolder = SimplePassHolder()
        val manager = createDesktopApiSessionManager(ports, passHolder)
        manager.userSignIn(
            AuthKey.P256(session.pemPrivateKey, session.derPrivateKey, session.derPublicKey),
            passHolder
        )
        return AuthenticatedSession(session, manager)
    }

    private fun resolveDesktopRuntimeClasspath(): String = sequenceOf(
        File("../../app/desktopApp/build/appium/runtimeClasspath.txt"),
        File("app/desktopApp/build/appium/runtimeClasspath.txt"),
    ).firstOrNull { it.isFile }
        ?.readText()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: error("Desktop runtime classpath not found. Run :app:desktopApp:writeAppiumRuntimeClasspath first.")

    private fun buildLaunchScript(
        ports: AppiumPorts,
        sessionFile: File,
        runtimeDir: File,
        appLogFile: File,
        runtimeClasspath: String
    ): File {
        val javaExec = System.getenv("APP_DESKTOP_TEST_JAVA") ?: "java"
        val atspiClasspath = listOf(runtimeClasspath, "/usr/share/java/java-atk-wrapper.jar")
            .joinToString(File.pathSeparator)
        val prefsDir = runtimeDir.resolve("prefs").also { it.mkdirs() }
        val tmpDir = runtimeDir.resolve("tmp").also { it.mkdirs() }
        val scriptDir = File("build/test/appium/tmp").also { it.mkdirs() }
        val script = File.createTempFile("desktop-appium-", ".sh", scriptDir)
        script.writeText(
            """
            #!/bin/bash
            mkdir -p "${appLogFile.parentFile.canonicalPath}"
            exec "$javaExec" \
              "-Dappium.server.url=http://127.0.0.1:${ports.server}" \
              "-Dappium.ws.url=ws://127.0.0.1:${ports.ws}" \
              "-Dappium.session.file=${sessionFile.canonicalPath}" \
              "-Djava.util.prefs.userRoot=${prefsDir.canonicalPath}" \
              "-Djava.io.tmpdir=${tmpDir.canonicalPath}" \
              "-XX:ErrorFile=${appLogFile.parentFile.canonicalPath}/hs_err_pid%p.log" \
              "-Djavax.accessibility.assistive_technologies=org.GNOME.Accessibility.AtkWrapper" \
              "-cp" "$atspiClasspath" \
              com.storyteller_f.a.app.JvmMainKt \
              >> "${appLogFile.canonicalPath}" 2>&1
            """.trimIndent()
        )
        script.setExecutable(true)
        return script
    }

    private fun safeName(value: String): String =
        value.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}

private fun createDesktopApiSessionManager(ports: AppiumPorts, passHolder: SimplePassHolder): UserSessionManager =
    createSimpleUserSessionManager(
        buildWebSocketUrl("ws://127.0.0.1:${ports.ws}"),
        AcceptAllCookiesStorage(),
        passHolder,
        { model, cookieStorage ->
            getClient {
                defaultClientConfigure(cookieStorage, model, passHolder, "http://127.0.0.1:${ports.server}")
            }
        }
    ) { _, _, _ -> }
