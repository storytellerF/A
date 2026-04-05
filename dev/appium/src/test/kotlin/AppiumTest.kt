@file:Suppress("SameParameterValue")

import com.storyteller_f.shared.getAlgo
import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import io.appium.java_client.plugins.storage.StorageClient
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TestName
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.net.URI
import java.time.Duration
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class AppiumTest {
    @get:Rule
    val name = TestName()

    @Test
    fun `test sign up`() = runTest(timeout = 10.minutes) {
        runAppiumTest { driver ->
            val privateKeyContent = generatePrivateKey()
            clickElement(driver, """new UiSelector().description("avatar")""")
            clickElement(driver, """new UiSelector().text("Sign in")""")
            clickElement(driver, """new UiSelector().text("Go to sign up")""")
            clickElement(driver, """new UiSelector().text("Private Key")""")
            clickElement(driver, """new UiSelector().description("Edit Private Key")""")
            inputElement(
                driver,
                """new UiSelector().className("android.widget.EditText")""",
                privateKeyContent
            )
            clickElement(driver, """new UiSelector().text("Confirm")""")
            clickElement(driver, """new UiSelector().text("Start sign up")""")
            assertElementVisible(driver, """new UiSelector().description("avatar")""")
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun runAppiumTest(block: suspend (AndroidDriver) -> Unit) {
        val sessionId = Uuid.random().toHexString()
        val hostSessionPath = File("build/test/appium/sessions", sessionId).canonicalPath
        prepareSessionDirectories(hostSessionPath)
        val containerDataPath = "/appium-session"
        System.setProperty("api.version", "1.44")
        Network.newNetwork().use { network ->
            PostgreSQLContainer("pgvector/pgvector:pg16").apply {
                withNetwork(network)
                withNetworkAliases("appium-postgres")
            }.use { container ->
                container.start()
                val commonEnv = buildContainerEnv(containerDataPath, container)
                GenericContainer(DockerImageName.parse("a-server:latest")).apply {
                    withNetwork(network)
                    withEnv(commonEnv)
                    withFileSystemBind(hostSessionPath, containerDataPath, BindMode.READ_WRITE)
                    withExposedPorts(8811)
                    withStartupAttempts(3)
                }.use { serverContainer ->
                    serverContainer.start()
                    val hostServerPort = serverContainer.getMappedPort(8811)
                    bindAndroidReverse(hostPort = hostServerPort, devicePort = 8811)
                    GenericContainer(DockerImageName.parse("a-worker:latest")).apply {
                        withNetwork(network)
                        withEnv(commonEnv)
                        withFileSystemBind(hostSessionPath, containerDataPath, BindMode.READ_WRITE)
                        withStartupAttempts(3)
                    }.use { workerContainer ->
                        workerContainer.start()
                        var driver: AndroidDriver? = null
                        try {
                            val url = "http://127.0.0.1:4723"
                            val storageClient = StorageClient(URI(url).toURL())
                            storageClient.reset()
                            val file = File("../../app/android/build/outputs/apk/debug/android-universal-debug.apk")

                            println("apk path ${file.canonicalPath}")
                            storageClient.add(file)
                            val path = storageClient.list().first().path
                            println("file upload to $path")
                            val options = UiAutomator2Options().setApp(path).setDeviceName("device-test")
                            val remoteAddress = URI(url).toURL()
                            driver = AndroidDriver(remoteAddress, options)
                            driver.startRecordingScreen()
                            block(driver)
                        } finally {
                            if (driver != null) {
                                try {
                                    val content = driver.stopRecordingScreen()
                                    val decoded = Base64.getDecoder().decode(content)
                                    val dir = File("build/test/appium-records/${this@AppiumTest.javaClass.simpleName}")
                                    dir.mkdirs()
                                    val file = File(dir, "${name.methodName}.mp4")
                                    file.writeBytes(decoded)
                                } catch (e: Exception) {
                                    println(e)
                                }
                                driver.quit()
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun generatePrivateKey(): String {
        return getAlgo().generatePemKeyPair().getOrThrow().first
    }
}

private fun prepareSessionDirectories(sessionPath: String) {
    val sessionDir = File(sessionPath)
    sessionDir.mkdirs()
    File(sessionDir, "logs").mkdirs()
    File(sessionDir, "lucene").mkdirs()
    File(sessionDir, "files").mkdirs()
}

private fun buildContainerEnv(
    containerDataPath: String,
    postgresContainer: PostgreSQLContainer<*>
): Map<String, String> {
    val envFromFile = parseEnvFile(File("../../cloud/server/src/test/resources/test.env"))
    val databaseUri = "r2dbc:postgresql://appium-postgres:5432/${postgresContainer.databaseName}"
    return envFromFile + mapOf(
        "BUILD_TYPE" to "test",
        "FLAVOR" to "dev",
        "SERVER_PORT" to "8811",
        "SERVER_URL" to "http://10.0.2.2:8811",
        "WS_SERVER_URL" to "ws://10.0.2.2:8811",
        "DATABASE_URI" to databaseUri,
        "DATABASE_DRIVER" to "postgresql",
        "DATABASE_USER" to postgresContainer.username,
        "DATABASE_PASS" to postgresContainer.password,
        "LUCENE_BASE_PATH" to "$containerDataPath/lucene",
        "FILE_SYSTEM_MEDIA_PATH" to "$containerDataPath/files",
        "LOG_PATH" to "$containerDataPath/logs",
        "INIT_ENABLE" to "false"
    )
}

private fun parseEnvFile(file: File): Map<String, String> {
    if (!file.exists()) return emptyMap()
    return file.readLines().asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val split = line.split("=", limit = 2)
            split.firstOrNull()?.takeIf { it.isNotBlank() }?.let { key ->
                key to split.getOrElse(1) { "" }
            }
        }
        .toMap()
}

private fun bindAndroidReverse(hostPort: Int, devicePort: Int) {
    val script = sequenceOf(
        File("scripts/android_scripts/forward-android-devices.sh"),
        File("../../scripts/android_scripts/forward-android-devices.sh")
    ).firstOrNull { it.exists() }
        ?: error("forward-android-devices.sh not found")

    val process = ProcessBuilder(
        "sh",
        script.canonicalPath,
        devicePort.toString(),
        hostPort.toString()
    )
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    val exitCode = process.waitFor()
    check(exitCode == 0) {
        if (output.isNotEmpty()) {
            "Failed to execute forward-android-devices.sh: $output"
        } else {
            "Failed to execute forward-android-devices.sh"
        }
    }
}

private fun assertElementVisible(driver: AndroidDriver, selector: String) {
    val wait = WebDriverWait(driver, Duration.ofSeconds(100))
    val element = wait.until(ExpectedConditions.presenceOfElementLocated(AppiumBy.androidUIAutomator(selector)))
    assertTrue(element.isDisplayed)
}

private fun clickElement(
    driver: AndroidDriver,
    selector: String,
    seconds: Long = 100
) {
    val locator = AppiumBy.androidUIAutomator(selector)
    if (seconds > 0) {
        WebDriverWait(driver, Duration.ofSeconds(seconds)).until(ExpectedConditions.presenceOfElementLocated(locator))
    }
    driver.findElement(locator).click()
}

private fun inputElement(
    driver: AndroidDriver,
    selector: String,
    input: String,
    seconds: Long = 100
) {
    val locator = AppiumBy.androidUIAutomator(selector)
    if (seconds > 0) {
        WebDriverWait(driver, Duration.ofSeconds(seconds)).until(ExpectedConditions.presenceOfElementLocated(locator))
    }
    driver.findElement(locator).sendKeys(input)
}
