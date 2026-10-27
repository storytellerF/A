/*
 * This is a private project. All rights reserved.
 */

import java.io.File
import java.net.URI
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

/** Keep covered Windows distributions below cmd.exe's limit while preserving classpath order. */
internal fun prepareCoverageWindowsLaunchers(distribution: File) {
    distribution.resolve("bin").listFiles().orEmpty().filter { it.extension == "bat" }.forEach { launcher ->
        val content = launcher.readText()
        val assignment = Regex("(?m)^set CLASSPATH=([^\\r\\n]+)").find(content)
            ?: error("Windows launcher is missing its classpath: ${launcher.name}")
        val prefix = "%APP_HOME%\\lib\\"
        val entries = assignment.groupValues[1].split(';').map { entry ->
            check(entry.startsWith(prefix)) { "Unexpected Windows classpath entry: $entry" }
            val name = entry.removePrefix(prefix)
            check(distribution.resolve("lib").resolve(name).isFile) { "Missing Windows classpath entry: $name" }
            URI(null, null, name, null).toASCIIString()
        }
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes[Attributes.Name.CLASS_PATH] = entries.joinToString(" ")
        }
        val pathingJar = "e2e-classpath-${launcher.nameWithoutExtension}.jar"
        JarOutputStream(distribution.resolve("lib/$pathingJar").outputStream().buffered(), manifest).use { }
        launcher.writeText(content.replaceRange(assignment.range, "set CLASSPATH=$prefix$pathingJar"))
    }
}
