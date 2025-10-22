package com.storyteller_f.shared

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import org.slf4j.LoggerFactory
import java.util.regex.Pattern

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val id: String
        get() = ""
}

actual fun getPlatform(): Platform = JVMPlatform()

class CustomAntilog() : Antilog() {

    companion object {
        private const val CALL_STACK_INDEX = 8
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

        val log = buildLog(tag, fullMessage)
        when (priority) {
            LogLevel.VERBOSE -> logger.trace(log)
            LogLevel.DEBUG -> logger.debug(log)
            LogLevel.INFO -> logger.info(log)
            LogLevel.WARNING -> logger.warn(log)
            LogLevel.ERROR -> logger.error(log)
            LogLevel.ASSERT -> logger.error(log)
        }
    }

    private fun buildLog(tag: String?, message: String?): String {
        val customTag = tag ?: Thread.currentThread().stackTrace[CALL_STACK_INDEX].run {
            "${createStackElementTag(className)}$${methodName}"
        }
        return "$customTag - $message - ${Thread.currentThread().stackTrace[CALL_STACK_INDEX]}"
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

actual val kmpLogger: Antilog = CustomAntilog()