/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import kotlinx.coroutines.delay
import org.openqa.selenium.OutputType
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.time.Duration
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class AndroidAppTestDriver(private val driver: AndroidDriver, private val runAdbCommand: (List<String>) -> String) :
    AppTestDriver {
    suspend fun assertAsciidocPreviewOpened(appPackageName: String, expectedSource: String) {
        assertTrue(expectedSource.isNotBlank(), "AsciiDoc source must not be blank")
        val expectedTitle =
            expectedSource.lineSequence()
                .firstOrNull { it.startsWith("= ") }
                ?.removePrefix("= ")
                ?: error("AsciiDoc source must include a document title")
        var resumedActivity = ""
        var previewPollCount = 0
        while (previewPollCount < 30) {
            resumedActivity =
                runAdbCommand(
                    listOf("shell", "dumpsys", "activity", "activities"),
                ).lineSequence()
                    .firstOrNull { "topResumedActivity=" in it }
                    .orEmpty()
            if (resumedActivity.contains(appPackageName)) {
                break
            }
            previewPollCount += 1
            delay(500.milliseconds)
        }
        assertTrue(
            resumedActivity.contains(appPackageName),
            "Expected the app to remain foreground for the AsciiDoc preview, " +
                "but the resumed activity is $resumedActivity",
        )
        assertElementVisible("""new UiSelector().text("AsciiDoc preview")""")
        assertElementVisible("""new UiSelector().text("$expectedTitle")""")
    }

    override suspend fun clickByDescription(description: String) {
        clickElement("""new UiSelector().description("$description")""")
    }

    override suspend fun clickByText(text: String) {
        clickElement("""new UiSelector().text("$text")""")
    }

    override suspend fun clickByTextContaining(text: String) {
        clickElement("""new UiSelector().textContains("$text")""")
    }

    override suspend fun inputText(text: String) {
        inputLastElement("""new UiSelector().className("android.widget.EditText")""", text)
    }

    override suspend fun assertVisibleByDescription(description: String) {
        assertElementVisible("""new UiSelector().description("$description")""")
    }

    override suspend fun assertVisibleByText(text: String) {
        assertElementVisible("""new UiSelector().text("$text")""")
    }

    override suspend fun assertVisibleByTextContaining(text: String) {
        assertElementVisible("""new UiSelector().textContains("$text")""")
    }

    override suspend fun assertNotVisibleByText(text: String, timeoutSeconds: Long) {
        assertElementNotVisible("""new UiSelector().text("$text")""", timeoutSeconds)
    }

    override suspend fun navigateBack() {
        driver.navigate().back()
    }

    override suspend fun saveSnapshot(name: String) {
        saveDebugSnapshot(name)
    }

    private fun assertElementVisible(selector: String) {
        try {
            val element =
                WebDriverWait(driver, Duration.ofSeconds(UI_WAIT_SECONDS))
                    .until(ExpectedConditions.presenceOfElementLocated(AppiumBy.androidUIAutomator(selector)))
            assertTrue(element.isDisplayed)
        } catch (throwable: Throwable) {
            runCatching {
                saveDebugSnapshot("assert-visible-failed-${System.currentTimeMillis()}")
            }
            throw throwable
        }
    }

    private fun assertElementNotVisible(selector: String, seconds: Long) {
        try {
            val locator = AppiumBy.androidUIAutomator(selector)
            WebDriverWait(driver, Duration.ofSeconds(seconds)).until {
                driver.findElements(locator).isEmpty()
            }
        } catch (throwable: Throwable) {
            runCatching {
                saveDebugSnapshot("assert-not-visible-failed-${System.currentTimeMillis()}")
            }
            throw throwable
        }
    }

    private fun clickElement(selector: String, seconds: Long = UI_WAIT_SECONDS) {
        try {
            val locator = AppiumBy.androidUIAutomator(selector)
            if (seconds > 0) {
                WebDriverWait(driver, Duration.ofSeconds(seconds))
                    .until(ExpectedConditions.presenceOfElementLocated(locator))
            }
            driver.findElement(locator).click()
        } catch (throwable: Throwable) {
            runCatching {
                saveDebugSnapshot("click-failed-${System.currentTimeMillis()}")
            }
            throw throwable
        }
    }

    private fun inputLastElement(selector: String, input: String, seconds: Long = UI_WAIT_SECONDS) {
        try {
            val locator = AppiumBy.androidUIAutomator(selector)
            val elements =
                if (seconds > 0) {
                    WebDriverWait(driver, Duration.ofSeconds(seconds)).until {
                        driver.findElements(locator).takeIf(List<*>::isNotEmpty)
                    }
                } else {
                    driver.findElements(locator)
                }
            elements.last().sendKeys(input)
        } catch (throwable: Throwable) {
            runCatching {
                saveDebugSnapshot("input-failed-${System.currentTimeMillis()}")
            }
            throw throwable
        }
    }

    private fun saveDebugSnapshot(name: String) {
        val outputDir = File("build/test/appium-debug/AppiumTest")
        outputDir.mkdirs()
        val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        File(outputDir, "$safeName.xml").writeText(driver.pageSource.orEmpty())
        File(outputDir, "$safeName.png").writeBytes(driver.getScreenshotAs(OutputType.BYTES))
    }

    private companion object {
        const val UI_WAIT_SECONDS = 15L
    }
}
