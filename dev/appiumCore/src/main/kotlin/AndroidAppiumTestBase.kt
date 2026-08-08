import com.storyteller_f.a.client.core.UserSessionManager
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.time.Duration

const val CLI_READY_PORT = 8081

data class AppiumPorts(
    val server: Int,
    val ws: Int,
)

data class AuthenticatedSession(
    val session: InjectedSession,
    val sessionManager: UserSessionManager,
)

data class AppUnderTest(
    val packageName: String,
    val mainActivityClassName: String,
)

suspend fun useDatabaseContainer(
    network: Network,
    block: suspend (PostgreSQLContainer<*>) -> Unit,
) {
    PostgreSQLContainer("pgvector/pgvector:pg16").apply {
        withNetwork(network)
        withNetworkAliases("appium-postgres")
    }.use { container ->
        container.start()
        block(container)
    }
}

suspend fun useCliInitContainer(
    network: Network,
    commonEnv: Map<String, String>,
    hostSessionPath: String,
    containerDataPath: String,
    block: suspend () -> Unit,
) {
    val presetPath = resolveAppiumPresetPath()
    GenericContainer(DockerImageName.parse("a-cli:latest")).apply {
        withNetwork(network)
        withEnv(
            commonEnv + mapOf(
                "CLI_INIT_ENABLE" to "true",
                "CLI_READY_PORT" to CLI_READY_PORT.toString(),
            ),
        )
        withFileSystemBind(hostSessionPath, containerDataPath, BindMode.READ_WRITE)
        withFileSystemBind(presetPath.canonicalPath, "/app/deploy/preset_data", BindMode.READ_ONLY)
        withExposedPorts(CLI_READY_PORT)
        waitingFor(
            Wait.forHttp("/")
                .forPort(CLI_READY_PORT)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofSeconds(90)),
        )
        withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger("appium-test-cli")))
        withStartupAttempts(3)
    }.use { cliContainer ->
        cliContainer.start()
        block()
    }
}

suspend fun useWsContainer(
    network: Network,
    commonEnv: Map<String, String>,
    hostSessionPath: String,
    containerDataPath: String,
    block: suspend (GenericContainer<*>) -> Unit,
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

suspend fun useServerContainer(
    network: Network,
    commonEnv: Map<String, String>,
    hostSessionPath: String,
    containerDataPath: String,
    block: suspend (GenericContainer<*>) -> Unit,
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
                .withStartupTimeout(Duration.ofSeconds(90)),
        )
        withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger("appium-test-server")))
        withStartupAttempts(3)
    }.use { serverContainer ->
        serverContainer.start()
        block(serverContainer)
    }
}

suspend fun useWorkerContainer(
    network: Network,
    commonEnv: Map<String, String>,
    hostSessionPath: String,
    containerDataPath: String,
    block: suspend (GenericContainer<*>) -> Unit,
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

fun resolveAppiumPresetPath(): File {
    val presetPaths =
        generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .map { File(it, "dev/preset") }
    return presetPaths.firstOrNull { File(it, "0_preset_user.json").exists() }
        ?: error("Shared E2E preset data directory not found")
}

fun prepareSessionDirectories(sessionPath: String) {
    val sessionDir = File(sessionPath)
    sessionDir.mkdirs()
    File(sessionDir, "logs").mkdirs()
    File(sessionDir, "lucene").mkdirs()
    File(sessionDir, "files").mkdirs()
}

fun buildContainerEnv(
    containerDataPath: String,
    postgresContainer: PostgreSQLContainer<*>,
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
        "INIT_ENABLE" to "false",
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
