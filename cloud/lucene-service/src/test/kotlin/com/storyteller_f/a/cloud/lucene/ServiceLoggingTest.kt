/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.cloud.lucene

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.util.LogbackMDCAdapter
import ch.qos.logback.core.status.Status
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ServiceLoggingTest {
    @Test
    fun writeServiceLogsToConfiguredDirectory() {
        assertIs<LoggerContext>(LoggerFactory.getILoggerFactory())
        val parent = File("build/test/service-logging").also { it.mkdirs() }
        val directory = Files.createTempDirectory(parent.toPath(), "logs-").toFile()
        val context = LoggerContext()
        try {
            context.mdcAdapter = LogbackMDCAdapter()
            context.putProperty("LOG_PATH", directory.canonicalPath)
            val configuration = JoranConfigurator().also { it.context = context }
            configuration.doConfigure(requireNotNull(javaClass.getResource("/logback.xml")))
            context.getLogger("service-logging-test").info("synthetic service event")
            assertTrue(context.statusManager.copyOfStatusList.none { it.level == Status.ERROR })
            val log = directory.resolve("lucene.log").readText()
            assertTrue(log.contains("synthetic service event"))
            assertTrue(log.contains("INFO"))
        } finally {
            context.stop()
            directory.deleteRecursively()
        }
    }
}
