/*
 * This is a private project. All rights reserved.
 */
package com.storytellerf.a.cloud.worker.llm

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.params.LLMParams
import com.storyteller_f.shared.model.LlmConfig
import com.storyteller_f.shared.model.LlmProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val OPENROUTER_MODEL_ID = "dots-studio/dots-3-note-preview:free"

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
                baseUrl = "https://llm.example.com/v1",
                model = "gpt-3.5-turbo",
            )

        val client = KoogClientFactory.createClient(config)
        assertNotNull(client)
    }

    @Test
    fun `OpenRouter client ignores configured base URL`() {
        val config =
            LlmConfig(
                provider = LlmProvider.OPENROUTER,
                apiKey = "test-key",
                baseUrl = " ",
                model = OPENROUTER_MODEL_ID,
            )

        val client = KoogClientFactory.createClient(config)

        assertEquals(LLMProvider.OpenRouter, client.llmProvider())
        client.close()
    }

    @Test
    fun `OpenRouter model supports structured output`() {
        val config =
            LlmConfig(
                provider = LlmProvider.OPENROUTER,
                apiKey = "test-key",
                model = OPENROUTER_MODEL_ID,
            )

        val model = KoogClientFactory.resolveModel(config)

        assertEquals(OPENROUTER_MODEL_ID, model.id)
        assertEquals(LLMProvider.OpenRouter, model.provider)
        assertTrue(model.supports(LLMCapability.Schema.JSON.Standard))
    }

    @Test
    fun `test explicit model identifier is preserved`() {
        val config =
            LlmConfig(
                provider = LlmProvider.OPENAI,
                apiKey = "test-key",
                model = "gpt-4",
            )

        val model = KoogClientFactory.resolveModel(config)
        assertNotNull(model)
        assertEquals("gpt-4", model.id)
    }

    @Test
    fun `LiteRT model uses local provider and filename`() {
        val config =
            LlmConfig(
                provider = LlmProvider.LITERT_LLM,
                modelPath = "models/safety.litertlm",
            )

        val model = KoogClientFactory.resolveModel(config)

        assertEquals(LITE_RT_LLM_PROVIDER, model.provider)
        assertEquals("safety.litertlm", model.id)
        assertEquals(LITE_RT_MODEL_CONTEXT_SIZE, model.contextLength)
        assertEquals(LITE_RT_MODEL_MAX_OUTPUT_TOKENS, model.maxOutputTokens)
    }

    @Test
    fun `LiteRT config without cache path is compatible`() {
        val config =
            Json.decodeFromString<LlmConfig>(
                """{"provider":"LITERT_LLM","modelPath":"models/safety.litertlm"}""",
            )

        assertNull(config.cachePath)
    }

    @Test
    fun `OpenAI format contains JSON schema`() {
        val schema =
            LLMParams.Schema.JSON.Standard(
                name = "decision",
                schema = buildJsonObject { put("type", "object") },
            )

        val responseFormat = assertNotNull(schema.toOpenAIResponseFormat())

        val responseType = assertNotNull(responseFormat["type"])
        val jsonSchema = assertNotNull(responseFormat["json_schema"]).jsonObject
        val schemaName = assertNotNull(jsonSchema["name"])
        val strict = assertNotNull(jsonSchema["strict"])
        val schemaObject = assertNotNull(jsonSchema["schema"]).jsonObject
        val schemaType = assertNotNull(schemaObject["type"])
        assertEquals("json_schema", responseType.jsonPrimitive.content)
        assertEquals("decision", schemaName.jsonPrimitive.content)
        assertEquals("true", strict.jsonPrimitive.content)
        assertEquals("object", schemaType.jsonPrimitive.content)
    }

    @Test
    fun `OpenAI format absent without schema`() {
        assertNull(null.toOpenAIResponseFormat())
    }
}
