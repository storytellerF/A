package com.storyteller_f.a.app.dev

import io.github.aakira.napier.Napier
import kotlinx.coroutines.*
import java.io.File
import kotlin.collections.mapOf
import kotlin.collections.putAll
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

const val GIT_BASH = "C:/Program Files/Git/bin/bash.exe"

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

fun stopServer(serverProcess: Process) {
    serverProcess.destroy()
}

@OptIn(ExperimentalUuidApi::class)
suspend fun CoroutineScope.startServerByRun(projectRoot: String, port: Int): Process? {
    val testEnvFile = File(projectRoot, "cloud/server/src/test/resources/.env")
    if (!testEnvFile.exists()) {
        println("${testEnvFile.canonicalPath} not exists")
        return null
    }
    install(projectRoot)
    val serverProcess =
        ProcessBuilder(GIT_BASH, "-c", "cloud/server/build/install/server/bin/server")
            .redirectErrorStream(true)
            .directory(File(projectRoot))
            .bindGradleProcessEnv(testEnvFile, port)
            .start()
    waitRunServerProcess(serverProcess)
    return serverProcess
}

private suspend fun CoroutineScope.install(projectRoot: String) {
    val installDistProcess = getGradleProcessBuilder(
        File(projectRoot),
        arrayOf(
            "cloud:server:installDist"
        )
    ).start()
    launch {
        withContext(Dispatchers.IO) {
            installDistProcess.inputStream.bufferedReader().use {
                while (installDistProcess.isRunning()) {
                    val line = it.readLine() ?: break
                    println(line)
                }
            }
        }
    }
    val result = withContext(Dispatchers.IO) {
        installDistProcess.waitFor()
    }
    check(result == 0) {
        "install failed"
    }
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
    environment.putAll(
        mapOf(
            "DATABASE_URI" to url,
            "DATABASE_DRIVER" to "h2"
        )
    )
    environment["SERVER_PORT"] = port.toString()
    return this
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

private fun Process.isRunning() = runCatching { this@isRunning.exitValue() }.getOrNull() == null
