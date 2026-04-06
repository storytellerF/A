package com.storyteller_f.a.app.dev

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

fun isWin(): Boolean {
    val property = System.getProperty("os.name").orEmpty()
    return property.lowercase().contains("win")
}

fun forceStop(port: Int) {
    println("forceStop $port ${File(".").canonicalPath}")
    val extension = if (isWin()) "bat" else "sh"
    val process = ProcessBuilder().command(
        File("../../scripts/tool_scripts/kill-port.$extension").canonicalPath,
        port.toString()
    ).redirectErrorStream(true).start()
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
    if (result != 0) {
        println("forceStop failed $result")
    }
}

fun stopServer(serverProcess: Process) {
    serverProcess.destroy()
}

suspend fun CoroutineScope.startCloudServerByGradle(
    projectRoot: String,
): ProcessMate? {
    val extension = if (isWin()) ".bat" else ""
    val gradlew = File(projectRoot, "gradlew$extension").canonicalPath
    val process = withContext(Dispatchers.IO) {
        ProcessBuilder(gradlew, "cloud:server:run", "--no-daemon")
            .redirectErrorStream(true)
            .directory(File(projectRoot))
            .start()
    }
    return waitRunProcess(process) {
        it.contains("Application started")
    }
}

suspend fun CoroutineScope.startCloudWorkerByGradle(
    projectRoot: String,
): ProcessMate? {
    val extension = if (isWin()) ".bat" else ""
    val gradlew = File(projectRoot, "gradlew$extension").canonicalPath
    val process = withContext(Dispatchers.IO) {
        ProcessBuilder(gradlew, "cloud:worker:run", "--no-daemon")
            .redirectErrorStream(true)
            .directory(File(projectRoot))
            .start()
    }
    return waitRunProcess(process) {
        it.contains("worker started")
    }
}

fun startDevServerByGradle(projectRoot: String) {
    val extension = if (isWin()) ".bat" else ""
    val gradlew = File(projectRoot, "gradlew$extension").canonicalPath
    val latch = CountDownLatch(1)
    thread(isDaemon = true) {
        val process = ProcessBuilder(gradlew, "dev:server:run", "--no-daemon")
            .redirectErrorStream(true)
            .directory(File(projectRoot))
            .start()
        println("dev server start")
        waitThreadRunProcess(process) {
            it.contains("Application started")
        }
        latch.countDown()
    }
    latch.await()
    println("start dev server success")
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

private suspend fun CoroutineScope.waitRunProcess(
    process: Process,
    success: (String) -> Boolean
): ProcessMate? {
    val task = CompletableDeferred<Unit>()
    val job = launch {
        process.inputStream.bufferedReader().use {
            while (isActive) {
                val line = withContext(Dispatchers.IO) {
                    try {
                        it.readLine()
                    } catch (e: Exception) {
                        Napier.w { "read line failed, may be process end: ${e.message}" }
                        null
                    }
                }
                line ?: break
                println(line)
                if (success(line)) {
                    task.complete(Unit)
                }
            }
        }
    }
    // 等待task.await 和process 哪个先结束
    val waitProcess = launch {
        while (process.isAlive) {
            delay(100)
        }
    }
    val result = select {
        task.onAwait {
            true
        }
        waitProcess.onJoin {
            false
        }
    }
    if (!result) {
        Napier.i {
            "server start failed"
        }
        return null
    }
    Napier.i {
        "server started"
    }
    return ProcessMate(process, job)
}

fun waitThreadRunProcess(
    process: Process,
    success: (String) -> Boolean
): Process? {
    var successFound = false
    val thread = thread {
        process.inputStream.bufferedReader().use {
            while (process.isAlive) {
                val line = try {
                    it.readLine()
                } catch (e: Exception) {
                    Napier.w { "read line failed, may be process end: ${e.message}" }
                    null
                }
                if (line == null) {
                    println("line is null")
                    break
                }
                println(line)
                if (success(line)) {
                    successFound = true
                }
            }
        }
    }
    // 等待直到找到成功条件，不使用 process.waitFor()
    while (!successFound && thread.isAlive) {
        Thread.sleep(100)
    }
    if (!successFound) {
        return null
    }
    return process
}
