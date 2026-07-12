import io.appium.java_client.android.AndroidDriver

class AndroidAppTestDriver(private val driver: AndroidDriver) : AppTestDriver {

    override suspend fun clickByDescription(description: String) {
        clickElement(driver, """new UiSelector().description("$description")""")
    }

    override suspend fun clickByText(text: String) {
        clickElement(driver, """new UiSelector().text("$text")""")
    }

    override suspend fun clickByTextContaining(text: String) {
        clickElement(driver, """new UiSelector().textContains("$text")""")
    }

    override suspend fun clickByDescriptionContaining(description: String) {
        clickElement(driver, """new UiSelector().descriptionContains("$description")""")
    }

    override suspend fun inputText(text: String) {
        inputElement(driver, """new UiSelector().className("android.widget.EditText")""", text)
    }

    override suspend fun assertVisibleByDescription(description: String) {
        assertElementVisible(driver, """new UiSelector().description("$description")""")
    }

    override suspend fun assertVisibleByText(text: String) {
        assertElementVisible(driver, """new UiSelector().text("$text")""")
    }

    override suspend fun assertNotVisibleByDescription(description: String, timeoutSeconds: Long) {
        assertElementNotVisible(driver, """new UiSelector().description("$description")""", timeoutSeconds)
    }

    override suspend fun assertNotVisibleByText(text: String, timeoutSeconds: Long) {
        assertElementNotVisible(driver, """new UiSelector().text("$text")""", timeoutSeconds)
    }

    override suspend fun navigateBack() {
        driver.navigate().back()
    }

    override suspend fun saveSnapshot(name: String) {
        saveDebugSnapshot(driver, name)
    }
}
