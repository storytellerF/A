package com.storyteller_f.shared

actual suspend fun getDerPublicKeyFromPrivateKey(pemPrivateKeyStr: String): String {
    TODO("Not yet implemented")
}

actual suspend fun calcAddress(derPublicKeyStr: String): String {
    TODO("Not yet implemented")
}

actual suspend fun encrypt(data: String): Pair<ByteArray, ByteArray> {
    TODO("Not yet implemented")
}

actual suspend fun decrypt(derPrivateKeyStr: String, encrypted: ByteArray, encryptedAesKey: ByteArray): String {
    TODO("Not yet implemented")
}

actual suspend fun encryptAesKey(derPublicKeyStr: String, aesKey: ByteArray): ByteArray {
    TODO("Not yet implemented")
}
