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

    @Test
    fun `test subscribe topic from community page`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        val now = System.currentTimeMillis()
        val topicContent = "appium-desktop-subscription-topic-$now"
        runDesktopType1Test(
            beforeLaunch = { ports, sessionFilePath ->
                val scenario = prepareSubscriptionTopicScenario(now, topicContent) {
                    createAuthenticatedSession(ports)
                }
                writeSessionFile(sessionFilePath, buildInjectedSessionJson(scenario.authenticated.session))
                scenario
            }
        ) { driver, data ->
            try {
                val appDriver = DesktopAppTestDriver(driver)
                scenarioSubscribeTopic(appDriver, data.communityName, topicContent)
                waitUntilTopicSubscribed(data.authenticated.sessionManager, data.topicId)
                appDriver.navigateBack()
                appDriver.assertVisible(description = "topic")
            } finally {
                data.authenticated.sessionManager.client.close()
            }
        }
    }
}
