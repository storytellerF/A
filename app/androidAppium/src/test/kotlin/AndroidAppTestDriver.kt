import io.appium.java_client.android.AndroidDriver

class AndroidAppTestDriver(private val driver: AndroidDriver) : AppTestDriver {

    override suspend fun clickByDescription(description: String) {
        clickElement(driver, """new UiSelector().description("$description")""")
    }

    override suspend fun clickByText(text: String) {
        clickElement(driver, """new UiSelector().text("$text")""")
    }

    override suspend fun clickByTextContaining(text: String) {
        runCatching {
            clickElement(driver, """new UiSelector().textContains("$text")""", seconds = 5)
        }.getOrElse {
            clickElement(driver, """new UiSelector().descriptionContains("$text")""")
        }
    }

    override suspend fun inputText(text: String) {
        inputElement(driver, """new UiSelector().className("android.widget.EditText")""", text)
    }

    override suspend fun assertVisible(description: String?, text: String?) {
        val selector = when {
            description != null -> """new UiSelector().description("$description")"""
            text != null -> """new UiSelector().text("$text")"""
            else -> error("description or text must be provided")
        }
        assertElementVisible(driver, selector)
    }

    override suspend fun assertNotVisible(text: String, timeoutSeconds: Long) {
        assertElementNotVisible(driver, """new UiSelector().text("$text")""", timeoutSeconds)
    }

    override suspend fun navigateBack() {
        driver.navigate().back()
    }

    override suspend fun saveSnapshot(name: String) {
        saveDebugSnapshot(driver, name)
    }
}
