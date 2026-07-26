import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.OutputType
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.time.Duration

/** Drives Compose/Wasm through its transparent Appium-only HTML semantics overlay. */
class WasmAppTestDriver(private val driver: WebDriver) : AppTestDriver {
    override suspend fun clickByDescription(description: String) = click(descriptionLocator(description))

    override suspend fun clickByText(text: String) = click(textLocator(text))

    override suspend fun clickByTextContaining(text: String) = click(
        By.cssSelector("[data-appium-text*='${text.cssAttributeValue()}']"),
    )

    override suspend fun clickByDescriptionContaining(description: String) = click(
        By.cssSelector("[aria-label*='${description.cssAttributeValue()}']"),
    )

    override suspend fun inputText(text: String) {
        try {
            val expectedValue = text.replace("\r\n", "\n").replace('\r', '\n')
            val inputLocator = By.cssSelector("[data-appium-input='true']")
            val input = findOnScreen(inputLocator)
            (driver as JavascriptExecutor).executeScript(
                """
                arguments[0].value = arguments[1];
                arguments[0].dispatchEvent(new Event('input', { bubbles: true }));
                """.trimIndent(),
                input,
                expectedValue,
            )
            wait.until {
                inputLengthAttributeMatchesValue("data-appium-input-delivered-length")
            }
            wait.until {
                inputLengthAttributeMatchesValue("data-appium-input-compose-length")
            }
            val actualValue = (driver as JavascriptExecutor).executeScript(
                "return document.querySelector('[data-appium-input=true]')?.value || '';"
            ).toString()
            check(actualValue == expectedValue) {
                val mismatch = expectedValue.indices.firstOrNull { index ->
                    index >= actualValue.length || expectedValue[index] != actualValue[index]
                } ?: minOf(expectedValue.length, actualValue.length)
                val expectedCode = expectedValue.getOrNull(mismatch)?.code
                val actualCode = actualValue.getOrNull(mismatch)?.code
                "Wasm HTML input changed the supplied text at index $mismatch; " +
                    "expectedCode=$expectedCode, actualCode=$actualCode, " +
                    "expectedLength=${expectedValue.length}, actualLength=${actualValue.length}"
            }
        } catch (throwable: Throwable) {
            runCatching { saveFailureSnapshot("input") }
            throw throwable
        }
    }

    private fun inputLengthAttributeMatchesValue(attribute: String): Boolean {
        return (driver as JavascriptExecutor).executeScript(
            """
            const input = document.querySelector('[data-appium-input=true]');
            return input?.getAttribute(arguments[0]) === String(input.value.length);
            """.trimIndent(),
            attribute,
        ) == true
    }

    override suspend fun assertVisibleByDescription(description: String) = assertVisible(
        descriptionLocator(description)
    )

    override suspend fun assertVisibleByText(text: String) = assertVisible(textLocator(text))

    override suspend fun assertNotVisibleByDescription(description: String, timeoutSeconds: Long) =
        assertNotVisible(descriptionLocator(description), timeoutSeconds)

    override suspend fun assertNotVisibleByText(text: String, timeoutSeconds: Long) =
        assertNotVisible(textLocator(text), timeoutSeconds)

    override suspend fun navigateBack() {
        val dismissDialogLocator = By.cssSelector(
            "[aria-label='dialog'][data-appium-action='true']"
        )
        if (driver.findElements(dismissDialogLocator).any(::isOnScreen)) {
            click(dismissDialogLocator)
        } else {
            click(descriptionLocator("back"))
        }
    }

    override suspend fun saveSnapshot(name: String) {
        val outputDir = File("build/test/appium-debug/WasmAppiumTest").also(File::mkdirs)
        val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        File(outputDir, "$safeName.html").writeText(driver.pageSource.orEmpty())
        (driver as? TakesScreenshot)?.getScreenshotAs(
            OutputType.FILE
        )?.copyTo(File(outputDir, "$safeName.png"), overwrite = true)
    }

    private fun click(locator: By) {
        try {
            val element = findOnScreen(locator)
            if (element.getAttribute("data-appium-action") == "true") {
                (driver as JavascriptExecutor).executeScript("arguments[0].click();", element)
            } else {
                Actions(driver).moveToElement(element).click().perform()
            }
            waitForUiCommit()
        } catch (throwable: Throwable) {
            runCatching { saveFailureSnapshot("click") }
            throw throwable
        }
    }

