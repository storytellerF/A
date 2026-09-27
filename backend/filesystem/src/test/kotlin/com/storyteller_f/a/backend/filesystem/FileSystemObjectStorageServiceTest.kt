/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.backend.filesystem

import com.storyteller_f.a.backend.core.service.UploadPack
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileSystemObjectStorageServiceTest {
    @Test
    fun `object paths cannot escape their bucket`() =
        runBlocking {
        val base = createTempDirectory("filesystem-storage-")
        val source = Files.createTempFile("filesystem-upload-", ".txt").toFile()
        source.writeText("private")
        val service = FileSystemObjectStorageService("https://example.invalid", base)
        val escaped = base.resolve("escaped.txt")
        val pack =
            UploadPack(
                file = source,
                name = "escaped.txt",
                size = source.length(),
                fullName = "../escaped.txt",
                sha256 = "unused",
            )

        assertTrue(service.upload("files", listOf(pack)).isFailure)
        assertFalse(escaped.exists())
        assertTrue(service.getInputStream("files", "../escaped.txt").isFailure)
        assertTrue(service.delete("files", listOf("../escaped.txt")).isFailure)
        assertNull(service.getPathResponse(listOf("files", "..", "escaped.txt")))
    }
}
