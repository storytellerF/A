package com.storyteller_f.shared

import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.utils.mapResult
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.*
import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.serialization.Serializable

@Serializable
class SignUpPack(val pk: String, val sig: String)

@Serializable
class SignInPack(val ad: String, val sig: String)

fun finalData(data: String, salt: String = "a"): String {
    return (data + salt).toCharArray().sorted().joinToString("")
}

interface Algo {
    suspend fun verify(derPublicKey: String, derSignature: String, data: String): Result<Boolean>
    suspend fun signature(pemPrivateKey: String, data: String): Result<String>
    suspend fun getDerPrivateKey(pemPrivateKey: String): Result<String>
    suspend fun getPemPrivateKeyFromDer(derPrivateKey: String): Result<String>
    suspend fun decryptMessage(
        derPrivateKey: String,
        encrypted: ByteArray,
        encryptedAesKey: ByteArray
    ): Result<String>

    suspend fun eciesEncrypt(derPublicKeyStr: String, data: ByteArray): Result<ByteArray>
    suspend fun eciesDecrypt(derPrivateKeyStr: String, encrypted: ByteArray): Result<ByteArray>
    suspend fun getDerPublicKeyFromPrivateKey(pemPrivateKeyStr: String): Result<String>
    suspend fun calcAddress(derPublicKeyStr: String): Result<String>
    suspend fun generateECDSAPemPrivateKey(): Result<String>
}

object AlgoP256 : Algo {
    override suspend fun verify(
        derPublicKey: String,
        derSignature: String,
        data: String
    ): Result<Boolean> {
        return runCatching {
            val publicKey = CryptographyProvider.Default.get(ECDSA).publicKeyDecoder(EC.Curve.P256)
                .decodeFromByteArray(EC.PublicKey.Format.DER, derPublicKey.hexToByteArray())
            publicKey.signatureVerifier(SHA256, ECDSA.SignatureFormat.DER)
                .tryVerifySignature(data.encodeToByteArray(), derSignature.hexToByteArray())
        }
    }

    override suspend fun signature(pemPrivateKey: String, data: String): Result<String> {
        return runCatching {
            val privateKey =
                CryptographyProvider.Default.get(ECDSA).privateKeyDecoder(EC.Curve.P256)
                    .decodeFromByteArray(
                        EC.PrivateKey.Format.PEM,
                        pemPrivateKey.encodeToByteArray()
                    )
            val signature =
                privateKey.signatureGenerator(SHA256, ECDSA.SignatureFormat.DER)
                    .generateSignature(data.encodeToByteArray())
            signature.toHexString()
        }
    }

    override suspend fun getDerPrivateKey(pemPrivateKey: String): Result<String> {
        return runCatching {
            CryptographyProvider.Default.get(ECDSA).privateKeyDecoder(EC.Curve.P256)
                .decodeFromByteArray(EC.PrivateKey.Format.PEM, pemPrivateKey.encodeToByteArray())
                .encodeToByteArray(EC.PrivateKey.Format.DER)
                .toHexString()
        }
    }

    override suspend fun getPemPrivateKeyFromDer(derPrivateKey: String): Result<String> {
        return runCatching {
            CryptographyProvider.Default.get(ECDSA).privateKeyDecoder(EC.Curve.P256)
                .decodeFromByteArray(EC.PrivateKey.Format.DER, derPrivateKey.hexToByteArray())
                .encodeToByteArray(EC.PrivateKey.Format.PEM)
                .decodeToString()
        }
    }

    override suspend fun decryptMessage(
        derPrivateKey: String,
        encrypted: ByteArray,
        encryptedAesKey: ByteArray
    ): Result<String> {
        return eciesDecrypt(derPrivateKey, encryptedAesKey).mapResult {
            AlgoP256.runCatching {
                val key = CryptographyProvider.Default.get(AES.CBC).keyDecoder()
                    .decodeFromByteArray(AES.Key.Format.RAW, it)
                val encryptedData = key.cipher(true).decrypt(encrypted)
                encryptedData.decodeToString()
            }
        }
    }

