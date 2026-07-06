import com.storyteller_f.shared.loadCryptoLibIfNeed
import kotlin.test.Test

class DesktopAppiumTest : DesktopAppiumTestBase() {

    @Test
    fun `test sign up`() = runAppiumBlockingTest {
        runDesktopType2Test { driver ->
            scenarioSignUp(DesktopAppTestDriver(driver), generatePrivateKey())
        }
    }

    @Test
    fun `test sign in by injected session`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        runDesktopType1Test(
            beforeLaunch = { ports, sessionFilePath ->
                val injected = createPreRegisteredSession(ports)
                writeSessionFile(sessionFilePath, buildInjectedSessionJson(injected))
            }
        ) { driver, _ ->
            scenarioVerifyInjectedSessionLoaded(DesktopAppTestDriver(driver))
        }
    }

    @Test
    fun `test publish topic in user space`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        val topicContent = "appium-desktop-topic-${System.currentTimeMillis()}"
        runDesktopType1Test(
            beforeLaunch = { ports, sessionFilePath ->
                val injected = createPreRegisteredSession(ports)
                writeSessionFile(sessionFilePath, buildInjectedSessionJson(injected))
                injected
            }
        ) { driver, injected ->
            scenarioPublishTopicInUserSpace(DesktopAppTestDriver(driver), injected.address, topicContent)
        }
    }
}
