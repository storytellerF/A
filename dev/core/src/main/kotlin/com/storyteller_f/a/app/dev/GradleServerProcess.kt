package com.storyteller_f.a.app.dev

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
suspend fun CoroutineScope.startServerByRun(projectRoot: String, port: Int): ProcessMate? {
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
    val job = waitRunServerProcess(serverProcess)
    return ProcessMate(serverProcess, job)
}

private suspend fun CoroutineScope.install(projectRoot: String) {
    val installDistProcess = getGradleProcessBuilder(
        File(projectRoot),
        arrayOf("cloud:server:installDist")
    ).start()
    val job = launch {
        withContext(Dispatchers.IO) {
            installDistProcess.inputStream.bufferedReader().use {
                while (installDistProcess.isRunning()) {
                    val line = it.readLine() ?: break
                    println(line)
                    delay(100)
                }
            }
        }
    }
    val result = withContext(Dispatchers.IO) {
        installDistProcess.waitFor()
    }
    job.cancel()
    check(result == 0) {
        "install failed"
    }
}

class ProcessMate(val process: Process, val job: Job) {
    fun stop() {
        stopServer(process)
        job.cancel()
    }
}

private suspend fun CoroutineScope.waitRunServerProcess(serverProcess: Process): Job {
    val task = CompletableDeferred<String>()
    val job = launch {
        withContext(Dispatchers.IO) {
            serverProcess.inputStream.bufferedReader().use {
                while (serverProcess.isRunning()) {
                    val line = it.readLine() ?: break
                    println(line)
                    if (line.contains("Responding at")) {
                        task.complete(line)
                    } else if (line.contains("Execution failed for task") || line.contains("Exception in thread")) {
                        task.completeExceptionally(RuntimeException(line))
                    }
                    delay(100)
                }
            }
        }
    }
    task.await()
    Napier.i {
        "server started"
    }
    return job
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
