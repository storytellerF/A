/*
 * This is a private project. All rights reserved.
 */

import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.time.Duration
import kotlin.test.Test

class PanelWasmAppiumTest : AppiumTestBase() {
    private val targetHelper = PanelAppiumHelper()
    private val platformHelper =
        WasmAppiumHelper(
            resolveWasmDistribution(),
            By.cssSelector("[data-appium-text='Sign in'], [data-appium-text='Overview']"),
            listOf("-headless", "--width=500", "--height=800"),
        )

    @Test
    fun `test html semantics bridge mounts`() {
        WasmDistributionServer(resolveWasmDistribution()).use { server ->
            val browser = FirefoxDriver(FirefoxOptions().addArguments("-headless", "--width=500", "--height=800"))
            try {
                browser.get("${server.url}?appium=true")
                runCatching {
                    WebDriverWait(browser, Duration.ofSeconds(30)).until(
                        ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-appium-text='Sign in']")),
                    )
                }.getOrElse { cause ->
                    val body = browser.findElement(By.tagName("body")).getAttribute("innerHTML").orEmpty()
                    val errors =
                        (browser as JavascriptExecutor)
                            .executeScript("return window.appiumErrors || []")
                            ?: emptyList<Any>()
                    throw AssertionError(
                        "Panel Wasm semantics did not mount; requested=${server.requestedPaths}; " +
                            "body=$body; errors=$errors",
                        cause,
                    )
                }
            } finally {
                browser.quit()
            }
        }
    }

    @Test
    fun `test panel sign in by injected private session`() =
        testPanelInjectedSessionByHelper(name.methodName, targetHelper, platformHelper)

    private fun resolveWasmDistribution(): File {
        val distribution = File("build/wasmDistribution")
        return distribution.takeIf { File(it, "index.html").isFile }
            ?: error("Wasm distribution was not prepared; run :panel:wasmAppium:prepareWasmDistribution first.")
    }
}
