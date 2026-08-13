/*
 * This is a private project. All rights reserved.
 */
package com.storytellerf.a.cloud.worker.llm

import com.storyteller_f.shared.model.LlmConfig
import com.storyteller_f.shared.model.LlmProvider
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class KoogIntegrationTest {
    @BeforeTest
    fun setUpLogPath() {
        val logPath = File("build/test/logs").canonicalPath
        System.setProperty("LOG_PATH", logPath)
    }

    @Test
    fun `test OpenAI client creation`() {
        val config =
            LlmConfig(
                provider = LlmProvider.OPENAI,
                apiKey = "test-key",
                model = "gpt-4o",
            )

        val client = KoogClientFactory.createClient(config)
        assertNotNull(client)
    }

    @Test
    fun `test Anthropic client creation`() {
        val config =
            LlmConfig(
                provider = LlmProvider.ANTHROPIC,
                apiKey = "test-key",
                model = "claude-sonnet-4-0",
            )

        val client = KoogClientFactory.createClient(config)
        assertNotNull(client)
    }

    @Test
    fun `test Google client creation`() {
        val config =
            LlmConfig(
                provider = LlmProvider.GOOGLE,
                apiKey = "test-key",
                model = "gemini-2.0-flash",
            )

        val client = KoogClientFactory.createClient(config)
        assertNotNull(client)
    }

    @Test
    fun `test Ollama client creation`() {
        val config =
            LlmConfig(
                provider = LlmProvider.OLLAMA,
                baseUrl = "http://localhost:11434",
                model = "llama3",
            )

        val client = KoogClientFactory.createClient(config)
        assertNotNull(client)
    }

    @Test
    fun `test OpenAI-compatible client creation`() {
        val config =
            LlmConfig(
                provider = LlmProvider.OPENAI_COMPATIBLE,
                apiKey = "test-key",
                baseUrl = "https://openrouter.ai/api/v1",
                model = "gpt-3.5-turbo",
            )

        val client = KoogClientFactory.createClient(config)
        assertNotNull(client)
    }

    @Test
    fun `test model resolution`() {
        val config =
            LlmConfig(
                provider = LlmProvider.OPENAI,
                apiKey = "test-key",
                model = "gpt-4o",
            )

        val model = KoogClientFactory.resolveModel(config)
        assertNotNull(model)
        assertTrue(model.id.contains("gpt-4o", ignoreCase = true))
    }
}
