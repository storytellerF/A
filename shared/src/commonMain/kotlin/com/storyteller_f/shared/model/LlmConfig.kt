package com.storyteller_f.shared.model

import kotlinx.serialization.Serializable

enum class LlmProvider {
    OPENAI,
    ANTHROPIC,
    GOOGLE,
    OLLAMA,
    LITELLM,    // OpenAI-compatible endpoint
}

@Serializable
data class LlmConfig(
    /** LLM provider type */
    val provider: LlmProvider,
    /** API key for the provider */
    val apiKey: String? = null,
    /** Base URL for OpenAI-compatible endpoints (e.g., LiteLLM) */
    val baseUrl: String? = null,
    /** Model name/identifier */
    val model: String? = null,
    /** Temperature for generation */
    val temperature: Double = 0.7,
    /** Maximum tokens to generate */
    val maxTokens: Int = 1024,
) {
    companion object
}
