package com.storyteller_f.a.app.service

import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.storyteller_f.shared.getAppContextRefValue
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.io.files.Path
import java.io.File
import java.lang.reflect.Method

actual fun buildGPT(): GPT {
    return AndroidEdgeGPT()
}

actual fun getGPTModelDirectory(): Path {
    val context = getAppContextRefValue() ?: error("context is nil")
    return Path(context.filesDir.resolve("llm").absolutePath)
}

class AndroidEdgeGPT : GPT {
    override val supportList: List<String> by lazy {
        val (instance, method) = getLlamaBuildMethod()
        if (instance != null && method != null) {
            listOf("gguf", "task", "tflite")
        } else {
            listOf("task", "tflite")
        }
    }

    override fun generate(path: String, prompt: String, stopWord: String): Result<Flow<GPTOutput>> {
        return runCatching {
            val file = File(path)
            if (!file.exists()) {
                throw Exception("modal not exists")
            } else if (file.extension == "gguf") {
                val (instance, method) = getLlamaBuildMethod()
                if (instance == null || method == null) {
                    throw Exception("generate failed")
                } else {
                    val result = method.invoke(instance, path, prompt, stopWord)
                    (result as Flow<*>).filterIsInstance<GPTOutput>()
                }
            } else {
                buildMediaPipe(path, prompt)
            }
        }
    }

    private fun getLlamaBuildMethod(): Pair<Any?, Method?> {
        try {
            val clazz = Class.forName("com.storyteller_f.android_llama_cpp.LibraryKt")
            val instance = clazz.getConstructor().newInstance()
            val method = clazz.getMethod("buildLlampCpp")
            return Pair(instance, method)
        } catch (_: Exception) {
            return null to null
        }
    }

    private fun buildMediaPipe(path: String, prompt: String): Flow<GPTOutput> {
        val context = getAppContextRefValue() ?: throw Exception("context is nil")
        Napier.i(tag = "gpt") {
            "load $path done"
        }
        val taskOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTopK(64)
            .build()

        val llmInference = LlmInference.createFromOptions(context, taskOptions)
        return callbackFlow {
            val future = llmInference.generateResponseAsync(prompt) { partialResult, done ->
                if (partialResult.isNotBlank()) {
                    Napier.i(tag = "gpt") {
                        "send $partialResult"
                    }
                    trySend(GPTOutput(partialResult))
                }
                if (done) {
                    Napier.i(tag = "gpt") {
                        "close"
                    }
                    close()
                }
            }
            awaitClose {
                Napier.i(tag = "gpt") {
                    "release model ${future.isDone} ${future.isCancelled}"
                }
                if (!future.isDone) {
                    future.cancel(true)
                }
            }
        }
            .flowOn(Dispatchers.IO)
    }

    override fun models(scope: CoroutineScope): Flow<List<GPTModel>> {
        return observeModels(scope, getGPTModelDirectory(), supportList)
    }
}
