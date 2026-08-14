/*
 * This is a private project. All rights reserved.
 */
package com.storytellerf.a.cloud.worker.llm

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.storyteller_f.shared.model.LlmConfig
import com.storyteller_f.shared.model.LlmProvider
import io.github.aakira.napier.Napier

private const val OPENAI_CONTEXT_LENGTH = 128_000L
private const val OPENAI_MAX_OUTPUT_TOKENS = 4_096L
private const val ANTHROPIC_CONTEXT_LENGTH = 200_000L
private const val ANTHROPIC_MAX_OUTPUT_TOKENS = 4_096L
private const val GOOGLE_CONTEXT_LENGTH = 1_000_000L
private const val GOOGLE_MAX_OUTPUT_TOKENS = 8_192L
private const val OLLAMA_CONTEXT_LENGTH = 8_192L
private const val OLLAMA_MAX_OUTPUT_TOKENS = 4_096L

private data class ModelLimits(val provider: LLMProvider, val contextLength: Long, val maxOutputTokens: Long)

private val customModelLimits =
    mapOf(
        LlmProvider.OPENAI to ModelLimits(LLMProvider.OpenAI, OPENAI_CONTEXT_LENGTH, OPENAI_MAX_OUTPUT_TOKENS),
        LlmProvider.OPENAI_COMPATIBLE to
            ModelLimits(LLMProvider.OpenAI, OPENAI_CONTEXT_LENGTH, OPENAI_MAX_OUTPUT_TOKENS),
        LlmProvider.ANTHROPIC to
            ModelLimits(LLMProvider.Anthropic, ANTHROPIC_CONTEXT_LENGTH, ANTHROPIC_MAX_OUTPUT_TOKENS),
        LlmProvider.GOOGLE to ModelLimits(LLMProvider.Google, GOOGLE_CONTEXT_LENGTH, GOOGLE_MAX_OUTPUT_TOKENS),
        LlmProvider.OLLAMA to ModelLimits(LLMProvider.Ollama, OLLAMA_CONTEXT_LENGTH, OLLAMA_MAX_OUTPUT_TOKENS),
    )

private fun createCustomModel(provider: LlmProvider, modelName: String): LLModel {
    val limits = checkNotNull(customModelLimits[provider]) { "LiteRT does not use a Koog model" }
    return LLModel(
        provider = limits.provider,
        id = modelName,
        capabilities =
        listOf(
            ai.koog.prompt.llm.LLMCapability.Temperature,
            ai.koog.prompt.llm.LLMCapability.Tools,
            ai.koog.prompt.llm.LLMCapability.Completion,
        ),
        contextLength = limits.contextLength,
        maxOutputTokens = limits.maxOutputTokens,
    )
}

/**
 * Factory for creating LLM clients based on configuration.
 * Uses koog's LLMClient interface directly for simple text generation.
 *
 * Note: LITERT_LLM provider is not handled here as it uses the litertlm library directly.
 */
object KoogClientFactory {
    /**
     * Creates an LLM client for the given configuration.
     * Returns null for LITERT_LLM provider (handled separately).
     */
    fun createClient(config: LlmConfig): LLMClient? {
        val client =
            when (config.provider) {
                LlmProvider.OPENAI -> createOpenAiClient(config)
                LlmProvider.ANTHROPIC -> createAnthropicClient(config)
                LlmProvider.GOOGLE -> createGoogleClient(config)
                LlmProvider.OLLAMA -> createOllamaClient(config)
                LlmProvider.OPENAI_COMPATIBLE -> createOpenAICompatibleClient(config)
                LlmProvider.LITERT_LLM -> null
            }
        return client
    }

    /**
     * Resolves the model for the given configuration.
     */
    fun resolveModel(config: LlmConfig): LLModel? {
        val modelName = config.model
        return when (config.provider) {
            LlmProvider.OPENAI ->
                modelName?.let { createCustomModel(config.provider, it) } ?: resolveOpenAIModel("gpt-4o")

            LlmProvider.ANTHROPIC ->
                modelName?.let { createCustomModel(config.provider, it) }
                    ?: resolveAnthropicModel("claude-sonnet-4-20250514")

            LlmProvider.GOOGLE ->
                modelName?.let { createCustomModel(config.provider, it) } ?: resolveGoogleModel("gemini-2.0-flash")

            LlmProvider.OLLAMA ->
                modelName?.let { createCustomModel(config.provider, it) } ?: resolveOllamaModel("llama3")

            LlmProvider.OPENAI_COMPATIBLE ->
                modelName?.let { createCustomModel(config.provider, it) }
                    ?: resolveOpenAIModel("gpt-3.5-turbo")

            LlmProvider.LITERT_LLM -> null
        }
    }

    private fun createOpenAiClient(config: LlmConfig): LLMClient {
        val apiKey = config.apiKey ?: error("API key required for OpenAI")
        val settings =
            OpenAIClientSettings(
                baseUrl = config.baseUrl ?: "https://api.openai.com/v1",
            )
        val httpClientFactory = KtorKoogHttpClient.Factory()

        Napier.i(tag = "koog") {
            "Creating OpenAI client with model: ${config.model ?: "gpt-4o"}"
        }

        return OpenAILLMClient(apiKey, settings, httpClientFactory)
    }

    private fun createAnthropicClient(config: LlmConfig): LLMClient {
        val apiKey = config.apiKey ?: error("API key required for Anthropic")
        val settings =
            AnthropicClientSettings(
                baseUrl = config.baseUrl ?: "https://api.anthropic.com",
            )
        val httpClientFactory = KtorKoogHttpClient.Factory()

        Napier.i(tag = "koog") {
            "Creating Anthropic client with model: ${config.model ?: "claude-sonnet-4-20250514"}"
        }

        return AnthropicLLMClient(apiKey, settings, httpClientFactory)
    }

