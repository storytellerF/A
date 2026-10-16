/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import org.openqa.selenium.Dimension
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.edge.EdgeDriver
import org.openqa.selenium.edge.EdgeOptions
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.firefox.GeckoDriverService
import org.openqa.selenium.net.PortProber
import java.io.File
import kotlin.uuid.Uuid

/** Browser variants run serially with isolated profiles, including across process restarts. */
enum class WasmBrowser {
    CHROME,
    EDGE,
    FIREFOX,
    ;

    fun createDriver(profile: File, windowSize: Dimension): WebDriver {
        val binary =
            System.getenv("WASM_E2E_${name}_BINARY")?.let(::File)?.also {
                require(it.isFile) { "WASM_E2E_${name}_BINARY must name an existing browser executable" }
            }
        // Leaving the binary unset lets Selenium Manager discover installed browsers before downloading.
        return when (this) {
            CHROME ->
                ChromeDriver(
                    ChromeOptions().addArguments(chromiumArguments(profile, windowSize)).also {
                        if (binary != null) it.setBinary(binary.canonicalPath)
                        it.setExperimentalOption("prefs", mapOf("intl.accept_languages" to "en-US,en"))
                    },
                )

            EDGE ->
                EdgeDriver(
                    EdgeOptions().addArguments(chromiumArguments(profile, windowSize)).also {
                        if (binary != null) it.setBinary(binary.canonicalPath)
                        it.setExperimentalOption("prefs", mapOf("intl.accept_languages" to "en-US,en"))
                    },
                )

            FIREFOX ->
                FirefoxDriver(
                    firefoxService(),
                    FirefoxOptions().addPreference("intl.accept_languages", "en-US,en")
                        .addPreference("intl.locale.requested", "en-US")
                        .addArguments(
                            "-headless",
                            "--profile",
                            profile.canonicalPath,
                            "--width=${windowSize.width}",
                            "--height=${windowSize.height}",
                        ).also { options -> if (binary != null) options.setBinary(binary.canonicalPath) },
                )
        }
    }

    suspend fun <T> withBrowser(windowSize: Dimension = Dimension(1280, 900), block: (WebDriver) -> T): T =
        withProfile { profile ->
            val browser = createDriver(profile, windowSize)
            try {
                block(browser)
            } finally {
                browser.quit()
            }
        }

    suspend fun <T> withProfile(block: suspend (File) -> T): T {
        val profile = File("build/test/appium/$name-profile-${Uuid.random().toHexString()}").also(File::mkdirs)
        return try {
            block(profile)
        } finally {
            profile.deleteRecursively()
        }
    }

    private fun chromiumArguments(profile: File, windowSize: Dimension) =
        listOf(
        "--headless=new",
        "--user-data-dir=${profile.canonicalPath}",
        "--lang=en-US",
        "--window-size=${windowSize.width},${windowSize.height}",
        "--no-first-run",
        "--no-default-browser-check",
    )

    private fun firefoxService(): GeckoDriverService =
        object : GeckoDriverService.Builder() {
        // In-place profiles require an explicit port, including when reusing the profile after quit.
        // https://firefox-source-docs.mozilla.org/testing/geckodriver/Profiles.html
        override fun createArgs(): List<String> =
            super.createArgs() + listOf("--marionette-port", PortProber.findFreePort().toString())
    }.build()
}