    private fun waitForUiCommit() {
        (driver as JavascriptExecutor).executeAsyncScript(
            """
            const done = arguments[arguments.length - 1];
            const startedAt = performance.now();
            let previousSignature = '';
            let stableFrames = 0;
            const check = () => {
              const signature = Array.from(
                document.querySelectorAll('#appium-html-semantics > [data-appium-semantics]')
              ).map(element => {
                const rect = element.getBoundingClientRect();
                return [
                  element.getAttribute('data-appium-semantics'),
                  element.getAttribute('data-appium-text'),
                  element.getAttribute('aria-label'),
                  Math.round(rect.left),
                  Math.round(rect.top),
                  Math.round(rect.width),
                  Math.round(rect.height)
                ].join(':');
              }).join('|');
              stableFrames = signature === previousSignature ? stableFrames + 1 : 0;
              previousSignature = signature;
              if ((stableFrames >= 4 && performance.now() - startedAt >= 100) ||
                  performance.now() - startedAt >= 1500) {
                done();
              } else {
                window.requestAnimationFrame(check);
              }
            };
            window.requestAnimationFrame(check);
            """.trimIndent()
        )
    }

    private fun assertVisible(locator: By) {
        try {
            findOnScreen(locator)
        } catch (throwable: Throwable) {
            runCatching { saveFailureSnapshot("assert-visible") }
            throw throwable
        }
    }

    private fun assertNotVisible(locator: By, timeoutSeconds: Long) {
        try {
            WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds)).until {
                driver.findElements(locator).none(::isOnScreen)
            }
        } catch (throwable: Throwable) {
            runCatching { saveFailureSnapshot("assert-not-visible") }
            throw throwable
        }
    }

    private fun descriptionLocator(description: String) = By.cssSelector(
        "[aria-label='${description.cssAttributeValue()}']"
    )

    private fun textLocator(text: String) = By.cssSelector("[data-appium-text='${text.cssAttributeValue()}']")

    private fun findOnScreen(locator: By): WebElement = wait.until {
        driver.findElements(locator).firstOrNull(::isOnScreen)
    }

    private fun isOnScreen(element: WebElement): Boolean = runCatching {
        (driver as JavascriptExecutor).executeScript(
            """
            const rect = arguments[0].getBoundingClientRect();
            const centerX = rect.left + rect.width / 2;
            const centerY = rect.top + rect.height / 2;
            return rect.width > 0 && rect.height > 0 &&
              centerX >= 0 && centerX <= window.innerWidth &&
              centerY >= 0 && centerY <= window.innerHeight;
            """.trimIndent(),
            element,
        ) == true
    }.getOrDefault(false)

    private fun saveFailureSnapshot(prefix: String) {
        val outputDir = File("build/test/appium-debug/WasmAppiumTest").also(File::mkdirs)
        val name = "$prefix-${System.currentTimeMillis()}"
        File(outputDir, "$name.html").writeText(driver.pageSource.orEmpty())
        File(outputDir, "$name-active-element.txt").writeText(activeElementDescription())
        File(outputDir, "$name-local-storage.txt").writeText(localStorageDescription())
        File(outputDir, "$name-browser-errors.txt").writeText(browserErrorsDescription())
        (driver as? TakesScreenshot)?.getScreenshotAs(
            OutputType.FILE
        )?.copyTo(File(outputDir, "$name.png"), overwrite = true)
    }

    private fun activeElementDescription(): String = runCatching {
        val activeElement = driver.switchTo().activeElement()
        "tag=${activeElement.tagName}; type=${activeElement.getAttribute("type")}; " +
            "contenteditable=${activeElement.getAttribute("contenteditable")}; " +
            "outerHTML=${activeElement.getAttribute("outerHTML")}"
    }.getOrElse { throwable ->
        "unavailable=${throwable.message}"
    }

    private fun localStorageDescription(): String = runCatching {
        (driver as JavascriptExecutor).executeScript(
            "return JSON.stringify(Object.fromEntries(Object.entries(window.localStorage)));"
        ).toString()
    }.getOrElse { throwable ->
        "unavailable=${throwable.message}"
    }

    private fun browserErrorsDescription(): String = runCatching {
        (driver as JavascriptExecutor).executeScript(
            "return JSON.stringify(window.appiumErrors || []);"
        ).toString()
    }.getOrElse { throwable ->
        "unavailable=${throwable.message}"
    }

    private val wait get() = WebDriverWait(driver, Duration.ofSeconds(UI_WAIT_SECONDS))

    private fun String.cssAttributeValue(): String = replace("\\", "\\\\").replace("'", "\\'")

    private companion object {
        const val UI_WAIT_SECONDS = 15L
    }
}
