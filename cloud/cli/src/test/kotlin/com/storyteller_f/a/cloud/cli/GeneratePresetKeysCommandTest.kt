package com.storyteller_f.a.cloud.cli

import com.storyteller_f.a.backend.core.setLogPath
import com.storyteller_f.shared.Type2Algo
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.loadCryptoLibIfNeed
import com.storyteller_f.shared.model.AlgoType
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GeneratePresetKeysCommandTest {
    private val names = listOf(
        "font-provider",
        "robot1",
        "robot2",
        "system",
        "user1",
        "user2",
        "user3",
    )

    @Test
    fun `generate Dilithium preset private keys`() = runTest {
        setLogPath()
        loadCryptoLibIfNeed()
        val tempDir = createTempDirectory(prefix = "preset-keys-").toFile()
        try {
            val generated = generatePresetDilithiumKeys(tempDir, overwrite = false)

            assertEquals(names.size * 2, generated.size)
            val signAlgo = getAlgo(AlgoType.DILITHIUM)
            val encryptionAlgo = signAlgo.encryptionAlgo as Type2Algo
            names.forEach { name ->
                val privateKeyFile = File(tempDir, "p-$name")
                val encryptionPrivateKeyFile = File(tempDir, "ep-$name")

                assertTrue(privateKeyFile.exists())
                assertTrue(encryptionPrivateKeyFile.exists())
                assertTrue(privateKeyFile.readText().contains("BEGIN PRIVATE KEY"))
                assertTrue(encryptionPrivateKeyFile.readText().contains("BEGIN PRIVATE KEY"))
                assertTrue(
                    signAlgo.getDerPublicKeyFromPrivateKey(privateKeyFile.readText())
                        .getOrThrow()
                        .isNotBlank()
                )
                assertTrue(
                    encryptionAlgo.getDerEncryptionPublicKeyFromPemPrivateKey(
                        encryptionPrivateKeyFile.readText()
                    ).getOrThrow().isNotBlank()
                )
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `refuse to overwrite existing preset keys by default`() = runTest {
        setLogPath()
        loadCryptoLibIfNeed()
        val tempDir = createTempDirectory(prefix = "preset-keys-existing-").toFile()
        try {
            generatePresetDilithiumKeys(tempDir, overwrite = false)

            assertFailsWith<IllegalArgumentException> {
                generatePresetDilithiumKeys(tempDir, overwrite = false)
            }
            val overwritten = generatePresetDilithiumKeys(tempDir, overwrite = true)
            assertEquals(names.size * 2, overwritten.size)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
