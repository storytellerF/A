package com.storyteller_f.shared

import com.storyteller_f.crypto_jvm.CryptoJvm

actual suspend fun getDerPublicKeyFromPrivateKey(pemPrivateKeyStr: String): String {
    return CryptoJvm.getDerPublicKeyFromPrivateKey(pemPrivateKeyStr)
}

actual suspend fun calcAddress(derPublicKeyStr: String): String {
    return CryptoJvm.calcAddress(derPublicKeyStr)
}

actual suspend fun encrypt(data: String): Pair<ByteArray, ByteArray> {
    return CryptoJvm.encrypt(data)
}

actual suspend fun decrypt(derPrivateKeyStr: String, encrypted: ByteArray, encryptedAesKey: ByteArray): String {
    return CryptoJvm.decrypt(derPrivateKeyStr, encrypted, encryptedAesKey)
}

actual suspend fun encryptAesKey(derPublicKeyStr: String, aesKey: ByteArray): ByteArray {
    return CryptoJvm.encryptAesKey(derPublicKeyStr, aesKey)
}