package com.storyteller_f.shared

actual suspend fun getDerPublicKeyFromPrivateKey(pemPrivateKeyStr: String): String {
    TODO("Not yet implemented")
}

actual suspend fun calcAddress(derPublicKeyStr: String): String {
    return createKeccakHash("keccak256").update(derPublicKeyStr).digest("hex")
}

actual fun loadIfNeed() = Unit
