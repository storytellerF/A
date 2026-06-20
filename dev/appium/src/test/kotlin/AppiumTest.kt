import com.storyteller_f.a.api.NewCommunity
import com.storyteller_f.a.api.NewRoom
import com.storyteller_f.a.client.core.AuthKey
import com.storyteller_f.a.client.core.PanelSessionManager
import com.storyteller_f.a.client.core.SimplePassHolder
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.buildWebSocketUrl
import com.storyteller_f.a.client.core.createCommunity
import com.storyteller_f.a.client.core.createRoom
import com.storyteller_f.a.client.core.createSimplePanelSessionManager
import com.storyteller_f.a.client.core.createSimpleUserSessionManager
import com.storyteller_f.a.client.core.createTopic
import com.storyteller_f.a.client.core.defaultClientConfigure
import com.storyteller_f.a.client.core.defaultClientConfigureForPanel
import com.storyteller_f.a.client.core.getAuthKey
import com.storyteller_f.a.client.core.getClient
import com.storyteller_f.a.client.core.getCommunityInfo
import com.storyteller_f.a.client.core.getTopicInfo
import com.storyteller_f.a.client.core.joinCommunity
import com.storyteller_f.a.client.core.panelSignUp
import com.storyteller_f.a.client.core.userSignIn
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.loadCryptoLibIfNeed
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.type.ObjectType
import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.rules.TestName
import org.openqa.selenium.OutputType
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.net.URI
import java.time.Duration
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val APP_LOG_FILE_NAME = "appium-app.log"
private const val INJECTED_SESSION_TEMP_PATH = "/data/local/tmp/appium-session-session.json"
private const val INJECTED_SESSION_DIR = "files/appium-session"
private const val INJECTED_SESSION_FILE = "files/appium-session/session.json"
private const val CLI_READY_PORT = 8081
private const val APP_MAIN_ACTIVITY_CLASS_NAME = "com.storyteller_f.a.app.MainActivity"
private const val PANEL_MAIN_ACTIVITY_CLASS_NAME = "com.storyteller_f.a.panel.MainActivity"

private val appUnderTest = AppUnderTest(
    packageName = resolveAppPackageName(),
    mainActivityClassName = APP_MAIN_ACTIVITY_CLASS_NAME,
)

private val panelUnderTest = AppUnderTest(
    packageName = resolvePanelPackageName(),
    mainActivityClassName = PANEL_MAIN_ACTIVITY_CLASS_NAME,
)

/**
 * 禁止等待设备上线，如果设备不存在就任由其失败。
 * 禁止直接通过gradle 启动测试，必须使用 build-and-test.sh --appium 启动，build-and-test.sh 中会尝试连接模拟器
 */
class AppiumTest {
    @get:Rule
    val name = TestName()

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
                clickElement(driver, """new UiSelector().resourceIdMatches(".*user-dialog-cell")""")
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

