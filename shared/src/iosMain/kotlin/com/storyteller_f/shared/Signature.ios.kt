/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared

import com.storyteller_f.shared.utils.cancellableRunCatching
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.RIPEMD160

actual suspend fun getDerPublicKeyFromPrivateKeyP256(pemPrivateKeyStr: String): Result<String> =
    cancellableRunCatching {
        CryptographyProvider.Default.get(ECDSA).privateKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(EC.PrivateKey.Format.PEM, pemPrivateKeyStr.encodeToByteArray())
            .publicKey
            .encodeToByteArray(EC.PublicKey.Format.DER)
            .toHexString()
    }

actual suspend fun getDerPrivateKeyP256(pemPrivateKeyStr: String): Result<String> =
    cancellableRunCatching {
    CryptographyProvider.Default.get(ECDSA).privateKeyDecoder(EC.Curve.P256)
        .decodeFromByteArray(EC.PrivateKey.Format.PEM, pemPrivateKeyStr.encodeToByteArray())
        .encodeToByteArray(EC.PrivateKey.Format.DER)
        .toHexString()
}

@OptIn(dev.whyoleg.cryptography.DelicateCryptographyApi::class)
actual suspend fun ripemd160Platform(data: ByteArray): ByteArray =
    CryptographyProvider.Default.get(RIPEMD160).hasher().hash(data)

actual fun loadCryptoLibIfNeed() = Unit
