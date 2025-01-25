package com.storyteller_f.a.test_server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.io.IOException
import startServer
import stopServer
import java.net.ServerSocket

fun main() {
    EngineMain.main(emptyArray())
}

@Suppress("unused")
fun Application.module() {
    val processMap = mutableMapOf<Int, Process>()
    monitor.subscribe(ApplicationStopped) { application ->
        application.environment.log.info("Server is stopped")
        processMap.forEach {
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
            val port = findAvailablePort()
            val server = startServer(port, ".")
            if (server != null) {
                log.info("start $port server success")
                processMap[port] = server
                call.respondText {
                    port.toString()
                }
            } else {
                log.info("start $port server failed")
                call.respond(HttpStatusCode.InternalServerError)
            }
        }
        post("/stop") {
            val port = call.receiveText().toIntOrNull()
            if (port != null) {
                val server = processMap.remove(port)
                if (server != null) {
                    log.info("stop $port server success")
                    stopServer(server, port)
                    call.respond(HttpStatusCode.OK)
                } else {
                    log.info("stop $port server not found process")
                    call.respond(HttpStatusCode.NotFound)
                }
            } else {
                log.info("stop $port server not found")
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}

// 尝试绑定到指定端口号，检查端口是否可用
fun isPortAvailable(port: Int): Boolean {
    return try {
        ServerSocket(port).use { it.localPort }  // 如果能成功绑定，说明端口可用
        true
    } catch (e: IOException) {
        false  // 捕获异常说明端口已经被占用
    }
}

// 获取一个未被使用的端口号
fun findAvailablePort(startingPort: Int = 8080, maxRetries: Int = 100): Int {
    var port = startingPort
    var retries = 0
    while (retries < maxRetries) {
        if (isPortAvailable(port)) {
            return port  // 找到空闲端口
        }
        port++  // 尝试下一个端口
        retries++
    }
    throw IllegalStateException("No available port found after $maxRetries retries")
}