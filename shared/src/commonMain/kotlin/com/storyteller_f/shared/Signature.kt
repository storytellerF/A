package com.storyteller_f.shared

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.asymmetric.EC
import dev.whyoleg.cryptography.algorithms.asymmetric.ECDSA
import dev.whyoleg.cryptography.algorithms.digest.SHA512
import dev.whyoleg.cryptography.algorithms.symmetric.HMAC
import kotlinx.serialization.Serializable

@Serializable
class SignUpPack(val pk: String, val sig: String)

@Serializable
class SignInPack(val ad: String, val sig: String)

fun finalData(data: String, salt: String = "a"): String {
    return (data + salt).toCharArray().sorted().joinToString("")
}

suspend fun generateKeyPair(): String {
    val keyPair = CryptographyProvider.Default.get(ECDSA).keyPairGenerator(EC.Curve.P256).generateKey()
    return keyPair.privateKey.encodeTo(EC.PrivateKey.Format.PEM).decodeToString()
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun verify(derPublicKeyStr: String, derSignature: String, data: String): Boolean {
    val publicKey = CryptographyProvider.Default.get(ECDSA).publicKeyDecoder(EC.Curve.P256)
        .decodeFrom(EC.PublicKey.Format.DER, derPublicKeyStr.hexToByteArray())
    return publicKey.signatureVerifier(SHA512, ECDSA.SignatureFormat.DER)
        .verifySignature(data.encodeToByteArray(), derSignature.hexToByteArray())
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun signature(pemPrivateKeyStr: String, data: String): String {
    val privateKey = CryptographyProvider.Default.get(ECDSA).privateKeyDecoder(EC.Curve.P256)
        .decodeFrom(EC.PrivateKey.Format.PEM, pemPrivateKeyStr.encodeToByteArray())
    val signature =
        privateKey.signatureGenerator(SHA512, ECDSA.SignatureFormat.DER).generateSignature(data.encodeToByteArray())
    return signature.toHexString()
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun getDerPublicKey(pemPublicKeyStr: String): String {
    return CryptographyProvider.Default.get(ECDSA).publicKeyDecoder(EC.Curve.P256)
        .decodeFrom(EC.PublicKey.Format.PEM, pemPublicKeyStr.encodeToByteArray()).encodeTo(EC.PublicKey.Format.DER)
        .toHexString()
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun getDerPrivateKey(pemPrivateKeyStr: String): String {
    return CryptographyProvider.Default.get(ECDSA).privateKeyDecoder(EC.Curve.P256)
        .decodeFrom(EC.PrivateKey.Format.PEM, pemPrivateKeyStr.encodeToByteArray()).encodeTo(EC.PrivateKey.Format.DER)
        .toHexString()
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun hmacSign(key: String, input: String): String {
    val key1 = CryptographyProvider.Default.get(HMAC).keyDecoder(SHA512).decodeFrom(
        HMAC.Key.Format.RAW,
        key.hexToByteArray()
    )
    return key1.signatureGenerator().generateSignature(input.encodeToByteArray()).toHexString()
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun hmacVerify(key: String, sig: String, input: String): Boolean {
    val key1 = CryptographyProvider.Default.get(HMAC).keyDecoder(SHA512).decodeFrom(
        HMAC.Key.Format.RAW,
        key.hexToByteArray()
    )
    return key1.signatureVerifier().verifySignature(input.encodeToByteArray(), sig.hexToByteArray())
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun newHmacSha512(): String {
    return CryptographyProvider.Default.get(HMAC).keyGenerator(SHA512).generateKey().encodeTo(HMAC.Key.Format.RAW)
        .toHexString()
}

expect suspend fun getDerPublicKeyFromPrivateKey(pemPrivateKeyStr: String): String
expect suspend fun calcAddress(derPublicKeyStr: String): String
expect suspend fun encrypt(data: String): Pair<ByteArray, ByteArray>
expect suspend fun encryptAesKey(derPublicKeyStr: String, aesKey: ByteArray): ByteArray
expect suspend fun decrypt(derPrivateKeyStr: String, encrypted: ByteArray, encryptedAesKey: ByteArray): String
