/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import io.appium.java_client.AppiumDriver
import org.openqa.selenium.By
import org.openqa.selenium.StaleElementReferenceException
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.time.Duration

class DesktopAppTestDriver(private val driver: AppiumDriver, windowsProcessId: Long? = null) : AppTestDriver {
    private val platform = DesktopTestPlatform.current()
    private val windows =
        if (platform == DesktopTestPlatform.Windows) {
            DesktopWindowsWindowTracker(driver, requireNotNull(windowsProcessId))
        } else {
            null
        }
    private val nameAttribute = if (platform == DesktopTestPlatform.Windows) "Name" else "name"
    private val valueAttribute = if (platform == DesktopTestPlatform.Windows) "Value" else "value"
    suspend fun assertAsciidocPreviewOpened() {
        assertVisibleByDescription("close preview")
    }

    override suspend fun clickByDescription(description: String) {
        clickElement { findDescriptionElement(description) }
    }

    override suspend fun clickByText(text: String) {
        clickElement { findTextElement(text, false) }
    }

    override suspend fun clickByTextContaining(text: String) {
        clickElement { findTextElement(text, true) }
    }

    private fun findDescriptionElement(description: String): org.openqa.selenium.WebElement =
        WebDriverWait(driver, Duration.ofSeconds(UI_WAIT_SECONDS)).until {
            windows?.synchronizeWindow()
            driver.findElements(By.xpath("//*[@$nameAttribute='$description']")).firstOrNull()
                // Incoming content follows the outgoing scene during Compose navigation transitions.
                ?: driver.findElements(By.xpath("//*[${namePredicate(description)}]")).lastOrNull()
        }

    private fun clickElement(findElement: () -> org.openqa.selenium.WebElement) {
        WebDriverWait(driver, Duration.ofSeconds(UI_WAIT_SECONDS))
            .ignoring(StaleElementReferenceException::class.java)
            .until {
                performElementClick(findElement())
                true
            }
    }

    private fun performElementClick(element: org.openqa.selenium.WebElement) {
        if (platform == DesktopTestPlatform.Windows) {
            driver.executeScript(
                "windows: invoke",
                mapOf("elementId" to (element as org.openqa.selenium.remote.RemoteWebElement).id),
            )
        } else {
            element.click()
        }
    }

    override suspend fun inputText(text: String) {
        if (platform == DesktopTestPlatform.Windows) {
            inputWindowsText(text)
            return
        }
        val elements =
            WebDriverWait(driver, Duration.ofSeconds(UI_WAIT_SECONDS)).until {
                windows?.synchronizeWindow()
                driver.findElements(By.xpath("//text-field")).takeIf(List<*>::isNotEmpty)
            }
        elements.last().sendKeys(text)
    }

    private fun inputWindowsText(text: String) {
        WebDriverWait(driver, Duration.ofSeconds(UI_WAIT_SECONDS)).until {
            windows?.synchronizeWindow()
            val element = driver.findElements(By.xpath("//Edit")).lastOrNull() ?: return@until false
            driver.executeScript(
                "windows: invoke",
                mapOf("elementId" to (element as org.openqa.selenium.remote.RemoteWebElement).id),
            )
            element.getAttribute("States").orEmpty().split(",").contains("focused")
        }
        driver.executeScript("windows: keys", mapOf("actions" to buildWindowsTextActions(text), "forceUnicode" to true))
    }

    override suspend fun assertVisibleByDescription(description: String) {
        findDescriptionElement(description)
    }

    override suspend fun assertVisibleByText(text: String) {
        findTextElement(text, false)
    }

    override suspend fun assertVisibleByTextContaining(text: String) {
        findTextElement(text, true)
    }

    private fun findTextElement(text: String, contains: Boolean): org.openqa.selenium.WebElement =
        WebDriverWait(driver, Duration.ofSeconds(UI_WAIT_SECONDS)).until {
            windows?.synchronizeWindow()
            val predicate = if (contains) "contains(@$nameAttribute,'$text')" else namePredicate(text)
            driver.findElements(By.xpath("//*[@$nameAttribute='$text']")).lastOrNull()
                ?: driver.findElements(By.xpath("//*[$predicate]")).lastOrNull()
                ?: if (platform == DesktopTestPlatform.Windows) {
                    driver.findElements(By.xpath("//Text | //Edit | //Unknown")).lastOrNull { element ->
                        val value = element.text
                        if (contains) value.contains(text) else value == text
                    }
                } else {
                    val valuePredicate = if (contains) "contains(@value,'$text')" else "@value='$text'"
                    driver.findElements(By.xpath("//*[$valuePredicate]")).lastOrNull()
                }
        }

    private fun namePredicate(value: String): String =
        if (platform == DesktopTestPlatform.Windows) {
        "contains(concat(', ', @Name, ', '), ', $value, ')"
    } else {
        "@name='$value'"
    }

    override suspend fun assertNotVisibleByText(text: String, timeoutSeconds: Long) {
        assertNotVisible(By.xpath("//*[${namePredicate(text)} or @$valueAttribute='$text']"), timeoutSeconds)
    }

    private fun assertNotVisible(locator: By, timeoutSeconds: Long) {
        WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds)).until {
            windows?.synchronizeWindow()
            driver.findElements(locator).isEmpty()
        }
    }

    override suspend fun navigateBack() {
        if (platform == DesktopTestPlatform.Windows) {
            clickByDescription("back")
        } else {
            driver.navigate().back()
        }
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

internal fun buildWindowsTextActions(text: String): List<Map<String, Any>> =
    buildList {
    text.replace("\r\n", "\n").split("\n").forEachIndexed { index, line ->
        if (index > 0) add(mapOf("virtualKeyCode" to 0x0D))
        if (line.isNotEmpty()) add(mapOf("text" to line))
    }
}
