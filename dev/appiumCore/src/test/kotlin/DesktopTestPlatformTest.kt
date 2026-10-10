/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopTestPlatformTest {
    @Test
    fun `windows launches with argument file`() {
        val arguments = File("build/test/desktop arguments.args")
        val capabilities = DesktopTestPlatform.Windows.createCapabilities(arguments, 60L)

        assertEquals("windows", requireNotNull(capabilities.getCapability("platformName")).toString())
        assertEquals("DesktopDriver", capabilities.getCapability("appium:automationName"))
        assertEquals(true, capabilities.getCapability("appium:javaSwing"))
        assertEquals("\"@${arguments.canonicalPath}\"", capabilities.getCapability("appium:appArguments"))
    }

    @Test
    fun `argument file preserves special characters`() {
        val arguments = listOf("-cp", "C:\\runtime folder\\app.jar", "-Dexample=\"quoted\"")

        assertEquals(
            """
            "-cp"
            "C:\\runtime folder\\app.jar"
            "-Dexample=\"quoted\""
            """.trimIndent() + "\n",
            buildJavaArgumentFile(arguments),
        )
        assertFailsWith<IllegalArgumentException> { buildJavaArgumentFile(listOf("invalid\nargument")) }
    }

    @Test
    fun `linux retains executable script launch`() {
        val script = File("build/test/desktop.sh")
        val capabilities = DesktopTestPlatform.Linux.createCapabilities(script, 60L)

        assertEquals("linux", capabilities.getCapability("appium:automationName"))
        assertEquals(script.canonicalPath, capabilities.getCapability("appium:app"))
    }

    @Test
    fun `multiline input sends Enter keys`() {
        assertEquals(
            listOf(mapOf("text" to "first"), mapOf("virtualKeyCode" to 13), mapOf("text" to "second")),
            buildWindowsTextActions("first\r\nsecond"),
        )
    }

    @Test
    fun `preferences isolate JVMs and retain nodes`() {
        val first = DesktopTestPreferences().userRoot()
        val second = DesktopTestPreferences().userRoot()
        first.node("app").put("test-key", "value")

        assertEquals("value", first.node("app").get("test-key", null))
        assertEquals(null, second.node("app").get("test-key", null))
        first.node("app").removeNode()
        assertEquals(null, first.node("app").get("test-key", null))
    }
}
