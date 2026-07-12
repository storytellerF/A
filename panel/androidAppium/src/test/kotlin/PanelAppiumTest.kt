import com.storyteller_f.a.dev.appium.buildInjectedSessionJson
import com.storyteller_f.shared.loadCryptoLibIfNeed
import kotlin.test.Test

class PanelAppiumTest : AppiumTestBase() {

    @Test
    fun `test panel sign in by injected private session`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        runType1Test(
            app = panelUnderTest,
            beforeDriverLaunch = { ports, packageName ->
                val injected = createPreRegisteredPanelSession(ports)
                val sessionJson = buildInjectedSessionJson(injected)
                pushInjectedSessionToPrivateDir(packageName, sessionJson)
                injected
            }
        ) { driver, _ ->
            scenarioOpenAllUsersFromOverview(AndroidAppTestDriver(driver))
        }
    }
}
