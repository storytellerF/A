@file:Suppress("SameParameterValue")

import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import io.appium.java_client.plugins.storage.StorageClient
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.net.URI
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertTrue

class AppiumTest {
    @Test
    fun `test sign in`() {
        val url = "http://127.0.0.1:4723"
        val storageClient = StorageClient(URI(url).toURL())
        ProcessBuilder("")
        storageClient.reset()
        val file = File("../../app/composeApp/build/outputs/apk/release/composeApp-universal-release.apk")
        val privateKeyContent = File("../../../AData/data/ecdsa/p-system")
            .readText()
            .replace("\r\n", "\n")
        println("apk path ${file.canonicalPath}")
        storageClient.add(file)
        val path = storageClient.list().first().path
        println("file upload to $path")
        val options = UiAutomator2Options()
            .setApp(path)
        val remoteAddress = URI(url).toURL()
        val driver = AndroidDriver(remoteAddress, options)
        try {
            clickElement(driver, """new UiSelector().description("avatar")""")
            clickElement(driver, """new UiSelector().text("Sign in")""")
            clickElement(driver, """new UiSelector().text("Private Key")""")
            inputElement(driver, """new UiSelector().className("android.widget.EditText")""", privateKeyContent)
            clickElement(driver, """new UiSelector().text("Start sign in")""")
            assertElementVisible(driver, """new UiSelector().description("avatar")""")
        } finally {
            driver.quit()
        }
    }
}

private fun assertElementVisible(driver: AndroidDriver, selector: String) {
    val wait = WebDriverWait(driver, Duration.ofSeconds(10))
    val element = wait.until(ExpectedConditions.presenceOfElementLocated(AppiumBy.androidUIAutomator(selector)))
    assertTrue(element.isDisplayed)
}

private fun clickElement(
    driver: AndroidDriver,
    selector: String
) {
    driver.findElement(AppiumBy.androidUIAutomator(selector)).click()
}

private fun inputElement(
    driver: AndroidDriver,
    selector: String,
    input: String
) {
    driver.findElement(AppiumBy.androidUIAutomator(selector)).sendKeys(input)
}
