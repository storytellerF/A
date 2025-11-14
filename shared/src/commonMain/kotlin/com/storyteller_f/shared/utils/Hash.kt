package com.storyteller_f.shared.utils

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.MD5
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.io.Source
import kotlinx.io.bytestring.toHexString

@OptIn(DelicateCryptographyApi::class, ExperimentalStdlibApi::class)
fun md5(input: String): String {
    return CryptographyProvider.Default.get(MD5).hasher().hashBlocking(input.encodeToByteArray()).toHexString()
}

suspend fun sha256(source: Source): String {
    return CryptographyProvider.Default.get(SHA256).hasher().hash(source).toHexString()
}
