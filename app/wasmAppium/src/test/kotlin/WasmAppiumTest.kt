import org.openqa.selenium.By
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.time.Duration
import kotlin.test.Test

class WasmAppiumTest : AppiumTestBase() {
    private val targetHelper = AppAppiumHelper()
    private val platformHelper = WasmAppiumHelper(resolveWasmDistribution())

    @Test
    fun `test html semantics bridge mounts`() {
        WasmDistributionServer(resolveWasmDistribution()).use { server ->
            val driver = FirefoxDriver(FirefoxOptions().addArguments("-headless"))
            try {
                driver.get("${server.url}?appium=true")
                runCatching {
                    WebDriverWait(driver, Duration.ofSeconds(30)).until(
                        ExpectedConditions.presenceOfElementLocated(By.cssSelector("[aria-label='avatar']")),
                    )
                }.getOrElse { cause ->
                    val body = driver.findElement(By.tagName("body")).getAttribute("innerHTML")
                    val console = runCatching { driver.manage().logs().get("browser").all }.getOrNull()
                    val errors = (driver as org.openqa.selenium.JavascriptExecutor)
                        .executeScript("return window.appiumErrors || []")
                    throw AssertionError(
                        "Wasm semantics did not mount; requested=${server.requestedPaths}; body=$body; " +
                            "console=$console; errors=$errors",
                        cause,
                    )
                }
            } finally {
                driver.quit()
            }
        }
    }

    @Test
    fun `test sign up`() = testSignUpByHelper(name.methodName, targetHelper, platformHelper)

    @Test
    fun `test sign in as system user`() =
        testSignInAsSystemUserByHelper(name.methodName, targetHelper, platformHelper)

    @Test
    fun `test sign in by injected session`() =
        testInjectedSessionByHelper(name.methodName, targetHelper, platformHelper)

    @Test
    fun `test publish topic in user space`() =
        testPublishTopicInUserSpaceByHelper(name.methodName, targetHelper, platformHelper)

    @Test
    fun `test favorite topic from topic page`() =
        testFavoriteTopicByHelper(name.methodName, targetHelper, platformHelper)

    @Test
    fun `test opens asciidoc preview`() =
        testOpenAsciidocPreviewByHelper(name.methodName, targetHelper, platformHelper)

    @Test
    fun `test subscribe topic from community page`() =
        testSubscribeTopicByHelper(name.methodName, targetHelper, platformHelper)

    @Test
    fun `test community profile actions from joined community`() =
        testCommunityProfileActionsByHelper(name.methodName, targetHelper, platformHelper)

    @Test
    fun `test publish topic in joined community`() =
        testPublishTopicInCommunityByHelper(name.methodName, targetHelper, platformHelper)

    @Test
    fun `test publish topic in community room`() =
        testPublishTopicInCommunityRoomByHelper(name.methodName, targetHelper, platformHelper)

    private fun resolveWasmDistribution(): File = File("build/wasmDistribution")
        .takeIf { File(it, "index.html").isFile }
        ?: error("Wasm distribution was not prepared; run :app:wasmAppium:prepareWasmDistribution first.")
}
