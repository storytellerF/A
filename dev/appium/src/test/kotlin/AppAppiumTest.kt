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
import io.appium.java_client.android.AndroidDriver
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

class AppAppiumTest : AppiumTestBase() {

    @Test
    fun `test sign up`() = runAppiumBlockingTest {
        runType2Test { driver ->
            val privateKeyContent = generatePrivateKey()
            clickElement(driver, """new UiSelector().description("avatar")""")
            clickElement(driver, """new UiSelector().text("Sign in")""")
            clickElement(driver, """new UiSelector().text("Go to sign up")""")
            assertElementVisible(driver, """new UiSelector().text("Sign up")""")
            clickElement(driver, """new UiSelector().text("Private Key")""")
            clickElement(driver, """new UiSelector().description("Edit Private Key")""")
            inputElement(
                driver,
                """new UiSelector().className("android.widget.EditText")""",
                privateKeyContent
            )
            clickElement(driver, """new UiSelector().text("Confirm")""")
            clickElement(driver, """new UiSelector().text("Start sign up")""")
            assertElementVisible(driver, """new UiSelector().description("avatar")""")
        }
    }

    @Test
    fun `test sign in as system user`() = runAppiumBlockingTest {
        runType2Test { driver ->
            val privateKeyContent = readAppiumSystemPrivateKey()
            clickElement(driver, """new UiSelector().description("avatar")""")
            clickElement(driver, """new UiSelector().text("Sign in")""")
            clickElement(driver, """new UiSelector().text("Private Key")""")
            clickElement(driver, """new UiSelector().description("Edit Private Key")""")
            inputElement(
                driver,
                """new UiSelector().className("android.widget.EditText")""",
                privateKeyContent
            )
            clickElement(driver, """new UiSelector().text("Confirm")""")
            clickElement(driver, """new UiSelector().text("Start sign in")""")
            clickElement(driver, """new UiSelector().description("avatar")""")
            assertElementNotVisible(driver, """new UiSelector().text("Sign in")""")
            assertElementVisible(driver, """new UiSelector().text("System")""")
        }
    }

