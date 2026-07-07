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
    fun `test sign in as system user`() = runAppiumBlockingTest {
        runDesktopType2Test { driver ->
            scenarioSignInAsSystemUser(DesktopAppTestDriver(driver), readAppiumSystemPrivateKey())
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
    fun `test favorite topic from topic page`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        val topicContent = "appium-desktop-favorite-topic-${System.currentTimeMillis()}"
        runDesktopType1Test(
            beforeLaunch = { ports, sessionFilePath ->
                val scenario = prepareFavoriteTopicScenario(topicContent) {
                    createAuthenticatedSession(ports)
                }
                writeSessionFile(sessionFilePath, buildInjectedSessionJson(scenario.authenticated.session))
                scenario
            }
        ) { driver, data ->
            try {
                val appDriver = DesktopAppTestDriver(driver)
                scenarioFavoriteTopic(appDriver, data.authenticated.session.address, topicContent)
                waitUntilTopicFavorited(data.authenticated.sessionManager, data.topicId)
                appDriver.navigateBack()
                appDriver.assertVisible(description = "topic")
            } finally {
                data.authenticated.sessionManager.client.close()
            }
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

    @Test
    fun `test community profile actions from joined community`() =
        runPreparedCommunityRoomScenario { appDriver, data ->
            scenarioCommunityProfileActions(appDriver, data.communityName, data.ownerSession.address)
        }

    @Test
    fun `test publish topic in joined community`() = runPreparedCommunityRoomScenario { appDriver, data ->
        val topicContent = "appium-desktop-community-topic-${System.currentTimeMillis()}"
        scenarioPublishTopicInCommunity(appDriver, data.communityName, topicContent)
    }

    @Test
    fun `test publish topic in community room`() = runPreparedCommunityRoomScenario { appDriver, data ->
        val topicContent = "appium-desktop-room-topic-${System.currentTimeMillis()}"
        scenarioPublishTopicInRoom(appDriver, data.communityName, data.roomName, topicContent)
    }

    private fun runPreparedCommunityRoomScenario(
        block: suspend (AppTestDriver, PreparedCommunityRoomScenario) -> Unit,
    ) = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        val now = System.currentTimeMillis()
        runDesktopType1Test(
            beforeLaunch = { ports, sessionFilePath ->
                val prepared = prepareCommunityRoomScenario(now) {
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
