/*
 * This is a private project. All rights reserved.
 */
package com.storytellerf.a.cloud.worker.llm

import ai.koog.prompt.dsl.emptyPrompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import com.storyteller_f.shared.model.LlmConfig
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

private const val LOG_PREVIEW_LENGTH = 100

internal interface LlmService : AutoCloseable {
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

internal class KoogLlmService(
    private val client: LLMClient,
    private val model: LLModel,
    private val config: LlmConfig,
) : LlmService {
    override suspend fun generateResponse(prompt: String, systemPrompt: String?): String {
        val response =
            try {
                Napier.d(tag = "llm") {
                    "Generating response for prompt: ${prompt.take(LOG_PREVIEW_LENGTH)}..."
                }

                val promptObj =
                    if (systemPrompt != null) {
                        prompt(existing = emptyPrompt()) {
                            system(systemPrompt)
                            user(prompt)
                        }
                    } else {
                        prompt(existing = emptyPrompt()) {
                            user(prompt)
                        }
                    }

                val result =
                    client.execute(
                        promptObj.withParams(
                            LLMParams(
                                temperature = config.temperature,
                                maxTokens = config.maxTokens,
                            ),
                        ),
                        model,
                    )
                val text = result.textContent()

                Napier.d(tag = "llm") {
                    "Generated response: ${text.take(LOG_PREVIEW_LENGTH)}..."
                }

                text
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: IllegalStateException) {
                Napier.e(tag = "llm", throwable = exception) {
                    "LLM generation failed"
                }
                throw exception
            }
        return response
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
            val model =
                KoogClientFactory.resolveModel(config)
                    ?: error("Model resolution failed for ${config.provider}")
            return KoogLlmService(client, model, config)
        }
    }
}
