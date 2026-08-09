/*
 * This is a private project. All rights reserved.
 */
package com.storytellerf.a.cloud.worker.llm

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val TAG = "OpenAICompatibleClient"
private const val LOG_PREVIEW_LENGTH = 100

/**
 * Creates an LLMClient that directly calls OpenAI-compatible APIs.
 * This bypasses koog's parameter determination logic for non-standard model names.
 */
fun createOpenAICompatibleClient(
    apiKey: String,
    baseUrl: String,
): LLMClient {
    val httpClient = HttpClient(OkHttp)
    val jsonSerializer = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    return object : LLMClient() {
        @OptIn(ExperimentalTime::class)
        override suspend fun execute(
            prompt: ai.koog.prompt.Prompt,
            model: LLModel,
            tools: List<ai.koog.agents.core.tools.ToolDescriptor>,
        ): Message.Assistant {
            val systemPrompt = prompt.messages
                .filterIsInstance<Message.System>()
                .joinToString("\n") { it.textContent() }

            val userPrompt = prompt.messages
                .filterIsInstance<Message.User>()
                .joinToString("\n") { it.textContent() }

            Napier.d(tag = TAG) {
                "Calling OpenAI-compatible API with model: ${model.id}"
            }

            val request = ChatCompletionRequest(
                model = model.id,
                messages = buildList {
                    if (systemPrompt.isNotBlank()) {
                        add(ChatMessage(role = "system", content = systemPrompt))
                    }
                    add(ChatMessage(role = "user", content = userPrompt))
                },
                temperature = 0.7,
                maxTokens = 1024,
            )

            val requestBody = jsonSerializer.encodeToString(
                ChatCompletionRequest.serializer(),
                request,
            )

            val response = httpClient.post("$baseUrl/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                header("HTTP-Referer", "https://github.com/storyteller")
                header("X-Title", "StoryTeller Worker")
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.body<String>()

            val chatResponse = jsonSerializer.decodeFromString<ChatCompletionResponse>(response)

            val content = run {
                val message = chatResponse.choices.firstOrNull()?.message?.content
                checkNotNull(message) { "No response content from LLM" }
            }

            Napier.d(tag = TAG) {
                "LLM response: ${content.take(LOG_PREVIEW_LENGTH)}..."
            }

            return Message.Assistant(
                content = content,
                metaInfo = ResponseMetaInfo(
                    timestamp = Clock.System.now(),
                    modelId = model.id,
                ),
            )
        }

        override suspend fun moderate(
            prompt: ai.koog.prompt.Prompt,
            model: LLModel,
        ): ai.koog.prompt.dsl.ModerationResult {
            throw UnsupportedOperationException("Moderation not supported by OpenAI-compatible client")
        }

        override fun llmProvider(): ai.koog.prompt.llm.LLMProvider =
            ai.koog.prompt.llm.LLMProvider.OpenAI

        override fun close() {
            httpClient.close()
        }
    }
}

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    @SerialName("max_tokens")
    val maxTokens: Int = 1024,
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<Choice>,
)

@Serializable
data class Choice(
    val message: ChatMessage,
)
