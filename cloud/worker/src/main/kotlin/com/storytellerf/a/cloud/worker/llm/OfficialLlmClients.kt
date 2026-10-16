/*
 * This is a private project. All rights reserved.
 */
package com.storytellerf.a.cloud.worker.llm

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.bedrock.BedrockAPIMethod
import ai.koog.prompt.executor.clients.bedrock.BedrockClientSettings
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.clients.bedrock.StaticBearerTokenProvider
import ai.koog.prompt.executor.clients.dashscope.DashscopeClientSettings
import ai.koog.prompt.executor.clients.dashscope.DashscopeLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.mistralai.MistralAIClientSettings
import ai.koog.prompt.executor.clients.mistralai.MistralAILLMClient
import aws.sdk.kotlin.runtime.auth.credentials.DefaultChainCredentialsProvider
import com.storyteller_f.shared.model.LlmConfig

internal fun createDeepSeekClient(config: LlmConfig): LLMClient =
    DeepSeekLLMClient(
    apiKey = requireProviderKey(config),
    settings = DeepSeekClientSettings(baseUrl = config.baseUrl ?: DeepSeekClientSettings().baseUrl),
    httpClientFactory = KtorKoogHttpClient.Factory(),
)

internal fun createMistralClient(config: LlmConfig): LLMClient =
    MistralAILLMClient(
    apiKey = requireProviderKey(config),
    settings = MistralAIClientSettings(baseUrl = config.baseUrl ?: MistralAIClientSettings().baseUrl),
    httpClientFactory = KtorKoogHttpClient.Factory(),
)

internal fun createDashscopeClient(config: LlmConfig): LLMClient =
    DashscopeLLMClient(
    apiKey = requireProviderKey(config),
    settings = DashscopeClientSettings(baseUrl = config.baseUrl ?: DashscopeClientSettings().baseUrl),
    httpClientFactory = KtorKoogHttpClient.Factory(),
)

internal fun createBedrockClient(config: LlmConfig): LLMClient {
    val region = config.region ?: BedrockClientSettings().region
    require(region.isNotBlank()) { "Bedrock region must not be blank" }
    // Native AWS credentials stay in the SDK chain; apiKey optionally supplies a Bedrock bearer token.
    val identityProvider =
        config.apiKey?.let {
            require(it.isNotBlank()) { "Bedrock API key must not be blank" }
            StaticBearerTokenProvider(it)
        } ?: DefaultChainCredentialsProvider()
    return BedrockLLMClient(
        identityProvider = identityProvider,
        settings =
        BedrockClientSettings(
            region = region,
            endpointUrl = config.baseUrl,
            apiMethod = BedrockAPIMethod.Converse,
            maxRetries = 1,
        ),
    )
}

private fun requireProviderKey(config: LlmConfig): String =
    requireNotNull(config.apiKey?.takeIf { it.isNotBlank() }) { "API key required for ${config.provider}" }
