/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import org.testcontainers.containers.Network

/** Starts the backend topology shared by desktop and browser Appium tests. */
internal suspend fun runAppiumTestEnvironment(
    report: AppiumTestReport,
    allowedOrigins: String = "",
    block: suspend (AppiumPorts) -> Unit,
) {
    System.setProperty("api.version", "1.44")
    Network.newNetwork().use { network ->
        useLightweightBackendContainers(network, report) { filesystem, redis ->
            val environment =
                buildContainerEnv() + ("SIGNIN_ALLOWED_ORIGINS" to allowedOrigins) +
                    ("FILESYSTEM_PUBLIC_URL" to "http://${filesystem.host}:${filesystem.getMappedPort(8822)}")
            useCliInitContainer(network, environment, report) {
                useWsContainer(network, environment, report) { ws ->
                    val wsPort = ws.getMappedPort(8813)
                    useServerContainer(network, environment, report) { server ->
                        val serverPort = server.getMappedPort(8811)
                        useWorkerContainer(network, environment, report) {
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
