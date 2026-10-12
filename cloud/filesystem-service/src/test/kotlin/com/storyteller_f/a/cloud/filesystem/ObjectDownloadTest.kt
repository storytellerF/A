/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.cloud.filesystem

import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class ObjectDownloadTest {
    @Test
    fun `direct downloads support byte ranges`() =
        testApplication {
        val base = createTempDirectory("object-download-")
        val source = Files.createTempFile("object-upload-", ".txt").toFile()
        source.writeText("abcdef")
        val storage = LocalFileSystemObjectStorageService("http://localhost", base)
        storage.upload(
            "custom",
            listOf(UploadPack(source, "test.txt")),
        ).getOrThrow()
        application { configureObjectDownloads(storage) }

        val url = storage.get("custom", listOf("test.txt")).getOrThrow().single().url
        assertEquals("http://localhost/objects/custom/test.txt", url)
        assertEquals("abcdef", client.get(url).bodyAsText())
        val head = client.head(url)
        assertEquals(HttpStatusCode.OK, head.status)
        assertEquals("6", head.headers[HttpHeaders.ContentLength])
        assertEquals("", head.bodyAsText())
        val partial = client.get(url) { header(HttpHeaders.Range, "bytes=1-3") }
        assertEquals(HttpStatusCode.PartialContent, partial.status)
        assertEquals("bcd", partial.bodyAsText())
        assertEquals("*", partial.headers[HttpHeaders.AccessControlAllowOrigin])
        assertEquals(HttpStatusCode.NotFound, client.get("/rpc").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/objects/custom/missing.txt").status)
    }
}