    private fun runAppiumBlockingTest(block: suspend () -> Unit) = runBlocking {
        withTimeout(10.minutes) {
            block()
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun runAppiumTest(
        block: suspend (AppiumPorts) -> Unit
    ) {
        val sessionId = Uuid.random().toHexString()
        val hostSessionPath = File("build/test/appium/sessions", sessionId).canonicalPath
        prepareSessionDirectories(hostSessionPath)
        val containerDataPath = "/appium-session"
        System.setProperty("api.version", "1.44")
        Network.newNetwork().use { network ->
            useDatabaseContainer(network) { databaseContainer ->
                val commonEnv = buildContainerEnv(containerDataPath, databaseContainer)
                useCliInitContainer(
                    network = network,
                    commonEnv = commonEnv,
                    hostSessionPath = hostSessionPath,
                    containerDataPath = containerDataPath,
                ) {
                    useWsContainer(
                        network = network,
                        commonEnv = commonEnv,
                        hostSessionPath = hostSessionPath,
                        containerDataPath = containerDataPath,
                    ) { wsContainer ->
                        val hostWsPort = wsContainer.getMappedPort(8813)
                        bindAndroidReverse(hostPort = hostWsPort, devicePort = 8813)
                        useServerContainer(
                            network = network,
                            commonEnv = commonEnv,
                            hostSessionPath = hostSessionPath,
                            containerDataPath = containerDataPath,
                        ) { serverContainer ->
                            val hostServerPort = serverContainer.getMappedPort(8811)
                            bindAndroidReverse(hostPort = hostServerPort, devicePort = 8811)
                            useWorkerContainer(
                                network = network,
                                commonEnv = commonEnv,
                                hostSessionPath = hostSessionPath,
                                containerDataPath = containerDataPath,
                            ) {
                                block(AppiumPorts(server = hostServerPort, ws = hostWsPort))
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun runType2Test(
        app: AppUnderTest = appUnderTest,
        block: suspend (AndroidDriver) -> Unit
    ) {
        runType1Test(app, { _, _ ->
        }, { driver, _ ->
            block(driver)
        })
    }

    private suspend fun <T> runType1Test(
        app: AppUnderTest = appUnderTest,
        beforeDriverLaunch: suspend (AppiumPorts, String) -> T,
        block: suspend (AndroidDriver, T) -> Unit
    ) {
        runAppiumTest {
            var driver: AndroidDriver? = null
            val packageName = app.packageName
            try {
                val url = "http://127.0.0.1:4723"
                val remoteAddress = URI(url).toURL()

                clearAppData(packageName)
                val session = beforeDriverLaunch(it, packageName)
                val options = UiAutomator2Options()
                    .setAppPackage(packageName)
                    .setAppActivity(app.mainActivityClassName)
                    .setNoReset(true)
                driver = AndroidDriver(remoteAddress, options)
                driver.startRecordingScreen()
                block(driver, session)
            } finally {
                if (driver != null) {
                    try {
                        val content = driver.stopRecordingScreen()
                        val decoded = Base64.getDecoder().decode(content)
                        val dir =
                            File("build/test/appium-records/${this@AppiumTest.javaClass.simpleName}")
                        dir.mkdirs()
                        val file = File(dir, "${name.methodName}.mp4")
                        file.writeBytes(decoded)
                    } catch (e: Exception) {
                        println(e)
                    }
                    copyAppLogToBuild(name.methodName, packageName)
                    driver.quit()
                }
            }
        }
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

    private suspend fun generatePrivateKey(): String {
        return getAlgo(AlgoType.P256).generatePemKeyPair().getOrThrow().first
    }

    private suspend fun createPreRegisteredSession(ports: AppiumPorts): InjectedSession {
        val algo = getAlgo(AlgoType.P256)
        val (pemPrivateKey, _) = algo.generatePemKeyPair().getOrThrow()
        val derPrivateKey = algo.getDerPrivateKey(pemPrivateKey).getOrThrow()
        val derPublicKey = algo.getDerPublicKeyFromPrivateKey(pemPrivateKey).getOrThrow()
        val address = algo.calcAddress(derPublicKey).getOrThrow()
        val passHolder = SimplePassHolder()
        val manager = createApiSessionManager(ports, passHolder)
        val authKey = getAuthKey(AlgoType.P256, pemPrivateKey)
        manager.userSignIn(authKey, passHolder)
        manager.client.close()
        return InjectedSession(
            address = address,
            pemPrivateKey = pemPrivateKey,
            derPrivateKey = derPrivateKey,
            derPublicKey = derPublicKey,
        )
    }

    private suspend fun createPreRegisteredPanelSession(ports: AppiumPorts): InjectedSession {
        val algo = getAlgo(AlgoType.P256)
        val (pemPrivateKey, _) = algo.generatePemKeyPair().getOrThrow()
        val derPrivateKey = algo.getDerPrivateKey(pemPrivateKey).getOrThrow()
        val derPublicKey = algo.getDerPublicKeyFromPrivateKey(pemPrivateKey).getOrThrow()
        val address = algo.calcAddress(derPublicKey).getOrThrow()
        val passHolder = SimplePassHolder()
        val manager = createPanelApiSessionManager(ports, passHolder)
        val authKey = getAuthKey(AlgoType.P256, pemPrivateKey)
        manager.panelSignUp(authKey, passHolder)
        manager.client.close()
        return InjectedSession(
            address = address,
            pemPrivateKey = pemPrivateKey,
            derPrivateKey = derPrivateKey,
            derPublicKey = derPublicKey,
        )
    }

    private suspend fun createAuthenticatedSession(ports: AppiumPorts): AuthenticatedSession {
        val session = createPreRegisteredSession(ports)
        val passHolder = SimplePassHolder()
        val manager = createApiSessionManager(ports, passHolder)
        val authKey = AuthKey.P256(
            pemPrivateKey = session.pemPrivateKey,
            derPrivateKey = session.derPrivateKey,
            derPublicKey = session.derPublicKey,
        )
        manager.userSignIn(authKey, passHolder)
        return AuthenticatedSession(session, manager)
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
        waitUntilCommunityJoined(viewer.sessionManager, communityId)
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

    private fun buildInjectedSessionJson(session: InjectedSession): String {
        return buildJsonObject {
            put("algo", "P256")
            put("address", session.address)
            put("pemPrivateKey", session.pemPrivateKey)
            put("derPrivateKey", session.derPrivateKey)
            put("derPublicKey", session.derPublicKey)
        }.toString()
    }

    private fun pushInjectedSessionToPrivateDir(packageName: String, content: String) {
        val file = File("build/test/appium/tmp/injected-session-${name.methodName}.json")
        file.parentFile?.mkdirs()
        file.writeText(content)
        runAdbCommand("push", file.canonicalPath, INJECTED_SESSION_TEMP_PATH)
        runAdbCommand("shell", "run-as", packageName, "mkdir", "-p", INJECTED_SESSION_DIR)
        runAdbCommand(
            "shell",
            "run-as",
            packageName,
            "cp",
            INJECTED_SESSION_TEMP_PATH,
            INJECTED_SESSION_FILE
        )
        // 确保成功推送到目标位置
        runAdbCommand("shell", "run-as", packageName, "cat", INJECTED_SESSION_FILE)
    }
}

private suspend fun waitUntilTopicFavorited(
    sessionManager: UserSessionManager,
    topicId: Long,
    timeoutMillis: Long = Duration.ofSeconds(30).toMillis(),
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
    timeoutMillis: Long = Duration.ofSeconds(30).toMillis(),
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

private suspend fun waitUntilCommunityJoined(
    sessionManager: UserSessionManager,
    communityId: Long,
    timeoutMillis: Long = Duration.ofSeconds(30).toMillis(),
) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
        val community = sessionManager.getCommunityInfo(communityId).getOrThrow()
        if (community.isJoined) {
            return
        }
        delay(500.milliseconds)
    }
    error("Community not visible in joined communities: $communityId")
}

private fun createApiSessionManager(ports: AppiumPorts, passHolder: SimplePassHolder) = createSimpleUserSessionManager(
    buildWebSocketUrl("ws://127.0.0.1:${ports.ws}"),
    AcceptAllCookiesStorage(),
    passHolder,
    { model, cookieStorage ->
        getClient {
            defaultClientConfigure(
                cookieStorage,
                model,
                passHolder,
                "http://127.0.0.1:${ports.server}"
            )
        }
    }
) { _, _, _ -> }

private fun createPanelApiSessionManager(ports: AppiumPorts, passHolder: SimplePassHolder): PanelSessionManager =
    createSimplePanelSessionManager(passHolder, AcceptAllCookiesStorage()) { model, cookieStorage ->
        getClient {
            defaultClientConfigureForPanel(
                cookieStorage,
                model,
                passHolder,
                "http://127.0.0.1:${ports.server}",
            )
        }
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

private suspend fun useDatabaseContainer(
    network: Network,
    block: suspend (PostgreSQLContainer<*>) -> Unit
) {
    PostgreSQLContainer("pgvector/pgvector:pg16").apply {
        withNetwork(network)
        withNetworkAliases("appium-postgres")
    }.use { container ->
        container.start()
        block(container)
    }
}

private suspend fun useCliInitContainer(
    network: Network,
    commonEnv: Map<String, String>,
    hostSessionPath: String,
    containerDataPath: String,
    block: suspend () -> Unit
) {
    val presetPath = resolveAppiumPresetPath()
    GenericContainer(DockerImageName.parse("a-cli:latest")).apply {
        withNetwork(network)
        withEnv(
            commonEnv + mapOf(
                "CLI_INIT_ENABLE" to "true",
                "CLI_READY_PORT" to CLI_READY_PORT.toString(),
            )
        )
        withFileSystemBind(hostSessionPath, containerDataPath, BindMode.READ_WRITE)
        withFileSystemBind(presetPath.canonicalPath, "/app/deploy/preset_data", BindMode.READ_ONLY)
        withExposedPorts(CLI_READY_PORT)
        waitingFor(
            Wait.forHttp("/")
                .forPort(CLI_READY_PORT)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofSeconds(90))
        )
        withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger("appium-test-cli")))
        withStartupAttempts(3)
    }.use { cliContainer ->
        cliContainer.start()
        block()
    }
}

private suspend fun useWsContainer(
    network: Network,
    commonEnv: Map<String, String>,
    hostSessionPath: String,
    containerDataPath: String,
    block: suspend (GenericContainer<*>) -> Unit
) {
    GenericContainer(DockerImageName.parse("a-ws:latest")).apply {
        withNetwork(network)
        withNetworkAliases("appium-ws")
        withEnv(commonEnv)
        withFileSystemBind(hostSessionPath, containerDataPath, BindMode.READ_WRITE)
        withExposedPorts(8813)
        waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(90)))
        withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger("appium-test-ws")))
        withStartupAttempts(3)
    }.use { wsContainer ->
        wsContainer.start()
        block(wsContainer)
    }
}

private suspend fun useServerContainer(
    network: Network,
    commonEnv: Map<String, String>,
    hostSessionPath: String,
    containerDataPath: String,
    block: suspend (GenericContainer<*>) -> Unit
) {
    GenericContainer(DockerImageName.parse("a-server:latest")).apply {
        withNetwork(network)
        withEnv(commonEnv)
        withFileSystemBind(hostSessionPath, containerDataPath, BindMode.READ_WRITE)
        withExposedPorts(8811)
        waitingFor(
            Wait.forHttp("/metrics")
                .forPort(8811)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofSeconds(90))
        )
        withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger("appium-test-server")))
        withStartupAttempts(3)
    }.use { serverContainer ->
        serverContainer.start()
        block(serverContainer)
    }
}

private suspend fun useWorkerContainer(
    network: Network,
    commonEnv: Map<String, String>,
    hostSessionPath: String,
    containerDataPath: String,
    block: suspend (GenericContainer<*>) -> Unit
) {
    GenericContainer(DockerImageName.parse("a-worker:latest")).apply {
        withNetwork(network)
        withEnv(commonEnv)
        withFileSystemBind(hostSessionPath, containerDataPath, BindMode.READ_WRITE)
        withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger("appium-test-worker")))
        withStartupAttempts(3)
    }.use { workerContainer ->
        workerContainer.start()
        block(workerContainer)
    }
}

