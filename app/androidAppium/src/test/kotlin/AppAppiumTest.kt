import com.storyteller_f.shared.loadCryptoLibIfNeed
import kotlin.test.Test

class AppAppiumTest : AppiumTestBase() {

    @Test
    fun `test sign up`() = runAppiumBlockingTest {
        runType2Test { driver ->
            scenarioSignUp(AndroidAppTestDriver(driver), generatePrivateKey())
        }
    }

    @Test
    fun `test sign in as system user`() = runAppiumBlockingTest {
        runType2Test { driver ->
            scenarioSignInAsSystemUser(AndroidAppTestDriver(driver), readAppiumSystemPrivateKey())
        }
    }

    @Test
    fun `test sign in by injected private session`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        runType1Test(
            beforeDriverLaunch = { ports, packageName ->
                val injected = createPreRegisteredSession(ports)
                pushInjectedSessionToPrivateDir(packageName, buildInjectedSessionJson(injected))
            }
        ) { driver, _ ->
            scenarioVerifyInjectedSessionLoaded(AndroidAppTestDriver(driver))
        }
    }

    @Test
    fun `test publish topic in user space`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        val topicContent = "appium-user-space-topic-${System.currentTimeMillis()}"
        runType1Test(
            beforeDriverLaunch = { ports, packageName ->
                val injected = createPreRegisteredSession(ports)
                pushInjectedSessionToPrivateDir(packageName, buildInjectedSessionJson(injected))
                injected
            }
        ) { driver, injectedSession ->
            scenarioPublishTopicInUserSpace(AndroidAppTestDriver(driver), injectedSession.address, topicContent)
        }
    }

    @Test
    fun `test favorite topic from topic page`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        val topicContent = "appium-favorite-topic-${System.currentTimeMillis()}"
        runType1Test(
            beforeDriverLaunch = { ports, packageName ->
                val scenario = prepareFavoriteTopicScenario(topicContent) {
                    createAuthenticatedSession(ports)
                }
                pushInjectedSessionToPrivateDir(packageName, buildInjectedSessionJson(scenario.authenticated.session))
                scenario
            }
        ) { driver, data ->
            try {
                val appDriver = AndroidAppTestDriver(driver)
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
        val topicContent = "appium-subscription-topic-$now"
        runType1Test(
            beforeDriverLaunch = { ports, packageName ->
                val scenario = prepareSubscriptionTopicScenario(now, topicContent) {
                    createAuthenticatedSession(ports)
                }
                pushInjectedSessionToPrivateDir(packageName, buildInjectedSessionJson(scenario.authenticated.session))
                scenario
            }
        ) { driver, data ->
            try {
                val appDriver = AndroidAppTestDriver(driver)
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
        val topicContent = "appium-community-topic-${System.currentTimeMillis()}"
        scenarioPublishTopicInCommunity(appDriver, data.communityName, topicContent)
    }

    @Test
    fun `test publish topic in community room`() = runPreparedCommunityRoomScenario { appDriver, data ->
        val topicContent = "appium-room-topic-${System.currentTimeMillis()}"
        scenarioPublishTopicInRoom(appDriver, data.communityName, data.roomName, topicContent)
    }

    private fun runPreparedCommunityRoomScenario(
        block: suspend (AppTestDriver, PreparedCommunityRoomScenario) -> Unit,
    ) = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        val now = System.currentTimeMillis()
        runType1Test(
            beforeDriverLaunch = { ports, packageName ->
                val prepared = prepareCommunityRoomScenario(now) {
                    createAuthenticatedSession(ports)
                }
                pushInjectedSessionToPrivateDir(packageName, buildInjectedSessionJson(prepared.viewerSession))
                prepared
            }
        ) { driver, data ->
            block(AndroidAppTestDriver(driver), data)
        }
    }
}
