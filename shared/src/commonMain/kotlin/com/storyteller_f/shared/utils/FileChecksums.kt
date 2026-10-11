/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared.utils

import com.storyteller_f.shared.model.FileChecksums
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.Source
import kotlinx.io.bytestring.toHexString
import kotlinx.io.readByteArray
import kotlin.io.encoding.Base64

private const val CHECKSUM_BUFFER_SIZE = 65_536L
private const val CRC32C_POLYNOMIAL = -2_097_792_136
private const val BYTE_BITS = 8
private const val BYTE_MASK = 255
private const val CRC32C_BYTES = 4

private val crc32cTable =
    IntArray(BYTE_MASK + 1) { byte ->
        (0 until BYTE_BITS).fold(byte) { value, _ ->
            value ushr 1 xor if (value and 1 == 1) CRC32C_POLYNOMIAL else 0
        }
    }

/** Consumes the source once, feeding CRC32C alongside the provider's SHA256 reader. */
suspend fun calculateFileChecksums(source: Source): FileChecksums {
    val context = currentCoroutineContext()
    var crc = -1
    val hashingSource =
        object : RawSource {
            override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
                context.ensureActive()
                val buffer = Buffer()
                val count = source.readAtMostTo(buffer, minOf(byteCount, CHECKSUM_BUFFER_SIZE))
                if (count > 0) {
                    val bytes = buffer.readByteArray()
                    crc =
                        bytes.fold(crc) { value, byte ->
                            value ushr BYTE_BITS xor crc32cTable[value xor byte.toInt() and BYTE_MASK]
                        }
                    sink.write(bytes)
                }
                return count
            }

            override fun close() = Unit
        }
    val digest = CryptographyProvider.Default.get(SHA256).hasher().hash(hashingSource)
    val checksum = crc.inv()
    val encodedCrc =
        Base64.Default.encode(
            ByteArray(CRC32C_BYTES) { index ->
                (checksum ushr (CRC32C_BYTES - index - 1) * BYTE_BITS).toByte()
            },
        )
    return FileChecksums(digest.toHexString(), encodedCrc)
}