    private fun createGoogleClient(config: LlmConfig): LLMClient {
        val apiKey = config.apiKey ?: error("API key required for Google")
        val settings =
            OpenAIClientSettings(
                baseUrl =
                config.baseUrl
                    ?: "https://generativelanguage.googleapis.com/v1beta/openai",
            )
        val httpClientFactory = KtorKoogHttpClient.Factory()

        Napier.i(tag = "koog") {
            "Creating Google client with model: ${config.model ?: "gemini-2.0-flash"}"
        }

        return OpenAILLMClient(apiKey, settings, httpClientFactory)
    }

    private fun createOllamaClient(config: LlmConfig): LLMClient {
        val baseUrl = config.baseUrl ?: "http://localhost:11434"
        val httpClientFactory = KtorKoogHttpClient.Factory()

        Napier.i(tag = "koog") {
            "Creating Ollama client at $baseUrl with model: ${config.model ?: "llama3"}"
        }

        return ai.koog.prompt.executor.ollama.client.OllamaClient(
            httpClientFactory = httpClientFactory,
            baseUrl = baseUrl,
        )
    }

    private fun createOpenAICompatibleClient(config: LlmConfig): LLMClient {
        val baseUrl =
            config.baseUrl
                ?: error("Base URL required for OpenAI-compatible provider")
        val apiKey = config.apiKey ?: "no-key"

        Napier.i(tag = "koog") {
            "Creating OpenAI-compatible client at $baseUrl with model: ${config.model ?: "gpt-3.5-turbo"}"
        }

        // Use custom OpenAI-compatible client for OpenRouter and similar providers
        // This bypasses koog's parameter determination logic for non-standard model names
        return createOpenAICompatibleClient(
            apiKey = apiKey,
            baseUrl = baseUrl,
            temperature = config.temperature,
            maxTokens = config.maxTokens,
        )
    }

    private fun resolveOpenAIModel(modelName: String): LLModel {
        val model =
            when (modelName.lowercase()) {
                "gpt-4o", "gpt-4" ->
                    ai.koog.prompt.executor.clients.openai.OpenAIModels.Chat.GPT4o

                "gpt-4o-mini" ->
                    ai.koog.prompt.executor.clients.openai.OpenAIModels.Chat.GPT4oMini

                "gpt-4-turbo" ->
                    ai.koog.prompt.executor.clients.openai.OpenAIModels.Chat.GPT4_1

                "gpt-3.5-turbo" ->
                    ai.koog.prompt.executor.clients.openai.OpenAIModels.Chat.GPT4oMini

                "o1" ->
                    ai.koog.prompt.executor.clients.openai.OpenAIModels.Chat.O1

                "o3" ->
                    ai.koog.prompt.executor.clients.openai.OpenAIModels.Chat.O3

                "o3-mini" ->
                    ai.koog.prompt.executor.clients.openai.OpenAIModels.Chat.O3Mini

                "o4-mini" ->
                    ai.koog.prompt.executor.clients.openai.OpenAIModels.Chat.O4Mini

                else ->
                    LLModel(
                        provider = LLMProvider.OpenAI,
                        id = modelName,
                        capabilities =
                        listOf(
                            ai.koog.prompt.llm.LLMCapability.Temperature,
                            ai.koog.prompt.llm.LLMCapability.Tools,
                            ai.koog.prompt.llm.LLMCapability.Completion,
                        ),
                        contextLength = 128_000,
                        maxOutputTokens = 4_096,
                    )
            }
        return model
    }

    private fun resolveAnthropicModel(modelName: String): LLModel {
        val model =
            when (modelName.lowercase()) {
                "claude-3-sonnet", "claude-3-sonnet-20240229" ->
                    ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Sonnet_4

                "claude-3-opus", "claude-3-opus-20240229" ->
                    ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Opus_4

                "claude-3-haiku", "claude-3-haiku-20240307" ->
                    ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Haiku_4_5

                "claude-sonnet-4-0" ->
                    ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Sonnet_4

                "claude-sonnet-4-5" ->
                    ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Sonnet_4_5

                "claude-opus-4-0" ->
                    ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Opus_4

                else ->
                    LLModel(
                        provider = LLMProvider.Anthropic,
                        id = modelName,
                        capabilities =
                        listOf(
                            ai.koog.prompt.llm.LLMCapability.Temperature,
                            ai.koog.prompt.llm.LLMCapability.Tools,
                            ai.koog.prompt.llm.LLMCapability.Completion,
                        ),
                        contextLength = 200_000,
                        maxOutputTokens = 4_096,
                    )
            }
        return model
    }

    private fun resolveGoogleModel(modelName: String): LLModel {
        val model =
            LLModel(
                provider = LLMProvider.Google,
                id = modelName,
                capabilities =
                listOf(
                    ai.koog.prompt.llm.LLMCapability.Temperature,
                    ai.koog.prompt.llm.LLMCapability.Tools,
                    ai.koog.prompt.llm.LLMCapability.Completion,
                ),
                contextLength = 1_000_000,
                maxOutputTokens = 8_192,
            )
        return model
    }

    private fun resolveOllamaModel(modelName: String): LLModel {
        val model =
            LLModel(
                provider = LLMProvider.Ollama,
                id = modelName,
                capabilities =
                listOf(
                    ai.koog.prompt.llm.LLMCapability.Temperature,
                    ai.koog.prompt.llm.LLMCapability.Tools,
                    ai.koog.prompt.llm.LLMCapability.Completion,
                ),
                contextLength = 8_192,
                maxOutputTokens = 4_096,
            )
        return model
    }
}
