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
            assertElementVisible(driver, """new UiSelector().text("Overview")""")
            clickElement(driver, """new UiSelector().description("Menu")""")
            clickElement(driver, """new UiSelector().text("All users")""")
            assertElementVisible(driver, """new UiSelector().text("All users")""")
        }
    }
}
