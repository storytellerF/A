package com.storyteller_f.shared.utils

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.MD5

@OptIn(DelicateCryptographyApi::class)
internal actual fun md5Platform(input: ByteArray): ByteArray =
    CryptographyProvider.Default.get(MD5).hasher().hashBlocking(input)
