package com.storyteller_f.android_llama_cpp

import com.storyteller_f.shared.type.GPTOutput
import de.kherud.llama.InferenceParameters
import de.kherud.llama.LlamaModel
import de.kherud.llama.ModelParameters
import de.kherud.llama.args.MiroStat
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

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