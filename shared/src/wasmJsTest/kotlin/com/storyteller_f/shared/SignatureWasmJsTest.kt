package com.storyteller_f.shared

import com.storyteller_f.shared.utils.md5
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignatureWasmJsTest {

    @Test
    fun md5UsesWasmImplementation() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", md5("hello"))
    }

    @Test
    fun p256CanReadJvmGeneratedPrivateKey() = runTest {
        val privatePem = """
            -----BEGIN PRIVATE KEY-----
            MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCAeAx1tytf8DDzj7hXF
            3Yj/nWmeQ/PivDcglFiGezFIPQ==
            -----END PRIVATE KEY-----
        """.trimIndent()

        val privateDer = AlgoP256.getDerPrivateKey(privatePem).getOrThrow()
        val publicDer = AlgoP256.getDerPublicKeyFromPrivateKey(privatePem).getOrThrow()
        val signature = AlgoP256.signature(privateDer, "JVM generated key").getOrThrow()

        assertTrue(AlgoP256.verify(publicDer, signature, "JVM generated key").getOrThrow())
        assertTrue(AlgoP256.calcAddress(publicDer).getOrThrow().isNotBlank())
    }

    @Test
    fun p256CanSignEncryptAndDerivePublicKey() = runTest {
        val (privatePem, publicPem) = AlgoP256.generatePemKeyPair().getOrThrow()
        val privateDer = AlgoP256.getDerPrivateKey(privatePem).getOrThrow()
        val publicDer = AlgoP256.getDerPublicKeyFromPem(publicPem).getOrThrow()

        assertEquals(publicDer, getDerPublicKeyFromPrivateKeyP256(privatePem).getOrThrow())

        val signature = AlgoP256.signature(privateDer, "wasm signature").getOrThrow()
        assertTrue(AlgoP256.verify(publicDer, signature, "wasm signature").getOrThrow())

        val (encrypted, aesKey) = encryptDataByAES("wasm content").getOrThrow()
        assertEquals("wasm content", decryptDataByAES(encrypted, aesKey).getOrThrow())

        val encryptedAesKey = AlgoP256.encryptionAlgo.kemEncrypt(publicDer, aesKey).getOrThrow()
        assertContentEquals(
            aesKey,
            AlgoP256.encryptionAlgo.kemDecrypt(privateDer, encryptedAesKey).getOrThrow()
        )
    }

    @Test
    fun mlDsaAndMlKemCanSignAndEncrypt() = runTest {
        val (privatePem, publicPem) = AlgoDilithium.generatePemKeyPair().getOrThrow()
        val privateKey = AlgoDilithium.getDerPrivateKey(privatePem).getOrThrow()
        val publicKey = AlgoDilithium.getDerPublicKeyFromPem(publicPem).getOrThrow()
        val signature = AlgoDilithium.signature(privateKey, "wasm ML-DSA").getOrThrow()

        assertTrue(AlgoDilithium.verify(publicKey, signature, "wasm ML-DSA").getOrThrow())

        val encryptionAlgorithm = AlgoDilithium.encryptionAlgo as Type2Algo
        val (encryptionPrivatePem, encryptionPublicPem) =
            encryptionAlgorithm.generateEncryptionPemKeyPair().getOrThrow()
        val encryptionPrivateKey = AlgoDilithium.getDerPrivateKey(encryptionPrivatePem).getOrThrow()
        val encryptionPublicKey = AlgoDilithium.getDerPublicKeyFromPem(encryptionPublicPem).getOrThrow()
        val aesKey = ByteArray(32) { it.toByte() }
        val encryptedAesKey = encryptionAlgorithm.kemEncrypt(encryptionPublicKey, aesKey).getOrThrow()

        assertContentEquals(aesKey, encryptionAlgorithm.kemDecrypt(encryptionPrivateKey, encryptedAesKey).getOrThrow())
    }
}