    @Test
    fun `test sign in by injected private session`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        runType1Test(
            beforeDriverLaunch = { ports, packageName ->
                val injected = createPreRegisteredSession(ports)
                val sessionJson = buildInjectedSessionJson(injected)
                pushInjectedSessionToPrivateDir(packageName, sessionJson)
                injected
            }
        ) { driver, _ ->
            clickElement(driver, """new UiSelector().description("avatar")""")
            assertElementNotVisible(driver, """new UiSelector().text("Sign in")""")
        }
    }

    @Test
    fun `test publish topic in user space`() = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        val topicContent = "appium-user-space-topic-${System.currentTimeMillis()}"
        runType1Test(
            beforeDriverLaunch = { ports, packageName ->
                val injected = createPreRegisteredSession(ports)
                val sessionJson = buildInjectedSessionJson(injected)
                pushInjectedSessionToPrivateDir(packageName, sessionJson)
                injected
            }
        ) { driver, injectedSession ->
            clickElement(driver, """new UiSelector().description("avatar")""")
            clickElement(driver, """new UiSelector().text("ad: ${injectedSession.address}")""")
            clickElement(driver, """new UiSelector().description("add topic")""")
            clickElement(driver, """new UiSelector().text("Raw")""")
            inputElement(
                driver,
                """new UiSelector().className("android.widget.EditText")""",
                topicContent
            )
            clickElement(driver, """new UiSelector().description("submit")""")
            assertElementVisible(driver, """new UiSelector().text("$topicContent")""")
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
                val sessionJson = buildInjectedSessionJson(authenticated.session)
                pushInjectedSessionToPrivateDir(packageName, sessionJson)
                FavoriteTopicScenario(authenticated, topicId)
            }
        ) { driver, data ->
            try {
                clickElement(driver, """new UiSelector().description("avatar")""")
                assertElementVisible(
                    driver,
                    """new UiSelector().text("ad: ${data.authenticated.session.address}")"""
                )
                clickElement(driver, """new UiSelector().description("user-dialog-cell")""")
                assertElementVisible(driver, """new UiSelector().text("$topicContent")""")

                clickElement(driver, """new UiSelector().text("$topicContent")""")
                clickElement(driver, """new UiSelector().description("topic")""")
                clickElement(driver, """new UiSelector().text("Favorite")""")
                waitUntilTopicFavorited(data.authenticated.sessionManager, data.topicId)

                driver.navigate().back()
                assertElementVisible(driver, """new UiSelector().description("topic")""")
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
                val owner = createAuthenticatedSession(ports)
                val aidSuffix = (now % 1_000_000).toString().padStart(6, '0')
                val communityName = "community-$aidSuffix"
                val communityAid = "sc$aidSuffix"
                val communityId = createCommunityByApi(
                    owner.sessionManager,
                    communityName,
                    communityAid
                )
                val topicId = createTopicByApi(
                    owner.sessionManager,
                    ObjectType.COMMUNITY,
                    communityId,
                    topicContent
                )
                val viewer = createAuthenticatedSession(ports)
                viewer.sessionManager.joinCommunity(communityId).getOrThrow()
                val sessionJson = buildInjectedSessionJson(viewer.session)
                pushInjectedSessionToPrivateDir(packageName, sessionJson)
                owner.sessionManager.client.close()
                SubscriptionTopicScenario(viewer, topicId, communityName)
            }
        ) { driver, data ->
            try {
                openCommunityFromCommunitiesTab(driver, data.communityName)

                clickElement(
                    driver,
                    """new UiSelector().text("$topicContent")""",
                )
                clickElement(driver, """new UiSelector().description("topic")""")
                clickElement(driver, """new UiSelector().text("Subscription")""")
                waitUntilTopicSubscribed(data.authenticated.sessionManager, data.topicId)

                driver.navigate().back()
                assertElementVisible(driver, """new UiSelector().description("topic")""")
            } finally {
                data.authenticated.sessionManager.client.close()
            }
        }
    }

    @Test
    fun `test community profile actions from joined community`() =
        runPreparedCommunityRoomScenario {
                driver,
                data,
            ->
            openPreparedCommunity(driver, data)
            openCommunityDialog(driver, data)

            clickElement(driver, """new UiSelector().text("Favorite")""")
            clickElement(driver, """new UiSelector().text("Subscription")""")
            clickElement(driver, """new UiSelector().text("All members")""")

            clickElement(
                driver,
                """new UiSelector().textContains("${data.ownerSession.address}")""",
            )
        }

    @Test
    fun `test publish topic in joined community`() = runPreparedCommunityRoomScenario {
            driver,
            data,
        ->
        val communityTopicContent = "appium-community-topic-${System.currentTimeMillis()}"
        openPreparedCommunity(driver, data)
        openCommunityDialog(driver, data)

        clickElement(driver, """new UiSelector().text("Add")""")
        saveDebugSnapshot(driver, "community-after-add")
        clickElement(driver, """new UiSelector().text("Raw")""")
        inputElement(
            driver,
            """new UiSelector().className("android.widget.EditText")""",
            communityTopicContent
        )
        clickElement(
            driver,
            """new UiSelector().description("submit")""",
        )
        assertElementVisible(driver, """new UiSelector().text("$communityTopicContent")""")
    }

    @Test
    fun `test publish topic in community room`() = runPreparedCommunityRoomScenario {
            driver,
            data,
        ->
        val roomTopicContent = "appium-room-topic-${System.currentTimeMillis()}"
        openPreparedCommunity(driver, data)

        clickElement(driver, """new UiSelector().text("Rooms")""")
        clickElement(driver, """new UiSelector().text("${data.roomName}")""")
        inputElement(
            driver,
            """new UiSelector().className("android.widget.EditText")""",
            roomTopicContent
        )
        clickElement(
            driver,
            """new UiSelector().description("Send")""",
        )
        assertElementVisible(driver, """new UiSelector().text("$roomTopicContent")""")
    }

    private fun runPreparedCommunityRoomScenario(
        block: suspend (AndroidDriver, PreparedScenario) -> Unit
    ) = runAppiumBlockingTest {
        loadCryptoLibIfNeed()
        val now = System.currentTimeMillis()

        runType1Test(
            beforeDriverLaunch = { ports, packageName ->
                val prepared = prepareJoinAndPostingScenario(
                    ports = ports,
                    now = now,
                )
                val sessionJson = buildInjectedSessionJson(prepared.viewerSession)
                pushInjectedSessionToPrivateDir(packageName, sessionJson)
                prepared
            }
        ) { driver, data ->
            block(driver, data)
        }
    }

    private fun openPreparedCommunity(driver: AndroidDriver, data: PreparedScenario) {
        openCommunityFromCommunitiesTab(driver, data.communityName)
    }

    private fun openCommunityFromCommunitiesTab(driver: AndroidDriver, communityName: String) {
        clickElement(driver, """new UiSelector().text("Communities")""")
        clickElement(
            driver,
            """new UiSelector().text("$communityName")""",
        )
    }

    private fun openCommunityDialog(driver: AndroidDriver, data: PreparedScenario) {
        clickElement(
            driver,
            """new UiSelector().text("${data.communityName.first()}")"""
        )
    }

    private suspend fun prepareJoinAndPostingScenario(
        ports: AppiumPorts,
        now: Long,
    ): PreparedScenario {
        val owner = createAuthenticatedSession(ports)
        val aidSuffix = (now % 1_000_000).toString().padStart(6, '0')
        val communityName = "community-$aidSuffix"
        val roomName = "room-$aidSuffix"
        val communityAid = "c$aidSuffix"
        val roomAid = "r$aidSuffix"
        val ownerCommunityTopic = "appium-owner-community-topic-$now"
        val communityId = createCommunityByApi(owner.sessionManager, communityName, communityAid)
        val roomId = createRoomByApi(owner.sessionManager, roomName, roomAid, communityId)
        createTopicByApi(
            owner.sessionManager,
            ObjectType.COMMUNITY,
            communityId,
            ownerCommunityTopic
        )

        val viewer = createAuthenticatedSession(ports)
        viewer.sessionManager.joinCommunity(communityId).getOrThrow()
        val result = PreparedScenario(
            ownerSession = owner.session,
            viewerSession = viewer.session,
            communityId = communityId,
            roomId = roomId,
            communityName = communityName,
            roomName = roomName,
        )
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
        val topicInfo = sessionManager.getTopicInfo(topicId).getOrThrow()
        if (topicInfo.favoriteId != null) {
            return
        }
        delay(500.milliseconds)
    }
    error("Topic not marked as favorite: $topicId")
}

private suspend fun waitUntilTopicSubscribed(
    sessionManager: UserSessionManager,
    topicId: Long,
    timeoutMillis: Long = java.time.Duration.ofSeconds(30).toMillis(),
) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
        val topicInfo = sessionManager.getTopicInfo(topicId).getOrThrow()
        if (topicInfo.subscriptionId != null) {
            return
        }
        delay(500.milliseconds)
    }
    error("Topic not marked as subscribed: $topicId")
}

private suspend fun createCommunityByApi(
    manager: UserSessionManager,
    name: String,
    aid: String
): Long {
    return manager.createCommunity(NewCommunity(name, aid)).getOrThrow().id
}

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

private data class FavoriteTopicScenario(
    val authenticated: AuthenticatedSession,
    val topicId: Long,
)

private data class SubscriptionTopicScenario(
    val authenticated: AuthenticatedSession,
    val topicId: Long,
    val communityName: String,
)

private data class PreparedScenario(
    val ownerSession: InjectedSession,
    val viewerSession: InjectedSession,
    val communityId: Long,
    val roomId: Long,
    val communityName: String,
    val roomName: String,
)
