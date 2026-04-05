@file:Suppress("SameParameterValue")

import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.loadCryptoLibIfNeed
import com.storyteller_f.a.api.NewCommunity
import com.storyteller_f.a.api.NewRoom
import com.storyteller_f.a.client.core.AuthKey
import com.storyteller_f.a.client.core.RawUserPass
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.buildWebSocketUrl
import com.storyteller_f.a.client.core.createCommunity
import com.storyteller_f.a.client.core.createRoom
import com.storyteller_f.a.client.core.createTopic
import com.storyteller_f.a.client.core.createUserSessionManager
import com.storyteller_f.a.client.core.defaultClientConfigure
import com.storyteller_f.a.client.core.getAuthKey
import com.storyteller_f.a.client.core.getClient
import com.storyteller_f.a.client.core.getUserPass
import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import io.appium.java_client.plugins.storage.StorageClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.rules.TestName
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.net.URI
import java.time.Duration
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.type.ObjectType

private const val appLogFileName = "appium-app.log"
private const val appLogDeviceTempPath = "/data/local/tmp/appium-app.log"
private const val injectedSessionTempPath = "/data/local/tmp/appium-session-session.json"
private const val injectedSessionDir = "files/appium-session"
private const val injectedSessionFile = "files/appium-session/session.json"
private const val mainActivityClassName = "com.storyteller_f.a.app.MainActivity"
private val applicationIdRegex = Regex("\"applicationId\"\\s*:\\s*\"([^\"]+)\"")

class AppiumTest {
    @get:Rule
    val name = TestName()

