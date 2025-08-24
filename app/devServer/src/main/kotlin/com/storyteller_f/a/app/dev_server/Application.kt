package com.storyteller_f.a.app.dev_server

import com.storyteller_f.a.app.dev.forceStop
import com.storyteller_f.a.app.dev.getConnectedDevices
import com.storyteller_f.a.app.dev.isWin
import com.storyteller_f.a.app.dev.startListening
import com.storyteller_f.a.app.dev.startServerByRun
import com.storyteller_f.a.app.dev.stopServer
import com.storyteller_f.shared.kmpLogger
import io.github.aakira.napier.Napier
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import java.io.File
import java.net.ServerSocket

val ext = if (isWin()) "bat" else "sh"
private val previousDevices = mutableSetOf<String>()
const val GIT_BASH = "C:/Program Files/Git/bin/bash.exe"
fun main() {
    Napier.base(kmpLogger)
    val runPath = File("").canonicalPath
    println("current path: $runPath")
    val isNested = runPath.endsWith("devCli")
    val forwardScriptPath = File(
        if (isNested) "../.." else ".",
        "scripts/android_scripts/forward-android-devices.sh"
    ).canonicalPath.replace("\\", "/")
    val process = ProcessBuilder(GIT_BASH, "-c", "$forwardScriptPath 8888").start()
    check(process.waitFor() == 0)
    println(process.inputReader().readText())
    previousDevices.addAll(getConnectedDevices())
    forceStop(8888)
    EngineMain.main(emptyArray())
}

@Suppress("unused")
fun Application.module() {
    val processMap = mutableMapOf<Int, Process?>()
    val processLock = Mutex()
    val job = startListening(8888, previousDevices)
    monitor.subscribe(ApplicationStopped) { application ->
        application.log.info("Server is stopped")
        job.cancel()
        processMap.forEach {
            application.log.info("stop :${it.key}")
            it.value?.let { process -> stopServer(process, it.key) }
        }
        // Release resources and unsubscribe from events
        monitor.unsubscribe(ApplicationStarted) {}
        monitor.unsubscribe(ApplicationStopped) {}
    }
    routing {
        get("/ping") {
            call.respondText {
                "pong"
            }
        }
        post("/start") {
            call.handleStartRoute(processMap, processLock)
        }
        post("/stop") {
            call.handleStopRoute(processMap, processLock)
        }
    }
}

private suspend fun RoutingCall.handleStopRoute(
    processMap: MutableMap<Int, Process?>,
    processLock: Mutex,
) {
    val application = application
    val port = receiveText().toIntOrNull()
    if (port != null) {
        val server = processLock.withLock {
            processMap.remove(port)
        }
        if (server != null) {
            application.log.info("stop $port server success")
            stopServer(server, port)
            respond(HttpStatusCode.OK)
        } else {
            application.log.info("stop $port server not found process")
            respond(HttpStatusCode.NotFound)
        }
    } else {
        application.log.info("stop port server not found")
        respond(HttpStatusCode.NotFound)
    }
}

private suspend fun RoutingCall.handleStartRoute(
    processMap: MutableMap<Int, Process?>,
    processLock: Mutex,
) {
    val application = application
    val platformInfo = receiveText().split("\n")
    if (platformInfo.size < 2) {
        respond(HttpStatusCode.BadRequest)
        return
    }
    val isNested = File("").canonicalPath.endsWith("test-server")
    val (name, id) = platformInfo
    val port = processLock.withLock {
        val port = findAvailablePort {
            !processMap.containsKey(it)
        }
        processMap[port] = null
        port
    }

    val server = startServerByRun(if (isNested) ".." else ".", port)
    if (server != null) {
        application.log.info("start $port server success")
        processLock.withLock {
            processMap[port] = server
        }
        if (name.startsWith("Android", true)) {
            val path =
                File(
                    if (isNested) ".." else ".",
                    "scripts/android_scripts/forward-special-device.sh"
                ).canonicalPath.replace("\\", "/")
            withContext(Dispatchers.IO) {
                val start = ProcessBuilder(GIT_BASH, "-c", "$path $id $port").start()
                val serverResult = start.waitFor()
                if (serverResult != 0) {
                    println("forward $id device failed: ${start.inputReader().readText()}")
                    System.err.println(start.errorReader().readText())
                } else {
                    println("forward $id device success")
                }
            }
        }
        respondText {
            port.toString()
        }
    } else {
        processLock.withLock {
            processMap.remove(port)
        }
        application.log.info("start $port server failed")
        respond(HttpStatusCode.InternalServerError)
    }
}

// 尝试绑定到指定端口号，检查端口是否可用
fun isPortAvailable(port: Int): Boolean {
    return try {
        ServerSocket(port).use { it.localPort } // 如果能成功绑定，说明端口可用
        true
    } catch (_: IOException) {
        false // 捕获异常说明端口已经被占用
    }
}

// 获取一个未被使用的端口号
fun findAvailablePort(
    startingPort: Int = 8080,
    maxRetries: Int = 100,
    usable: (Int) -> Boolean
): Int {
    var port = startingPort
    var retries = 0
    while (retries < maxRetries) {
        if (isPortAvailable(port) && usable(port)) {
            return port // 找到空闲端口
        }
        port++ // 尝试下一个端口
        retries++
    }
    throw Exception("No available port found after $maxRetries retries")
}
