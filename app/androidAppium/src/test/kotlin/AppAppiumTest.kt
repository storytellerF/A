import com.storyteller_f.a.api.NewCommunity
import com.storyteller_f.a.api.NewRoom
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.createCommunity
import com.storyteller_f.a.client.core.createRoom
import com.storyteller_f.a.client.core.createTopic
import com.storyteller_f.a.client.core.getTopicInfo
import com.storyteller_f.a.client.core.joinCommunity
import com.storyteller_f.shared.loadCryptoLibIfNeed
import com.storyteller_f.shared.type.ObjectType
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

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
            val appDriver = AndroidAppTestDriver(driver)
            scenarioSignIn(appDriver, readAppiumSystemPrivateKey())
            appDriver.assertVisible(text = "System")
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
                val authenticated = createAuthenticatedSession(ports)
                val topicId = createTopicByApi(
                    authenticated.sessionManager,
                    ObjectType.USER,
                    authenticated.sessionManager.model.uid ?: error("not login"),
                    topicContent
                )
                pushInjectedSessionToPrivateDir(packageName, buildInjectedSessionJson(authenticated.session))
                FavoriteTopicScenario(authenticated, topicId)
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
        block: suspend (AppTestDriver, PreparedScenario) -> Unit,
    ) = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        val now = System.currentTimeMillis()
        runType1Test(
            beforeDriverLaunch = { ports, packageName ->
                val prepared = prepareJoinAndPostingScenario(ports, now)
                pushInjectedSessionToPrivateDir(packageName, buildInjectedSessionJson(prepared.viewerSession))
                prepared
            }
        ) { driver, data ->
            block(AndroidAppTestDriver(driver), data)
        }
    }

    private suspend fun prepareJoinAndPostingScenario(ports: AppiumPorts, now: Long): PreparedScenario {
        val owner = createAuthenticatedSession(ports)
        val aidSuffix = (now % 1_000_000).toString().padStart(6, '0')
        val communityName = "community-$aidSuffix"
        val roomName = "room-$aidSuffix"
        val communityId = createCommunityByApi(owner.sessionManager, communityName, "c$aidSuffix")
        val roomId = createRoomByApi(owner.sessionManager, roomName, "r$aidSuffix", communityId)
        createTopicByApi(owner.sessionManager, ObjectType.COMMUNITY, communityId, "appium-owner-community-topic-$now")
        val viewer = createAuthenticatedSession(ports)
        viewer.sessionManager.joinCommunity(communityId).getOrThrow()
        val result = PreparedScenario(owner.session, viewer.session, communityId, roomId, communityName, roomName)
        owner.sessionManager.client.close()
        viewer.sessionManager.client.close()
        return result
    }
}

private suspend fun waitUntilTopicFavorited(
    sessionManager: UserSessionManager,
    topicId: Long,
    timeoutMillis: Long = java.time.Duration.ofSeconds(30).toMillis(),
) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
        if (sessionManager.getTopicInfo(topicId).getOrThrow().favoriteId != null) return
        delay(500.milliseconds)
    }
    error("Topic not marked as favorite: $topicId")
}

private suspend fun createCommunityByApi(manager: UserSessionManager, name: String, aid: String): Long =
    manager.createCommunity(NewCommunity(name, aid)).getOrThrow().id

private suspend fun createRoomByApi(
    manager: UserSessionManager,
    name: String,
    aid: String,
    communityId: Long,
): Long = manager.createRoom(NewRoom(name, aid, communityId = communityId)).getOrThrow().id

private suspend fun createTopicByApi(
    manager: UserSessionManager,
    parentType: ObjectType,
    parentId: Long,
    content: String,
): Long = manager.createTopic(parentType, parentId, content).getOrThrow().id

private data class FavoriteTopicScenario(val authenticated: AuthenticatedSession, val topicId: Long)

private data class PreparedScenario(
    val ownerSession: InjectedSession,
    val viewerSession: InjectedSession,
    val communityId: Long,
    val roomId: Long,
    val communityName: String,
    val roomName: String,
)
