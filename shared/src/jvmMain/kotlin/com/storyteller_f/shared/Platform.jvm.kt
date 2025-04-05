package com.storyteller_f.shared

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import java.io.PrintWriter
import java.io.StringWriter
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.logging.*
import java.util.logging.Formatter
import java.util.regex.Pattern

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val id: String
        get() = ""
}

actual fun getPlatform(): Platform = JVMPlatform()

class CustomAntilog(
    private val defaultTag: String = "app",
    private val handler: List<Handler> = listOf()
) : Antilog() {

    companion object {
        private const val CALL_STACK_INDEX = 8
    }

    private val consoleHandler: ConsoleHandler = ConsoleHandler().apply {
        level = Level.ALL
        formatter = object : Formatter() {
            private val formatStr = "%1\$tF %1\$tT %4\$s %2\$s%n%5\$s%6\$s%n"

            override fun format(record: LogRecord?): String {
                record ?: return ""
                val zdt = ZonedDateTime.ofInstant(record.instant, ZoneId.systemDefault())
                val source = if (record.sourceClassName != null) {
                    """${record.sourceClassName} ${Exception().stackTrace[14]}"""
                } else {
                    record.loggerName
                }
                val message = formatMessage(record)
                val throwable = record.thrown?.stackTraceString ?: ""
                return String.format(
                    Locale.getDefault(),
                    formatStr,
                    zdt,
                    source,
                    record.loggerName,
                    record.level,
                    message,
                    throwable
                )
            }

        }
    }

    private val logger: Logger = Logger.getLogger(CustomAntilog::class.java.name).apply {
        level = Level.ALL

        if (handler.isEmpty()) {
            addHandler(consoleHandler)
            return@apply
        }
        handler.forEach {
            addHandler(it)
        }
    }.also { it.useParentHandlers = false }

    private val anonymousClass = Pattern.compile("(\\$\\d+)+$")

    private val tagMap: HashMap<LogLevel, String> = hashMapOf(
        LogLevel.VERBOSE to "[VERBOSE]",
        LogLevel.DEBUG to "[DEBUG]",
        LogLevel.INFO to "[INFO]",
        LogLevel.WARNING to "[WARN]",
        LogLevel.ERROR to "[ERROR]",
        LogLevel.ASSERT to "[ASSERT]"
    )

    override fun performLog(
        priority: LogLevel,
        tag: String?,
        throwable: Throwable?,
        message: String?,
    ) {

        val debugTag = tag ?: performTag(defaultTag)

        val fullMessage = if (message != null) {
            if (throwable != null) {
                "$message\n${throwable.stackTraceString}"
            } else {
                message
            }
        } else throwable?.stackTraceString ?: return

        when (priority) {
            LogLevel.VERBOSE -> logger.finest(buildLog(priority, debugTag, fullMessage))
            LogLevel.DEBUG -> logger.fine(buildLog(priority, debugTag, fullMessage))
            LogLevel.INFO -> logger.info(buildLog(priority, debugTag, fullMessage))
            LogLevel.WARNING -> logger.warning(buildLog(priority, debugTag, fullMessage))
            LogLevel.ERROR -> logger.severe(buildLog(priority, debugTag, fullMessage))
            LogLevel.ASSERT -> logger.severe(buildLog(priority, debugTag, fullMessage))
        }
    }

    private fun buildLog(priority: LogLevel, tag: String?, message: String?): String {
        return "${tagMap[priority]} ${tag ?: performTag(defaultTag)} - $message"
    }

    private fun performTag(defaultTag: String): String {
        val thread = Thread.currentThread().stackTrace

        return if (thread.size >= CALL_STACK_INDEX) {
            thread[CALL_STACK_INDEX].run {
                "${createStackElementTag(className)}\$$methodName"
            }
        } else {
            defaultTag
        }
    }

    private fun createStackElementTag(className: String): String {
        var tag = className
        val m = anonymousClass.matcher(tag)
        if (m.find()) {
            tag = m.replaceAll("")
        }
        return tag.substring(tag.lastIndexOf('.') + 1)
    }

    private val Throwable.stackTraceString
        get(): String {
            // DO NOT replace this with Log.getStackTraceString() - it hides UnknownHostException, which is
            // not what we want.
            val sw = StringWriter(256)
            val pw = PrintWriter(sw, false)
            printStackTrace(pw)
            pw.flush()
            return sw.toString()
        }
}


actual val logger: Antilog
    get() = CustomAntilog()