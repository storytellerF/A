/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import com.storyteller_f.a.client.core.UserSessionManager
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import java.io.File
import java.time.Duration

const val CLI_READY_PORT = 8081
private const val FILESYSTEM_PORT = 8820
private const val LUCENE_PORT = 8821
private const val POSTGRES_DATABASE = "a"
private const val POSTGRES_USER = "a"
private const val POSTGRES_PASSWORD = "a-test"

private fun testServiceImage(name: String, target: String): ImageFromDockerfile {
    val projectRoot =
        generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .firstOrNull { File(it, "TestServices.Dockerfile").isFile && File(it, "settings.gradle.kts").isFile }
            ?: error("Project root containing TestServices.Dockerfile not found")
    val image =
        ImageFromDockerfile(name, false)
            .withFileFromPath("TestServices.Dockerfile", File(projectRoot, "TestServices.Dockerfile").toPath())
            .withDockerfilePath("TestServices.Dockerfile")
            .withTarget(target)
    listOf("server", "worker", "ws", "cli", "filesystem-service", "lucene-service").forEach { service ->
        image.withFileFromPath(
            "deploy/build/$service.tar",
            File(projectRoot, "cloud/$service/build/distributions/$service.tar").toPath(),
        )
    }
    listOf(
        "scripts/docker/cli-entrypoint.sh",
        "scripts/tool_scripts/flush-database.sh",
        "scripts/tool_scripts/terminal-log.sh",
    ).forEach { path -> image.withFileFromPath(path, File(projectRoot, path).toPath()) }
    return image
}

data class AppiumPorts(val server: Int, val ws: Int)

data class AuthenticatedSession(val session: InjectedSession, val sessionManager: UserSessionManager)

data class AppUnderTest(val packageName: String, val mainActivityClassName: String)

suspend fun useLightweightBackendContainers(network: Network, block: suspend () -> Unit) {
    PostgreSQLContainer("pgvector/pgvector:pg16").apply {
        withNetwork(network)
        withNetworkAliases("appium-postgresql")
        withDatabaseName(POSTGRES_DATABASE)
        withUsername(POSTGRES_USER)
        withPassword(POSTGRES_PASSWORD)
        withTmpFs(mapOf("/var/lib/postgresql/data" to "rw"))
    }.use { postgresql ->
        postgresql.start()
        GenericContainer(testServiceImage("a-filesystem:latest", "filesystem-service")).apply {
            withNetwork(network)
            withNetworkAliases("appium-filesystem")
            withEnv("SERVER_URL", "http://10.0.2.2:8811")
            withTmpFs(mapOf("/data" to "rw,uid=1000,gid=1000"))
            withExposedPorts(FILESYSTEM_PORT)
            waitingFor(Wait.forHttp("/health").forPort(FILESYSTEM_PORT).withStartupTimeout(Duration.ofSeconds(30)))
        }.use { filesystem ->
            filesystem.start()
            GenericContainer(testServiceImage("a-lucene:latest", "lucene-service")).apply {
                withNetwork(network)
                withNetworkAliases("appium-lucene")
                withTmpFs(mapOf("/data" to "rw,uid=1000,gid=1000"))
                withExposedPorts(LUCENE_PORT)
                waitingFor(Wait.forHttp("/health").forPort(LUCENE_PORT).withStartupTimeout(Duration.ofSeconds(30)))
            }.use { lucene ->
                lucene.start()
                block()
            }
        }
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
    GenericContainer(testServiceImage("a-cli:latest", "cli")).apply {
        withNetwork(network)
        withEnv(
            commonEnv +
                mapOf(
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
    GenericContainer(testServiceImage("a-ws:latest", "ws")).apply {
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
    GenericContainer(testServiceImage("a-server:latest", "server")).apply {
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
    GenericContainer(testServiceImage("a-worker:latest", "worker")).apply {
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

fun buildContainerEnv(containerDataPath: String): Map<String, String> {
    val envFromFile = parseEnvFile(File("../../cloud/server/src/test/resources/test.env"))
    return envFromFile +
        mapOf(
            "BUILD_TYPE" to "test",
            "FLAVOR" to "dev",
            "SERVER_PORT" to "8811",
            "WS_SERVER_PORT" to "8813",
            "SERVER_URL" to "http://10.0.2.2:8811",
            "WS_SERVER_URL" to "ws://10.0.2.2:8813",
            "WS_RPC_URL" to "ws://appium-ws:8813/rpc",
            "SESSION_SECRET" to "appium-session-secret",
            "DATABASE_URI" to "r2dbc:postgresql://appium-postgresql:5432/$POSTGRES_DATABASE",
            "DATABASE_DRIVER" to "postgresql",
            "DATABASE_USER" to POSTGRES_USER,
            "DATABASE_PASS" to POSTGRES_PASSWORD,
            "MEDIA_SERVICE" to "rpc",
            "FILESYSTEM_RPC_URL" to "ws://appium-filesystem:$FILESYSTEM_PORT/rpc",
            "SEARCH_SERVICE" to "rpc",
            "LUCENE_RPC_URL" to "ws://appium-lucene:$LUCENE_PORT/rpc",
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
