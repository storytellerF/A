package com.storyteller_f.a.client.core

import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import org.slf4j.ILoggerFactory
import org.slf4j.IMarkerFactory
import org.slf4j.Logger
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.AbstractLogger
import org.slf4j.helpers.BasicMDCAdapter
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.spi.MDCAdapter
import org.slf4j.spi.SLF4JServiceProvider

class CustomSLF4JServiceProvider : SLF4JServiceProvider {
    override fun getLoggerFactory(): ILoggerFactory {
        return CustomLoggerFactory()
    }

    override fun getMarkerFactory(): IMarkerFactory {
        return BasicMarkerFactory()
    }

    override fun getMDCAdapter(): MDCAdapter {
        return BasicMDCAdapter()
    }

    override fun getRequestedApiVersion(): String {
        return "2.0.0"
    }

    override fun initialize() = Unit
}

class CustomLoggerFactory : ILoggerFactory {
    override fun getLogger(name: String?): Logger {
        return CustomSlf4jLogger(name ?: "Default")
    }
}

class CustomSlf4jLogger(private val customName: String) : AbstractLogger() {
    override fun isTraceEnabled(): Boolean {
        return false
    }

    override fun isTraceEnabled(marker: Marker?): Boolean {
        return false
    }

    override fun isDebugEnabled(): Boolean {
        return true
    }

    override fun isDebugEnabled(marker: Marker?): Boolean {
        return true
    }

    override fun isInfoEnabled(): Boolean {
        return true
    }

    override fun isInfoEnabled(marker: Marker?): Boolean {
        return true
    }

    override fun isWarnEnabled(): Boolean {
        return true
    }

    override fun isWarnEnabled(marker: Marker?): Boolean {
        return true
    }

    override fun isErrorEnabled(): Boolean {
        return true
    }

    override fun isErrorEnabled(marker: Marker?): Boolean {
        return true
    }

    override fun getFullyQualifiedCallerName(): String? {
        return null
    }

    override fun handleNormalizedLoggingCall(
        level: Level?,
        marker: Marker?,
        messagePattern: String?,
        arguments: Array<out Any>?,
        throwable: Throwable?
    ) {
        level ?: return
        messagePattern ?: return
        if (!arguments.isNullOrEmpty()) {
            Napier.w(tag = customName) {
                "argument is not empty $arguments"
            }
        }
        Napier.log(getLevel(level), customName, throwable, messagePattern)
    }

    private fun getLevel(level: Level): LogLevel = when (level) {
        Level.ERROR -> LogLevel.ERROR
        Level.WARN -> LogLevel.WARNING
        Level.INFO -> LogLevel.INFO
        Level.DEBUG -> LogLevel.DEBUG
        Level.TRACE -> LogLevel.VERBOSE
    }
}
