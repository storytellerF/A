package jvm_based

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.CountDownLatch

fun runGradle(vararg args: String): Process {
    val file = File("../")  // 确保这个路径正确，指向包含 gradlew.bat 的父目录
    val isWin = isWin()
    val gradleCommand = if (isWin) {
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
        *args
    )
        .directory(file.canonicalFile)
    val pairs = File("../server/src/test/resources/.env").readLines().filter {
        it.isNotBlank()
    }.map {
        val list = it.split("=", limit = 2)
        list[0] to list.getOrElse(1) {
            ""
        }
    }
    builder.environment().putAll(pairs)

    return builder.start()
}

private fun isWin(): Boolean {
    val property = System.getProperty("os.name").orEmpty()
    val isWin = property.lowercase().contains("win")
    return isWin
}

fun forceStop() {
    if (isWin()) {
        Runtime.getRuntime()
            .exec("cmd /c for /f \"tokens=5\" %i in ('netstat -ano ^| findstr :8811') do taskkill /PID %i /F\n")
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun jvmBasedTest(block: () -> Unit) {
    forceStop()
    System.loadLibrary("LiteCore")
    val latch = CountDownLatch(1)
    val serverProcess = runGradle()
    GlobalScope.launch {
        serverProcess.inputStream.bufferedReader().use {
            while (serverProcess.isAlive) {
                val line = it.readLine() ?: break
                println(line)
                if (line.contains("Application started")) {
                    latch.countDown()
                }
            }
        }
    }
    GlobalScope.launch {
        serverProcess.errorStream.bufferedReader().use {
            while (serverProcess.isAlive) {
                val line = it.readLine() ?: break
                if (line == "Execution failed for task ':server:run'.") {
                    error(line)
                } else {
                    System.err.println(line)
                }
            }
        }
    }
    latch.await()
    block()
    serverProcess.destroy()
    forceStop()
}