import com.storyteller_f.a.dev.appium.buildInjectedSessionJson
import com.storyteller_f.shared.loadCryptoLibIfNeed
import kotlin.test.Test

private const val APP_MAIN_ACTIVITY_CLASS_NAME = "com.storyteller_f.a.app.MainActivity"

private val appUnderTest = AppUnderTest(
    packageName = resolveAppPackageName(),
    mainActivityClassName = APP_MAIN_ACTIVITY_CLASS_NAME,
)

class AppAppiumTest : AppiumTestBase() {

    @Test
    fun `test sign up`() = runAppiumBlockingTest {
        runAndroidAppiumTest(appUnderTest) { driver ->
            scenarioSignUp(AndroidAppTestDriver(driver))
        }
    }

    @Test
    fun `test sign in as system user`() = runAppiumBlockingTest {
        runAndroidAppiumTest(appUnderTest) { driver ->
            scenarioSignInAsSystemUser(AndroidAppTestDriver(driver), readAppiumSystemPrivateKey())
        }
    }

    @Test
    fun `test sign in by injected private session`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        runAndroidAppiumTestWithSetup(
            app = appUnderTest,
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
        runAndroidAppiumTestWithSetup(
            app = appUnderTest,
            beforeDriverLaunch = { ports, packageName ->
                val injected = createPreRegisteredSession(ports)
                pushInjectedSessionToPrivateDir(packageName, buildInjectedSessionJson(injected))
                injected
            }
        ) { driver, injectedSession ->
            scenarioPublishTopicInUserSpace(AndroidAppTestDriver(driver), injectedSession.address)
        }
    }

    @Test
    fun `test favorite topic from topic page`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        runAndroidAppiumTestWithSetup(
            app = appUnderTest,
            beforeDriverLaunch = { ports, packageName ->
                val scenario = prepareFavoriteTopicScenario {
                    createAuthenticatedSession(ports)
                }
                pushInjectedSessionToPrivateDir(packageName, buildInjectedSessionJson(scenario.authenticated.session))
                scenario
            }
        ) { driver, data ->
            try {
                val appDriver = AndroidAppTestDriver(driver)
                scenarioFavoritePreparedTopic(appDriver, data)
            } finally {
                data.authenticated.sessionManager.client.close()
            }
        }
    }

    @Test
    fun `test subscribe topic from community page`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        runAndroidAppiumTestWithSetup(
            app = appUnderTest,
            beforeDriverLaunch = { ports, packageName ->
                val scenario = prepareSubscriptionTopicScenario {
                    createAuthenticatedSession(ports)
                }
                pushInjectedSessionToPrivateDir(packageName, buildInjectedSessionJson(scenario.authenticated.session))
                scenario
            }
        ) { driver, data ->
            try {
                val appDriver = AndroidAppTestDriver(driver)
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
        runAndroidAppiumTestWithSetup(
            app = appUnderTest,
            beforeDriverLaunch = { ports, packageName ->
                val prepared = prepareCommunityRoomScenario {
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
