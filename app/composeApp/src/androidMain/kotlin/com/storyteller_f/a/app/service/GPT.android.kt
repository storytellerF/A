package com.storyteller_f.a.app.service

import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Backend.GPU
import com.storyteller_f.shared.getAppContextRefValue
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import java.io.File

actual fun buildGPT(): GPT {
    return AndroidEdgeGPT()
}

actual fun getGPTModelDirectory(): Path {
    val context = getAppContextRefValue() ?: error("context is nil")
    return Path(context.filesDir.resolve("llm").absolutePath)
}

class AndroidEdgeGPT : GPT {
    override val supportList: List<String> = listOf("litertlm")

    override suspend fun generate(path: String, prompt: String): Result<Flow<GPTOutput>> {
        val application = getAppContextRefValue() ?:
            return Result.failure(UnsupportedOperationException())
        val file = File(path)
        if (!file.exists()) {
            return Result.failure(Exception("modal not exists"))
        }
        val engineConfig = EngineConfig(
            modelPath = path,
            backend = GPU(),
            cacheDir = application.cacheDir.path
        )
        val engine = Engine(engineConfig)
        return runCatching {
            withContext(Dispatchers.IO) {
                engine.initialize()
            }
            Napier.i(tag = "gpt") {
                "engine initialized"
            }
            val conversation = engine.createConversation()
            conversation.sendMessageAsync(prompt).map {
                Napier.d(tag = "gpt") {
                    "gpt output ${it.contents}"
                }
                GPTOutput(it.contents.contents.joinToString())
            }.onCompletion {
                engine.close()
                Napier.i(tag = "gpt") {
                    "engine closed"
                }
            }
        }
    }

    override fun models(scope: CoroutineScope): Flow<List<GPTModel>> {
        return observeModels(scope, getGPTModelDirectory(), supportList)
    }
}
