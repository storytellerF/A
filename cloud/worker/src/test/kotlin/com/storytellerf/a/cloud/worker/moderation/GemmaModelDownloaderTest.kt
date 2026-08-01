/*
 * This is a private project. All rights reserved.
 */
package com.storytellerf.a.cloud.worker.moderation

import com.storyteller_f.a.backend.core.MergedEnv
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class GemmaModelDownloaderTest {
    @Test
    fun `verified existing model does not require token`() {
        val homeDirectory = Files.createTempDirectory("worker-model-test")
        val modelPath = homeDirectory.resolve(GEMMA_MODEL_FILE_NAME)
        try {
            Files.newByteChannel(modelPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).close()

            assertEquals(
                modelPath,
                ensureGemmaModel(
                    env = MergedEnv(emptyList()),
                    homeDirectory = homeDirectory,
                    modelVerifier = { it == modelPath },
                ),
            )
        } finally {
            Files.deleteIfExists(modelPath)
            Files.deleteIfExists(homeDirectory)
        }
    }

    @Test
    fun `unverified model starts download without token`() {
        val homeDirectory = Files.createTempDirectory("worker-model-test")
        try {
            assertFailsWith<Exception> {
                ensureGemmaModel(
                    env = MergedEnv(emptyList()),
                    homeDirectory = homeDirectory,
                    modelVerifier = { false },
                    modelDownloader = { token, _ ->
                        check(token == null)
                        error("download started")
                    },
                )
            }
        } finally {
            Files.deleteIfExists(homeDirectory)
        }
    }
}
