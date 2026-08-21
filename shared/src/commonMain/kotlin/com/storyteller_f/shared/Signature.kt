/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared

import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.UserPubKeyInfo
import com.storyteller_f.shared.utils.cancellableRunCatching
import com.storyteller_f.shared.utils.mapResult
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256

fun finalData(data: String, salt: String = "a"): String = (data + salt).toCharArray().sorted().joinToString("")

interface Algo {
    /**
     * @param derPublicKey hex string
     * @param derSignature hex string
     * @param data the data to verify
     * @return true if the signature is valid
     */
    suspend fun verify(derPublicKey: String, derSignature: String, data: String): Result<Boolean>

    /**
     * @param derPrivateKey Der format private key
     * @param data the data to sign
     * @return hex string
     */
    suspend fun signature(derPrivateKey: String, data: String): Result<String>

    /**
     * @param pemPrivateKey PEM format private key
     * @return hex string
     */
    suspend fun getDerPrivateKey(pemPrivateKey: String): Result<String>

    /**
     * @param derPrivateKey hex string
     * @return PEM format private key
     */
    suspend fun getPemPrivateKeyFromDer(derPrivateKey: String): Result<String>

    /**
     * @return hex string
     */
    suspend fun getDerPublicKeyFromPem(pemPublicKeyStr: String): Result<String>

    /**
     * @param derPublicKey hex 编码的der 格式的publicKey
     * @return pem 格式的publicKey
     */
    suspend fun getPemPublicKeyFromDer(derPublicKey: String): Result<String>

    /**
     * @param pemPrivateKeyStr PEM format private key
     * @return hex string
     */
    suspend fun getDerPublicKeyFromPrivateKey(pemPrivateKeyStr: String): Result<String>

    /**
     * @param derPublicKeyStr hex string
     * @return base64 格式的地址
     */
    suspend fun calcAddress(derPublicKeyStr: String): Result<String>

    /**
     * @return pem string
     */
    suspend fun generatePemKeyPair(): Result<Pair<String, String>>

    val encryptionAlgo: EncryptionAlgo
}

interface EncryptionAlgo {
    /**
     * @param derPublicKeyStr hex string
     * @param aesKeyBytes the AES key to encrypt

     * @return the encrypted AES key
     */
    suspend fun kemEncrypt(derPublicKeyStr: String, aesKeyBytes: ByteArray): Result<ByteArray>

    /**
     * @param derPrivateKeyStr hex string
     * @param encrypted the encrypted AES key
     * @return the decrypted AES key
     */
    suspend fun kemDecrypt(derPrivateKeyStr: String, encrypted: ByteArray): Result<ByteArray>
}

interface Type1Algo : EncryptionAlgo

interface Type2Algo : EncryptionAlgo {
    suspend fun generateEncryptionPemKeyPair(): Result<Pair<String, String>>

    suspend fun getDerEncryptionPublicKeyFromPemPrivateKey(pemPrivateKeyStr: String): Result<String>

    suspend fun getDerEncryptionPrivateKeyFromPemPrivateKey(pemPrivateKeyStr: String): Result<String>

    suspend fun getPemEncryptionPrivateKeyFromDerPrivateKey(derPrivateKeyStr: String): Result<String>
}

object AlgoP256 : Algo {
    override val encryptionAlgo: EncryptionAlgo = createEncryptionAlgo()

    override suspend fun verify(derPublicKey: String, derSignature: String, data: String): Result<Boolean> =
        cancellableRunCatching {
            val publicKey =
                CryptographyProvider.Default.get(ECDSA).publicKeyDecoder(EC.Curve.P256)
                    .decodeFromByteArray(EC.PublicKey.Format.DER, derPublicKey.hexToByteArray())
            publicKey.signatureVerifier(SHA256, ECDSA.SignatureFormat.DER)
                .tryVerifySignature(data.encodeToByteArray(), derSignature.hexToByteArray())
        }

    override suspend fun signature(derPrivateKey: String, data: String): Result<String> =
        cancellableRunCatching {
        val privateKey =
            CryptographyProvider.Default.get(ECDSA).privateKeyDecoder(EC.Curve.P256)
                .decodeFromByteArray(EC.PrivateKey.Format.DER, derPrivateKey.hexToByteArray())
        val signature =
            privateKey.signatureGenerator(SHA256, ECDSA.SignatureFormat.DER)
                .generateSignature(data.encodeToByteArray())
        signature.toHexString()
    }

    override suspend fun getDerPrivateKey(pemPrivateKey: String): Result<String> = getDerPrivateKeyP256(pemPrivateKey)

    override suspend fun getPemPrivateKeyFromDer(derPrivateKey: String): Result<String> =
        cancellableRunCatching {
        CryptographyProvider.Default.get(ECDSA).privateKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(EC.PrivateKey.Format.DER, derPrivateKey.hexToByteArray())
            .encodeToByteArray(EC.PrivateKey.Format.PEM)
            .decodeToString()
    }

