/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import com.storyteller_f.a.client.core.UserSessionManager
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.io.File
import java.time.Duration

const val CLI_READY_PORT = 8081
private const val FILESYSTEM_PORT = 8820
private const val LUCENE_PORT = 8821
private const val POSTGRES_DATABASE = "a"
private const val POSTGRES_USER = "a"
private const val POSTGRES_PASSWORD = "a-test"

data class AppiumPorts(val server: Int, val ws: Int)

data class AuthenticatedSession(val session: InjectedSession, val sessionManager: UserSessionManager)

data class AppUnderTest(val packageName: String, val mainActivityClassName: String)

suspend fun useLightweightBackendContainers(network: Network, block: suspend (GenericContainer<*>) -> Unit) {
    PostgreSQLContainer("pgvector/pgvector:pg16").apply {
        withNetwork(network)
        withNetworkAliases("appium-postgresql")
        withDatabaseName(POSTGRES_DATABASE)
        withUsername(POSTGRES_USER)
        withPassword(POSTGRES_PASSWORD)
        withTmpFs(mapOf("/var/lib/postgresql/data" to "rw"))
    }.use { postgresql ->
        postgresql.start()
        GenericContainer(DockerImageName.parse("a-filesystem:latest")).apply {
            withNetwork(network)
            withNetworkAliases("appium-filesystem")
            withEnv("FILESYSTEM_PUBLIC_URL", "http://appium-filesystem:8822")
            withTmpFs(mapOf("/data" to "rw,uid=1000,gid=1000"))
            withExposedPorts(FILESYSTEM_PORT, 8822)
            waitingFor(Wait.forHttp("/health").forPort(FILESYSTEM_PORT).withStartupTimeout(Duration.ofSeconds(30)))
        }.use { filesystem ->
            filesystem.start()
            GenericContainer(DockerImageName.parse("a-lucene:latest")).apply {
                withNetwork(network)
                withNetworkAliases("appium-lucene")
                withTmpFs(mapOf("/data" to "rw,uid=1000,gid=1000"))
                withExposedPorts(LUCENE_PORT)
                waitingFor(Wait.forHttp("/health").forPort(LUCENE_PORT).withStartupTimeout(Duration.ofSeconds(30)))
            }.use { lucene ->
                lucene.start()
                block(filesystem)
            }
        }
    }
}

suspend fun useCliInitContainer(
    network: Network,
    commonEnv: Map<String, String>,
    hostSessionPath: String,
    block: suspend () -> Unit,
) {
    val presetPath = resolveAppiumPresetPath()
    GenericContainer(DockerImageName.parse("a-cli:latest")).apply {
        withNetwork(network)
        withEnv(
            commonEnv +
                mapOf(
                    "CLI_INIT_ENABLE" to "true",
                    "CLI_READY_PORT" to CLI_READY_PORT.toString(),
                ),
        )
        withCopyFileToContainer(MountableFile.forHostPath(presetPath.toPath()), "/app/deploy/preset_data")
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
        cliContainer.runWithSessionLogs(hostSessionPath, "cli") {
            block()
        }
    }
}

suspend fun useWsContainer(
    network: Network,
    commonEnv: Map<String, String>,
    hostSessionPath: String,
    block: suspend (GenericContainer<*>) -> Unit,
) {
    GenericContainer(DockerImageName.parse("a-ws:latest")).apply {
        withNetwork(network)
        withNetworkAliases("appium-ws")
        withEnv(commonEnv)
        withExposedPorts(8813)
        waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(90)))
        withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger("appium-test-ws")))
        withStartupAttempts(3)
    }.use { wsContainer ->
        wsContainer.runWithSessionLogs(hostSessionPath, "ws") {
            block(wsContainer)
        }
    }
}

suspend fun useServerContainer(
    network: Network,
    commonEnv: Map<String, String>,
    hostSessionPath: String,
    block: suspend (GenericContainer<*>) -> Unit,
) {
    GenericContainer(DockerImageName.parse("a-server:latest")).apply {
        withNetwork(network)
        withEnv(commonEnv)
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
        serverContainer.runWithSessionLogs(hostSessionPath, "server") {
            block(serverContainer)
        }
    }
}

suspend fun useWorkerContainer(
    network: Network,
    commonEnv: Map<String, String>,
    hostSessionPath: String,
    block: suspend (GenericContainer<*>) -> Unit,
) {
    GenericContainer(DockerImageName.parse("a-worker:latest")).apply {
        withNetwork(network)
        withEnv(commonEnv)
        withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger("appium-test-worker")))
        withStartupAttempts(3)
    }.use { workerContainer ->
        workerContainer.runWithSessionLogs(hostSessionPath, "worker") {
            block(workerContainer)
        }
    }
}

private suspend fun GenericContainer<*>.runWithSessionLogs(
    hostSessionPath: String,
    serviceName: String,
    block: suspend () -> Unit,
) {
    try {
        start()
        block()
    } finally {
        if (containerId != null) {
            // Export before use closes the container, including startup and test failures.
            runCatching {
                val archive = File(hostSessionPath, "logs/$serviceName.tar")
                archive.parentFile.mkdirs()
                dockerClient.copyArchiveFromContainerCmd(containerId, "/home/app/logs/$serviceName").exec()
                    .buffered().use { input ->
                        archive.outputStream().buffered().use { output -> input.copyTo(output) }
                    }
            }.onFailure { failure ->
                LoggerFactory.getLogger("appium-test-$serviceName").warn("Failed to export service logs", failure)
            }
        }
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
}

fun buildContainerEnv(): Map<String, String> {
    val envFromFile = parseEnvFile(File("../../cloud/server/src/test/resources/test.env"))
    return envFromFile - "LOG_PATH" +
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
            "MEDIA_SERVICE" to "filesystem",
            "FILESYSTEM_RPC_URL" to "ws://appium-filesystem:$FILESYSTEM_PORT/rpc",
            "SEARCH_SERVICE" to "lucene",
            "LUCENE_RPC_URL" to "ws://appium-lucene:$LUCENE_PORT/rpc",
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
