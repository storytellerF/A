@file:Suppress("UnusedParameter")

package com.storyteller_f.shared

import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
@JsModule("./mlCrypto.mjs")
private external object MlCrypto {
    fun p256PrivateKeyDer(privateKeyPem: String): String
    fun p256PublicKeyDer(privateKeyPem: String): String
    fun ripemd160Hex(data: String): String
    fun mlDsa65KeyPair(): String
    fun mlDsa65PublicKey(privateKey: String): String
    fun mlDsa65Sign(message: String, privateKey: String): String
    fun mlDsa65Verify(signature: String, message: String, publicKey: String): Boolean
    fun mlKem768KeyPair(): String
    fun mlKem768PublicKey(privateKey: String): String
    fun mlKem768Encapsulate(publicKey: String): String
    fun mlKem768Decapsulate(cipherText: String, privateKey: String): String
}

@Serializable
private data class MlKeyPair(val privateKey: String, val publicKey: String)

@Serializable
private data class MlKemEncapsulation(val cipherText: String, val sharedSecret: String)

@OptIn(ExperimentalEncodingApi::class)
private fun pemToRaw(pem: String, type: String): ByteArray = Base64.decode(
    pem.replace("-----BEGIN $type-----", "")
        .replace("-----END $type-----", "")
        .replace(Regex("\\s"), "")
)

@OptIn(ExperimentalEncodingApi::class)
private fun rawToPem(raw: ByteArray, type: String): String = buildString {
    appendLine("-----BEGIN $type-----")
    appendLine(Base64.encode(raw).chunked(64).joinToString("\n"))
    appendLine("-----END $type-----")
}

private infix fun ByteArray.xor(other: ByteArray): ByteArray {
    require(size == other.size) { "ML-KEM shared secret must match AES key size" }
    return ByteArray(size) { index -> (this[index].toInt() xor other[index].toInt()).toByte() }
}

actual suspend fun getDerPublicKeyFromPrivateKeyP256(pemPrivateKeyStr: String): Result<String> = runCatching {
    MlCrypto.p256PublicKeyDer(pemPrivateKeyStr)
}

actual suspend fun getDerPrivateKeyP256(pemPrivateKeyStr: String): Result<String> =
    runCatching { MlCrypto.p256PrivateKeyDer(pemPrivateKeyStr) }

actual suspend fun ripemd160Platform(data: ByteArray): ByteArray =
    MlCrypto.ripemd160Hex(data.toHexString()).hexToByteArray()

actual fun loadCryptoLibIfNeed() = Unit

actual val AlgoDilithium: Algo = object : Algo {
    override suspend fun verify(derPublicKey: String, derSignature: String, data: String): Result<Boolean> =
        runCatching { MlCrypto.mlDsa65Verify(derSignature, data, derPublicKey) }

    override suspend fun signature(derPrivateKey: String, data: String): Result<String> =
        runCatching { MlCrypto.mlDsa65Sign(data, derPrivateKey) }

    override suspend fun getDerPrivateKey(pemPrivateKey: String): Result<String> =
        runCatching { pemToRaw(pemPrivateKey, "PRIVATE KEY").toHexString() }

    override suspend fun getPemPrivateKeyFromDer(derPrivateKey: String): Result<String> =
        runCatching { rawToPem(derPrivateKey.hexToByteArray(), "PRIVATE KEY") }

    override suspend fun getDerPublicKeyFromPem(pemPublicKeyStr: String): Result<String> =
        runCatching { pemToRaw(pemPublicKeyStr, "PUBLIC KEY").toHexString() }

    override suspend fun getPemPublicKeyFromDer(derPublicKey: String): Result<String> =
        runCatching { rawToPem(derPublicKey.hexToByteArray(), "PUBLIC KEY") }

    override suspend fun getDerPublicKeyFromPrivateKey(pemPrivateKeyStr: String): Result<String> =
        runCatching { MlCrypto.mlDsa65PublicKey(getDerPrivateKey(pemPrivateKeyStr).getOrThrow()) }

    override suspend fun calcAddress(derPublicKeyStr: String): Result<String> =
        calcAddressSHA256AndRipemd160(derPublicKeyStr)

    override suspend fun generatePemKeyPair(): Result<Pair<String, String>> =
        runCatching {
            val keyPair = commonJson.decodeFromString<MlKeyPair>(MlCrypto.mlDsa65KeyPair())
            rawToPem(keyPair.privateKey.hexToByteArray(), "PRIVATE KEY") to
                rawToPem(keyPair.publicKey.hexToByteArray(), "PUBLIC KEY")
        }

    override val encryptionAlgo: EncryptionAlgo = object : Type2Algo {
        override suspend fun kemEncrypt(derPublicKeyStr: String, aesKeyBytes: ByteArray): Result<ByteArray> =
            runCatching {
                val encapsulation = commonJson.decodeFromString<MlKemEncapsulation>(
                    MlCrypto.mlKem768Encapsulate(derPublicKeyStr)
                )
                encapsulation.cipherText.hexToByteArray() +
                    (aesKeyBytes xor encapsulation.sharedSecret.hexToByteArray())
            }

        override suspend fun kemDecrypt(derPrivateKeyStr: String, encrypted: ByteArray): Result<ByteArray> =
            runCatching {
                require(encrypted.size > 32) { "invalid ML-KEM ciphertext" }
                val encryptedAesKey = encrypted.copyOfRange(encrypted.size - 32, encrypted.size)
                val cipherText = encrypted.copyOfRange(0, encrypted.size - 32)
                encryptedAesKey xor MlCrypto.mlKem768Decapsulate(
                    cipherText.toHexString(),
                    derPrivateKeyStr
                ).hexToByteArray()
            }

        override suspend fun generateEncryptionPemKeyPair(): Result<Pair<String, String>> = runCatching {
            val keyPair = commonJson.decodeFromString<MlKeyPair>(MlCrypto.mlKem768KeyPair())
            rawToPem(keyPair.privateKey.hexToByteArray(), "PRIVATE KEY") to
                rawToPem(keyPair.publicKey.hexToByteArray(), "PUBLIC KEY")
        }

        override suspend fun getDerEncryptionPublicKeyFromPemPrivateKey(
            pemPrivateKeyStr: String
        ): Result<String> = runCatching {
            MlCrypto.mlKem768PublicKey(pemToRaw(pemPrivateKeyStr, "PRIVATE KEY").toHexString())
        }

        override suspend fun getDerEncryptionPrivateKeyFromPemPrivateKey(
            pemPrivateKeyStr: String
        ): Result<String> = runCatching {
            pemToRaw(pemPrivateKeyStr, "PRIVATE KEY").toHexString()
        }

        override suspend fun getPemEncryptionPrivateKeyFromDerPrivateKey(
            derPrivateKeyStr: String
        ): Result<String> = runCatching {
            rawToPem(derPrivateKeyStr.hexToByteArray(), "PRIVATE KEY")
        }
    }
}
