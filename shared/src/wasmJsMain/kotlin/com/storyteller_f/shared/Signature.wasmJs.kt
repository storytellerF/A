package com.storyteller_f.shared

// wasmJs 平台暂未实现真实的 P256/Dilithium 加解密，这里仅提供与 expect 匹配的桩实现以通过编译。
private fun <T> notImplementedOnWasm(): Result<T> =
    Result.failure(NotImplementedError("crypto not implemented on wasmJs"))

actual suspend fun getDerPublicKeyFromPrivateKeyP256(pemPrivateKeyStr: String): Result<String> =
    notImplementedOnWasm()

actual fun loadCryptoLibIfNeed() = Unit

actual val AlgoDilithium: Algo = object : Algo {
    override suspend fun verify(derPublicKey: String, derSignature: String, data: String): Result<Boolean> =
        notImplementedOnWasm()

    override suspend fun signature(derPrivateKey: String, data: String): Result<String> =
        notImplementedOnWasm()

    override suspend fun getDerPrivateKey(pemPrivateKey: String): Result<String> =
        notImplementedOnWasm()

    override suspend fun getPemPrivateKeyFromDer(derPrivateKey: String): Result<String> =
        notImplementedOnWasm()

    override suspend fun getDerPublicKeyFromPem(pemPublicKeyStr: String): Result<String> =
        notImplementedOnWasm()

    override suspend fun getPemPublicKeyFromDer(derPublicKey: String): Result<String> =
        notImplementedOnWasm()

    override suspend fun getDerPublicKeyFromPrivateKey(pemPrivateKeyStr: String): Result<String> =
        notImplementedOnWasm()

    override suspend fun calcAddress(derPublicKeyStr: String): Result<String> =
        notImplementedOnWasm()

    override suspend fun generatePemKeyPair(): Result<Pair<String, String>> =
        notImplementedOnWasm()

    override val encryptionAlgo: EncryptionAlgo = object : EncryptionAlgo {
        override suspend fun kemEncrypt(derPublicKeyStr: String, aesKeyBytes: ByteArray): Result<ByteArray> =
            notImplementedOnWasm()

        override suspend fun kemDecrypt(derPrivateKeyStr: String, encrypted: ByteArray): Result<ByteArray> =
            notImplementedOnWasm()
    }
}
