/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import org.testcontainers.containers.Network
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Starts the backend topology shared by desktop and browser Appium tests. */
@OptIn(ExperimentalUuidApi::class)
suspend fun runAppiumTestEnvironment(allowedOrigins: String = "", block: suspend (AppiumPorts) -> Unit) {
    val sessionId = Uuid.random().toHexString()
    val hostSessionPath = File("build/test/appium/sessions", sessionId).canonicalPath
    prepareSessionDirectories(hostSessionPath)
    System.setProperty("api.version", "1.44")
    Network.newNetwork().use { network ->
        useLightweightBackendContainers(network) { filesystem, redis ->
            val environment =
                buildContainerEnv() + ("SIGNIN_ALLOWED_ORIGINS" to allowedOrigins) +
                    ("FILESYSTEM_PUBLIC_URL" to "http://${filesystem.host}:${filesystem.getMappedPort(8822)}")
            useCliInitContainer(network, environment, hostSessionPath) {
                useWsContainer(network, environment, hostSessionPath) { ws ->
                    val wsPort = ws.getMappedPort(8813)
                    useServerContainer(network, environment, hostSessionPath) { server ->
                        val serverPort = server.getMappedPort(8811)
                        useWorkerContainer(network, environment, hostSessionPath) {
                            block(
                                AppiumPorts(server = serverPort, ws = wsPort, expireUserSessions = {
                                    val result = redis.execInContainer("redis-cli", "EVAL", EXPIRE_USER_SESSIONS, "0")
                                    check(result.exitCode == 0) { "Test session expiry failed" }
                                }),
                            )
                        }
                    }
                }
            }
        }
    }
}

// Move the isolated fixture's stored deadline into the past; retain the Cookie and Redis key.
private const val EXPIRE_USER_SESSIONS = """
    for _, key in ipairs(redis.call('KEYS', 'signin:user:*')) do
        if redis.call('TYPE', key).ok == 'string' then
            local record = cjson.decode(redis.call('GET', key))
            record.absolute = 0
            redis.call('SET', key, cjson.encode(record), 'KEEPTTL')
        end
    end
    return 1
"""
