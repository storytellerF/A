/*
 * This is a private project. All rights reserved.
*/

package com.storyteller_f.a.dev.appium

import io.appium.java_client.AppiumDriver
import org.openqa.selenium.By
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.time.Duration

class DesktopAppTestDriver(private val driver: AppiumDriver) : AppTestDriver {
    suspend fun assertAsciidocPreviewOpened() {
        assertVisibleByDescription("close preview")
    }

    override suspend fun clickByDescription(description: String) {
        waitAndClick(By.xpath("//*[@name='$description']"))
    }

    override suspend fun clickByText(text: String) {
        waitAndClick(By.xpath("//*[@value='$text' or @name='$text']"))
    }

    override suspend fun clickByTextContaining(text: String) {
        waitAndClick(By.xpath("//*[contains(@value,'$text') or contains(@name,'$text')]"))
    }

    private fun waitAndClick(locator: By) {
        WebDriverWait(driver, Duration.ofSeconds(UI_WAIT_SECONDS))
            .until(ExpectedConditions.presenceOfElementLocated(locator))
        driver.findElement(locator).click()
    }

    override suspend fun inputText(text: String) {
        val wait = WebDriverWait(driver, Duration.ofSeconds(UI_WAIT_SECONDS))
        val elements =
            wait.until {
                driver.findElements(By.xpath("//text-field")).takeIf(List<*>::isNotEmpty)
            }
        val element = elements.last()
        element.sendKeys(text)
    }

    override suspend fun assertVisibleByDescription(description: String) {
        assertVisible(By.xpath("//*[@name='$description']"))
    }

    override suspend fun assertVisibleByText(text: String) {
        assertVisible(By.xpath("//*[@value='$text' or @name='$text']"))
    }

    override suspend fun assertVisibleByTextContaining(text: String) {
        assertVisible(By.xpath("//*[contains(@value,'$text') or contains(@name,'$text')]"))
    }

    private fun assertVisible(locator: By) {
        WebDriverWait(driver, Duration.ofSeconds(UI_WAIT_SECONDS))
            .until(ExpectedConditions.presenceOfElementLocated(locator))
    }

    override suspend fun assertNotVisibleByText(text: String, timeoutSeconds: Long) {
        assertNotVisible(By.xpath("//*[@value='$text' or @name='$text']"), timeoutSeconds)
    }

    private fun assertNotVisible(locator: By, timeoutSeconds: Long) {
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
