package com.storyteller_f.a.app.dev

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.concurrent.thread
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

const val GIT_BASH = "C:/Program Files/Git/bin/bash.exe"

fun isWin(): Boolean {
    val property = System.getProperty("os.name").orEmpty()
    return property.lowercase().contains("win")
}

fun forceStop(port: Int) {
    println("forceStop $port ${File(".").canonicalPath}")
    val process = if (isWin()) {
        ProcessBuilder().command(File("../../scripts/tool_scripts/kill-port.bat").canonicalPath, port.toString())
    } else {
        ProcessBuilder().command(
            "/bin/sh",
            "-c",
            $$""""pid=$(lsof -t -i :$$port) && kill -9 $pid""""
        )
    }.redirectErrorStream(true).start()
    val thread = thread {
        process.inputStream.bufferedReader().use {
            while (process.isAlive) {
                val output = it.readLine() ?: break
                println(output)
            }
        }
    }
    val result = process.waitFor()
    thread.interrupt()
    check(result == 0) {
        "forceStop failed"
    }
}

fun stopServer(serverProcess: Process) {
    serverProcess.destroy()
}

@OptIn(ExperimentalUuidApi::class)
suspend fun CoroutineScope.startServerByRun(projectRoot: String, port: Int): ProcessMate? {
    val testEnvFile = File(projectRoot, "cloud/server/src/test/resources/.env")
    if (!testEnvFile.exists()) {
        println("${testEnvFile.canonicalPath} not exists")
        return null
    }
    val serverProcess = ProcessBuilder(GIT_BASH, "-c", "cloud/server/build/install/server/bin/server")
        .redirectErrorStream(true)
        .directory(File(projectRoot))
        .bindGradleProcessEnv(testEnvFile, port)
        .start()
    return waitRunServerProcess(serverProcess)
}

class ProcessMate(val process: Process, val job: Job) {
    fun stop() {
        Napier.i {
            "stop process ${process.pid()}"
        }
        stopServer(process)
        job.cancel()
    }
}

private suspend fun CoroutineScope.waitRunServerProcess(serverProcess: Process): ProcessMate {
    val task = CompletableDeferred<String>()
    val job = launch {
        suspendCancellableCoroutine { continuation ->
            // 进程停止还是会卡在readLine，需要使用thread 保证job 正常退出
            thread {
                serverProcess.inputStream.bufferedReader().use {
                    while (serverProcess.isAlive && isActive) {
                        val line = it.readLine() ?: break
                        println(line)
                        if (line.contains("Responding at")) {
                            task.complete(line)
                        } else if (line.contains("Execution failed for task") ||
                            line.contains("Exception in thread")
                        ) {
                            task.completeExceptionally(RuntimeException(line))
                        }
                    }
                }
                continuation.resumeWith(Result.success(Unit))
            }
        }
    }
    task.await()
    Napier.i {
        "server started"
    }
    return ProcessMate(serverProcess, job)
}

@OptIn(ExperimentalUuidApi::class)
private fun ProcessBuilder.bindGradleProcessEnv(envFile: File, port: Int): ProcessBuilder {
    val envList = envFile.readLines().filter {
        it.isNotBlank()
    }.map {
        val list = it.split("=", limit = 2)
        list[0] to list.getOrElse(1) {
            ""
        }
    }
    val url = "r2dbc:h2:file:///./build/test/process/h2/${Uuid.random().toHexString()}"
    val environment = environment()
    environment.putAll(envList)
    environment.putAll(mapOf("DATABASE_URI" to url, "DATABASE_DRIVER" to "h2", "BUILD_TYPE" to "dev-test"))
    environment["SERVER_PORT"] = port.toString()
    return this
}
