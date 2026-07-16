import io.appium.java_client.AppiumDriver
import org.junit.Rule
import org.junit.rules.TestName

abstract class DesktopPanelAppiumTestBase {

    @get:Rule
    val name = TestName()

    protected fun runAppiumBlockingTest(block: suspend () -> Unit) =
        runDesktopAppiumBlockingTest(block)

    protected suspend fun <T> runDesktopPanelAppiumTestWithSetup(
        beforeLaunch: suspend (ports: AppiumPorts, sessionFilePath: String) -> T,
        block: suspend (AppiumDriver, T) -> Unit,
    ) = runConfiguredDesktopAppiumTestWithSetup(
        testName = name.methodName,
        config = panelDesktopRuntimeConfig,
        beforeLaunch = beforeLaunch,
        block = block,
    )

    protected fun writeSessionFile(path: String, sessionJson: String) {
        writeDesktopSessionFile(path, sessionJson)
    }
}
