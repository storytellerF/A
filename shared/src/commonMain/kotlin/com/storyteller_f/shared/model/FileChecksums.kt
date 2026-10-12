/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared.model

import kotlin.io.encoding.Base64

/** Raw-file SHA256 in hexadecimal and CRC32C in S3's big-endian Base64 representation. */
data class FileChecksums(val sha256: String, val crc32c: String) {
    init {
        require(sha256.matches(Regex("[a-f0-9]{64}"))) { "invalid file sha256" }
        val bytes = Base64.Default.decode(crc32c)
        require(bytes.size == Int.SIZE_BYTES && Base64.Default.encode(bytes) == crc32c) { "invalid file crc32c" }
    }
}
