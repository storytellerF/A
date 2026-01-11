@file:Suppress("SameParameterValue")

import com.storyteller_f.a.app.dev.startServerByRun
import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import io.appium.java_client.plugins.storage.StorageClient
import kotlinx.coroutines.runBlocking
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.net.URI
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertTrue

class AppiumTest {
    @Test
    fun `test sign up`() = runBlocking {
        val processMate = startServerByRun("../..", 8811)
        try {
            val url = "http://127.0.0.1:4723"
            val storageClient = StorageClient(URI(url).toURL())
            storageClient.reset()
            val file = File("../../app/android/build/outputs/apk/debug/android-universal-debug.apk")
            val privateKeyContent = File("../../../AData/data/ecdsa/p-system")
                .readText()
                .replace("\r\n", "\n")
            println("apk path ${file.canonicalPath}")
            storageClient.add(file)
            val path = storageClient.list().first().path
            println("file upload to $path")
            val options = UiAutomator2Options().setApp(path).setDeviceName("ATest")
            val remoteAddress = URI(url).toURL()
            val driver = AndroidDriver(remoteAddress, options)
            try {
                clickElement(driver, """new UiSelector().description("avatar")""")
                clickElement(driver, """new UiSelector().text("Sign in")""")
                clickElement(driver, """new UiSelector().text("Go to sign up")""")
                clickElement(driver, """new UiSelector().text("Private Key")""")
                clickElement(driver, """new UiSelector().description("Edit Private Key")""")
                inputElement(driver, """new UiSelector().className("android.widget.EditText")""", privateKeyContent)
                clickElement(driver, """new UiSelector().text("Confirm")""")
                clickElement(driver, """new UiSelector().text("Start sign up")""")
                assertElementVisible(driver, """new UiSelector().description("avatar")""")
            } finally {
                driver.quit()
            }
        } finally {
            processMate?.stop()
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
