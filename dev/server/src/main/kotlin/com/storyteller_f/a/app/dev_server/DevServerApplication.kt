package com.storyteller_f.a.app.dev_server

import com.storyteller_f.a.app.dev.DevControlService
import com.storyteller_f.a.app.dev.ProcessMate
import com.storyteller_f.a.app.dev.ServiceStatus
import com.storyteller_f.a.app.dev.forceStop
import com.storyteller_f.a.app.dev.startCloudServerByGradle
import com.storyteller_f.a.app.dev.startCloudWorkerByGradle
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.netty.EngineMain
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlin.system.exitProcess

class DevControlServiceImpl(
    private val specialProcessMap: MutableMap<String, ProcessMate?>,
    private val processLock: Mutex,
    private val application: Application,
    private val scope: CoroutineScope,
) : DevControlService {
    override suspend fun startCloudServer(): String {
        return processLock.withLock {
            if (specialProcessMap["cloud-server"] != null) {
                return@withLock "Cloud server already running"
            }
            val process = scope.startCloudServerByGradle("../..")
            if (process != null) {
                specialProcessMap["cloud-server"] = process
                "Cloud server started"
            } else {
                "Failed to start cloud server"
            }
        }
    }

    override suspend fun stopCloudServer(): String {
        return processLock.withLock {
            specialProcessMap.remove("cloud-server")?.stop()
            "Cloud server stopped"
        }
    }

    override suspend fun startCloudWorker(): String {
        return processLock.withLock {
            if (specialProcessMap["cloud-worker"] != null) {
                return@withLock "Cloud worker already running"
            }
            val process = scope.startCloudWorkerByGradle("../..")
            if (process != null) {
                specialProcessMap["cloud-worker"] = process
                "Cloud worker started"
            } else {
                "Failed to start cloud worker"
            }
        }
    }

    override suspend fun stopCloudWorker(): String {
        return processLock.withLock {
            specialProcessMap.remove("cloud-worker")?.stop()
            "Cloud worker stopped"
        }
    }

    override suspend fun shutdown(): String {
        application.launch {
            delay(500)
            exitProcess(0)
        }
        return "Shutting down dev server"
    }

    override suspend fun getStatus(): ServiceStatus {
        return processLock.withLock {
            val serverRunning = specialProcessMap["cloud-server"] != null
            val workerRunning = specialProcessMap["cloud-worker"] != null
            ServiceStatus(serverRunning, workerRunning)
        }
    }
}

fun main() {
    forceStop(8888)
    EngineMain.main(emptyArray())
}

@OptIn(DelicateCoroutinesApi::class)
fun Application.module() {
    val processMap = mutableMapOf<Int, ProcessMate?>()
    val specialProcessMap = mutableMapOf<String, ProcessMate?>()
    val processLock = Mutex()

    install(Krpc)

    monitor.subscribe(ApplicationStopped) { application ->
        application.log.info("Dev Server is stopped")
        processMap.forEach {
            application.log.info("stop :${it.key}")
            it.value?.stop()
        }
        specialProcessMap.forEach {
            application.log.info("stop special :${it.key}")
            it.value?.stop()
        }
        // Release resources and unsubscribe from events
        monitor.unsubscribe(ApplicationStarted) {}
        monitor.unsubscribe(ApplicationStopped) {}
    }
    routing {
        rpc("/rpc") {
            rpcConfig {
                serialization {
                    json()
                }
            }

            registerService<DevControlService> {
                DevControlServiceImpl(specialProcessMap, processLock, this@module, GlobalScope)
            }
        }
    }
}