private fun resolveAppiumPresetPath(): File {
    return sequenceOf(
        File("src/test/resources/preset"),
        File("dev/appium/src/test/resources/preset"),
    ).firstOrNull { File(it, "0_preset_user.json").exists() }
        ?: error("Appium preset data directory not found")
}

private fun readAppiumSystemPrivateKey(): String {
    return File(resolveAppiumPresetPath(), "secrets/p-system").readText().replace("\r\n", "\n")
}

private data class InjectedSession(
    val address: String,
    val pemPrivateKey: String,
    val derPrivateKey: String,
    val derPublicKey: String,
)

private data class AppiumPorts(
    val server: Int,
    val ws: Int,
)

private data class AuthenticatedSession(
    val session: InjectedSession,
    val sessionManager: UserSessionManager,
)

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

private data class AppUnderTest(
    val packageName: String,
    val mainActivityClassName: String,
)

private fun prepareSessionDirectories(sessionPath: String) {
    val sessionDir = File(sessionPath)
    sessionDir.mkdirs()
    File(sessionDir, "logs").mkdirs()
    File(sessionDir, "lucene").mkdirs()
    File(sessionDir, "files").mkdirs()
}

private fun clearAppData(packageName: String) {
    runAdbCommand("shell", "pm", "clear", packageName)
}

