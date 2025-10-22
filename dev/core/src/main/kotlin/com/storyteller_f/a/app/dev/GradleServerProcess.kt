package com.storyteller_f.a.app.dev

import io.github.aakira.napier.Napier
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.collections.mapOf
import kotlin.collections.putAll
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun isWin(): Boolean {
    val property = System.getProperty("os.name").orEmpty()
    return property.lowercase().contains("win")
}

fun forceStop(port: Int) {
    Napier.i {
        "forceStop $port"
    }
    if (isWin()) {
        // for /f "tokens=5" %a in ('netstat -ano ^| findstr :8080') do taskkill /PID %a /F
        Runtime.getRuntime()
            .exec(
                arrayOf(
                    "cmd",
                    "/c",
                    """for /f "tokens=5" %i in ('netstat -ano ^| findstr :$port') do taskkill /PID %i /F"""
                )
            )
    } else {
        Runtime.getRuntime()
            .exec(arrayOf("/bin/sh", "-c", "pid=$(lsof -t -i :8080) && kill -9 \$pid"))
    }
}

fun stopServer(serverProcess: Process, port: Int) {
    serverProcess.destroy()
    forceStop(port)
}

@OptIn(ExperimentalUuidApi::class)
suspend fun CoroutineScope.startServerByRun(envFilePath: String, port: Int): Process? {
    val testEnvFile = File(envFilePath, "cloud/server/src/test/resources/.env")
    if (!testEnvFile.exists()) {
        println("${testEnvFile.canonicalPath} not exists")
        return null
    }
    val builder = getGradleProcessBuilder(
        File(envFilePath),
        arrayOf(
            "cloud:server:run",
            "-Dorg.gradle.logging.level=quiet",
            "--quiet",
            "--no-daemon"
        )
    )
    bindGradleProcessEnv(testEnvFile, builder, port)
    val serverProcess = builder.start()
    waitRunServerProcess(serverProcess)
    return serverProcess
}

private suspend fun CoroutineScope.waitRunServerProcess(serverProcess: Process) {
    val task = CompletableDeferred<String>()
    launch {
        withContext(Dispatchers.IO) {
            serverProcess.inputStream.bufferedReader().use {
                while (serverProcess.isRunning()) {
                    val line = it.readLine() ?: break
                    println(line)
                    if (line.contains("Responding at")) {
                        task.complete(line)
                    } else if (line.contains("Execution failed for task")) {
                        task.completeExceptionally(RuntimeException(line))
                    }
                }
            }
        }
    }
    task.await()
    Napier.i {
        "server started"
    }
}

suspend fun CoroutineScope.startServerByJar(rootPath: String, port: Int): Process? {
    forceStop(port)
    val envFile = File(rootPath, "cloud/server/src/test/resources/.env")
    if (!envFile.exists()) {
        println("${envFile.canonicalPath} not exists")
        return null
    }
    val projectRoot = File(rootPath) // 确保这个路径正确，指向包含 gradlew.bat 的父目录
    val buildBuilder = getGradleProcessBuilder(
        projectRoot,
        arrayOf(
            "cloud:server:buildFatJar",
            "-Dorg.gradle.logging.level=quiet",
            "--quiet",
        )
    )
    val buildProcess = buildBuilder.start()
    waitBuildProcess(buildProcess)
    val builder = ProcessBuilder(
        "java",
        "-jar",
        File(projectRoot, "cloud/server/build/libs/server-all.jar").absolutePath,
    ).directory(projectRoot.canonicalFile)
    bindGradleProcessEnv(envFile, builder, port)
    val serverProcess = builder.start()
    waitJarServerProcess(serverProcess)
    return serverProcess
}

@OptIn(ExperimentalUuidApi::class)
private fun bindGradleProcessEnv(envFile: File, builder: ProcessBuilder, port: Int) {
    val envList = envFile.readLines().filter {
        it.isNotBlank()
    }.map {
        val list = it.split("=", limit = 2)
        list[0] to list.getOrElse(1) {
            ""
        }
    }
    val url = "r2dbc:h2:file:///./${Uuid.random().toHexString()}"
    val environment = builder.environment()
    environment.putAll(envList)
    environment.putAll(
        mapOf(
            "DATABASE_URI" to url,
            "DATABASE_DRIVER" to "h2"
        )
    )
    environment["SERVER_PORT"] = port.toString()
}

private fun getGradleProcessBuilder(
    file: File,
    args: Array<String>
): ProcessBuilder {
    val gradleCommand = if (isWin()) {
        // Windows
        File(file, "gradlew.bat").absolutePath
    } else {
        // Linux/MacOS
        "./gradlew"
    }
    return ProcessBuilder(
        gradleCommand,
        *args
    ).directory(file.canonicalFile).redirectErrorStream(true)
}

private suspend fun CoroutineScope.waitJarServerProcess(serverProcess: Process) {
    val task = CompletableDeferred<String>()
    launch {
        withContext(Dispatchers.IO) {
            serverProcess.inputStream.bufferedReader().use {
                while (serverProcess.isRunning()) {
                    val line = it.readLine() ?: break
                    if (line.contains("Application started")) {
                        task.complete(line)
                    } else if (line.contains("Execution failed for task ':server:")) {
                        task.completeExceptionally(RuntimeException(line))
                    }
                }
            }
        }
    }
    task.await()
}

private suspend fun CoroutineScope.waitBuildProcess(buildProcess: Process) {
    val buildTask = CompletableDeferred<String>()
    launch {
        withContext(Dispatchers.IO) {
            buildProcess.inputStream.bufferedReader().use {
                while (buildProcess.isRunning()) {
                    val line = it.readLine() ?: break
                    if (line.contains("BUILD SUCCESSFUL")) {
                        buildTask.complete(line)
                    } else if (line.contains("BUILD FAILED")) {
                        buildTask.completeExceptionally(RuntimeException(line))
                    }
                }
            }
        }
    }
    buildTask.await()
    Napier.i {
        "build success"
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
                            ProcessBuilder(
                                "adb",
                                "-s",
                                device,
                                "reverse",
                                "tcp:$port",
                                "tcp:$port"
                            ).start().waitFor()
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
            "getConnectedDevices failed"
        }
    }
    return devices
}