    override suspend fun eciesEncrypt(
        derPublicKeyStr: String,
        data: ByteArray
    ): Result<ByteArray> {
        return runCatching {
            val tempKeyPair =
                CryptographyProvider.Default.get(ECDH).keyPairGenerator(EC.Curve.P256).generateKey()

            val publicKey = CryptographyProvider.Default.get(ECDH).publicKeyDecoder(EC.Curve.P256)
                .decodeFromByteArray(EC.PublicKey.Format.DER, derPublicKeyStr.hexToByteArray())

            val shared =
                tempKeyPair.privateKey.sharedSecretGenerator()
                    .generateSharedSecretToByteArray(publicKey)

            val derivedKeys = CryptographyProvider.Default.get(HKDF)
                .secretDerivation(SHA256, 512.bits, null, "ecies".encodeToByteArray())
                .deriveSecretToByteArray(shared)

            val aesKey = derivedKeys.copyOfRange(0, 32)
            val macKey = derivedKeys.copyOfRange(32, 64)

            val encrypted = CryptographyProvider.Default.get(AES.GCM)
                .keyDecoder()
                .decodeFromByteArray(AES.Key.Format.RAW, aesKey)
                .cipher()
                .encrypt(data)

            val concat =
                tempKeyPair.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW) + encrypted
            val signature =
                CryptographyProvider.Default.get(HMAC).keyDecoder(SHA256)
                    .decodeFromByteArray(HMAC.Key.Format.RAW, macKey)
                    .signatureGenerator()
                    .generateSignature(concat)

            concat + signature
        }
    }

    override suspend fun eciesDecrypt(
        derPrivateKeyStr: String,
        encrypted: ByteArray
    ): Result<ByteArray> {
        return runCatching {
            val publicKeyContent = encrypted.copyOfRange(0, 65)
            val publicKey = CryptographyProvider.Default.get(ECDH).publicKeyDecoder(EC.Curve.P256)
                .decodeFromByteArray(EC.PublicKey.Format.RAW, publicKeyContent)
            val shared = CryptographyProvider.Default.get(ECDH).privateKeyDecoder(EC.Curve.P256)
                .decodeFromByteArray(EC.PrivateKey.Format.DER, derPrivateKeyStr.hexToByteArray())
                .sharedSecretGenerator().generateSharedSecretToByteArray(publicKey)

            val derivedKey = CryptographyProvider.Default.get(HKDF)
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
                throw Exception("hmac verify failed")
            }
            val encryptedContent = encrypted.copyOfRange(65, encrypted.size - 32)

            CryptographyProvider.Default.get(AES.GCM)
                .keyDecoder()
                .decodeFromByteArray(AES.Key.Format.RAW, aesKey)
                .cipher()
                .decrypt(encryptedContent)
        }
    }

    override suspend fun getDerPublicKeyFromPrivateKey(pemPrivateKeyStr: String): Result<String> {
        return getDerPublicKeyFromPrivateKeyP256(pemPrivateKeyStr)
    }

    override suspend fun calcAddress(derPublicKeyStr: String): Result<String> {
        return calcAddressP256(derPublicKeyStr)
    }

    override suspend fun generateECDSAPemPrivateKey(): Result<String> {
        return runCatching {
            val keyPair = CryptographyProvider.Default.get(ECDSA).keyPairGenerator(EC.Curve.P256)
                .generateKey()
            keyPair.privateKey.encodeToByteArray(EC.PrivateKey.Format.PEM).decodeToString()
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun encryptDataByAES(data: String): Result<Pair<ByteArray, ByteArray>> {
    return AlgoP256.runCatching {
        val key = CryptographyProvider.Default.get(AES.CBC).keyGenerator(256.bits).generateKey()
        val encodedAesKey = key.encodeToByteArray(AES.Key.Format.RAW)
        val encryptedData = key.cipher(true).encrypt(data.encodeToByteArray())
        encryptedData to encodedAesKey
    }
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun decryptDataByAES(encrypted: ByteArray, aesKey: ByteArray): Result<String> {
    return AlgoP256.runCatching {
        val key = CryptographyProvider.Default.get(AES.CBC).keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, aesKey)
        val encryptedData = key.cipher(true).decrypt(encrypted)
        encryptedData.decodeToString()
    }
}

fun getAlgo(algo: AlgoType = AlgoType.P256): Algo {
    return when (algo) {
        AlgoType.P256 -> AlgoP256
        AlgoType.DILITHIUM -> AlgoDilithium
    }
}

suspend fun<T> algoRunCatching(algo: AlgoType = AlgoType.P256, block: suspend Algo.() -> T): Result<T> {
    return runCatching {
        getAlgo(algo).block()
    }
}

expect suspend fun getDerPublicKeyFromPrivateKeyP256(pemPrivateKeyStr: String): Result<String>
expect suspend fun calcAddressP256(derPublicKeyStr: String): Result<String>
expect fun loadCryptoLibIfNeed()

expect val AlgoDilithium: Algo

fun String.replaceCrlf(): String {
    return replace("\r\n", "\n")
}