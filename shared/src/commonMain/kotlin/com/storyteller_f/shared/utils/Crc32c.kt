/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared.utils

import kotlin.io.encoding.Base64

private const val CRC_BITS = 32
private const val OCTET_BITS = 8
private const val OCTET_MASK = 255
private const val CASTAGNOLI_POLYNOMIAL = -2_097_792_136

/** Combines finalized CRC32C values for ordered concatenation without reading either input again. */
fun combineCrc32c(first: String, second: String, secondSize: Long): String {
    require(secondSize >= 0) { "negative source size" }
    val combined = combineCrcValues(decodeCrc(first), decodeCrc(second), secondSize)
    return Base64.Default.encode(
        ByteArray(Int.SIZE_BYTES) { index ->
            (combined ushr (Int.SIZE_BYTES - index - 1) * OCTET_BITS).toByte()
        },
    )
}

private fun decodeCrc(encoded: String): Int {
    val bytes = Base64.Default.decode(encoded)
    require(bytes.size == Int.SIZE_BYTES && Base64.Default.encode(bytes) == encoded) { "invalid crc32c" }
    return bytes.fold(0) { value, byte -> value shl OCTET_BITS or (byte.toInt() and OCTET_MASK) }
}

private fun combineCrcValues(first: Int, second: Int, secondSize: Long): Int {
    if (secondSize == 0L) return first
    val oneBit =
        IntArray(CRC_BITS) { index ->
            if (index == 0) CASTAGNOLI_POLYNOMIAL else 1 shl index - 1
        }
    var operator = squareMatrix(squareMatrix(squareMatrix(oneBit)))
    var remaining = secondSize
    var crc = first
    while (remaining != 0L) {
        if (remaining and 1L != 0L) crc = applyMatrix(operator, crc)
        remaining = remaining ushr 1
        if (remaining != 0L) operator = squareMatrix(operator)
    }
    return crc xor second
}

private fun squareMatrix(matrix: IntArray): IntArray = IntArray(CRC_BITS) { applyMatrix(matrix, matrix[it]) }

private fun applyMatrix(matrix: IntArray, value: Int): Int =
    (0 until CRC_BITS).fold(0) { result, bit ->
        if (value ushr bit and 1 != 0) result xor matrix[bit] else result
    }
