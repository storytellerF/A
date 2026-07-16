import com.storyteller_f.a.dev.appium.buildInjectedSessionJson
import com.storyteller_f.shared.loadCryptoLibIfNeed
import kotlin.test.Test

private const val PANEL_MAIN_ACTIVITY_CLASS_NAME = "com.storyteller_f.a.panel.MainActivity"

private val panelUnderTest = AppUnderTest(
    packageName = resolvePanelPackageName(),
    mainActivityClassName = PANEL_MAIN_ACTIVITY_CLASS_NAME,
)

class PanelAppiumTest : AppiumTestBase() {

    @Test
    fun `test panel sign in by injected private session`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        runAndroidAppiumTestWithSetup(
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
