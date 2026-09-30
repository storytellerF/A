/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.cloud.filesystem

import com.storyteller_f.services.filesystem.api.CopyPack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalFileSystemObjectStorageServiceTest {
    @Test
    fun `object lifecycle uses independent storage models`() =
        runBlocking {
        val base = createTempDirectory("filesystem-lifecycle-")
        val source = Files.createTempFile("filesystem-upload-", ".txt").toFile()
        source.writeText("content")
        val service = LocalFileSystemObjectStorageService("https://example.invalid", base)
        assertEquals(
            "original.txt",
            service.upload("objects", listOf(UploadPack(source, "original.txt")))
                .getOrThrow().single().fullName,
        )
        val copied = service.copy("objects", listOf(CopyPack("original.txt", "copy.txt"))).getOrThrow().single()
        assertEquals("https://example.invalid/objects/objects/copy.txt", copied.url)
        assertEquals(
            setOf("original.txt", "copy.txt"),
            service.list("objects", "").getOrThrow()
                .map { it.fullName }.toSet(),
        )
        service.compose("objects", "combined.txt", listOf("original.txt", "copy.txt")).getOrThrow()
        service.getInputStream("objects", "combined.txt").getOrThrow().use {
            assertEquals("contentcontent", it.readBytes().decodeToString())
        }
        service.delete("objects", listOf("copy.txt")).getOrThrow()
        assertTrue(service.get("objects", listOf("copy.txt")).getOrThrow().isEmpty())
        service.clean("objects").getOrThrow()
        assertTrue(service.list("objects", "").getOrThrow().isEmpty())
    }

    @Test
    fun `storage operations propagate cancellation`() =
        runBlocking<Unit> {
        val service = LocalFileSystemObjectStorageService("https://example.invalid", createTempDirectory("filesystem-"))
        assertFailsWith<CancellationException> {
            service.useFileSystem<Unit> { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun `object paths cannot escape their bucket`() =
        runBlocking {
        val base = createTempDirectory("filesystem-storage-")
        val source = Files.createTempFile("filesystem-upload-", ".txt").toFile()
        source.writeText("private")
        val service = LocalFileSystemObjectStorageService("https://example.invalid", base)
        val escaped = base.resolve("escaped.txt")
        val pack =
            UploadPack(
                file = source,
                fullName = "../escaped.txt",
            )

        assertTrue(service.upload("files", listOf(pack)).isFailure)
        assertFalse(escaped.exists())
        assertTrue(service.getInputStream("files", "../escaped.txt").isFailure)
        assertTrue(service.delete("files", listOf("../escaped.txt")).isFailure)
        assertNull(service.getPathResponse(listOf("files", "..", "escaped.txt")))
    }
}
