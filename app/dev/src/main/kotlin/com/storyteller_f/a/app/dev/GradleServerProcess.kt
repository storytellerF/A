package com.storyteller_f.a.app.dev

import io.github.aakira.napier.Napier
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

fun isWin(): Boolean {
    val property = System.getProperty("os.name").orEmpty()
    return property.lowercase().contains("win")
}

fun forceStop(port: Int) {
    if (isWin()) {
        Runtime.getRuntime()
            .exec(
                arrayOf(
                    "cmd",
                    "/c",
                    """for /f "tokens=5" %i in ('netstat -ano ^| findstr :$port') do taskkill /PID %i /F"""
                )
            )
    } else {
        Runtime.getRuntime().exec("kill -9 $(sudo lsof -t -i :8080)")
    }
}

fun stopServer(serverProcess: Process, port: Int) {
    serverProcess.destroy()
    forceStop(port)
}

suspend fun CoroutineScope.startServerByRun(envFilePath: String, port: Int): Process? {
    val envFile = File(envFilePath, "server/src/test/resources/.env")
    if (!envFile.exists()) {
        println("${envFile.canonicalPath} not exists")
        return null
    }
    val file = File(envFilePath) // 确保这个路径正确，指向包含 gradlew.bat 的父目录
    val gradleCommand = if (isWin()) {
        // Windows
        File(file, "gradlew.bat").absolutePath
    } else {
        // Linux/MacOS
        "./gradlew"
    }

    val builder = ProcessBuilder(
        gradleCommand,
        "cloud:server:run",
        "-Dorg.gradle.logging.level=quiet",
        "--quiet",
    )
        .directory(file.canonicalFile)
    val pairs = envFile.readLines().filter {
        it.isNotBlank()
    }.map {
        val list = it.split("=", limit = 2)
        list[0] to list.getOrElse(1) {
            ""
        }
    }
    val environment = builder.environment()
    environment.putAll(pairs)
    environment["SERVER_PORT"] = port.toString()
    val serverProcess = builder.start()
    waitRunServerProcess(serverProcess)
    return serverProcess
}

private suspend fun CoroutineScope.waitRunServerProcess(serverProcess: Process) {
    val task = CompletableDeferred<String>()
    launch {
        serverProcess.inputStream.bufferedReader().use {
            while (serverProcess.isRunning()) {
                val line = it.readLine() ?: break
                if (line.contains("Application started")) {
                    task.complete(line)
                }
            }
        }
    }
    launch {
        serverProcess.errorStream.bufferedReader().use {
            while (serverProcess.isRunning()) {
                val line = it.readLine() ?: break
                if (line.contains("Execution failed for task ':server:")) {
                    task.completeExceptionally(RuntimeException(line))
                }
            }
        }
    }
    withTimeout(10000) {
        task.await()
    }
}

suspend fun CoroutineScope.startServerByJar(envFilePath: String, port: Int): Process? {
    forceStop(port)
    val envFile = File(envFilePath, "server/src/test/resources/.env")
    if (!envFile.exists()) {
        println("${envFile.canonicalPath} not exists")
        return null
    }
    val file = File(envFilePath) // 确保这个路径正确，指向包含 gradlew.bat 的父目录

    val gradleCommand = if (isWin()) {
        // Windows
        File(file, "gradlew.bat").absolutePath
    } else {
        // Linux/MacOS
        "./gradlew"
    }
    val buildBuilder = ProcessBuilder(
        gradleCommand,
        "cloud:server:buildFatJar",
        "-Dorg.gradle.logging.level=quiet",
        "--quiet",
    ).directory(file.canonicalFile)
    val buildProcess = buildBuilder.start()
    waitBuildProcess(buildProcess)

    val builder = ProcessBuilder(
        "java",
        "-jar",
        File(file, "cloud/server/build/libs/server-all.jar").absolutePath,
    ).directory(file.canonicalFile)
    val pairs = envFile.readLines().filter {
        it.isNotBlank()
    }.map {
        val list = it.split("=", limit = 2)
        list[0] to list.getOrElse(1) {
            ""
        }
    }
    val environment = builder.environment()
    environment.putAll(pairs)
    environment["SERVER_PORT"] = port.toString()
    val serverProcess = builder.start()
    waitJarServerProcess(serverProcess)
    return serverProcess
}

private suspend fun CoroutineScope.waitJarServerProcess(serverProcess: Process) {
    val task = CompletableDeferred<String>()
    launch {
        serverProcess.inputStream.bufferedReader().use {
            while (serverProcess.isRunning()) {
                val line = it.readLine() ?: break
                if (line.contains("Application started")) {
                    task.complete(line)
                }
            }
        }
    }
    launch {
        serverProcess.errorStream.bufferedReader().use {
            while (serverProcess.isRunning()) {
                val line = it.readLine() ?: break
                if (line.contains("Execution failed for task ':server:")) {
                    task.completeExceptionally(RuntimeException(line))
                }
            }
        }
    }
    withTimeout(10000) {
        task.await()
    }
}

private suspend fun CoroutineScope.waitBuildProcess(buildProcess: Process) {
    val buildTask = CompletableDeferred<String>()
    launch {
        buildProcess.inputStream.bufferedReader().use {
            while (buildProcess.isRunning()) {
                val line = it.readLine() ?: break
                if (line.contains("BUILD SUCCESSFUL")) {
                    buildTask.complete(line)
                }
            }
        }
    }
    launch {
        buildProcess.errorStream.bufferedReader().use {
            while (buildProcess.isRunning()) {
                val line = it.readLine() ?: break
                if (line.contains("BUILD FAILED")) {
                    buildTask.completeExceptionally(RuntimeException(line))
                }
            }
        }
    }
    withTimeout(10000) {
        buildTask.await()
    }
}

private fun Process.isRunning() = runCatching { this@isRunning.exitValue() }.getOrNull() == null

// 启动协程监听 ADB 设备连接
@OptIn(DelicateCoroutinesApi::class)
fun startListening(port: Int, previousDevices: MutableSet<String>): Job {
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
            "com.storyteller_f.a.client.dev.getConnectedDevices failed"
        }
    }
    return devices
}
