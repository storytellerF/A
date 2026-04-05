package com.storyteller_f.a.app

import android.app.Application
import android.util.Log
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val logDirName = "logs"
private const val logFileName = "appium-app.log"
private const val defaultTag = "Napier"
private const val flushIntervalMillis = 1000L
private const val maxBatchSize = 50

fun setupAppLogger(application: Application) {
    val logFile = ensureAppLogFile(application)
    writeBootstrapLog(logFile)
    Napier.takeLogarithm()
    Napier.base(AppAntilog(logFile))
}

private fun ensureAppLogFile(application: Application): File {
    return File(application.filesDir, "$logDirName/$logFileName").apply {
        parentFile?.mkdirs()
        if (!exists()) {
            createNewFile()
        }
    }
}

private fun writeBootstrapLog(logFile: File) {
    val message = "${System.currentTimeMillis()} INFO [$defaultTag] logger initialized\n"
    runCatching {
        BufferedWriter(
            OutputStreamWriter(
                BufferedOutputStream(FileOutputStream(logFile, true)),
                StandardCharsets.UTF_8
            )
        ).use { writer ->
            writer.append(message)
        }
    }
}

private class AppAntilog(
    private val logFile: File
) : Antilog() {
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingLogs = ConcurrentLinkedQueue<String>()
    private val pendingCount = AtomicInteger(0)
    private val flushRunning = AtomicBoolean(false)

    init {
        scope.launch {
            while (isActive) {
                delay(flushIntervalMillis)
                requestFlush()
            }
        }
    }

    override fun performLog(priority: LogLevel, tag: String?, throwable: Throwable?, message: String?) {
        val actualTag = tag?.takeIf { it.isNotBlank() } ?: defaultTag
        val logcatMessage = when {
            message == null -> throwable?.stackTraceToString().orEmpty()
            throwable != null -> "$message\n${throwable.stackTraceToString()}"
            else -> message
        }
        if (logcatMessage.isNotBlank()) {
            Log.println(priority.toAndroidPriority(), actualTag, logcatMessage)
        }
        val renderedMessage = buildString {
            append(formatter.format(System.currentTimeMillis()))
            append(' ')
            append(priority.name)
            append(' ')
            append('[')
            append(actualTag)
            append(']')
            message?.takeIf { it.isNotBlank() }?.let {
                append(' ')
                append(it)
            }
            throwable?.let {
                if (isNotEmpty()) {
                    append('\n')
                }
                append(it.stackTraceToString())
            }
        }
        if (renderedMessage.isBlank()) {
            return
        }
        pendingLogs.add(renderedMessage)
        val currentCount = pendingCount.incrementAndGet()
        if (currentCount >= maxBatchSize) {
            requestFlush()
        }
    }

    private fun requestFlush() {
        if (!flushRunning.compareAndSet(false, true)) {
            return
        }
        scope.launch {
            try {
                while (true) {
                    val batch = drainBatch()
                    if (batch.isEmpty()) {
                        break
                    }
                    writeBatch(batch)
                }
            } finally {
                flushRunning.set(false)
                if (pendingCount.get() > 0) {
                    requestFlush()
                }
            }
        }
    }

    private fun drainBatch(): List<String> {
        val batch = ArrayList<String>(maxBatchSize)
        while (batch.size < maxBatchSize) {
            val message = pendingLogs.poll() ?: break
            batch += message
        }
        if (batch.isNotEmpty()) {
            pendingCount.addAndGet(-batch.size)
        }
        return batch
    }

    private fun writeBatch(batch: List<String>) {
        val content = batch.joinToString(separator = "\n", postfix = "\n")
        try {
            BufferedWriter(
                OutputStreamWriter(
                    BufferedOutputStream(FileOutputStream(logFile, true)),
                    StandardCharsets.UTF_8
                )
            ).use { writer ->
                writer.append(content)
            }
        } catch (e: Exception) {
            Log.e("AppAntilog", "Write log failed", e)
        }
    }
}

private fun LogLevel.toAndroidPriority(): Int {
    return when (this) {
        LogLevel.VERBOSE -> Log.VERBOSE
        LogLevel.DEBUG -> Log.DEBUG
        LogLevel.INFO -> Log.INFO
        LogLevel.WARNING -> Log.WARN
        LogLevel.ERROR -> Log.ERROR
        LogLevel.ASSERT -> Log.ASSERT
    }
}