package com.storyteller_f.shared

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import org.slf4j.LoggerFactory
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
    private val handler: List<Handler> = listOf()
) : Antilog() {

    companion object {
        private const val CALL_STACK_INDEX = 8
    }

    private val consoleHandler = ConsoleHandler().apply {
        level = Level.ALL
        formatter = object : Formatter() {
            override fun format(record: LogRecord?): String {
                record ?: return ""
                val zdt = ZonedDateTime.ofInstant(record.instant, ZoneId.systemDefault())
                val message = formatMessage(record)
                val threadName = Thread.currentThread().name
                return String.format(
                    Locale.getDefault(),
                    "%1\$tF %1\$tT.%1\$tL [%2\$s]  %3$-5s %4\$s%n",
                    zdt,
                    threadName,
                    record.level,
                    message,
                )
            }

        }
    }

    private val logger = LoggerFactory.getLogger(CustomAntilog::class.java)

    private val anonymousClass = Pattern.compile("(\\$\\d+)+$")

    override fun performLog(
        priority: LogLevel,
        tag: String?,
        throwable: Throwable?,
        message: String?,
    ) {
        val fullMessage = when {
            message == null -> throwable?.stackTraceToString() ?: return
            throwable != null -> "$message\n${throwable.stackTraceToString()}"
            else -> message
        }

        when (priority) {
            LogLevel.VERBOSE -> logger.trace(buildLog(tag, fullMessage))
            LogLevel.DEBUG -> logger.debug(buildLog(tag, fullMessage))
            LogLevel.INFO -> logger.info(buildLog(tag, fullMessage))
            LogLevel.WARNING -> logger.warn(buildLog(tag, fullMessage))
            LogLevel.ERROR -> logger.error(buildLog(tag, fullMessage))
            LogLevel.ASSERT -> logger.error(buildLog(tag, fullMessage))
        }
    }

    private fun buildLog(tag: String?, message: String?): String {
        val t = tag ?: Thread.currentThread().stackTrace[CALL_STACK_INDEX].run {
            "${createStackElementTag(className)}$${methodName}"
        }
        return "$t - $message ${Thread.currentThread().stackTrace[CALL_STACK_INDEX]}"
    }

    private fun createStackElementTag(className: String): String {
        val m = anonymousClass.matcher(className)
        val tag = if (m.find()) {
            m.replaceAll("")
        } else {
            className
        }
        return tag.substring(tag.lastIndexOf('.') + 1)
    }

}


actual val logger: Antilog
    get() = CustomAntilog()