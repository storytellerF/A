import com.storyteller_f.shared.loadCryptoLibIfNeed
import io.appium.java_client.AppiumDriver
import kotlin.test.Test

class DesktopAppiumTest : DesktopAppiumTestBase() {

    @Test
    fun `test sign up`() = runAppiumBlockingTest {
        runConfiguredDesktopAppiumTestWithSetup(
            testName = name.methodName,
            config = appDesktopRuntimeConfig,
            beforeLaunch = { _: AppiumPorts, _: String -> },
        ) { driver: AppiumDriver, _: Unit ->
            scenarioSignUp(DesktopAppTestDriver(driver))
        }
    }

    @Test
    fun `test sign in as system user`() = runAppiumBlockingTest {
        runConfiguredDesktopAppiumTestWithSetup(
            testName = name.methodName,
            config = appDesktopRuntimeConfig,
            beforeLaunch = { _: AppiumPorts, _: String -> },
        ) { driver: AppiumDriver, _: Unit ->
            scenarioSignInAsSystemUser(DesktopAppTestDriver(driver), readAppiumSystemPrivateKey())
        }
    }

    @Test
    fun `test sign in by injected session`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        runConfiguredDesktopAppiumTestWithSetup(
            testName = name.methodName,
            config = appDesktopRuntimeConfig,
            beforeLaunch = { ports: AppiumPorts, sessionFilePath: String ->
                val injected = createPreRegisteredSession(ports)
                writeSessionFile(sessionFilePath, buildInjectedSessionJson(injected))
            },
        ) { driver: AppiumDriver, _: Unit ->
            scenarioVerifyInjectedSessionLoaded(DesktopAppTestDriver(driver))
        }
    }

    @Test
    fun `test publish topic in user space`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        runConfiguredDesktopAppiumTestWithSetup(
            testName = name.methodName,
            config = appDesktopRuntimeConfig,
            beforeLaunch = { ports: AppiumPorts, sessionFilePath: String ->
                val injected = createPreRegisteredSession(ports)
                writeSessionFile(sessionFilePath, buildInjectedSessionJson(injected))
                injected
            },
        ) { driver: AppiumDriver, injected: InjectedSession ->
            scenarioPublishTopicInUserSpace(DesktopAppTestDriver(driver))
        }
    }

    @Test
    fun `test favorite topic from topic page`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        runConfiguredDesktopAppiumTestWithSetup(
            testName = name.methodName,
            config = appDesktopRuntimeConfig,
            beforeLaunch = { ports: AppiumPorts, sessionFilePath: String ->
                val scenario = prepareFavoriteTopicScenario {
                    createAuthenticatedSession(ports)
                }
                writeSessionFile(
                    sessionFilePath,
                    buildInjectedSessionJson(scenario.authenticated.session)
                )
                scenario
            },
        ) { driver: AppiumDriver, data: FavoriteTopicScenario ->
            try {
                val appDriver = DesktopAppTestDriver(driver)
                scenarioFavoritePreparedTopic(appDriver, data)
            } finally {
                data.authenticated.sessionManager.client.close()
            }
        }
    }

    @Test
    fun `test opens asciidoc preview in browser`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        val browserCapture = DesktopBrowserCapture.create(name.methodName)
        runConfiguredDesktopAppiumTestWithSetup(
            testName = name.methodName,
            config = appDesktopRuntimeConfig,
            beforeLaunch = { ports: AppiumPorts, sessionFilePath: String ->
                val scenario = prepareAsciidocPreviewScenario {
                    createAuthenticatedSession(ports)
                }
                writeSessionFile(sessionFilePath, buildInjectedSessionJson(scenario.authenticated.session))
                scenario
            },
            browserCapture = browserCapture,
        ) { driver: AppiumDriver, data: AsciidocPreviewScenario ->
            try {
                scenarioOpenAsciidocPreview(DesktopAppTestDriver(driver), data.topicMarker)
                browserCapture.assertOpenedAsciidocPreview(data.asciidocSource)
            } finally {
                data.authenticated.sessionManager.client.close()
            }
        }
    }

    @Test
    fun `test subscribe topic from community page`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        runConfiguredDesktopAppiumTestWithSetup(
            testName = name.methodName,
            config = appDesktopRuntimeConfig,
            beforeLaunch = { ports: AppiumPorts, sessionFilePath: String ->
                val scenario = prepareSubscriptionTopicScenario {
                    createAuthenticatedSession(ports)
                }
                writeSessionFile(
                    sessionFilePath,
                    buildInjectedSessionJson(scenario.authenticated.session)
                )
                scenario
            },
        ) { driver: AppiumDriver, data: SubscriptionTopicScenario ->
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
        runConfiguredDesktopAppiumTestWithSetup(
            testName = name.methodName,
            config = appDesktopRuntimeConfig,
            beforeLaunch = { ports: AppiumPorts, sessionFilePath: String ->
                val prepared = prepareCommunityRoomScenario {
                    createAuthenticatedSession(ports)
                }
                writeSessionFile(sessionFilePath, buildInjectedSessionJson(prepared.viewerSession))
                prepared
            },
        ) { driver: AppiumDriver, data: PreparedCommunityRoomScenario ->
            block(DesktopAppTestDriver(driver), data)
        }
    }
}
