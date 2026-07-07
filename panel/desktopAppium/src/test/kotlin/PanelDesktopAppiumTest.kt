import com.storyteller_f.shared.loadCryptoLibIfNeed
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

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
            val panelDriver = DesktopAppTestDriver(driver)
            panelDriver.assertVisible(text = "Overview")
            panelDriver.clickByDescription("Menu")
            delay(1.seconds)
            panelDriver.clickByText("All users")
            panelDriver.assertVisible(text = "All users")
        }
    }
}
