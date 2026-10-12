/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared

import com.storyteller_f.shared.model.UploadRecordInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.UploadRecordStatus
import com.storyteller_f.shared.utils.calculateFileChecksums
import com.storyteller_f.shared.utils.combineCrc32c
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FileChecksumsTest {
    @Test
    fun `cancelled hashing does not consume source bytes`() =
        runTest {
        val input = Buffer().apply { write("123456789".encodeToByteArray()) }
        coroutineScope {
            val task =
                async {
                    currentCoroutineContext().cancel()
                    calculateFileChecksums(input)
                }
            assertFailsWith<CancellationException> { task.await() }
        }
        assertEquals(9L, input.size)
    }

    @Test
    fun `upload records require both digest fields`() =
        runTest {
        val digests = calculateFileChecksums(Buffer())
        val record =
            UploadRecordInfo(
                1, LocalDateTime(2026, 1, 1, 0, 0), 1, ObjectType.USER,
                UploadRecordStatus.PENDING, 0, 0, "empty", 1, digests.sha256, digests.crc32c,
            )
        val encoded = commonJson.encodeToJsonElement(UploadRecordInfo.serializer(), record).jsonObject
        for (field in listOf("sha256", "crc32c")) {
            val incomplete = JsonObject(encoded.filterKeys { it != field })
            assertFailsWith<SerializationException> {
                commonJson.decodeFromJsonElement(UploadRecordInfo.serializer(), incomplete)
            }
        }
    }

    @Test
    fun `CRC32C concatenation matches the whole file`() =
        runTest {
        val bytes = "123456789".encodeToByteArray()
        for (offset in listOf(0, 1, 4, bytes.size)) {
            val first = calculateFileChecksums(Buffer().apply { write(bytes.copyOfRange(0, offset)) })
            val second = calculateFileChecksums(Buffer().apply { write(bytes.copyOfRange(offset, bytes.size)) })
            assertEquals("4waSgw==", combineCrc32c(first.crc32c, second.crc32c, (bytes.size - offset).toLong()))
        }
    }

    @Test
    fun `hashes match empty and Castagnoli vectors`() =
        runTest {
        val empty = calculateFileChecksums(Buffer())
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", empty.sha256)
        assertEquals("AAAAAA==", empty.crc32c)
        val known = calculateFileChecksums(Buffer().apply { write("123456789".encodeToByteArray()) })
        assertEquals("15e2b0d3c33891ebb0f1ef609ec419420c20e320ce94c65fbc8c3312448eb225", known.sha256)
        assertEquals("4waSgw==", known.crc32c)
    }

    @Test
    fun `both hashes consume each source byte once`() =
        runTest {
        val content = ByteArray(131_077) { it.toByte() }
        val expected = calculateFileChecksums(Buffer().apply { write(content) })
        val input = Buffer().apply { write(content) }
        var bytesRead = 0L
        val fragmented =
            object : RawSource {
                override fun readAtMostTo(sink: Buffer, byteCount: Long): Long =
                    input.readAtMostTo(sink, minOf(byteCount, 3)).also {
                        if (it > 0) bytesRead += it
                    }

                override fun close() = Unit
            }.buffered()
        assertEquals(expected, calculateFileChecksums(fragmented))
        assertEquals(content.size.toLong(), bytesRead)
    }
}
