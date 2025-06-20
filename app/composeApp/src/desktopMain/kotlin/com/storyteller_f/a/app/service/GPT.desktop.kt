package com.storyteller_f.a.app.service

import de.kherud.llama.InferenceParameters
import de.kherud.llama.LlamaModel
import de.kherud.llama.ModelParameters
import de.kherud.llama.args.MiroStat
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.io.files.Path
import java.io.File

actual fun buildGPT(): GPT {
    return LlamaGPT()
}

class LlamaGPT : GPT {
    override fun generate(
        path: String,
        prompt: String,
        stopWord: String
    ): Result<Flow<GPTOutput>> {
        return runCatching {
            buildLlampCpp(path, prompt, stopWord)
        }
    }

    fun buildLlampCpp(
        path: String,
        prompt: String,
        stopWord: String
    ): Flow<GPTOutput> {
        val modelParams = ModelParameters()
            .setModel(path)
            .setGpuLayers(43)
        val model = LlamaModel(modelParams)
        Napier.i(tag = "gpt") {
            "load $path done"
        }
        val inferParams = InferenceParameters(prompt)
            .setTemperature(0.7f)
            .setPenalizeNl(true)
            .setMiroStat(MiroStat.V2)
            .setStopStrings(stopWord)
        val iterator = model.generate(inferParams).iterator()
        return callbackFlow {
            while (true) {
                if (iterator.hasNext()) {
                    val n = iterator.next()
                    Napier.i(tag = "gpt") {
                        "send ${n.text}"
                    }
                    send(GPTOutput(n.text))
                } else {
                    Napier.i(tag = "gpt") {
                        "close"
                    }
                    close()
                    break
                }
            }
            awaitClose {
                Napier.i(tag = "gpt") {
                    "release model"
                }
                iterator.cancel()
                model.close()
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun models(scope: CoroutineScope): Flow<List<GPTModel>> {
        val userHome = System.getProperty("user.home")
        return observeModels(scope, Path(File(userHome, "llm").absolutePath), listOf("gguf"))
    }
}
