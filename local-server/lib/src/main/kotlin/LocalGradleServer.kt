
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import java.io.File

fun runGradle(envFilePath: String, port: Int): Process? {
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
        "server:run",
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
    return builder.start()
}

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
    }
}

fun stopServer(serverProcess: Process, port: Int) {
    serverProcess.destroy()
    forceStop(port)
}

@OptIn(DelicateCoroutinesApi::class)
suspend fun CoroutineScope.startServer(envFileBasePath: String, port: Int): Process? {
    forceStop(port)
    val serverProcess = runGradle(envFileBasePath, port) ?: return null
    val task = CompletableDeferred<String>()
    async {
        serverProcess.inputStream.bufferedReader().use {
            while (serverProcess.isRunning()) {
                val line = it.readLine() ?: break
                if (line.contains("Application started")) {
                    task.complete(line)
                }
            }
        }
    }
    async {
        serverProcess.errorStream.bufferedReader().use {
            while (serverProcess.isRunning()) {
                val line = it.readLine() ?: break
                if (line.contains("Execution failed for task ':server:")) {
                    task.completeExceptionally(RuntimeException(line))
                }
            }
        }
    }
    task.await()
    return serverProcess
}

private fun Process.isRunning() = runCatching { this@isRunning.exitValue() }.getOrNull() == null