private fun buildContainerEnv(
    containerDataPath: String,
    postgresContainer: PostgreSQLContainer<*>
): Map<String, String> {
    val envFromFile = parseEnvFile(File("../../cloud/server/src/test/resources/test.env"))
    val databaseUri = "r2dbc:postgresql://appium-postgres:5432/${postgresContainer.databaseName}"
    return envFromFile + mapOf(
        "BUILD_TYPE" to "test",
        "FLAVOR" to "dev",
        "SERVER_PORT" to "8811",
        "WS_SERVER_PORT" to "8813",
        "SERVER_URL" to "http://10.0.2.2:8811",
        "WS_SERVER_URL" to "ws://10.0.2.2:8813",
        "WS_RPC_URL" to "ws://appium-ws:8813/rpc",
        "SESSION_SECRET" to "appium-session-secret",
        "DATABASE_URI" to databaseUri,
        "DATABASE_DRIVER" to "postgresql",
        "DATABASE_USER" to postgresContainer.username,
        "DATABASE_PASS" to postgresContainer.password,
        "LUCENE_BASE_PATH" to "$containerDataPath/lucene",
        "FILE_SYSTEM_MEDIA_PATH" to "$containerDataPath/files",
        "LOG_PATH" to "$containerDataPath/logs",
        "INIT_ENABLE" to "false"
    )
}

