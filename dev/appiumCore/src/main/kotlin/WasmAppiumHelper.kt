/*
 * This is a private project. All rights reserved.
*/

package com.storyteller_f.a.dev.appium

import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebDriver
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.time.Duration

class WasmAppiumHelper(
    private val distribution: File,
    private val startupLocator: By,
    private val browserArguments: List<String> = listOf("-headless"),
) : PlatformAppiumHelper() {
    override val capturesExternalAsciidocPreview = false

    override suspend fun <T> runTest(
        testName: String,
        target: TargetAppiumHelper,
        captureBrowserOpen: Boolean,
        setup: suspend (AppiumPorts) -> AppiumTestSetup<T>,
        block: suspend (AppiumTestScope, T) -> Unit,
    ) {
        runAppiumTestEnvironment { ports ->
            WasmDistributionServer(distribution, backendUrl = "http://127.0.0.1:${ports.server}").use { server ->
                val browser = FirefoxDriver(FirefoxOptions().addArguments(browserArguments))
                try {
                    val prepared = setup(ports)
                    initializeBrowserState(browser, server, prepared.injectedSession)
                    browser.get(
                        "${server.url}?appium=true&appiumHttpUrl=${server.url.removeSuffix(
                            "/",
                        )}&appiumWsUrl=ws://127.0.0.1:${ports.ws}",
                    )
                    awaitStartup(browser, server)
                    if (prepared.injectedSession != null) {
                        WebDriverWait(browser, Duration.ofSeconds(30)).until {
                            (browser as JavascriptExecutor).executeScript(
                                "return window.localStorage.getItem('appium.session_ready') === 'true';",
                            ) == true
                        }
                    }
                    block(WasmAppiumTestScope(WasmAppTestDriver(browser), browser), prepared.data)
                } finally {
                    browser.quit()
                }
            }
        }
    }

    private fun awaitStartup(browser: WebDriver, server: WasmDistributionServer) {
        runCatching {
            WebDriverWait(browser, Duration.ofSeconds(30)).until(
                ExpectedConditions.presenceOfElementLocated(startupLocator),
            )
        }.getOrElse { cause ->
            val body = browser.findElement(By.tagName("body")).getAttribute("innerHTML")
            val errors = (browser as JavascriptExecutor).executeScript("return window.appiumErrors || [];")
            throw AssertionError(
                "Wasm Appium semantics did not mount; requested=${server.requestedPaths}; body=${body ?: "<none>"}; errors=${errors ?: "<none>"}",
                cause,
            )
        }
    }

    private fun initializeBrowserState(browser: WebDriver, server: WasmDistributionServer, session: InjectedSession?) {
        browser.get(server.bootstrapUrl)
        (browser as JavascriptExecutor).executeScript(
            """
            window.localStorage.clear();
            if (arguments[0] !== null) {
              window.localStorage.setItem('appium.injected_session', arguments[0]);
            }
            """.trimIndent(),
            session?.let(::buildInjectedSessionJson),
        )
    }
}

private class WasmAppiumTestScope(override val driver: WasmAppTestDriver, private val browser: WebDriver) :
    AppiumTestScope {
    override suspend fun assertAsciidocPreviewOpened(source: String) {
        val title =
            source.lineSequence().firstOrNull { it.startsWith("= ") }?.removePrefix("= ")
                ?: error("AsciiDoc source must include a document title")
        WebDriverWait(browser, Duration.ofSeconds(15)).until(
            ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("[aria-label='asciidoc-preview'][data-appium-text='${title.cssAttributeValue()}']"),
            ),
        )
    }

    private fun String.cssAttributeValue(): String = replace("\\", "\\\\").replace("'", "\\'")
}
