/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.openqa.selenium.By
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.time.Duration
import kotlin.test.Test

@RunWith(Parameterized::class)
class WasmAppiumTest(private val browserType: WasmBrowser) :
    AppiumTestBase<AppAppiumHelper, WasmAppiumHelper>(
        AppAppiumHelper(),
        WasmAppiumHelper(
            resolveWasmDistribution(),
            By.cssSelector("[aria-label='avatar']"),
            browserType,
        ),
    ) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun browsers() = WasmBrowser.entries
    }

    @Test
    fun `test activity previews track reading sending and navigation`() = testActivityPreviewByHelper()

    @Test
    fun `test activity previews decrypt private rooms`() = testActivityPreviewByHelper(privateRoom = true)

    @Test
    fun `test room refresh clears cross session unread state`() = testRoomActivityRefreshByHelper()

    @Test
    fun `test browser socket server expiry stops reconnect`() = testBrowserSocketServerExpiry()

    @Test
    fun `test browser socket expired cookie stops reconnect`() = testBrowserSocketCookieExpiry()

    @Test
    fun `test browser socket rejects foreign origin`() = testBrowserSocketOrigin()

    @Test
    fun `test browser socket reconnect and logout`() = testBrowserSocketReconnect()

    @Test
    fun `test browser socket cookie loss stops reconnect`() = testBrowserSocketCookieLoss()

    @Test
    fun `test browser socket revocation stops reconnect`() = testBrowserSocketRevocation()

    @Test
    fun `test explicit sign in and sign out survive restart`() = testSignoutPersistenceByHelper()

    @Test
    fun `test sign in survives restart and revocation requires explicit login`() = testSigninLifecycleByHelper()

    @Test
    fun `test browser profile survives restart`() =
        runAppiumBlockingTest { testWasmProfilePersistence(browserType, resolveWasmDistribution()) }

    @Test
    fun `test html semantics bridge mounts`() =
        runAppiumBlockingTest {
        WasmDistributionServer(resolveWasmDistribution()).use { server ->
            browserType.withBrowser { driver ->
                driver.get("${server.url}?appium=true")
                runCatching {
                    WebDriverWait(driver, Duration.ofSeconds(30)).until(
                        ExpectedConditions.presenceOfElementLocated(By.cssSelector("[aria-label='avatar']")),
                    )
                }.getOrElse { cause ->
                    val body = driver.findElement(By.tagName("body")).getAttribute("innerHTML").orEmpty()
                    val console = runCatching { driver.manage().logs().get("browser").all }.getOrNull().orEmpty()
                    val errors =
                        (driver as org.openqa.selenium.JavascriptExecutor)
                            .executeScript("return window.appiumErrors || []")
                            ?: emptyList<Any>()
                    throw AssertionError(
                        "Wasm semantics did not mount; requested=${server.requestedPaths}; body=$body; " +
                            "console=$console; errors=$errors",
                        cause,
                    )
                }
            }
        }
    }

    @Test
    fun `test sign up`() = testSignUpByHelper()

    @Test
    fun `test sign in as system user`() = testSignInAsSystemUserByHelper()

    @Test
    fun `test sign in by injected session`() = testInjectedSessionByHelper()

    @Test
    fun `test publish topic in user space`() = testPublishTopicInUserSpaceByHelper()

    @Test
    fun `test favorite topic from topic page`() = testFavoriteTopicByHelper()

    @Test
    fun `test opens asciidoc preview`() = testOpenAsciidocPreviewByHelper()

    @Test
    fun `test subscribe topic from community page`() = testSubscribeTopicByHelper()

    @Test
    fun `test community profile actions from joined community`() = testCommunityProfileActionsByHelper()

    @Test
    fun `test publish topic in joined community`() = testPublishTopicInCommunityByHelper()

    @Test
    fun `test publish topic in community room`() = testPublishTopicInCommunityRoomByHelper()
}

private fun resolveWasmDistribution(): File {
    val distribution = File("build/wasmDistribution")
    return distribution.takeIf { File(it, "index.html").isFile }
        ?: error("Wasm distribution was not prepared; run :app:wasmAppium:prepareWasmDistribution first.")
}
