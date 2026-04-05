@file:Suppress("SameParameterValue")

import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.finalData
import com.storyteller_f.shared.loadCryptoLibIfNeed
import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import io.appium.java_client.plugins.storage.StorageClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val appLogFileName = "appium-app.log"
private const val injectedSessionTempPath = "/data/local/tmp/appium-session-session.json"
private const val injectedSessionDir = "files/appium-session"
private const val injectedSessionFile = "files/appium-session/session.json"
private const val mainActivityClassName = "com.storyteller_f.a.app.MainActivity"
private val applicationIdRegex = Regex("\"applicationId\"\\s*:\\s*\"([^\"]+)\"")
private val appLogLookupScript = """
    for path in \
        ./files/logs/$appLogFileName \
        files/logs/$appLogFileName \
        /data/user/0/${'$'}0/files/logs/$appLogFileName \
        /data/data/${'$'}0/files/logs/$appLogFileName
    do
        if [ -f "${'$'}path" ]; then
            cat "${'$'}path"
            exit 0
        fi
    done
    echo "log file not found for package: ${'$'}0" 1>&2
    pwd 1>&2
    find . -maxdepth 4 -name "$appLogFileName" 2>/dev/null 1>&2
    exit 1
""".trimIndent()

class AppiumTest {
    @get:Rule
    val name = TestName()

    private val cookieManager = CookieManager().apply {
        setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    }
    private val apiHttpClient: HttpClient = HttpClient.newBuilder()
        .cookieHandler(cookieManager)
        .build()

    @Test
    fun `test sign up`() = runTest(timeout = 10.minutes) {
        runAppiumTest { driver ->
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
        runAppiumTest(
            launchInstalledApp = true,
            beforeDriverLaunch = { hostServerPort, packageName ->
                val injected = createPreRegisteredSession(hostServerPort)
                val sessionJson = buildInjectedSessionJson(injected)
                pushInjectedSessionToPrivateDir(packageName, sessionJson)
            }
        ) { driver ->
            clickElement(driver, """new UiSelector().description("avatar")""")
            assertElementNotVisible(driver, """new UiSelector().text("Sign in")""")
        }
    }

    @Test
    fun `test publish topic in user space`() = runTest(timeout = 10.minutes) {
        loadCryptoLibIfNeed()
        var injectedSession: InjectedSession? = null
        val topicContent = "appium-user-space-topic-${System.currentTimeMillis()}"
        runAppiumTest(
            launchInstalledApp = true,
            beforeDriverLaunch = { hostServerPort, packageName ->
                val injected = createPreRegisteredSession(hostServerPort)
                injectedSession = injected
                val sessionJson = buildInjectedSessionJson(injected)
                pushInjectedSessionToPrivateDir(packageName, sessionJson)
            }
        ) { driver ->
            val session = checkNotNull(injectedSession) { "Injected session should be initialized" }
            clickElement(driver, """new UiSelector().description("avatar")""")
            clickElement(driver, """new UiSelector().text("ad: ${session.address}")""")
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

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun runAppiumTest(
        launchInstalledApp: Boolean = false,
        beforeDriverLaunch: suspend (hostServerPort: Int, packageName: String) -> Unit = { _, _ -> },
        block: suspend (AndroidDriver) -> Unit
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
                        var driver: AndroidDriver? = null
                        try {
                            val url = "http://127.0.0.1:4723"
                            val file = File("../../app/android/build/outputs/apk/debug/android-universal-debug.apk")
                            val packageName = resolveAppPackageName()
                            val options = if (launchInstalledApp) {
                                installAppForPrivateInjection(file, packageName)
                                clearAppData(packageName)
                                beforeDriverLaunch(hostServerPort, packageName)
                                UiAutomator2Options()
                                    .setDeviceName("device-test")
                                    .setAppPackage(packageName)
                                    .setAppActivity(mainActivityClassName)
                                    .setNoReset(true)
                            } else {
                                val storageClient = StorageClient(URI(url).toURL())
                                storageClient.reset()
                                storageClient.add(file)
                                val path = storageClient.list().first().path
                                UiAutomator2Options().setApp(path).setDeviceName("device-test")
                            }
                            val remoteAddress = URI(url).toURL()
                            driver = AndroidDriver(remoteAddress, options)
                            driver.startRecordingScreen()
                            block(driver)
                        } finally {
                            if (driver != null) {
                                try {
                                    val content = driver.stopRecordingScreen()
                                    val decoded = Base64.getDecoder().decode(content)
                                    val dir = File("build/test/appium-records/${this@AppiumTest.javaClass.simpleName}")
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
        val data = getSignData(hostServerPort)
        val signature = algo.signature(derPrivateKey, finalData(data)).getOrThrow()
        signUpByApi(hostServerPort, derPublicKey, signature)
        return InjectedSession(
            address = address,
            pemPrivateKey = pemPrivateKey,
            derPrivateKey = derPrivateKey,
            derPublicKey = derPublicKey,
        )
    }

    private fun getSignData(hostServerPort: Int): String {
        val request = HttpRequest.newBuilder()
            .uri(URI("http://127.0.0.1:$hostServerPort/accounts/get-data"))
            .GET()
            .build()
        val response = apiHttpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "Failed to get sign data, status=${response.statusCode()}, body=${response.body()}"
        }
        return response.body()
    }

    private fun signUpByApi(hostServerPort: Int, derPublicKey: String, signature: String) {
        val body = buildJsonObject {
            put("publicKey", derPublicKey)
            put("signature", signature)
        }.toString()
        val request = HttpRequest.newBuilder()
            .uri(URI("http://127.0.0.1:$hostServerPort/accounts/sign-up"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = apiHttpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "Failed to sign up by api, status=${response.statusCode()}, body=${response.body()}"
        }
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
        runAdbCommand("push", file.canonicalPath, injectedSessionTempPath)
        runAdbCommand("shell", "run-as", packageName, "mkdir", "-p", injectedSessionDir)
        runAdbCommand("shell", "run-as", packageName, "cp", injectedSessionTempPath, injectedSessionFile)
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
    val process = ProcessBuilder(
        "adb",
        "exec-out",
        "run-as",
        packageName,
        "sh",
        "-c",
        appLogLookupScript,
        packageName
    )
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.readBytes()
    val exitCode = process.waitFor()
    if (exitCode == 0) {
        outputFile.writeBytes(output)
    } else {
        outputFile.writeBytes(output)
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

    val flavor = parseEnvFile(File("../../gradle.properties"))["server.flavor"].orEmpty().ifBlank { "dev" }
    return "com.storyteller_f.a.app.${flavor.replace('-', '_')}.debug"
}

private fun assertElementVisible(driver: AndroidDriver, selector: String) {
    val wait = WebDriverWait(driver, Duration.ofSeconds(100))
    val element = wait.until(ExpectedConditions.presenceOfElementLocated(AppiumBy.androidUIAutomator(selector)))
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
        WebDriverWait(driver, Duration.ofSeconds(seconds)).until(ExpectedConditions.presenceOfElementLocated(locator))
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
        WebDriverWait(driver, Duration.ofSeconds(seconds)).until(ExpectedConditions.presenceOfElementLocated(locator))
    }
    driver.findElement(locator).sendKeys(input)
}
