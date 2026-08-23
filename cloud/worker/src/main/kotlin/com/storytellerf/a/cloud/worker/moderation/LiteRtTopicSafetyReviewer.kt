/*
 * This is a private project. All rights reserved.
 */
package com.storytellerf.a.cloud.worker.moderation

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.nio.file.Path
import java.util.Locale

internal fun interface TopicSafetyReviewer : AutoCloseable {
    suspend fun isHarmful(content: String): Boolean

    override fun close() = Unit
}

internal class LiteRtTopicSafetyReviewer private constructor(private val engine: Engine) :
    TopicSafetyReviewer,
    AutoCloseable {
    override suspend fun isHarmful(content: String): Boolean =
        engine.createConversation(CONVERSATION_CONFIG).use { conversation ->
            val response = conversation.sendMessage(buildUntrustedTopicReviewPrompt(content))
            val decision =
                response.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString(separator = "") { it.text }
            parseSafetyDecision(decision)
        }

    override fun close() {
        engine.close()
    }

    companion object {
        fun create(modelPath: Path): LiteRtTopicSafetyReviewer {
            val engine =
                Engine(
                    EngineConfig(
                        modelPath = modelPath.toAbsolutePath().toString(),
                        backend = Backend.CPU(),
                        maxNumTokens = MODEL_CONTEXT_SIZE,
                        cacheDir = modelPath.parent.resolve(MODEL_CACHE_DIRECTORY).toString(),
                    ),
                )
            var isInitialized = false
            try {
                engine.initialize()
                isInitialized = true
            } finally {
                if (!isInitialized) {
                    engine.close()
                }
            }
            return LiteRtTopicSafetyReviewer(engine)
        }
    }
}

internal fun parseSafetyDecision(response: String): Boolean {
    val normalizedResponse = response.trim().uppercase(Locale.ROOT)
    return when (normalizedResponse) {
        SAFE_DECISION -> false
        UNSAFE_DECISION -> true
        else -> throw UnexpectedTopicSafetyDecisionException(response)
    }
}

internal class UnexpectedTopicSafetyDecisionException(val response: String) :
    IllegalStateException("Topic safety model did not return SAFE or UNSAFE, response: $response")

private const val MODEL_CONTEXT_SIZE = 4096
private const val MODEL_CACHE_DIRECTORY = ".litertlm-cache"
private const val SAFE_DECISION = "SAFE"
private const val UNSAFE_DECISION = "UNSAFE"
private val SYSTEM_INSTRUCTION = Contents.of(SAFETY_CLASSIFIER_SENTENCES.joinToString(separator = " "))
private val SAMPLER_CONFIG =
    SamplerConfig(
        topK = 1,
        topP = 1.0,
        temperature = 0.0,
        seed = 0,
    )
private val CONVERSATION_CONFIG =
    ConversationConfig(
        systemInstruction = SYSTEM_INSTRUCTION,
        samplerConfig = SAMPLER_CONFIG,
    )
