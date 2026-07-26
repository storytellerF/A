import org.testcontainers.containers.Network
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Starts the backend topology shared by desktop and browser Appium tests. */
@OptIn(ExperimentalUuidApi::class)
suspend fun runAppiumTestEnvironment(block: suspend (AppiumPorts) -> Unit) {
    val sessionId = Uuid.random().toHexString()
    val hostSessionPath = File("build/test/appium/sessions", sessionId).canonicalPath
    prepareSessionDirectories(hostSessionPath)
    val containerDataPath = "/appium-session"
    System.setProperty("api.version", "1.44")
    Network.newNetwork().use { network ->
        useDatabaseContainer(network) { database ->
            val environment = buildContainerEnv(containerDataPath, database)
            useCliInitContainer(network, environment, hostSessionPath, containerDataPath) {
                useWsContainer(network, environment, hostSessionPath, containerDataPath) { ws ->
                    val wsPort = ws.getMappedPort(8813)
                    useServerContainer(network, environment, hostSessionPath, containerDataPath) { server ->
                        val serverPort = server.getMappedPort(8811)
                        useWorkerContainer(network, environment, hostSessionPath, containerDataPath) {
                            block(AppiumPorts(server = serverPort, ws = wsPort))
                        }
                    }
                }
            }
        }
    }
}
