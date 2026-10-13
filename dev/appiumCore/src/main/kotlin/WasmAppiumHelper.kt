/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebDriver
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.firefox.GeckoDriverService
import org.openqa.selenium.net.PortProber
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.time.Duration
import kotlin.uuid.Uuid

class WasmAppiumHelper(
    private val distribution: File,
    private val startupLocator: By,
    private val browserArguments: List<String> = listOf("-headless"),
) : PlatformAppiumHelper {
    override val capturesExternalAsciidocPreview = false

    override suspend fun <T> runTest(
        testName: String,
        target: TargetAppiumHelper,
        captureBrowserOpen: Boolean,
        setup: suspend (AppiumPorts) -> AppiumTestSetup<T>,
        block: suspend (AppiumTestScope, T) -> Unit,
    ) {
        WasmDistributionServer(distribution).use { server ->
            runAppiumTestEnvironment(allowedOrigins = server.url.removeSuffix("/")) { ports ->
                server.setBackendUrl("http://127.0.0.1:${ports.server}")
                val profile =
                    File(
                        "build/test/appium/browser-profile-${Uuid.random().toHexString()}",
                    ).also(File::mkdirs)
                // An in-place profile retains HttpOnly Cookies and IndexedDB across real Firefox process restarts.
                val options =
                    FirefoxOptions().addPreference(
                        "intl.accept_languages",
                        "en-US,en",
                    ).addPreference("intl.locale.requested", "en-US").addArguments(
                        browserArguments + listOf("--profile", profile.canonicalPath),
                    )
                var browser: WebDriver = createBrowser(options)
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
                    block(
                        WasmAppiumTestScope({ browser }) {
                            val url = requireNotNull(browser.currentUrl) { "Browser target URL is unavailable" }
                            (browser as JavascriptExecutor).executeScript(
                                "window.localStorage.removeItem('appium.injected_session');",
                            )
                            browser.quit()
                            browser = createBrowser(options)
                            browser.get(url)
                            awaitStartup(browser, server)
                        },
                        prepared.data,
                    )
                } finally {
                    browser.quit()
                    profile.deleteRecursively()
                }
            }
        }
    }

    private fun createBrowser(options: FirefoxOptions): WebDriver {
        val service =
            object : GeckoDriverService.Builder() {
                // In-place profiles require an explicit Marionette port:
                // https://firefox-source-docs.mozilla.org/testing/geckodriver/Profiles.html
                override fun createArgs(): List<String> =
                    super.createArgs() + listOf("--marionette-port", PortProber.findFreePort().toString())
            }.build()
        return FirefoxDriver(service, options)
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
        session?.cookies?.forEach { cookie ->
            browser.manage().addCookie(
                org.openqa.selenium.Cookie.Builder(cookie.name, cookie.value)
                    .path(cookie.path ?: "/").isHttpOnly(cookie.httpOnly).isSecure(cookie.secure).build(),
            )
        }
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

private class WasmAppiumTestScope(private val getBrowser: () -> WebDriver, private val restartBrowser: () -> Unit) :
    AppiumTestScope {
    override val driver get() = WasmAppTestDriver(getBrowser())

    override suspend fun assertAsciidocPreviewOpened(source: String) {
        val browser = getBrowser()
        val title =
            source.lineSequence().firstOrNull { it.startsWith("= ") }?.removePrefix("= ")
                ?: error("AsciiDoc source must include a document title")
        WebDriverWait(browser, Duration.ofSeconds(15)).until(
            ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("[aria-label='asciidoc-preview'][data-appium-text='${title.cssAttributeValue()}']"),
            ),
        )
    }

    override suspend fun restart() = restartBrowser()

    private fun String.cssAttributeValue(): String = replace("\\", "\\\\").replace("'", "\\'")
}
