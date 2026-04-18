package com.storyteller_f.a.cloud.cli

import com.storyteller_f.a.backend.core.setLogPath
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.test.runTest
import java.io.File
import java.net.InetSocketAddress
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AddPresetTest {
    @Test
    fun `parse yaml download config with archive excludes`() {
        setLogPath()
        val tempDir = createTempDirectory(prefix = "addpreset-yaml-").toFile()
        try {
            val configFile = File(tempDir, "sample.download.yaml")
            configFile.writeText(
                """
                name: dataset.zip
                link: https://example.com/dataset.zip
                hash: sha256:abc123
                excludeArchiveEntries:
                  - "**/*.md"
                  - "docs/**"
                """.trimIndent()
            )

            val config = parseDownloadConfig(configFile)
            assertNotNull(config)
            assertEquals("dataset.zip", config.name)
            assertEquals("https://example.com/dataset.zip", config.link)
            assertEquals("sha256:abc123", config.hash)
            assertEquals(listOf("**/*.md", "docs/**"), config.excludeArchiveEntries)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `fallback to legacy 3-line download config`() {
        setLogPath()
        val tempDir = createTempDirectory(prefix = "addpreset-legacy-").toFile()
        try {
            val configFile = File(tempDir, "legacy.download")
            configFile.writeText(
                """
                archive.zip
                https://example.com/archive.zip
                sha256:deadbeef
                """.trimIndent()
            )

            val config = parseDownloadConfig(configFile)
            assertNotNull(config)
            assertEquals("archive.zip", config.name)
            assertEquals("https://example.com/archive.zip", config.link)
            assertEquals("sha256:deadbeef", config.hash)
            assertTrue(config.excludeArchiveEntries.isEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `repack zip excludes matched entries`() {
        setLogPath()
        val tempDir = createTempDirectory(prefix = "addpreset-repack-").toFile()
        try {
            val zipFile = File(tempDir, "bundle.zip")
            writeZip(
                zipFile,
                mapOf(
                    "keep.txt" to "keep",
                    "docs/readme.md" to "remove-md",
                    "nested/secret.txt" to "remove-folder",
                    "images/logo.png" to "keep-image",
                )
            )

            repackArchiveWithExclusionsAndInclusionsInPlace(
                zipFile,
                listOf("**/*.md", "nested/**"),
                emptyList()
            )

            ZipFile(zipFile).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toSet()
                assertTrue("keep.txt" in names)
                assertTrue("images/logo.png" in names)
                assertFalse("docs/readme.md" in names)
                assertFalse("nested/secret.txt" in names)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `repack zip with includeArchiveEntries only keeps included files after exclude`() {
        setLogPath()
        val tempDir = createTempDirectory(prefix = "addpreset-include-").toFile()
        try {
            val zipFile = File(tempDir, "bundle.zip")
            writeZip(
                zipFile,
                mapOf(
                    "keep.txt" to "keep",
                    "docs/readme.md" to "remove-md",
                    "nested/secret.txt" to "remove-folder",
                    "images/logo.png" to "keep-image",
                    "images/other.jpg" to "other-image",
                )
            )

            val exclude = listOf("**/*.md", "nested/**")
            val include = listOf("keep.txt", "images/logo.png")
            repackArchiveWithExclusionsAndInclusionsInPlace(
                zipFile,
                exclude,
                include
            )

            ZipFile(zipFile).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toSet()
                assertTrue("keep.txt" in names)
                assertTrue("images/logo.png" in names)
                assertFalse("docs/readme.md" in names)
                assertFalse("nested/secret.txt" in names)
                assertFalse("images/other.jpg" in names)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `download yaml config end to end repacks archive before returning`() = runTest {
        setLogPath()
        val tempDir = createTempDirectory(prefix = "addpreset-e2e-").toFile()
        val sourceZip = File(tempDir, "source.zip")
        writeZip(
            sourceZip,
            mapOf(
                "keep.txt" to "keep",
                "docs/readme.md" to "remove",
                "nested/secret.txt" to "remove-folder",
            )
        )
        val hash = sha256File(sourceZip)
        val server = HttpServer.create(InetSocketAddress(0), 0)
        try {
            server.createContext("/bundle.zip") { exchange ->
                val body = sourceZip.readBytes()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { output ->
                    output.write(body)
                }
            }
            server.start()

            val configFile = File(tempDir, "bundle.download.yaml")
            configFile.writeText(
                """
                name: bundle.zip
                link: http://127.0.0.1:${server.address.port}/bundle.zip
                hash: sha256:$hash
                excludeArchiveEntries:
                  - "**/*.md"
                  - "nested/**"
                """.trimIndent()
            )

            HttpClient(OkHttp).use { client ->
                val downloaded = downloadPresetFileIfNeed(configFile.name, tempDir, client)
                assertNotNull(downloaded)
                assertEquals(File(tempDir, "download/bundle.zip").canonicalPath, downloaded.canonicalPath)
                assertTrue(downloaded.exists())

                ZipFile(downloaded).use { zip ->
                    val names = zip.entries().asSequence().map { it.name }.toSet()
                    assertTrue("keep.txt" in names)
                    assertFalse("docs/readme.md" in names)
                    assertFalse("nested/secret.txt" in names)
                }
            }
        } finally {
            server.stop(0)
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `recognize download config path variants`() {
        setLogPath()
        assertTrue(isDownloadConfigPath("a/b/xxx.download"))
        assertTrue(isDownloadConfigPath("a/b/xxx.download.yaml"))
        assertTrue(isDownloadConfigPath("a/b/xxx.download.yml"))
        assertFalse(isDownloadConfigPath("a/b/xxx.txt"))
    }

    private fun writeZip(target: File, contentByPath: Map<String, String>) {
        setLogPath()
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream().buffered()).use { out ->
            contentByPath.forEach { (path, content) ->
                out.putNextEntry(ZipEntry(path))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
    }
}
