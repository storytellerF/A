package com.storyteller_f.a.test_server

import com.storyteller_f.shared.kmpLogger
import forceStop
import io.github.aakira.napier.Napier
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import isWin
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import startServer
import stopServer
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.*

val ext = if (isWin()) "bat" else "sh"

fun main(args: Array<String>) {
    Napier.base(kmpLogger)
    println("current path: ${File("").canonicalPath}")
    val isNested = File("").canonicalPath.endsWith("test-server")
    if (args.isNotEmpty() && args.any {
            it == "auto"
        }) {
        println("auto mode")
        val path = File(if (isNested) ".." else ".", "scripts/tool_scripts/forward-android-devices.$ext").canonicalPath
        val process = ProcessBuilder(path, "9000").start()
        check(process.waitFor() == 0)
        println(process.inputReader().readText())
        previousDevices.addAll(getConnectedDevices())
        val job = startListening(9000)
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                super.run()
                job.cancel()
                println("force shutdown")
            }
        })
        val scanner = Scanner(System.`in`)
        while (true) {
            if (!scanner.hasNextLine()) {
                println("manual shutdown")
                break
            }
        }
    } else {
        val path = File(if (isNested) ".." else ".", "scripts/tool_scripts/forward-android-devices.$ext").canonicalPath
        val process = ProcessBuilder(path, "8888").start()
        check(process.waitFor() == 0)
        println(process.inputReader().readText())
        previousDevices.addAll(getConnectedDevices())
        forceStop(8888)
        EngineMain.main(emptyArray())
    }
}

@Suppress("unused")
fun Application.module() {
    val processMap = mutableMapOf<Int, Process?>()
    val processLock = Mutex()
    val job = startListening(8888)
    monitor.subscribe(ApplicationStopped) { application ->
        application.log.info("Server is stopped")
        job.cancel()
        processMap.forEach {
            application.log.info("stop :${it.key}")
            it.value?.let { it1 -> stopServer(it1, it.key) }
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
    processLock: Mutex
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

    val server = startServer(if (isNested) ".." else ".", port)
    if (server != null) {
        application.log.info("start $port server success")
        processLock.withLock {
            processMap[port] = server
        }
        if (name.startsWith("Android", true)) {
            val path =
                File(if (isNested) ".." else ".", "scripts/tool_scripts/forward-special-device.$ext").canonicalPath
            withContext(Dispatchers.IO) {
                val start = ProcessBuilder(path, id, port.toString()).start()
                val serverResult = start.waitFor()
                if (serverResult != 0) {
                    println(start.inputReader().readText())
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
fun findAvailablePort(startingPort: Int = 8080, maxRetries: Int = 100, usable: (Int) -> Boolean): Int {
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

private val previousDevices = mutableSetOf<String>()

// 启动协程监听 ADB 设备连接
@OptIn(DelicateCoroutinesApi::class)
fun startListening(port: Int): Job {
    return GlobalScope.launch {
        while (isActive) { // 持续监听直到协程被取消
            val currentDevices = getConnectedDevices()
            if (currentDevices != previousDevices) {
                currentDevices.forEach { device ->
                    if (device !in previousDevices) {
                        val code =
                            ProcessBuilder("adb", "-s", device, "reverse", "tcp:$port", "tcp:$port").start().waitFor()
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
