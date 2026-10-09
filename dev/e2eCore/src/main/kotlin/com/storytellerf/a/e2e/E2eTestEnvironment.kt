/*
 * This is a private project. All rights reserved.
 */

package com.storytellerf.a.e2e

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import kotlin.time.Duration.Companion.minutes

private const val CLI_READY_PORT = 8081
private const val SERVER_PORT = 8811
private const val WEBSOCKET_PORT = 8813
private const val FILESYSTEM_PORT = 8820
private const val LUCENE_PORT = 8821
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
suspend fun runE2eTestEnvironment(block: suspend (E2ePorts) -> Unit) {
    System.setProperty("api.version", API_VERSION)
    Network.newNetwork().use { network ->
        useDatabaseContainer(network) { database ->
            useFilesystemContainer(network) { filesystem ->
                useLuceneContainer(network) {
                    val environment =
                        buildContainerEnv(database) +
                            ("FILESYSTEM_PUBLIC_URL" to "http://${filesystem.host}:${filesystem.getMappedPort(8822)}")
                    useCliInitContainer(
                        network = network,
                        commonEnv = environment,
                    ) {
                        useWsContainer(
                            network = network,
                            commonEnv = environment,
                        ) { ws ->
                            useServerContainer(
                                network = network,
                                commonEnv = environment,
                            ) { server ->
                                useWorkerContainer(
                                    network = network,
                                    commonEnv = environment,
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
    }
}

private suspend fun useFilesystemContainer(network: Network, block: suspend (GenericContainer<*>) -> Unit) {
    GenericContainer(DockerImageName.parse("a-filesystem:latest")).apply {
        withNetwork(network)
        withNetworkAliases("e2e-filesystem")
        withEnv("FILESYSTEM_PUBLIC_URL", "http://e2e-filesystem:8822")
        withTmpFs(mapOf("/data" to "rw,uid=1000,gid=1000"))
        withExposedPorts(FILESYSTEM_PORT, 8822)
        waitingFor(
            Wait.forHttp("/health")
                .forPort(FILESYSTEM_PORT)
                .withStartupTimeout(Duration.ofSeconds(STARTUP_TIMEOUT_SECONDS)),
        )
    }.use { container ->
        container.start()
        block(container)
    }
}

private suspend fun useLuceneContainer(network: Network, block: suspend () -> Unit) {
    GenericContainer(DockerImageName.parse("a-lucene:latest")).apply {
        withNetwork(network)
        withNetworkAliases("e2e-lucene")
        withTmpFs(mapOf("/data" to "rw,uid=1000,gid=1000"))
        withExposedPorts(LUCENE_PORT)
        waitingFor(
            Wait.forHttp("/health")
                .forPort(LUCENE_PORT)
                .withStartupTimeout(Duration.ofSeconds(STARTUP_TIMEOUT_SECONDS)),
        )
    }.use { container ->
        container.start()
        block()
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

private suspend fun useCliInitContainer(network: Network, commonEnv: Map<String, String>, block: suspend () -> Unit) {
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
        withCopyFileToContainer(MountableFile.forHostPath(presetPath.toPath()), "/app/deploy/preset_data")
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
    block: suspend (GenericContainer<*>) -> Unit,
) {
    GenericContainer(DockerImageName.parse("a-ws:latest")).apply {
        withNetwork(network)
        withNetworkAliases("e2e-ws")
        withEnv(commonEnv)
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
    block: suspend (GenericContainer<*>) -> Unit,
) {
    GenericContainer(DockerImageName.parse("a-server:latest")).apply {
        withNetwork(network)
        withEnv(commonEnv)
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
    block: suspend (GenericContainer<*>) -> Unit,
) {
    GenericContainer(DockerImageName.parse("a-worker:latest")).apply {
        withNetwork(network)
        withEnv(commonEnv)
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

private fun buildContainerEnv(postgresContainer: PostgreSQLContainer<*>): Map<String, String> {
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
            "MEDIA_SERVICE" to "filesystem",
            "FILESYSTEM_RPC_URL" to "ws://e2e-filesystem:$FILESYSTEM_PORT/rpc",
            "SEARCH_SERVICE" to "lucene",
            "LUCENE_RPC_URL" to "ws://e2e-lucene:$LUCENE_PORT/rpc",
            "LOG_PATH" to "/tmp/e2e/logs",
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