    override suspend fun getDerPublicKeyFromPrivateKey(pemPrivateKeyStr: String): Result<String> =
        getDerPublicKeyFromPrivateKeyP256(
            pemPrivateKeyStr,
        )

    override suspend fun calcAddress(derPublicKeyStr: String): Result<String> =
        calcAddressSHA256AndRipemd160(
        derPublicKeyStr,
    )

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    override suspend fun generatePemKeyPair(): Result<Pair<String, String>> =
        cancellableRunCatching {
        val keyPair =
            CryptographyProvider.Default.get(ECDSA).keyPairGenerator(EC.Curve.P256)
                .generateKey()
        val privatePem =
            keyPair.privateKey
                .encodeToByteArray(EC.PrivateKey.Format.PEM)
                .decodeToString()
        val publicPem =
            keyPair.publicKey
                .encodeToByteArray(EC.PublicKey.Format.PEM)
                .decodeToString()
        privatePem to publicPem
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    override suspend fun getDerPublicKeyFromPem(pemPublicKeyStr: String): Result<String> =
        cancellableRunCatching {
        CryptographyProvider.Default.get(ECDSA).publicKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(EC.PublicKey.Format.PEM, pemPublicKeyStr.encodeToByteArray())
            .encodeToByteArray(EC.PublicKey.Format.DER).toHexString()
    }

    override suspend fun getPemPublicKeyFromDer(derPublicKey: String): Result<String> =
        cancellableRunCatching {
        CryptographyProvider.Default.get(ECDSA).publicKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(EC.PublicKey.Format.DER, derPublicKey.hexToByteArray())
            .encodeToByteArray(EC.PublicKey.Format.PEM).decodeToString()
    }

    private fun createEncryptionAlgo(): EncryptionAlgo =
        object : Type1Algo {
        override suspend fun kemEncrypt(derPublicKeyStr: String, aesKeyBytes: ByteArray): Result<ByteArray> =
            cancellableRunCatching {
                val tempKeyPair =
                    CryptographyProvider.Default.get(
                        ECDH,
                    ).keyPairGenerator(EC.Curve.P256).generateKey()

                val publicKey =
                    CryptographyProvider.Default.get(ECDH).publicKeyDecoder(EC.Curve.P256)
                        .decodeFromByteArray(EC.PublicKey.Format.DER, derPublicKeyStr.hexToByteArray())

                val shared =
                    tempKeyPair.privateKey.sharedSecretGenerator()
                        .generateSharedSecretToByteArray(publicKey)

                val derivedKeys =
                    CryptographyProvider.Default.get(HKDF)
                        .secretDerivation(SHA256, 512.bits, null, "ecies".encodeToByteArray())
                        .deriveSecretToByteArray(shared)

                val aesKey = derivedKeys.copyOfRange(0, 32)
                val macKey = derivedKeys.copyOfRange(32, 64)

                val encrypted =
                    CryptographyProvider.Default.get(AES.GCM)
                        .keyDecoder()
                        .decodeFromByteArray(AES.Key.Format.RAW, aesKey)
                        .cipher()
                        .encrypt(aesKeyBytes)

                val concat = tempKeyPair.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW) + encrypted
                val signature =
                    CryptographyProvider.Default.get(HMAC).keyDecoder(SHA256)
                        .decodeFromByteArray(HMAC.Key.Format.RAW, macKey)
                        .signatureGenerator()
                        .generateSignature(concat)

                concat + signature
            }

        override suspend fun kemDecrypt(derPrivateKeyStr: String, encrypted: ByteArray): Result<ByteArray> =
            cancellableRunCatching {
                val publicKeyContent = encrypted.copyOfRange(0, 65)
                val publicKey =
                    CryptographyProvider.Default.get(ECDH).publicKeyDecoder(EC.Curve.P256)
                        .decodeFromByteArray(EC.PublicKey.Format.RAW, publicKeyContent)
                val shared =
                    CryptographyProvider.Default.get(ECDH).privateKeyDecoder(EC.Curve.P256)
                        .decodeFromByteArray(EC.PrivateKey.Format.DER, derPrivateKeyStr.hexToByteArray())
                        .sharedSecretGenerator().generateSharedSecretToByteArray(publicKey)

                val derivedKey =
                    CryptographyProvider.Default.get(HKDF)
                        .secretDerivation(SHA256, 512.bits, null, "ecies".encodeToByteArray())
                        .deriveSecretToByteArray(shared)

                val aesKey = derivedKey.copyOfRange(0, 32)
                val macKey = derivedKey.copyOfRange(32, 64)
                val noMacResult = encrypted.copyOfRange(0, encrypted.size - 32)
                val macResult = encrypted.copyOfRange(encrypted.size - 32, encrypted.size)
                if (!CryptographyProvider.Default.get(HMAC).keyDecoder(SHA256)
                        .decodeFromByteArray(HMAC.Key.Format.RAW, macKey)
                        .signatureVerifier().tryVerifySignature(noMacResult, macResult)
                ) {
                    throw IllegalStateException("hmac verify failed")
                }
                val encryptedContent = encrypted.copyOfRange(65, encrypted.size - 32)

                CryptographyProvider.Default.get(AES.GCM)
                    .keyDecoder()
                    .decodeFromByteArray(AES.Key.Format.RAW, aesKey)
                    .cipher()
                    .decrypt(encryptedContent)
            }
    }
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun encryptDataByAES(data: String): Result<Pair<ByteArray, ByteArray>> =
    AlgoP256.cancellableRunCatching {
    val key = CryptographyProvider.Default.get(AES.CBC).keyGenerator(256.bits).generateKey()
    val encodedAesKey = key.encodeToByteArray(AES.Key.Format.RAW)
    val encryptedData = key.cipher(true).encrypt(data.encodeToByteArray())
    encryptedData to encodedAesKey
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun encryptDataByAES(data: String, aesKey: ByteArray): Result<ByteArray> =
    AlgoP256.cancellableRunCatching {
    val key =
        CryptographyProvider.Default.get(AES.CBC).keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, aesKey)
    key.cipher(true).encrypt(data.encodeToByteArray())
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun decryptDataByAES(encrypted: ByteArray, aesKey: ByteArray): Result<String> =
    AlgoP256.cancellableRunCatching {
        val key =
            CryptographyProvider.Default.get(AES.CBC).keyDecoder()
                .decodeFromByteArray(AES.Key.Format.RAW, aesKey)
        val encryptedData = key.cipher(true).decrypt(encrypted)
        encryptedData.decodeToString()
    }

fun getAlgo(algo: AlgoType): Algo =
    when (algo) {
    AlgoType.P256 -> AlgoP256
    AlgoType.DILITHIUM -> AlgoDilithium
}

suspend fun <T> algoRunCatching(algo: AlgoType = AlgoType.P256, block: suspend Algo.() -> T): Result<T> =
    cancellableRunCatching {
        getAlgo(algo).block()
    }

expect suspend fun getDerPublicKeyFromPrivateKeyP256(pemPrivateKeyStr: String): Result<String>
expect suspend fun getDerPrivateKeyP256(pemPrivateKeyStr: String): Result<String>
expect suspend fun ripemd160Platform(data: ByteArray): ByteArray
expect fun loadCryptoLibIfNeed()

@OptIn(ExperimentalStdlibApi::class, dev.whyoleg.cryptography.DelicateCryptographyApi::class)
suspend fun calcAddressSHA256AndRipemd160(derPublicKeyStr: String): Result<String> =
    cancellableRunCatching {
    val decode = derPublicKeyStr.hexToByteArray()
    // 先计算SHA256
    val sha256Digest = CryptographyProvider.Default.get(SHA256).hasher().hash(decode)
    // 再计算RIPEMD160
    val ripemd160Digest = ripemd160Platform(sha256Digest)
    ripemd160Digest.toHexString()
}

expect val AlgoDilithium: Algo

fun String.replaceCrlf(): String = replace("\r\n", "\n")

suspend fun buildEncryptedTopicContent(input: String, keyData: List<UserPubKeyInfo>): TopicContent.Encrypted {
    val (encrypted, aes) = encryptDataByAES(input).getOrThrow()
    return TopicContent.Encrypted(
        encrypted.toHexString(),
        keyData.associate { keyInfo ->
            val algo = getAlgo(keyInfo.algo)
            val encryptedKey =
                (
                    if (keyInfo.algo == AlgoType.DILITHIUM) {
                        val key =
                            keyInfo.encryptionPublicKey
                                ?: throw IllegalArgumentException("Dilithium user ${keyInfo.id} missing encryption key")
                        algo.encryptionAlgo.kemEncrypt(key, aes)
                    } else {
                        algo.encryptionAlgo.kemEncrypt(keyInfo.pubKey, aes)
                    }
                    ).getOrThrow().toHexString()
            keyInfo.id to encryptedKey
        },
    )
}

/**
 * @param derPrivateKey hex string
 * @param encrypted the encrypted message
 * @param encryptedAesKey the encrypted AES key
 * @return the decrypted message
 */
suspend fun Algo.decryptMessage(
    derPrivateKey: String,
    encrypted: ByteArray,
    encryptedAesKey: ByteArray,
): Result<String> =
    encryptionAlgo.kemDecrypt(derPrivateKey, encryptedAesKey).mapResult {
    decryptDataByAES(encrypted, it)
}
