/*
 * This is a private project. All rights reserved.
 */
package com.storytellerf.a.cloud.worker.moderation

import com.storyteller_f.a.backend.core.MergedEnv
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class GemmaModelDownloaderTest {
    @Test
    fun `complete existing model does not require token`() {
        val homeDirectory = Files.createTempDirectory("worker-model-test")
        val modelPath = homeDirectory.resolve(GEMMA_MODEL_FILE_NAME)
        try {
            Files.newByteChannel(modelPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
                channel.position(GEMMA_MODEL_SIZE - 1)
                channel.write(ByteBuffer.wrap(byteArrayOf(0)))
            }

            assertEquals(modelPath, ensureGemmaModel(MergedEnv(emptyList()), homeDirectory))
        } finally {
            Files.deleteIfExists(modelPath)
            Files.deleteIfExists(homeDirectory)
        }
    }

    @Test
    fun `missing model requires token`() {
        val homeDirectory = Files.createTempDirectory("worker-model-test")
        try {
            assertFailsWith<IllegalStateException> {
                ensureGemmaModel(MergedEnv(emptyList()), homeDirectory)
            }
        } finally {
            Files.deleteIfExists(homeDirectory)
        }
    }
}
