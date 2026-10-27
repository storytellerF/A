/*
 * This is a private project. All rights reserved.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

class E2eCoverageWindowsLauncherTest {
    @Test
    fun manifestPreservesOrderAndEscapesPaths() {
        val root = File(System.getProperty("coverage.test.directory"), "windows-launcher")
        resetCoverageDirectory(root)
        val libraries = root.resolve("lib").apply { mkdirs() }
        val names = listOf("first # library.jar", "second library.jar")
        names.forEachIndexed { index, name ->
            JarOutputStream(libraries.resolve(name).outputStream().buffered()).use { jar ->
                jar.putNextEntry(JarEntry("selection.txt"))
                jar.write(index.toString().toByteArray())
                jar.closeEntry()
            }
        }
        val script = root.resolve("bin/cli.bat").apply { parentFile.mkdirs() }
        val original = "@echo off\r\nset CLASSPATH=${names.joinToString(";") { "%APP_HOME%\\lib\\$it" }}\r\n"
        script.writeText(original)
        prepareCoverageWindowsLaunchers(root)
        assertTrue(script.readText().contains("set CLASSPATH=%APP_HOME%\\lib\\e2e-classpath-cli.jar\r\n"))
        val pathingJar = libraries.resolve("e2e-classpath-cli.jar")
        JarFile(pathingJar).use { jar ->
            assertEquals("first%20%23%20library.jar second%20library.jar", jar.manifest.mainAttributes.getValue("Class-Path"))
        }
        URLClassLoader(arrayOf(pathingJar.toURI().toURL()), null).use { loader ->
            val selections = loader.getResources("selection.txt").toList().map { resource ->
                resource.openStream().buffered().use { it.readBytes().decodeToString() }
            }
            assertEquals(listOf("0", "1"), selections)
        }
    }
}
