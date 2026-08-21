/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared

import com.storyteller_f.shared.model.AlgoType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignatureTest {
    @Test
    fun algorithmsSignAndEncrypt() =
        runTest {
        loadCryptoLibIfNeed()
        listOf(AlgoP256, AlgoDilithium).forEach { algorithm ->
            val (privatePem, publicPem) = algorithm.generatePemKeyPair().getOrThrow()
            val privateKey = algorithm.getDerPrivateKey(privatePem).getOrThrow()
            val publicKey = algorithm.getDerPublicKeyFromPem(publicPem).getOrThrow()
            val signature = algorithm.signature(privateKey, "signed message").getOrThrow()

            assertTrue(algorithm.verify(publicKey, signature, "signed message").getOrThrow())

            val (encrypted, aesKey) = encryptDataByAES("encrypted message").getOrThrow()
            val (encryptionPrivateKey, encryptionPublicKey) =
                if (algorithm == AlgoDilithium) {
                    val encryptionAlgorithm = algorithm.encryptionAlgo as Type2Algo
                    val (privateEncryptionPem, publicEncryptionPem) =
                        encryptionAlgorithm.generateEncryptionPemKeyPair().getOrThrow()
                    algorithm.getDerPrivateKey(privateEncryptionPem).getOrThrow() to
                        algorithm.getDerPublicKeyFromPem(publicEncryptionPem).getOrThrow()
                } else {
                    privateKey to publicKey
                }
            val encryptedAesKey =
                algorithm.encryptionAlgo
                    .kemEncrypt(encryptionPublicKey, aesKey)
                    .getOrThrow()

            assertContentEquals(
                aesKey,
                algorithm.encryptionAlgo.kemDecrypt(encryptionPrivateKey, encryptedAesKey).getOrThrow(),
            )
            assertEquals(
                "encrypted message",
                algorithm.decryptMessage(encryptionPrivateKey, encrypted, encryptedAesKey).getOrThrow(),
            )
        }
    }

    @Test
    fun p256AddressIsCalculated() =
        runTest {
        loadCryptoLibIfNeed()
        val (_, publicPem) = getAlgo(AlgoType.P256).generatePemKeyPair().getOrThrow()
        val publicKey = getAlgo(AlgoType.P256).getDerPublicKeyFromPem(publicPem).getOrThrow()

        assertTrue(getAlgo(AlgoType.P256).calcAddress(publicKey).getOrThrow().isNotBlank())
    }
}
