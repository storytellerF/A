import com.storyteller_f.a.dev.appium.buildInjectedSessionJson
import com.storyteller_f.shared.loadCryptoLibIfNeed
import kotlin.test.Test

class PanelDesktopAppiumTest : DesktopPanelAppiumTestBase() {

    @Test
    fun `test panel sign in by injected private session`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        runDesktopPanelType1Test(
            beforeLaunch = { ports, sessionFilePath ->
                val injected = createPreRegisteredPanelSession(ports)
                writeSessionFile(sessionFilePath, buildInjectedSessionJson(injected))
                injected
            }
        ) { driver, _ ->
            scenarioOpenAllUsersFromOverview(DesktopAppTestDriver(driver))
        }
    }
}
