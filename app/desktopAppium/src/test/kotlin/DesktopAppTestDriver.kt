import io.appium.java_client.AppiumDriver
import org.openqa.selenium.By
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.time.Duration

class DesktopAppTestDriver(private val driver: AppiumDriver) : AppTestDriver {

    override suspend fun clickByDescription(description: String) {
        waitAndClick(By.xpath("//*[@name='$description']"))
    }

    override suspend fun clickByText(text: String) {
        waitAndClick(By.xpath("//*[@value='$text' or @name='$text']"))
    }

    override suspend fun clickByTextContaining(text: String) {
        waitAndClick(By.xpath("//*[contains(@value,'$text') or contains(@name,'$text')]"))
    }

    override suspend fun clickByDescriptionContaining(description: String) {
        waitAndClick(By.xpath("//*[contains(@name,'$description')]"))
    }

    private fun waitAndClick(locator: By) {
        WebDriverWait(driver, Duration.ofSeconds(UI_WAIT_SECONDS))
            .until(ExpectedConditions.presenceOfElementLocated(locator))
        driver.findElement(locator).click()
    }

    override suspend fun inputText(text: String) {
        val wait = WebDriverWait(driver, Duration.ofSeconds(UI_WAIT_SECONDS))
        val element = wait.until(
            ExpectedConditions.presenceOfElementLocated(By.xpath("//text-field"))
        )
        element.sendKeys(text)
    }

    override suspend fun assertVisible(description: String?, text: String?) {
        val locator = when {
            description != null -> By.xpath("//*[@name='$description']")
            text != null -> By.xpath("//*[@value='$text' or @name='$text']")
            else -> error("description or text must be provided")
        }
        val wait = WebDriverWait(driver, Duration.ofSeconds(UI_WAIT_SECONDS))
        wait.until(ExpectedConditions.presenceOfElementLocated(locator))
    }

    override suspend fun assertNotVisible(text: String, timeoutSeconds: Long) {
        val locator = By.xpath("//*[@value='$text' or @name='$text']")
        WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds)).until {
            driver.findElements(locator).isEmpty()
        }
    }

    override suspend fun navigateBack() {
        driver.navigate().back()
    }

    override suspend fun saveSnapshot(name: String) {
        val outputDir = File("build/test/appium-debug/DesktopAppiumTest")
        outputDir.mkdirs()
        val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        File(outputDir, "$safeName.xml").writeText(driver.pageSource.orEmpty())
    }

    companion object {
        private const val UI_WAIT_SECONDS = 15L
    }
}
