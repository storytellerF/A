package com.storyteller_f.a.app.compose_app.compontents

import androidx.compose.runtime.mutableStateMapOf
import com.storyteller_f.a.client.core.LoadingState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GlobalTask(val scope: CoroutineScope, val events: MutableSharedFlow<Any>) {
    val mutex = Mutex()
    val stateMap = mutableStateMapOf<String, LoadingState?>()

    fun use(
        key: String,
        block: suspend (MutableStateFlow<LoadingState?>, MutableSharedFlow<Any>) -> Unit
    ) {
        scope.launch {
            useInternal(key, block)
        }
    }

    private suspend fun useInternal(
        key: String,
        block: suspend (MutableStateFlow<LoadingState?>, MutableSharedFlow<Any>) -> Unit
    ) {
        val newFlow = mutex.withLock {
            if (stateMap.contains(key)) {
                null
            } else {
                val flow = MutableStateFlow<LoadingState?>(null)
                stateMap.put(key, null)
                flow
            }
        }
        if (newFlow == null) {
            Napier.e {
                "global task lock map failed"
            }
            return
        }
        coroutineScope {
            val job = launch {
                newFlow.collectLatest {
                    stateMap[key] = it
                }
            }
            try {
                block(newFlow, events)
            } catch (_: Exception) {
            } finally {
                job.cancel()
                mutex.withLock {
                    stateMap.remove(key)
                }
            }
        }
    }
}

suspend inline fun <T> MutableStateFlow<LoadingState?>.use(block: suspend () -> Result<T>) {
    value = LoadingState.Loading
    block()
}
