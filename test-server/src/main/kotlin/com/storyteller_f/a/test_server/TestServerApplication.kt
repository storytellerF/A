package com.storyteller_f.a.test_server

import forceStop
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import isWin
import kotlinx.coroutines.*
import kotlinx.io.IOException
import startServer
import stopServer
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket

val ext = if (isWin()) "bat" else "sh"

fun main() {
    Napier.base(DebugAntilog())
    val path = File("scripts/tool_scripts/forward-android-devices.$ext").canonicalPath
    val process = ProcessBuilder(path).start()
    check(process.waitFor() == 0)
    println(process.inputReader().readText())
    previousDevices.addAll(getConnectedDevices())
    forceStop(8888)
    EngineMain.main(emptyArray())
}

@Suppress("unused")
fun Application.module() {
    val processMap = mutableMapOf<Int, Process>()
    val job = startListening()
    monitor.subscribe(ApplicationStopped) { application ->
        application.log.info("Server is stopped")
        job.cancel()
        processMap.forEach {
            application.log.info("stop :${it.key}")
            stopServer(it.value, it.key)
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
            handleStartRoute(processMap, call)
        }
        post("/stop") {
            handleStopRoute(processMap, call)
        }
    }
}

private suspend fun handleStopRoute(
    processMap: MutableMap<Int, Process>,
    call: RoutingCall,
) {
    val application = call.application
    val port = call.receiveText().toIntOrNull()
    if (port != null) {
        val server = processMap.remove(port)
        if (server != null) {
            application.log.info("stop $port server success")
            stopServer(server, port)
            call.respond(HttpStatusCode.OK)
        } else {
            application.log.info("stop $port server not found process")
            call.respond(HttpStatusCode.NotFound)
        }
    } else {
        application.log.info("stop $port server not found")
        call.respond(HttpStatusCode.NotFound)
    }
}

private suspend fun handleStartRoute(
    processMap: MutableMap<Int, Process>,
    call: RoutingCall
) {
    val application = call.application
    val platformInfo = call.receiveText().split("\n")
    if (platformInfo.size < 2) {
        call.respond(HttpStatusCode.BadRequest)
        return
    }
    val (name, id) = platformInfo
    val port = findAvailablePort()
    val server = startServer(port, ".")
    if (server != null) {
        application.log.info("start $port server success")
        processMap[port] = server
        if (name.startsWith("Android", true)) {
            val path = File("scripts/tool_scripts/forward-special-device.$ext").absolutePath
            withContext(Dispatchers.IO) {
                check(ProcessBuilder(path, id, port.toString()).start().waitFor() == 0)
            }
        }
        call.respondText {
            port.toString()
        }
    } else {
        application.log.info("start $port server failed")
        call.respond(HttpStatusCode.InternalServerError)
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
fun findAvailablePort(startingPort: Int = 8080, maxRetries: Int = 100): Int {
    var port = startingPort
    var retries = 0
    while (retries < maxRetries) {
        if (isPortAvailable(port)) {
            return port // 找到空闲端口
        }
        port++ // 尝试下一个端口
        retries++
    }
    throw Exception("No available port found after $maxRetries retries")
}

private val previousDevices = mutableSetOf<String>()

// 启动协程监听 ADB 设备连接
@OptIn(DelicateCoroutinesApi::class)
fun startListening(): Job {
    return GlobalScope.launch {
        while (isActive) { // 持续监听直到协程被取消
            val currentDevices = getConnectedDevices()
            if (currentDevices != previousDevices) {
                currentDevices.forEach { device ->
                    if (device !in previousDevices) {
                        val code =
                            ProcessBuilder("adb", "-s", device, "reverse", "tcp:8888", "tcp:8888").start().waitFor()
                        println("New device connected: $device exitCode: $code")
                    }
                }
                previousDevices.forEach { device ->
                    if (device !in currentDevices) {
                        println("Device disconnected: $device")
                    }
                }
                previousDevices.clear()
                previousDevices.addAll(currentDevices)
            }

            // 每 5 秒检查一次
            delay(5000)
        }
    }
}

// 获取当前连接的设备列表
private fun getConnectedDevices(): Set<String> {
    val devices = mutableSetOf<String>()
    try {
        val process = ProcessBuilder("adb", "devices").start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String? = reader.readLine()
        while (line != null) {
            if (line.contains("\tdevice")) {
                val device = line.split("\t")[0].trim()
                devices.add(device)
            }
            line = reader.readLine()
        }
        process.waitFor()
    } catch (e: Exception) {
        Napier.e(e) {
            "getConnectedDevices failed"
        }
    }
    return devices
}
