/*
 * This is a private project. All rights reserved.
 */
package com.storyteller_f.shared.model

import kotlinx.serialization.Serializable

enum class LlmProvider {
    OPENAI,
    ANTHROPIC,
    GOOGLE,
    OLLAMA,
    OPENAI_COMPATIBLE,  // OpenAI-compatible endpoint (OpenRouter, LiteLLM, etc.)
    LITERT_LLM,         // Google LiteRT LM (local model)
}

@Serializable
data class LlmConfig(
    /** LLM provider type */
    val provider: LlmProvider,
    /** API key for the provider */
    val apiKey: String? = null,
    /** Base URL for OpenAI-compatible endpoints (e.g., OpenRouter, LiteLLM) */
    val baseUrl: String? = null,
    /** Model name/identifier */
    val model: String? = null,
    /** Temperature for generation */
    val temperature: Double = 0.7,
    /** Maximum tokens to generate */
    val maxTokens: Int = 1024,
    /** Model file path for LITERT_LLM provider */
    val modelPath: String? = null,
) {
    companion object
}
