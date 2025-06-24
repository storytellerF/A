package com.storyteller_f.shared

import com.storyteller_f.shared.utils.mapResult
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.*
import kotlinx.serialization.Serializable

@Serializable
class SignUpPack(val pk: String, val sig: String)

@Serializable
class SignInPack(val ad: String, val sig: String)

fun finalData(data: String, salt: String = "a"): String {
    return (data + salt).toCharArray().sorted().joinToString("")
}

suspend fun generateECDSAPemPrivateKey(): Result<String> {
    return runCatching {
        val keyPair = CryptographyProvider.Default.get(ECDSA).keyPairGenerator(EC.Curve.P256).generateKey()
        keyPair.privateKey.encodeToByteArray(EC.PrivateKey.Format.PEM).decodeToString()
    }
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun verify(derPublicKeyStr: String, derSignature: String, data: String): Result<Boolean> {
    return runCatching {
        val publicKey = CryptographyProvider.Default.get(ECDSA).publicKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(EC.PublicKey.Format.DER, derPublicKeyStr.hexToByteArray())
        publicKey.signatureVerifier(SHA256, ECDSA.SignatureFormat.DER)
            .tryVerifySignature(data.encodeToByteArray(), derSignature.hexToByteArray())
    }
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun signature(pemPrivateKeyStr: String, data: String): Result<String> {
    return runCatching {
        val privateKey = CryptographyProvider.Default.get(ECDSA).privateKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(EC.PrivateKey.Format.PEM, pemPrivateKeyStr.encodeToByteArray())
        val signature =
            privateKey.signatureGenerator(SHA256, ECDSA.SignatureFormat.DER).generateSignature(data.encodeToByteArray())
        signature.toHexString()
    }
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun getDerPrivateKey(pemPrivateKeyStr: String): Result<String> {
    return runCatching {
        CryptographyProvider.Default.get(ECDSA).privateKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(EC.PrivateKey.Format.PEM, pemPrivateKeyStr.encodeToByteArray())
            .encodeToByteArray(EC.PrivateKey.Format.DER)
            .toHexString()
    }
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun getPemPrivateKeyFromDer(derPrivateKeyStr: String): Result<String> {
    return runCatching {
        CryptographyProvider.Default.get(ECDSA).privateKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(EC.PrivateKey.Format.DER, derPrivateKeyStr.hexToByteArray())
            .encodeToByteArray(EC.PrivateKey.Format.PEM)
            .decodeToString()
    }
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun encryptData(data: String): Result<Pair<ByteArray, ByteArray>> {
    return runCatching {
        val key = CryptographyProvider.Default.get(AES.CBC).keyGenerator(256.bits).generateKey()
        val encodedAesKey = key.encodeToByteArray(AES.Key.Format.RAW)
        val encryptedData = key.cipher(true).encrypt(data.encodeToByteArray())
        encryptedData to encodedAesKey
    }
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun decryptData(encrypted: ByteArray, aesKey: ByteArray): Result<String> {
    return runCatching {
        val key = CryptographyProvider.Default.get(AES.CBC).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, aesKey)
        val encryptedData = key.cipher(true).decrypt(encrypted)
        encryptedData.decodeToString()
    }
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun decryptMessage(derPrivateKeyStr: String, encrypted: ByteArray, encryptedAesKey: ByteArray): Result<String> {
    return eciesDecrypt(derPrivateKeyStr, encryptedAesKey).mapResult {
        decryptData(encrypted, it)
    }
}

expect suspend fun getDerPublicKeyFromPrivateKey(pemPrivateKeyStr: String): Result<String>
expect suspend fun calcAddress(derPublicKeyStr: String): Result<String>
expect fun loadCryptoLibIfNeed()

@OptIn(ExperimentalStdlibApi::class)
suspend fun eciesEncrypt(derPublicKeyStr: String, data: ByteArray): Result<ByteArray> {
    return runCatching {
        val tempKeyPair = CryptographyProvider.Default.get(ECDH).keyPairGenerator(EC.Curve.P256).generateKey()

        val publicKey = CryptographyProvider.Default.get(ECDH).publicKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(EC.PublicKey.Format.DER, derPublicKeyStr.hexToByteArray())

        val shared =
            tempKeyPair.privateKey.sharedSecretGenerator().generateSharedSecretToByteArray(publicKey)

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

        val concat = tempKeyPair.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW) + encrypted
        val signature =
            CryptographyProvider.Default.get(HMAC).keyDecoder(SHA256).decodeFromByteArray(HMAC.Key.Format.RAW, macKey)
                .signatureGenerator()
                .generateSignature(concat)

        concat + signature
    }
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun eciesDecrypt(derPrivateKeyStr: String, encrypted: ByteArray): Result<ByteArray> {
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
        if (!CryptographyProvider.Default.get(HMAC).keyDecoder(SHA256).decodeFromByteArray(HMAC.Key.Format.RAW, macKey)
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
