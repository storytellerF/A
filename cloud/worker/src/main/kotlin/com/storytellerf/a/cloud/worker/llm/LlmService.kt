/*
 * This is a private project. All rights reserved.
 */
package com.storytellerf.a.cloud.worker.llm

import ai.koog.prompt.dsl.emptyPrompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import com.storyteller_f.shared.model.LlmConfig
import io.github.aakira.napier.Napier

private const val LOG_PREVIEW_LENGTH = 100

interface LlmService : AutoCloseable {
    /**
     * Generate a response from the LLM.
     *
     * @param prompt The user prompt to send to the LLM
     * @param systemPrompt Optional system prompt to set the context
     * @return The generated response
     */
    suspend fun generateResponse(prompt: String, systemPrompt: String? = null): String

    override fun close() {}
}

class KoogLlmService(
    private val client: LLMClient,
    private val model: LLModel,
) : LlmService {
    override suspend fun generateResponse(prompt: String, systemPrompt: String?): String {
        return try {
            Napier.d(tag = "llm") {
                "Generating response for prompt: ${prompt.take(LOG_PREVIEW_LENGTH)}..."
            }

            val promptObj = if (systemPrompt != null) {
                prompt(existing = emptyPrompt()) {
                    system(systemPrompt)
                    user(prompt)
                }
            } else {
                prompt(existing = emptyPrompt()) {
                    user(prompt)
                }
            }

            val result = client.execute(promptObj, model)
            val text = result.textContent()

            Napier.d(tag = "llm") {
                "Generated response: ${text.take(LOG_PREVIEW_LENGTH)}..."
            }

            text
        } catch (e: IllegalStateException) {
            Napier.e(tag = "llm", throwable = e) {
                "LLM generation failed"
            }
            throw e
        }
    }

    override fun close() {
        Napier.d(tag = "llm") {
            "Closing Koog LLM service"
        }
    }

    companion object {
        /**
         * Create a KoogLlmService from LLM configuration.
         * Returns null for LITERT_LLM provider (handled separately).
         */
        fun create(config: LlmConfig): KoogLlmService? {
            val client = KoogClientFactory.createClient(config) ?: return null
            val model = KoogClientFactory.resolveModel(config)
                ?: error("Model resolution failed for ${config.provider}")
            return KoogLlmService(client, model)
        }
    }
}