private fun parseEnvFile(file: File): Map<String, String> {
    if (!file.exists()) return emptyMap()
    return file.readLines().asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val split = line.split("=", limit = 2)
            split.firstOrNull()?.takeIf { it.isNotBlank() }?.let { key ->
                key to split.getOrElse(1) { "" }
            }
        }
        .toMap()
}

private fun bindAndroidReverse(hostPort: Int, devicePort: Int) {
    println("bind android reverse $hostPort $devicePort")
    val devices = runAdbCommandAllowFailure("devices").connectedDeviceSerials()
    check(devices.isNotEmpty()) { "No Android device available for adb reverse" }
    devices.forEach { device ->
        val result = runAdbCommandAllowFailure(
            "-s",
            device,
            "reverse",
            "tcp:$devicePort",
            "tcp:$hostPort"
        )
        check(result.exitCode == 0) {
            "Failed to bind android reverse for $device tcp:$devicePort -> tcp:$hostPort: " +
                result.output.ifBlank { "exitCode=${result.exitCode}" }
        }
        waitForAndroidReverse(device, devicePort)
    }
}

private fun waitForAndroidReverse(device: String, devicePort: Int) {
    val result = runAdbCommandAllowFailure(
        "-s",
        device,
        "shell",
        "nc",
        "-z",
        "127.0.0.1",
        devicePort.toString()

    )
    if (result.exitCode == 0) {
        println("android reverse $device tcp:$devicePort is reachable")
        return
    }

    val reverseList = runAdbCommandAllowFailure("-s", device, "reverse", "--list").output
    error(
        "android reverse $device tcp:$devicePort is not reachable. " +
            "adb reverse --list: ${reverseList.ifBlank { "<empty>" }}"
    )
}

private fun copyAppLogToBuild(testName: String, packageName: String) {
    val outputDir = File("build/test/appium-logs/AppiumTest")
    outputDir.mkdirs()
    val outputFile = File(outputDir, "$testName.log")
    val logResult = runAdbCommandAllowFailure(
        "exec-out",
        "run-as",
        packageName,
        "cat",
        "files/logs/$APP_LOG_FILE_NAME"
    )
    if (logResult.exitCode == 0 && logResult.output.isNotBlank()) {
        outputFile.writeText(logResult.output)
        return
    }

    outputFile.writeText(
        "Failed to export app log: ${logResult.output.ifBlank { "exitCode=${logResult.exitCode}" }}"
    )
}

private data class AdbCommandResult(
    val exitCode: Int,
    val output: String,
)

private fun adbProcessBuilder(args: List<String>): ProcessBuilder {
    val home = System.getProperty("user.home")
    val processBuilder = ProcessBuilder(listOf("$home/Android/Sdk/platform-tools/adb") + args)
        .redirectErrorStream(true)
    return processBuilder
}

private fun runAdbCommandAllowFailure(vararg args: String): AdbCommandResult {
    val process = adbProcessBuilder(args.toList()).start()
    val exitCode = process.waitFor()
    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    return AdbCommandResult(exitCode = exitCode, output = output)
}

private fun runAdbCommand(vararg args: String) {
    val process = adbProcessBuilder(args.toList()).start()
    val exitCode = process.waitFor()
    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    check(exitCode == 0) {
        if (output.isNotEmpty()) {
            "adb command failed (${args.joinToString(" ")}): $output"
        } else {
            "adb command failed (${args.joinToString(" ")})"
        }
    }
    println("execute [${args.joinToString(" ")}] success")
}

