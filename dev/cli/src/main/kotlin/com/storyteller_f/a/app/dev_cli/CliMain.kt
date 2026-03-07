package com.storyteller_f.a.app.dev_cli

import com.storyteller_f.a.app.dev.DevControlService
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
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val parser = ArgParser("dev-cli")
    val start by parser.option(ArgType.String, "start", "s", "Enable target (m/main, s/server, w/worker)")
    val stop by parser.option(ArgType.String, "stop", "t", "Stop target (m/main, s/server, w/worker)")

    parser.parse(args)

    val target = (start ?: stop)?.lowercase() ?: run {
        println("Please specify a target with -s or -t")
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
