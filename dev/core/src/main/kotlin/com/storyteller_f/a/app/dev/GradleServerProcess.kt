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
import kotlin.concurrent.thread

data class DatabaseConfig(
    val uri: String,
    val driver: String,
    val user: String,
    val password: String
) {
    companion object {
        fun h2(sessionPath: String) = DatabaseConfig(
            uri = "r2dbc:h2:file:///$sessionPath/h2",
            driver = "h2",
            user = "sa",
            password = ""
        )
    }
}

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

suspend fun CoroutineScope.startServerByRun(
    projectRoot: String,
    port: Int,
    sessionPath: String,
    dbConfig: DatabaseConfig = DatabaseConfig.h2(sessionPath)
): ProcessMate? {
    forceStop(port)
    val testEnvFile = File(projectRoot, "cloud/server/src/test/resources/test.env")
    if (!testEnvFile.exists()) {
        Napier.w { "${testEnvFile.canonicalPath} not exists" }
        return null
    }
    val extension = if (isWin()) ".bat" else ""
    val command = "cloud/server/build/install/server/bin/server$extension"
    val serverProcess = ProcessBuilder(command)
        .redirectErrorStream(true)
        .directory(File(projectRoot))
        .bindGradleProcessEnv(testEnvFile, port, projectRoot, sessionPath, dbConfig)
        .start()
    return waitRunProcess(serverProcess) {
        it.contains("Responding at")
    }
}

suspend fun CoroutineScope.startWorkerByRun(
    projectRoot: String,
    sessionPath: String,
    dbConfig: DatabaseConfig = DatabaseConfig.h2(sessionPath)
): ProcessMate? {
    val testEnvFile = File(projectRoot, "cloud/server/src/test/resources/test.env")
    if (!testEnvFile.exists()) {
        Napier.w { "${testEnvFile.canonicalPath} not exists" }
        return null
    }
    val extension = if (isWin()) ".bat" else ""
    val command = "cloud/worker/build/install/worker/bin/worker$extension"
    val process = ProcessBuilder(command)
        .redirectErrorStream(true)
        .directory(File(projectRoot))
        .bindGradleProcessEnv(testEnvFile, 0, projectRoot, sessionPath, dbConfig)
        .start()
    return waitRunProcess(process) {
        it.contains("worker started")
    }
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
): ProcessMate {
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
        throw Exception("server start failed")
    }
    Napier.i {
        "server started"
    }
    return ProcessMate(process, job)
}

private fun ProcessBuilder.bindGradleProcessEnv(
    envFile: File,
    port: Int,
    projectRoot: String,
    sessionPath: String,
    dbConfig: DatabaseConfig
): ProcessBuilder {
    val envList = envFile.readLines().filter {
        it.isNotBlank()
    }.map {
        val list = it.split("=", limit = 2)
        list[0] to list.getOrElse(1) {
            ""
        }
    }
    val environment = environment()
    environment.putAll(envList)
    environment.putAll(
        mapOf(
            "DATABASE_URI" to dbConfig.uri,
            "DATABASE_DRIVER" to dbConfig.driver,
            "DATABASE_USER" to dbConfig.user,
            "DATABASE_PASS" to dbConfig.password,
            "BUILD_TYPE" to "dev-test"
        )
    )
    environment["LUCENE_BASE_PATH"] = "$sessionPath/lucene"
    environment["SERVER_PORT"] = port.toString()
    val canonicalProjectRoot = File(projectRoot).canonicalPath
    val cliPath = File(projectRoot, "/cloud/cli/build/install/cli/bin/cli").canonicalPath
    val presetDataPath = File(projectRoot, "../AData/data").canonicalPath
    environment["INIT_ENABLE"] = "true"
    environment["INIT_WORKING_DIR"] = canonicalProjectRoot
    environment["INIT_SCRIPT"] = "./scripts/tool_scripts/flush-database.sh $cliPath $presetDataPath"
    environment["LOG_PATH"] = "$sessionPath/logs"
    environment["FILE_SYSTEM_MEDIA_PATH"] = "$sessionPath/files"
    return this
}
