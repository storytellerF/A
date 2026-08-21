/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared.utils

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.io.Source
import kotlinx.io.bytestring.toHexString

@OptIn(ExperimentalStdlibApi::class)
fun md5(input: String): String = md5Platform(input.encodeToByteArray()).toHexString()

internal expect fun md5Platform(input: ByteArray): ByteArray

suspend fun sha256(source: Source): String =
    CryptographyProvider.Default.get(
    SHA256,
).hasher().hash(source).toHexString()
