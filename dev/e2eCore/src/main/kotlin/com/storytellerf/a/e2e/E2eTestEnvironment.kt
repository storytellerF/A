/*
 * This is a private project. All rights reserved.
 */

package com.storytellerf.a.e2e

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val CLI_READY_PORT = 8081
private const val SERVER_PORT = 8811
private const val WEBSOCKET_PORT = 8813
private const val STARTUP_TIMEOUT_SECONDS = 90L
private const val STARTUP_ATTEMPTS = 3
private const val HEALTHY_STATUS_CODE = 200
private const val API_VERSION = "1.44"

/** Mapped host ports of the HTTP and WebSocket services started for a CLI E2E test. */
class E2ePorts(server: Int, ws: Int) {
    /** HTTP server port on the test host. */
    val server: Int = server

    /** WebSocket server port on the test host. */
    val ws: Int = ws
}

/** Run a suspending CLI E2E test from JUnit's synchronous test boundary. */
fun runE2eBlockingTest(block: suspend CoroutineScope.() -> Unit) {
    runBlocking {
        withTimeout(10.minutes) {
            block()
        }
    }
}

/** Start the complete backend topology required by an installed CLI distribution. */
@OptIn(ExperimentalUuidApi::class)
suspend fun runE2eTestEnvironment(block: suspend (E2ePorts) -> Unit) {
    val sessionId = Uuid.random().toHexString()
    val hostSessionPath = File("build/test/e2e/sessions", sessionId).canonicalPath
    prepareSessionDirectories(hostSessionPath)
    val containerDataPath = "/e2e-session"
    System.setProperty("api.version", API_VERSION)
    Network.newNetwork().use { network ->
        useDatabaseContainer(network) { database ->
            val environment = buildContainerEnv(containerDataPath, database)
            useCliInitContainer(
                network = network,
                commonEnv = environment,
                hostSessionPath = hostSessionPath,
                containerDataPath = containerDataPath,
            ) {
                useWsContainer(
                    network = network,
                    commonEnv = environment,
                    hostSessionPath = hostSessionPath,
                    containerDataPath = containerDataPath,
                ) { ws ->
                    useServerContainer(
                        network = network,
                        commonEnv = environment,
                        hostSessionPath = hostSessionPath,
                        containerDataPath = containerDataPath,
                    ) { server ->
                        useWorkerContainer(
                            network = network,
                            commonEnv = environment,
                            hostSessionPath = hostSessionPath,
                            containerDataPath = containerDataPath,
                        ) {
                            block(
                                E2ePorts(
                                    server = server.getMappedPort(SERVER_PORT),
                                    ws = ws.getMappedPort(WEBSOCKET_PORT),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun useDatabaseContainer(network: Network, block: suspend (PostgreSQLContainer<*>) -> Unit) {
    PostgreSQLContainer("pgvector/pgvector:pg16").apply {
        withNetwork(network)
        withNetworkAliases("e2e-postgres")
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
    block: suspend () -> Unit,
) {
    val presetPath = resolveE2ePresetPath()
    GenericContainer(DockerImageName.parse("a-cli:latest")).apply {
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
                .forStatusCode(HEALTHY_STATUS_CODE)
                .withStartupTimeout(Duration.ofSeconds(STARTUP_TIMEOUT_SECONDS)),
        )
        withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger("e2e-cli")))
        withStartupAttempts(STARTUP_ATTEMPTS)
    }.use { container ->
        container.start()
        block()
    }
}

private suspend fun useWsContainer(
    network: Network,
    commonEnv: Map<String, String>,
    hostSessionPath: String,
    containerDataPath: String,
    block: suspend (GenericContainer<*>) -> Unit,
) {
    GenericContainer(DockerImageName.parse("a-ws:latest")).apply {
        withNetwork(network)
        withNetworkAliases("e2e-ws")
        withEnv(commonEnv)
        withFileSystemBind(hostSessionPath, containerDataPath, BindMode.READ_WRITE)
        withExposedPorts(WEBSOCKET_PORT)
        waitingFor(
            Wait.forListeningPort()
                .withStartupTimeout(Duration.ofSeconds(STARTUP_TIMEOUT_SECONDS)),
        )
        withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger("e2e-ws")))
        withStartupAttempts(STARTUP_ATTEMPTS)
    }.use { container ->
        container.start()
        block(container)
    }
}

private suspend fun useServerContainer(
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
        withExposedPorts(SERVER_PORT)
        waitingFor(
            Wait.forHttp("/metrics")
                .forPort(SERVER_PORT)
                .forStatusCode(HEALTHY_STATUS_CODE)
                .withStartupTimeout(Duration.ofSeconds(STARTUP_TIMEOUT_SECONDS)),
        )
        withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger("e2e-server")))
        withStartupAttempts(STARTUP_ATTEMPTS)
    }.use { container ->
        container.start()
        block(container)
    }
}

private suspend fun useWorkerContainer(
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
        withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger("e2e-worker")))
        withStartupAttempts(STARTUP_ATTEMPTS)
    }.use { container ->
        container.start()
        block(container)
    }
}

private fun resolveE2ePresetPath(): File {
    val presetPaths =
        generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .map { File(it, "dev/preset") }
    return presetPaths.firstOrNull { File(it, "0_preset_user.json").exists() }
        ?: error("Shared E2E preset data directory not found")
}

private fun prepareSessionDirectories(sessionPath: String) {
    val sessionDir = File(sessionPath)
    sessionDir.mkdirs()
    File(sessionDir, "logs").mkdirs()
    File(sessionDir, "lucene").mkdirs()
    File(sessionDir, "files").mkdirs()
}

private fun buildContainerEnv(
    containerDataPath: String,
    postgresContainer: PostgreSQLContainer<*>,
): Map<String, String> {
    val envFromFile = parseEnvFile(File("../../cloud/server/src/test/resources/test.env"))
    val databaseUri = "r2dbc:postgresql://e2e-postgres:5432/${postgresContainer.databaseName}"
    return envFromFile +
        mapOf(
            "BUILD_TYPE" to "test",
            "FLAVOR" to "dev",
            "SERVER_PORT" to SERVER_PORT.toString(),
            "WS_SERVER_PORT" to WEBSOCKET_PORT.toString(),
            "SERVER_URL" to "http://10.0.2.2:$SERVER_PORT",
            "WS_SERVER_URL" to "ws://10.0.2.2:$WEBSOCKET_PORT",
            "WS_RPC_URL" to "ws://e2e-ws:$WEBSOCKET_PORT/rpc",
            "SESSION_SECRET" to "e2e-session-secret",
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

    val lines =
        file.readLines()
            .asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
    return lines.mapNotNull { line ->
        val separatorIndex = line.indexOf('=')
        val key = if (separatorIndex < 0) line else line.take(separatorIndex)
        if (key.isBlank()) {
            null
        } else {
            val value = if (separatorIndex < 0) "" else line.substring(separatorIndex + 1)
            key to value
        }
    }.toMap()
}
