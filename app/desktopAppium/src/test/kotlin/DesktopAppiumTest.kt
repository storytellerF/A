import com.storyteller_f.a.dev.appium.buildInjectedSessionJson
import com.storyteller_f.shared.loadCryptoLibIfNeed
import kotlin.test.Test

class DesktopAppiumTest : DesktopAppiumTestBase() {

    @Test
    fun `test sign up`() = runAppiumBlockingTest {
        runDesktopAppiumTest { driver ->
            scenarioSignUp(DesktopAppTestDriver(driver))
        }
    }

    @Test
    fun `test sign in as system user`() = runAppiumBlockingTest {
        runDesktopAppiumTest { driver ->
            scenarioSignInAsSystemUser(DesktopAppTestDriver(driver), readAppiumSystemPrivateKey())
        }
    }

    @Test
    fun `test sign in by injected session`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        runDesktopAppiumTestWithSetup(
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
        runDesktopAppiumTestWithSetup(
            beforeLaunch = { ports, sessionFilePath ->
                val injected = createPreRegisteredSession(ports)
                writeSessionFile(sessionFilePath, buildInjectedSessionJson(injected))
                injected
            }
        ) { driver, injected ->
            scenarioPublishTopicInUserSpace(DesktopAppTestDriver(driver))
        }
    }

    @Test
    fun `test favorite topic from topic page`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        runDesktopAppiumTestWithSetup(
            beforeLaunch = { ports, sessionFilePath ->
                val scenario = prepareFavoriteTopicScenario {
                    createAuthenticatedSession(ports)
                }
                writeSessionFile(sessionFilePath, buildInjectedSessionJson(scenario.authenticated.session))
                scenario
            }
        ) { driver, data ->
            try {
                val appDriver = DesktopAppTestDriver(driver)
                scenarioFavoritePreparedTopic(appDriver, data)
            } finally {
                data.authenticated.sessionManager.client.close()
            }
        }
    }

    @Test
    fun `test subscribe topic from community page`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        runDesktopAppiumTestWithSetup(
            beforeLaunch = { ports, sessionFilePath ->
                val scenario = prepareSubscriptionTopicScenario {
                    createAuthenticatedSession(ports)
                }
                writeSessionFile(sessionFilePath, buildInjectedSessionJson(scenario.authenticated.session))
                scenario
            }
        ) { driver, data ->
            try {
                val appDriver = DesktopAppTestDriver(driver)
                scenarioSubscribePreparedTopic(appDriver, data)
            } finally {
                data.authenticated.sessionManager.client.close()
            }
        }
    }

    @Test
    fun `test community profile actions from joined community`() =
        runPreparedCommunityRoomScenario { appDriver, data ->
            scenarioCommunityProfileActions(appDriver, data.communityName, data.ownerSession.address)
        }

    @Test
    fun `test publish topic in joined community`() = runPreparedCommunityRoomScenario { appDriver, data ->
        scenarioPublishTopicInCommunity(appDriver, data.communityName)
    }

    @Test
    fun `test publish topic in community room`() = runPreparedCommunityRoomScenario { appDriver, data ->
        scenarioPublishTopicInRoom(appDriver, data.communityName, data.roomName)
    }

    private fun runPreparedCommunityRoomScenario(
        block: suspend (AppTestDriver, PreparedCommunityRoomScenario) -> Unit,
    ) = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        runDesktopAppiumTestWithSetup(
            beforeLaunch = { ports, sessionFilePath ->
                val prepared = prepareCommunityRoomScenario {
                    createAuthenticatedSession(ports)
                }
                writeSessionFile(sessionFilePath, buildInjectedSessionJson(prepared.viewerSession))
                prepared
            }
        ) { driver, data ->
            block(DesktopAppTestDriver(driver), data)
        }
    }
}