    @Test
    fun `test sign up`() = runTest(timeout = 10.minutes) {
        runType2Test { driver ->
            val privateKeyContent = generatePrivateKey()
            clickElement(driver, """new UiSelector().description("avatar")""")
            clickElement(driver, """new UiSelector().text("Sign in")""")
            clickElement(driver, """new UiSelector().text("Go to sign up")""")
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
    fun `test sign in by injected private session`() = runTest(timeout = 10.minutes) {
        loadCryptoLibIfNeed()
        runType1Test(
            beforeDriverLaunch = { hostServerPort, packageName ->
                val injected = createPreRegisteredSession(hostServerPort)
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
    fun `test publish topic in user space`() = runTest(timeout = 10.minutes) {
        loadCryptoLibIfNeed()
        val topicContent = "appium-user-space-topic-${System.currentTimeMillis()}"
        runType1Test(
            beforeDriverLaunch = { hostServerPort, packageName ->
                val injected = createPreRegisteredSession(hostServerPort)
                val sessionJson = buildInjectedSessionJson(injected)
                pushInjectedSessionToPrivateDir(packageName, sessionJson)
                injected
            }
        ) { driver, injectedSession ->
            clickElement(driver, """new UiSelector().description("avatar")""")
            clickElement(driver, """new UiSelector().text("ad: ${injectedSession.address}")""")
            clickElement(driver, """new UiSelector().description("add topic")""")
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
    fun `test community room join and posting flows`() = runTest(timeout = 10.minutes) {
        loadCryptoLibIfNeed()
        val now = System.currentTimeMillis()
        val communityTopicContent = "appium-community-topic-$now"
        val roomTopicContent = "appium-room-topic-$now"
        runType1Test(
            beforeDriverLaunch = { hostServerPort, packageName ->
                val prepared = prepareJoinAndPostingScenario(hostServerPort, now)
                val sessionJson = buildInjectedSessionJson(prepared.viewerSession)
                pushInjectedSessionToPrivateDir(packageName, sessionJson)
                prepared
            }
        ) { driver, data ->
            clickElement(driver, """new UiSelector().text("Communities")""")
            clickElement(driver, """new UiSelector().text("${data.communityName}")""")

            clickAnyElement(
                driver,
                listOf(
                    """new UiSelector().description("Add")""",
                    """new UiSelector().text("Add")"""
                )
            )
            clickAnyElement(
                driver,
                listOf(
                    """new UiSelector().text("Confirm")""",
                    """new UiSelector().text("OK")""",
                    """new UiSelector().text("确定")"""
                )
            )

            clickElement(driver, """new UiSelector().text("Favorite")""")
            clickElement(driver, """new UiSelector().text("Subscription")""")
            clickElement(driver, """new UiSelector().text("Join community")""")
            clickElement(driver, """new UiSelector().text("All members")""")

            clickAnyElement(
                driver,
                listOf(
                    """new UiSelector().textContains("${data.ownerSession.address}")""",
                    """new UiSelector().textContains("ad:")"""
                )
            )
            assertElementVisible(driver, """new UiSelector().description("user-page")""")
            driver.navigate().back()
            driver.navigate().back()

            clickAnyElement(
                driver,
                listOf(
                    """new UiSelector().description("Add")""",
                    """new UiSelector().text("Add")"""
                )
            )
            inputElement(
                driver,
                """new UiSelector().className("android.widget.EditText")""",
                communityTopicContent
            )
            clickAnyElement(
                driver,
                listOf(
                    """new UiSelector().description("submit")""",
                    """new UiSelector().description("Send")"""
                )
            )
            assertElementVisible(driver, """new UiSelector().text("$communityTopicContent")""")

            driver.navigate().back()
            clickElement(driver, """new UiSelector().text("Rooms")""")
            clickElement(driver, """new UiSelector().text("${data.roomName}")""")

            inputElement(
                driver,
                """new UiSelector().className("android.widget.EditText")""",
                roomTopicContent
            )
            clickAnyElement(
                driver,
                listOf(
                    """new UiSelector().description("Send")""",
                    """new UiSelector().text("Send")"""
                )
            )
            clickAnyElement(
                driver,
                listOf(
                    """new UiSelector().text("Confirm")""",
                    """new UiSelector().text("OK")""",
                    """new UiSelector().text("确定")"""
                )
            )
            inputElement(
                driver,
                """new UiSelector().className("android.widget.EditText")""",
                roomTopicContent
            )
            clickAnyElement(
                driver,
                listOf(
                    """new UiSelector().description("Send")""",
                    """new UiSelector().text("Send")"""
                )
            )
            assertElementVisible(driver, """new UiSelector().text("$roomTopicContent")""")
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun runAppiumTest(
        block: suspend (Int) -> Unit
    ) {
        val sessionId = Uuid.random().toHexString()
        val hostSessionPath = File("build/test/appium/sessions", sessionId).canonicalPath
        prepareSessionDirectories(hostSessionPath)
        val containerDataPath = "/appium-session"
        System.setProperty("api.version", "1.44")
        Network.newNetwork().use { network ->
            PostgreSQLContainer("pgvector/pgvector:pg16").apply {
                withNetwork(network)
                withNetworkAliases("appium-postgres")
            }.use { container ->
                container.start()
                val commonEnv = buildContainerEnv(containerDataPath, container)
                GenericContainer(DockerImageName.parse("a-server:latest")).apply {
                    withNetwork(network)
                    withEnv(commonEnv)
                    withFileSystemBind(hostSessionPath, containerDataPath, BindMode.READ_WRITE)
                    withExposedPorts(8811)
                    withStartupAttempts(3)
                }.use { serverContainer ->
                    serverContainer.start()
                    val hostServerPort = serverContainer.getMappedPort(8811)
                    bindAndroidReverse(hostPort = hostServerPort, devicePort = 8811)
                    GenericContainer(DockerImageName.parse("a-worker:latest")).apply {
                        withNetwork(network)
                        withEnv(commonEnv)
                        withFileSystemBind(hostSessionPath, containerDataPath, BindMode.READ_WRITE)
                        withStartupAttempts(3)
                    }.use { workerContainer ->
                        workerContainer.start()
                        block(hostServerPort)
                    }
                }
            }
        }
    }

    private suspend fun runType2Test(block: suspend (AndroidDriver) -> Unit) {
        runAppiumTest {
            var driver: AndroidDriver? = null
            try {
                val url = "http://127.0.0.1:4723"
                val remoteAddress = URI(url).toURL()
                val file =
                    File("../../app/android/build/outputs/apk/debug/android-universal-debug.apk")
                val storageClient = StorageClient(URI(url).toURL())
                storageClient.reset()
                storageClient.add(file)
                val path = storageClient.list().first().path
                val options = UiAutomator2Options().setApp(path).setDeviceName("device-test")

                driver = AndroidDriver(remoteAddress, options)
                driver.startRecordingScreen()
                block(driver)
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
                    copyAppLogToBuild(name.methodName)
                    driver.quit()
                }
            }
        }
    }

    private suspend fun <T> runType1Test(
        beforeDriverLaunch: suspend (Int, String) -> T,
        block: suspend (AndroidDriver, T) -> Unit
    ) {
        runAppiumTest {
            var driver: AndroidDriver? = null
            try {
                val url = "http://127.0.0.1:4723"
                val remoteAddress = URI(url).toURL()
                val file =
                    File("../../app/android/build/outputs/apk/debug/android-universal-debug.apk")
                val packageName = resolveAppPackageName()

                installAppForPrivateInjection(file, packageName)
                clearAppData(packageName)
                val session = beforeDriverLaunch(it, packageName)
                val options = UiAutomator2Options()
                    .setDeviceName("device-test")
                    .setAppPackage(packageName)
                    .setAppActivity(mainActivityClassName)
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
                    copyAppLogToBuild(name.methodName)
                    driver.quit()
                }
            }
        }
    }

    private suspend fun generatePrivateKey(): String {
        return getAlgo().generatePemKeyPair().getOrThrow().first
    }

    private suspend fun createPreRegisteredSession(hostServerPort: Int): InjectedSession {
        val algo = getAlgo()
        val (pemPrivateKey, _) = algo.generatePemKeyPair().getOrThrow()
        val derPrivateKey = algo.getDerPrivateKey(pemPrivateKey).getOrThrow()
        val derPublicKey = algo.getDerPublicKeyFromPrivateKey(pemPrivateKey).getOrThrow()
        val address = algo.calcAddress(derPublicKey).getOrThrow()
        val manager = createApiSessionManager(hostServerPort)
        val authKey = getAuthKey(AlgoType.P256, pemPrivateKey)
        manager.getUserPass(authKey, true) { RawUserPass(it) }
        manager.client.close()
        return InjectedSession(
            address = address,
            pemPrivateKey = pemPrivateKey,
            derPrivateKey = derPrivateKey,
            derPublicKey = derPublicKey,
        )
    }

    private suspend fun createAuthenticatedSession(hostServerPort: Int): AuthenticatedSession {
        val session = createPreRegisteredSession(hostServerPort)
        val manager = createApiSessionManager(hostServerPort)
        val authKey = AuthKey.P256(
            pemPrivateKey = session.pemPrivateKey,
            derPrivateKey = session.derPrivateKey,
            derPublicKey = session.derPublicKey,
        )
        manager.getUserPass(authKey, false) { RawUserPass(it) }
        return AuthenticatedSession(session, manager)
    }

    private suspend fun prepareJoinAndPostingScenario(
        hostServerPort: Int,
        now: Long
    ): PreparedScenario {
        val owner = createAuthenticatedSession(hostServerPort)
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

        val viewer = createAuthenticatedSession(hostServerPort)
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

    private fun createApiSessionManager(hostServerPort: Int) = createUserSessionManager(
        buildWebSocketUrl("ws://127.0.0.1:$hostServerPort"),
        { model, cookieStorage ->
            getClient {
                defaultClientConfigure(
                    cookiesStorage = cookieStorage,
                    manager = model,
                    httpUrl = "http://127.0.0.1:$hostServerPort",
                )
            }
        },
        onReceiveFrame = { _, _, _ -> }
    )

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
        runAdbCommand("push", file.canonicalPath, injectedSessionTempPath)
        runAdbCommand("shell", "run-as", packageName, "mkdir", "-p", injectedSessionDir)
        runAdbCommand(
            "shell",
            "run-as",
            packageName,
            "cp",
            injectedSessionTempPath,
            injectedSessionFile
        )
        // 确保成功推送到目标位置
        runAdbCommand("shell", "run-as", packageName, "cat", injectedSessionFile)
    }
}

private data class InjectedSession(
    val address: String,
    val pemPrivateKey: String,
    val derPrivateKey: String,
    val derPublicKey: String,
)

private data class AuthenticatedSession(
    val session: InjectedSession,
    val sessionManager: UserSessionManager,
)

private data class PreparedScenario(
    val ownerSession: InjectedSession,
    val viewerSession: InjectedSession,
    val communityId: Long,
    val roomId: Long,
    val communityName: String,
    val roomName: String,
)

private fun prepareSessionDirectories(sessionPath: String) {
    val sessionDir = File(sessionPath)
    sessionDir.mkdirs()
    File(sessionDir, "logs").mkdirs()
    File(sessionDir, "lucene").mkdirs()
    File(sessionDir, "files").mkdirs()
}

private fun installAppForPrivateInjection(apkFile: File, packageName: String) {
    val installResult = runAdbCommandAllowFailure("install", "-r", apkFile.canonicalPath)
    if (installResult.exitCode == 0) {
        println("App installed successfully: ${apkFile.canonicalPath}")
        return
    }

    if (isInstallFailedBySignatureMismatch(installResult.output)) {
        println("Signature mismatch detected, uninstalling existing app and retrying installation")
        runAdbCommand("uninstall", packageName)
        runAdbCommand("install", "-r", apkFile.canonicalPath)
        println("App installed successfully after resolving signature mismatch: ${apkFile.canonicalPath}")
        return
    }

    error(
        "adb install failed (install -r ${apkFile.canonicalPath}): ${installResult.output.ifBlank { "exitCode=${installResult.exitCode}" }}"
    )
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
        "SERVER_URL" to "http://10.0.2.2:8811",
        "WS_SERVER_URL" to "ws://10.0.2.2:8811",
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
    val script = sequenceOf(
        File("scripts/android_scripts/forward-android-devices.sh"),
        File("../../scripts/android_scripts/forward-android-devices.sh")
    ).firstOrNull { it.exists() }
        ?: error("forward-android-devices.sh not found")

    val process = ProcessBuilder(
        "sh",
        script.canonicalPath,
        devicePort.toString(),
        hostPort.toString()
    )
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    val exitCode = process.waitFor()
    check(exitCode == 0) {
        if (output.isNotEmpty()) {
            "Failed to execute forward-android-devices.sh: $output"
        } else {
            "Failed to execute forward-android-devices.sh"
        }
    }
}

private fun copyAppLogToBuild(testName: String) {
    val outputDir = File("build/test/appium-logs/AppiumTest")
    outputDir.mkdirs()
    val outputFile = File(outputDir, "$testName.log")
    val packageName = resolveAppPackageName()
    val stageResult = runAdbCommandAllowFailure(
        "shell",
        "run-as",
        packageName,
        "cp",
        "files/logs/$appLogFileName",
        appLogDeviceTempPath
    )
    if (stageResult.exitCode != 0) {
        outputFile.writeText(
            "Failed to stage app log before pull: ${stageResult.output.ifBlank { "exitCode=${stageResult.exitCode}" }}"
        )
        return
    }

    val pullResult = runAdbCommandAllowFailure(
        "pull",
        appLogDeviceTempPath,
        outputFile.canonicalPath
    )
    runAdbCommandAllowFailure("shell", "rm", "-f", appLogDeviceTempPath)
    if (pullResult.exitCode != 0) {
        outputFile.writeText(
            "Failed to pull app log: ${pullResult.output.ifBlank { "exitCode=${pullResult.exitCode}" }}"
        )
    }
}

private fun runAdbCommand(vararg args: String) {
    val process = ProcessBuilder(listOf("adb") + args)
        .redirectErrorStream(true)
        .start()
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

private data class AdbCommandResult(
    val exitCode: Int,
    val output: String,
)

private fun runAdbCommandAllowFailure(vararg args: String): AdbCommandResult {
    val process = ProcessBuilder(listOf("adb") + args)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    val exitCode = process.waitFor()
    return AdbCommandResult(exitCode = exitCode, output = output)
}

private fun isInstallFailedBySignatureMismatch(output: String): Boolean {
    val normalizedOutput = output.lowercase()
    return "install_failed_update_incompatible" in normalizedOutput ||
            "signatures do not match" in normalizedOutput ||
            "install_parse_failed_inconsistent_certificates" in normalizedOutput
}

private fun resolveAppPackageName(): String {
    val metadataCandidates = sequenceOf(
        File("../../app/android/build/outputs/apk/debug/output-metadata.json"),
        File("app/android/build/outputs/apk/debug/output-metadata.json")
    )
    val metadataFile = metadataCandidates.firstOrNull { it.exists() }
    if (metadataFile != null) {
        val content = metadataFile.readText()
        val applicationId = applicationIdRegex.find(content)?.groupValues?.getOrNull(1)
        if (!applicationId.isNullOrBlank()) {
            return applicationId
        }
    }

    val flavor =
        parseEnvFile(File("../../gradle.properties"))["server.flavor"].orEmpty().ifBlank { "dev" }
    return "com.storyteller_f.a.app.${flavor.replace('-', '_')}.debug"
}

private fun assertElementVisible(driver: AndroidDriver, selector: String) {
    val wait = WebDriverWait(driver, Duration.ofSeconds(100))
    val element =
        wait.until(ExpectedConditions.presenceOfElementLocated(AppiumBy.androidUIAutomator(selector)))
    assertTrue(element.isDisplayed)
}

private fun assertElementNotVisible(
    driver: AndroidDriver,
    selector: String,
    seconds: Long = 5,
) {
    val locator = AppiumBy.androidUIAutomator(selector)
    WebDriverWait(driver, Duration.ofSeconds(seconds)).until {
        driver.findElements(locator).isEmpty()
    }
}

private fun clickElement(
    driver: AndroidDriver,
    selector: String,
    seconds: Long = 100
) {
    val locator = AppiumBy.androidUIAutomator(selector)
    if (seconds > 0) {
        WebDriverWait(
            driver,
            Duration.ofSeconds(seconds)
        ).until(ExpectedConditions.presenceOfElementLocated(locator))
    }
    driver.findElement(locator).click()
}

private fun inputElement(
    driver: AndroidDriver,
    selector: String,
    input: String,
    seconds: Long = 100
) {
    val locator = AppiumBy.androidUIAutomator(selector)
    if (seconds > 0) {
        WebDriverWait(
            driver,
            Duration.ofSeconds(seconds)
        ).until(ExpectedConditions.presenceOfElementLocated(locator))
    }
    driver.findElement(locator).sendKeys(input)
}

private fun clickAnyElement(
    driver: AndroidDriver,
    selectors: List<String>,
    seconds: Long = 100,
) {
    val deadline = System.currentTimeMillis() + Duration.ofSeconds(seconds).toMillis()
    var lastError: Throwable? = null
    while (System.currentTimeMillis() < deadline) {
        selectors.forEach { selector ->
            try {
                val locator = AppiumBy.androidUIAutomator(selector)
                val elements = driver.findElements(locator)
                if (elements.isNotEmpty()) {
                    elements.first().click()
                    return
                }
            } catch (e: Throwable) {
                lastError = e
            }
        }
    }
    throw IllegalStateException(
        "Unable to click any selector: ${selectors.joinToString()}",
        lastError
    )
}
