/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared

import kotlinx.coroutines.test.runTest
import org.bouncycastle.crypto.kems.MLKEMExtractor
import org.bouncycastle.crypto.kems.MLKEMGenerator
import org.bouncycastle.crypto.params.MLDSAParameters
import org.bouncycastle.crypto.params.MLDSAPrivateKeyParameters
import org.bouncycastle.crypto.params.MLDSAPublicKeyParameters
import org.bouncycastle.crypto.params.MLKEMParameters
import org.bouncycastle.crypto.params.MLKEMPrivateKeyParameters
import org.bouncycastle.crypto.params.MLKEMPublicKeyParameters
import org.bouncycastle.crypto.signers.MLDSASigner
import java.security.SecureRandom
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class PostQuantumCompatibilityTest : PlatformHeadlessTest() {
    @Test
    fun nobleSignatureVerifiesWithJca() =
        runTest {
        loadCryptoLibIfNeed()
        val vector = readNobleVector()
        assertTrue(
            AlgoDilithium.verify(
                vector.getProperty("signingPublic"),
                vector.getProperty("signature"),
                vector.getProperty("message"),
            ).getOrThrow(),
        )
        val privatePem = AlgoDilithium.getPemPrivateKeyFromDer(vector.getProperty("signingPrivate")).getOrThrow()
        assertEquals(
            vector.getProperty("signingPublic"),
            AlgoDilithium.getDerPublicKeyFromPrivateKey(privatePem).getOrThrow(),
        )
    }

    @Test
    fun nobleEncapsulationDecodesWithJca() =
        runTest {
        loadCryptoLibIfNeed()
        val vector = readNobleVector()
        val algorithm = AlgoDilithium.encryptionAlgo as Type2Algo
        assertContentEquals(
            vector.getProperty("aesKey").hexToByteArray(),
            algorithm.kemDecrypt(
                vector.getProperty("encryptionPrivate"),
                vector.getProperty("encryptedAesKey").hexToByteArray(),
            ).getOrThrow(),
        )
        val privatePem =
            algorithm.getPemEncryptionPrivateKeyFromDerPrivateKey(
                vector.getProperty("encryptionPrivate"),
            ).getOrThrow()
        assertEquals(
            vector.getProperty("encryptionPublic"),
            algorithm.getDerEncryptionPublicKeyFromPemPrivateKey(privatePem).getOrThrow(),
        )
    }

    @Test
    fun jcaSignaturesVerifyWithLegacyApi() =
        runTest {
        loadCryptoLibIfNeed()
        val (privatePem, publicPem) = AlgoDilithium.generatePemKeyPair().getOrThrow()
        val privateKey = AlgoDilithium.getDerPrivateKey(privatePem).getOrThrow()
        val publicKey = AlgoDilithium.getDerPublicKeyFromPem(publicPem).getOrThrow()
        assertEquals(publicKey, AlgoDilithium.getDerPublicKeyFromPrivateKey(privatePem).getOrThrow())
        val signature = AlgoDilithium.signature(privateKey, MESSAGE).getOrThrow().hexToByteArray()
        val verifier =
            MLDSASigner().apply {
                init(false, MLDSAPublicKeyParameters(MLDSAParameters.ml_dsa_65, publicKey.hexToByteArray()))
                updateMessage()
            }
        assertTrue(verifier.verifySignature(signature))
    }

    @Test
    fun legacySignaturesVerifyWithJca() =
        runTest {
        loadCryptoLibIfNeed()
        val (privatePem, publicPem) = AlgoDilithium.generatePemKeyPair().getOrThrow()
        val privateKey = AlgoDilithium.getDerPrivateKey(privatePem).getOrThrow()
        val publicKey = AlgoDilithium.getDerPublicKeyFromPem(publicPem).getOrThrow()
        val signature =
            MLDSASigner().apply {
                init(true, MLDSAPrivateKeyParameters(MLDSAParameters.ml_dsa_65, privateKey.hexToByteArray()))
                updateMessage()
            }.generateSignature().toHexString()
        assertTrue(AlgoDilithium.verify(publicKey, signature, MESSAGE).getOrThrow())
        assertFalse(AlgoDilithium.verify(publicKey, signature, "changed message").getOrThrow())
    }

    @Test
    fun jcaEncapsulationDecodesWithLegacyApi() =
        runTest {
        loadCryptoLibIfNeed()
        val algorithm = AlgoDilithium.encryptionAlgo as Type2Algo
        val (privatePem, publicPem) = algorithm.generateEncryptionPemKeyPair().getOrThrow()
        val privateKey = algorithm.getDerEncryptionPrivateKeyFromPemPrivateKey(privatePem).getOrThrow()
        val publicKey = AlgoDilithium.getDerPublicKeyFromPem(publicPem).getOrThrow()
        assertEquals(publicKey, algorithm.getDerEncryptionPublicKeyFromPemPrivateKey(privatePem).getOrThrow())
        val aesKey = ByteArray(32) { it.toByte() }
        val encrypted = algorithm.kemEncrypt(publicKey, aesKey).getOrThrow()
        assertEquals(1088 + aesKey.size, encrypted.size)
        val secret =
            MLKEMExtractor(
                MLKEMPrivateKeyParameters(MLKEMParameters.ml_kem_768, privateKey.hexToByteArray()),
            ).extractSecret(encrypted.copyOfRange(0, 1088))
        assertContentEquals(aesKey, xorBytes(encrypted.copyOfRange(1088, encrypted.size), secret))
    }

    @Test
    fun legacyEncapsulationDecodesWithJca() =
        runTest {
        loadCryptoLibIfNeed()
        val algorithm = AlgoDilithium.encryptionAlgo as Type2Algo
        val (privatePem, publicPem) = algorithm.generateEncryptionPemKeyPair().getOrThrow()
        val privateKey = algorithm.getDerEncryptionPrivateKeyFromPemPrivateKey(privatePem).getOrThrow()
        val publicKey = AlgoDilithium.getDerPublicKeyFromPem(publicPem).getOrThrow()
        val aesKey = ByteArray(32) { it.toByte() }
        val encapsulated =
            MLKEMGenerator(SecureRandom()).generateEncapsulated(
                MLKEMPublicKeyParameters(MLKEMParameters.ml_kem_768, publicKey.hexToByteArray()),
            )
        try {
            val encrypted = encapsulated.encapsulation + xorBytes(aesKey, encapsulated.secret)
            assertContentEquals(aesKey, algorithm.kemDecrypt(privateKey, encrypted).getOrThrow())
            assertTrue(algorithm.kemDecrypt(privateKey, encrypted.copyOf(32)).isFailure)
        } finally {
            encapsulated.destroy()
        }
    }

    private fun MLDSASigner.updateMessage() {
        val bytes = MESSAGE.encodeToByteArray()
        update(bytes, 0, bytes.size)
    }

    private fun xorBytes(first: ByteArray, second: ByteArray): ByteArray =
        ByteArray(first.size) { (first[it].toInt() xor second[it].toInt()).toByte() }

    private fun readNobleVector(): Properties =
        Properties().apply {
        checkNotNull(
            this@PostQuantumCompatibilityTest.javaClass.getResourceAsStream("/post-quantum-noble.properties"),
        ).buffered().use { load(it) }
    }

    private companion object {
        const val MESSAGE = "ML-DSA compatibility"
    }
}
