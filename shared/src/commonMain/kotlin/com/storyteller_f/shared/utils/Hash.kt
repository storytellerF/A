package com.storyteller_f.shared.utils

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.MD5

@OptIn(DelicateCryptographyApi::class, ExperimentalStdlibApi::class)
fun md5(input: String): String {
    return CryptographyProvider.Default.get(MD5).hasher().hashBlocking(input.encodeToByteArray()).toHexString()
}
