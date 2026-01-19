@file:Suppress("SameParameterValue")

import com.storyteller_f.a.app.dev.DatabaseConfig
import com.storyteller_f.a.app.dev.startServerByRun
import com.storyteller_f.a.app.dev.startWorkerByRun
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
import org.testcontainers.containers.PostgreSQLContainer
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
            inputElement(driver, """new UiSelector().className("android.widget.EditText")""", privateKeyContent)
            clickElement(driver, """new UiSelector().text("Confirm")""")
            clickElement(driver, """new UiSelector().text("Start sign up")""")
            assertElementVisible(driver, """new UiSelector().description("avatar")""")
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun kotlinx.coroutines.CoroutineScope.runAppiumTest(block: suspend (AndroidDriver) -> Unit) {
        val sessionId = Uuid.random().toHexString()
        val sessionPath = File("build/test/appium/sessions", sessionId).canonicalPath
        PostgreSQLContainer("pgvector/pgvector:pg16").use { container ->
            container.start()
            val dbConfig = DatabaseConfig(
                uri = container.jdbcUrl.replace("jdbc", "r2dbc"),
                driver = "postgresql",
                user = container.username,
                password = container.password
            )
            val processMate = startServerByRun("../..", 8811, sessionPath, dbConfig)
            val workerMate = startWorkerByRun("../..", sessionPath, dbConfig)
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
                processMate?.stop()
                workerMate?.stop()
            }
        }
    }

    private suspend fun generatePrivateKey(): String {
        return getAlgo().generatePemKeyPair().getOrThrow().first
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
