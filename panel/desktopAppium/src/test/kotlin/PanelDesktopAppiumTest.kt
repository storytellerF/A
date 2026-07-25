import com.storyteller_f.shared.loadCryptoLibIfNeed
import io.appium.java_client.AppiumDriver
import kotlin.test.Test

class PanelDesktopAppiumTest : DesktopAppiumTestBase() {

    @Test
    fun `test panel sign in by injected private session`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        runConfiguredDesktopAppiumTestWithSetup(
            testName = name.methodName,
            config = panelDesktopRuntimeConfig,
            beforeLaunch = { ports: AppiumPorts, sessionFilePath: String ->
                val injected = createPreRegisteredPanelSession(ports)
                writeSessionFile(sessionFilePath, buildInjectedSessionJson(injected))
                injected
            },
        ) { driver: AppiumDriver, _: InjectedSession ->
            scenarioOpenAllUsersFromOverview(DesktopAppTestDriver(driver))
        }
    }
}