private fun AdbCommandResult.connectedDeviceSerials(): List<String> {
    return output.lineSequence()
        .drop(1)
        .map { it.trim().split(Regex("\\s+")) }
        .filter { it.size >= 2 && it[1] == "device" }
        .map { it[0] }
        .toList()
}

private fun resolveAppPackageName(): String = resolvePackageName(
    metadataCandidates = appMetadataCandidates(),
)

private fun resolvePanelPackageName(): String = resolvePackageName(
    metadataCandidates = panelMetadataCandidates(),
)

private fun appMetadataCandidates(): Sequence<File> = sequenceOf(
    File("../../app/androidApp/build/outputs/apk/debug/output-metadata.json"),
    File("app/androidApp/build/outputs/apk/debug/output-metadata.json"),
)

private fun panelMetadataCandidates(): Sequence<File> = sequenceOf(
    File("../../panel/androidApp/build/outputs/apk/debug/output-metadata.json"),
    File("panel/androidApp/build/outputs/apk/debug/output-metadata.json"),
)

private fun resolvePackageName(
    metadataCandidates: Sequence<File>,
): String {
    val metadataFile = metadataCandidates.firstOrNull { it.exists() }
    if (metadataFile == null) throw Exception("no metadata file")
    val applicationId = readApkMetadata(metadataFile).stringValue("applicationId")
    if (applicationId.isNullOrBlank()) {
        throw Exception("read applicationId failed")
    }
    return applicationId
}

private fun readApkMetadata(metadataFile: File): JsonObject {
    return Json.parseToJsonElement(metadataFile.readText()).jsonObject
}

private fun JsonObject.stringValue(name: String): String? {
    return this[name]?.jsonPrimitive?.contentOrNull
}

private fun assertElementVisible(driver: AndroidDriver, selector: String) {
    try {
        val wait = WebDriverWait(driver, Duration.ofSeconds(100))
        val element =
            wait.until(
                ExpectedConditions.presenceOfElementLocated(
                    AppiumBy.androidUIAutomator(
                        selector
                    )
                )
            )
        assertTrue(element.isDisplayed)
    } catch (e: Throwable) {
        runCatching {
            saveDebugSnapshot(driver, "assert-visible-failed-${System.currentTimeMillis()}")
        }
        throw e
    }
}

private fun assertElementNotVisible(
    driver: AndroidDriver,
    selector: String,
    seconds: Long = 5,
) {
    try {
        val locator = AppiumBy.androidUIAutomator(selector)
        WebDriverWait(driver, Duration.ofSeconds(seconds)).until {
            driver.findElements(locator).isEmpty()
        }
    } catch (e: Throwable) {
        runCatching {
            saveDebugSnapshot(driver, "click-failed-${System.currentTimeMillis()}")
        }
        throw e
    }
}

private fun clickElement(
    driver: AndroidDriver,
    selector: String,
    seconds: Long = 100
) {
    try {
        val locator = AppiumBy.androidUIAutomator(selector)
        if (seconds > 0) {
            WebDriverWait(
                driver,
                Duration.ofSeconds(seconds)
            ).until(ExpectedConditions.presenceOfElementLocated(locator))
        }
        driver.findElement(locator).click()
    } catch (e: Throwable) {
        runCatching {
            saveDebugSnapshot(driver, "click-failed-${System.currentTimeMillis()}")
        }
        throw e
    }
}

private fun inputElement(
    driver: AndroidDriver,
    selector: String,
    input: String,
    seconds: Long = 100
) {
    try {
        val locator = AppiumBy.androidUIAutomator(selector)
        if (seconds > 0) {
            WebDriverWait(
                driver,
                Duration.ofSeconds(seconds)
            ).until(ExpectedConditions.presenceOfElementLocated(locator))
        }
        driver.findElement(locator).sendKeys(input)
    } catch (e: Throwable) {
        runCatching {
            saveDebugSnapshot(driver, "click-failed-${System.currentTimeMillis()}")
        }
        throw e
    }
}

private fun saveDebugSnapshot(driver: AndroidDriver, name: String) {
    val outputDir = File("build/test/appium-debug/AppiumTest")
    outputDir.mkdirs()
    val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    File(outputDir, "$safeName.xml").writeText(driver.pageSource.orEmpty())
    File(outputDir, "$safeName.png").writeBytes(driver.getScreenshotAs(OutputType.BYTES))
}
