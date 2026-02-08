package com.storyteller_f.a.app.dev_server

import com.storyteller_f.a.app.dev.ProcessMate
import com.storyteller_f.a.app.dev.forceStop
import com.storyteller_f.a.app.dev.isWin
import com.storyteller_f.a.app.dev.startServerByRun
import io.github.aakira.napier.Napier
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.netty.EngineMain
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket
import kotlin.io.encoding.Base64
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val previousDevices = mutableSetOf<String>()
fun main() {
    forceStop(8888)
    EngineMain.main(emptyArray())
}

@Suppress("unused")
fun Application.module() {
    val processMap = mutableMapOf<Int, ProcessMate?>()
    val processLock = Mutex()
    val job = startListening(8888, previousDevices)
    monitor.subscribe(ApplicationStopped) { application ->
        application.log.info("Dev Server is stopped")
        job.cancel()
        processMap.forEach {
            application.log.info("stop :${it.key}")
            it.value?.stop()
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
        get("/ecdsa") {
            val file = File("../..", "AData/data/ecdsa")
            println(file.canonicalPath)
            val map = file.listFiles()?.joinToString("\n") {
                "${it.name}\t${Base64.encode(it.readText().replace("\r\n", "\n").encodeToByteArray())}"
            } ?: ""
            call.respondText(map)
        }
    }
}

private suspend fun RoutingCall.handleStopRoute(
    processMap: MutableMap<Int, ProcessMate?>,
    processLock: Mutex,
) {
    val application = application
    val port = receiveText().toIntOrNull()
    if (port == null) {
        application.log.info("stop port server not found")
        respond(HttpStatusCode.NotFound)
        return
    }
    val server = processLock.withLock {
        processMap.remove(port)
    }
    if (server == null) {
        application.log.info("stop $port server not found process")
        respond(HttpStatusCode.NotFound)
        return
    }
    server.stop()
    application.log.info("stop $port server success")
    respond(HttpStatusCode.OK)
}

@OptIn(ExperimentalUuidApi::class)
private suspend fun RoutingCall.handleStartRoute(
    processMap: MutableMap<Int, ProcessMate?>,
    processLock: Mutex,
) {
    val application = application
    val name = receiveText()
    if (name.isEmpty()) {
        respond(HttpStatusCode.BadRequest)
        return
    }
    val port = processLock.withLock {
        val port = findAvailablePort {
            !processMap.containsKey(it)
        }
        processMap[port] = null
        port
    }
    val server = startServerByRun(
        "../..",
        port,
        "./build/test/headless/sessions/${Uuid.random().toHexString()}"
    )
    if (server == null) {
        processLock.withLock {
            processMap.remove(port)
        }
        application.log.info("start $port server failed")
        respond(HttpStatusCode.InternalServerError)
        return
    }
    if (name.startsWith("Android", true) &&
        !forwardAllDevices(port.toString())
    ) {
        processLock.withLock {
            processMap.remove(port)
        }
        application.log.info("forward devices failed, release port $port")
        respond(HttpStatusCode.BadRequest, "forward devices failed")
        return
    }
    application.log.info("start $port server success")
    processLock.withLock {
        processMap[port] = server
    }
    respondText {
        port.toString()
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

private const val GIT_BASH = "C:/Program Files/Git/bin/bash.exe"

private fun forwardAllDevices(port: String): Boolean {
    val forwardScriptPath = File(
        "../..",
        "scripts/android_scripts/forward-android-devices.sh"
    ).canonicalPath.replace("\\", "/")
    val process = if (isWin()) {
        ProcessBuilder(GIT_BASH, "-c", "$forwardScriptPath $port")
    } else {
        ProcessBuilder("sh", forwardScriptPath, port)
    }.redirectErrorStream(true)
        .start()
    val message = process.inputReader().readText()
    println(message)
    if (process.waitFor() != 0) return false
    previousDevices.addAll(getConnectedDevices())
    return true
}

// 启动协程监听 ADB 设备连接
@OptIn(DelicateCoroutinesApi::class)
fun startListening(port: Int, previousDevices: MutableSet<String>): Job {
    return GlobalScope.launch {
        while (isActive) { // 持续监听直到协程被取消
            val currentDevices = getConnectedDevices()
            if (currentDevices != previousDevices) {
                currentDevices.forEach { device ->
                    if (device !in previousDevices) {
                        val code = forwardAllDevices(port.toString())
                        println("New device connected: $device result: $code")
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
fun getConnectedDevices(): Set<String> {
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
