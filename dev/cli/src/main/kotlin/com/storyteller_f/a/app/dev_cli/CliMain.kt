package com.storyteller_f.a.app.dev_cli

import com.storyteller_f.a.app.dev.DevControlService
import com.storyteller_f.a.app.dev.ServiceStatus
import com.storyteller_f.a.app.dev.startDevServerByGradle
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.http.*
import kotlinx.cli.*
import kotlinx.coroutines.runBlocking
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.ktor.client.rpcConfig
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.withService
import java.io.File
import java.net.ConnectException
import java.net.Socket
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val parser = ArgParser("dev-cli")
    val start by parser.option(ArgType.String, "start", "s", "Enable target (m/main, s/server, w/worker)")
    val stop by parser.option(ArgType.String, "stop", "t", "Stop target (m/main, s/server, w/worker)")
    val status by parser.option(ArgType.Boolean, "status", "st", "Check status of dev server and services").default(false)
    val log by parser.option(ArgType.String, "log", "l", "Tail log file for target (s/server, w/worker)")

    parser.parse(args)

    if (status) {
        checkStatus()
        return
    }

    if (log != null) {
        tailLog(log!!.lowercase())
        return
    }

    val target = (start ?: stop)?.lowercase() ?: run {
        println("Please specify a target with -s, -t, or -l, or use --status")
        exitProcess(1)
    }
    val isStart = start != null

    if (isStart && (target == "main" || target == "m")) {
        startDevServer()
        return
    }

    val client = HttpClient(OkHttp) {
        installKrpc()
    }

    runBlocking {
        try {
            val rpcClient = client.rpc {
                url {
                    takeFrom("ws://localhost:8888/rpc")
                }
                rpcConfig {
                    serialization {
                        json()
                    }
                }
            }

            val service = rpcClient.withService<DevControlService>()

            val result = when (target) {
                "main", "m" -> service.shutdown()
                "server", "s" -> if (isStart) service.startCloudServer() else service.stopCloudServer()
                "worker", "w" -> if (isStart) service.startCloudWorker() else service.stopCloudWorker()
                else -> {
                    println("Unknown target: $target")
                    exitProcess(1)
                }
            }
            println(result)
        } catch (e: Exception) {
            println("Failed to execute command: ${e.message}")
            e.printStackTrace()
            exitProcess(1)
        } finally {
            client.close()
            exitProcess(0)
        }
    }
}

fun checkStatus() {
    val mainRunning = isPortInUse(8888)
    if (!mainRunning) {
        println("Main server is NOT running (port 8888 not in use)")
        exitProcess(0)
    }

    println("Main server is running (port 8888 in use)")

    val client = HttpClient(OkHttp) {
        installKrpc()
    }

    runBlocking {
        try {
            val rpcClient = client.rpc {
                url {
                    takeFrom("ws://localhost:8888/rpc")
                }
                rpcConfig {
                    serialization {
                        json()
                    }
                }
            }

            val service = rpcClient.withService<DevControlService>()
            val status = service.getStatus()
            printStatus(status)
        } catch (e: ConnectException) {
            println("Failed to connect to main server: ${e.message}")
        } catch (e: Exception) {
            println("Failed to get status: ${e.message}")
        } finally {
            client.close()
        }
    }
}

fun printStatus(status: ServiceStatus) {
    println("Cloud server: ${if (status.serverRunning) "RUNNING" else "NOT running"}")
    println("Cloud worker: ${if (status.workerRunning) "RUNNING" else "NOT running"}")
}

fun isPortInUse(port: Int): Boolean {
    return try {
        Socket("localhost", port).use { true }
    } catch (e: ConnectException) {
        false
    } catch (e: Exception) {
        false
    }
}

fun startDevServer() {
    println("Starting dev server...")
    val projectRoot = findProjectRoot() ?: run {
        println("Could not find project root (gradlew file)")
        exitProcess(1)
    }
    startDevServerByGradle(projectRoot)
    exitProcess(0)
}

fun findProjectRoot(): String? {
    var current = File(".").absoluteFile
    while (current != null) {
        if (File(current, "gradlew").exists() || File(current, "gradlew.bat").exists()) {
            return current.absolutePath
        }
        current = current.parentFile
    }
    return null
}

fun getLogPath(): String {
    val customLogPath = System.getenv("LOG_PATH")
    if (!customLogPath.isNullOrBlank()) {
        return File(customLogPath).canonicalPath
    }
    val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
    val envOs = System.getenv("OSTYPE")?.lowercase(Locale.getDefault()) ?: ""

    val isWindowsLike = osName.contains("win") || envOs.contains("cygwin") || envOs.contains("mingw")

    val logPath = if (isWindowsLike) {
        File(System.getProperty("java.io.tmpdir"), "log")
    } else {
        File(System.getProperty("user.home"), "log")
    }
    return logPath.canonicalPath
}

fun tailLog(target: String) {
    val logPath = getLogPath()
    val logFileName = when (target) {
        "server", "s" -> "A.log"
        "worker", "w" -> "A_worker.log"
        else -> {
            println("Unknown target for log: $target. Use s/server or w/worker.")
            exitProcess(1)
        }
    }
    val logFile = File(logPath, logFileName)

    if (!logFile.exists()) {
        println("Log file does not exist: ${logFile.canonicalPath}")
        exitProcess(1)
    }

    println("Tailing log file: ${logFile.canonicalPath}")

    val processBuilder = ProcessBuilder("tail", "-f", logFile.canonicalPath)
        .redirectErrorStream(true)
    val process = processBuilder.start()

    val reader = process.inputStream.bufferedReader()
    thread(isDaemon = true) {
        while (process.isAlive) {
            val line = reader.readLine()
            if (line != null) {
                println(line)
            }
        }
    }

    process.waitFor()
}
