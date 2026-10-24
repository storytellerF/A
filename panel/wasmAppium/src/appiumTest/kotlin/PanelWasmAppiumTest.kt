/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import com.storyteller_f.a.test.ArtifactCollectionError
import com.storyteller_f.result.captureFailure
import com.storyteller_f.result.getOrElse
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.openqa.selenium.By
import org.openqa.selenium.Dimension
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.time.Duration
import kotlin.test.Test

@RunWith(Parameterized::class)
class PanelWasmAppiumTest(private val browserType: WasmBrowser) :
    AppiumTestBase<PanelAppiumHelper, WasmAppiumHelper>(
        PanelAppiumHelper(),
        WasmAppiumHelper(
            resolveWasmDistribution(),
            By.cssSelector("[data-appium-text='Sign in'], [data-appium-text='Overview']"),
            browserType,
            Dimension(500, 800),
        ),
    ) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun browsers() = WasmBrowser.entries
    }

    @Test
    fun `test explicit sign out survives restart`() = testPanelSignoutPersistenceByHelper()

    @Test
    fun `test sign in survives restart and revocation requires explicit login`() = testPanelSigninLifecycleByHelper()

    @Test
    fun `test html semantics bridge mounts`() =
        runAppiumBlockingTest {
        WasmDistributionServer(resolveWasmDistribution()).use { server ->
            browserType.withBrowser(Dimension(500, 800)) { browser ->
                browser.get("${server.url}?appium=true")
                captureFailure(ArtifactCollectionError::CollectionFailed) {
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
                        cause.cause,
                    )
                }
            }
        }
    }

    @Test
    fun `test panel sign in by injected private session`() = testPanelInjectedSessionByHelper()
}

private fun resolveWasmDistribution(): File {
    val distribution = File("build/wasmDistribution")
    return distribution.takeIf { File(it, "index.html").isFile }
        ?: error("Wasm distribution was not prepared; run :panel:wasmAppium:prepareWasmDistribution first.")
}
