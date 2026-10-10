/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import org.openqa.selenium.remote.DesiredCapabilities
import java.io.File

internal enum class DesktopTestPlatform {
    Linux,
    Windows,
    ;

    fun createCapabilities(launchFile: File, timeoutSeconds: Long): DesiredCapabilities =
        DesiredCapabilities().apply {
        setCapability("appium:newCommandTimeout", timeoutSeconds)
        when (this@DesktopTestPlatform) {
            Linux -> {
                setCapability("platformName", "linux")
                setCapability("appium:automationName", "linux")
                setCapability("appium:app", launchFile.canonicalPath)
            }

            Windows -> {
                setCapability("platformName", "Windows")
                setCapability("appium:automationName", "DesktopDriver")
                setCapability("appium:app", resolveDesktopJava(this@DesktopTestPlatform))
                setCapability("appium:appArguments", "\"@${launchFile.canonicalPath}\"")
                setCapability("appium:appWorkingDir", File(".").canonicalPath)
                setCapability("appium:javaSwing", true)
                setCapability("appium:ms:forcequit", true)
                setCapability("appium:ms:waitForAppLaunch", timeoutSeconds)
            }
        }
    }

    companion object {
        fun current(): DesktopTestPlatform {
            val requested = System.getenv("APPIUM_DESKTOP_PLATFORM") ?: System.getProperty("os.name")
            return when {
                requested.equals("linux", ignoreCase = true) -> Linux
                requested.startsWith("Windows", ignoreCase = true) -> Windows
                else -> error("Unsupported desktop test platform: $requested; expected Linux or Windows")
            }
        }
    }
}

internal fun resolveDesktopJava(platform: DesktopTestPlatform): String =
    System.getenv("APP_DESKTOP_TEST_JAVA")
    ?: File(
        System.getProperty("java.home"),
        "bin/${if (platform == DesktopTestPlatform.Windows) "javaw.exe" else "java"}",
    )
        .canonicalPath

internal fun buildJavaArgumentFile(arguments: List<String>): String =
    arguments.joinToString("\n", postfix = "\n") { argument ->
        require('\n' !in argument && '\r' !in argument) { "JVM arguments must not contain newlines" }
        val escaped = argument.replace("\\", "\\\\").replace("\"", "\\\"")
        "\"$escaped\""
    }
