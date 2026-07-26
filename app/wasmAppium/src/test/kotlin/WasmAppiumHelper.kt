import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebDriver
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.time.Duration

class WasmAppiumHelper(private val distribution: File) : PlatformAppiumHelper() {
    override val capturesExternalAsciidocPreview = false

    override suspend fun <T> runTest(
        testName: String,
        target: TargetAppiumHelper,
        captureBrowserOpen: Boolean,
        setup: suspend (AppiumPorts) -> AppiumTestSetup<T>,
        block: suspend (AppiumTestScope, T) -> Unit,
    ) {
        runAppiumTestEnvironment { ports ->
            WasmDistributionServer(
                distribution,
                backendUrl = "http://127.0.0.1:${ports.server}",
            ).use { server ->
                val driver = FirefoxDriver(FirefoxOptions().addArguments("-headless"))
                try {
                    val prepared = setup(ports)
                    initializeBrowserState(driver, server, prepared.injectedSession)
                    driver.get(
                        "${server.url}?appium=true&appiumHttpUrl=${server.url.removeSuffix("/")}" +
                            "&appiumWsUrl=ws://127.0.0.1:${ports.ws}",
                    )
                    runCatching {
                        WebDriverWait(driver, Duration.ofSeconds(30)).until(
                            ExpectedConditions.presenceOfElementLocated(
                                org.openqa.selenium.By.cssSelector("[aria-label='avatar']"),
                            ),
                        )
                    }.getOrElse { cause ->
                        val body = driver.findElement(org.openqa.selenium.By.tagName("body")).getAttribute("innerHTML")
                        val errors = (driver as JavascriptExecutor)
                            .executeScript("return window.appiumErrors || [];")
                        throw AssertionError(
                            "Wasm Appium semantics did not mount; requested=${server.requestedPaths}; " +
                                "body=$body; errors=$errors",
                            cause,
                        )
                    }
                    if (prepared.injectedSession != null) {
                        WebDriverWait(driver, Duration.ofSeconds(30)).until {
                            (driver as JavascriptExecutor).executeScript(
                                "return window.localStorage.getItem('appium.session_ready') === 'true';"
                            ) == true
                        }
                    }
                    block(WasmAppiumTestScope(WasmAppTestDriver(driver), driver), prepared.data)
                } finally {
                    driver.quit()
                }
            }
        }
    }

    private fun initializeBrowserState(driver: WebDriver, server: WasmDistributionServer, session: InjectedSession?) {
        driver.get(server.bootstrapUrl)
        val sessionJson = session?.let(::buildInjectedSessionJson)
        (driver as JavascriptExecutor).executeScript(
            """
            window.localStorage.clear();
            if (arguments[0] !== null) {
              window.localStorage.setItem('appium.injected_session', arguments[0]);
            }
            """.trimIndent(),
            sessionJson,
        )
    }
}

private class WasmAppiumTestScope(
    override val driver: WasmAppTestDriver,
    private val browser: WebDriver,
) : AppiumTestScope {
    override suspend fun assertAsciidocPreviewOpened(source: String) {
        val expectedTitle: String = source.lineSequence().firstOrNull { it.startsWith("= ") }?.removePrefix("= ")
            ?: error("AsciiDoc source must include a document title")
        WebDriverWait(browser, Duration.ofSeconds(15)).until(
            ExpectedConditions.presenceOfElementLocated(
                By.cssSelector(
                    "[aria-label='asciidoc-preview']" +
                        "[data-appium-text='${expectedTitle.cssAttributeValue()}']",
                ),
            ),
        )
    }

    private fun String.cssAttributeValue(): String = replace("\\", "\\\\").replace("'", "\\'")
}
