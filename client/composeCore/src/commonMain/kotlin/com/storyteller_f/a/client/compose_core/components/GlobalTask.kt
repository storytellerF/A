package com.storyteller_f.a.client.compose_core.components

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
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

class CustomGlobalTask<C>(
    val scope: CoroutineScope,
    val context: C
) {
    private val mutex = Mutex()
    val stateMap: SnapshotStateMap<String, LoadingState?> = mutableStateMapOf()

    fun launch(key: String, block: suspend NestedGlobalTask<C>.() -> Unit) {
        scope.launch {
            useInternal(key, block)
        }
    }

    private suspend fun useInternal(
        key: String,
        block: suspend NestedGlobalTask<C>.() -> Unit
    ) {
        val newFlow = mutex.withLock {
            if (stateMap.contains(key)) {
                null
            } else {
                val flow = MutableStateFlow<LoadingState?>(null)
                stateMap[key] = null
                flow
            }
        }
        if (newFlow == null) {
            Napier.e {
                "global task lock map failed"
            }
            return
        }
        try {
            coroutineScope {
                val job = launch {
                    newFlow.collectLatest {
                        stateMap[key] = it
                    }
                }
                try {
                    val nestedTask = NestedGlobalTask(this@CustomGlobalTask, newFlow)
                    nestedTask.block()
                } catch (e: Exception) {
                    Napier.e(e) {
                        "global task $key failed"
                    }
                } finally {
                    job.cancel()
                }
            }
        } finally {
            mutex.withLock {
                stateMap.remove(key)
            }
        }
    }
}

class NestedGlobalTask<C>(
    val controller: CustomGlobalTask<C>,
    val state: MutableStateFlow<LoadingState?>
) {
    val context: C get() = controller.context

    suspend inline fun <T> use(block: suspend () -> Result<T>): Result<T> {
        state.value = LoadingState.Loading
        return block()
    }
}

class GlobalTaskContext<C>(val events: MutableSharedFlow<Any>, val sessionManager: C) {
    suspend fun emitEvent(any: Any) {
        events.emit(any)
    }

    suspend fun <T> request(block: suspend C.() -> Result<T>): Result<T> {
        return block(sessionManager)
    }
}

/**
 * 便捷方法：从 NestedGlobalTask 触发事件或发起请求。
 */
suspend inline fun <T, R> NestedGlobalTask<GlobalTaskContext<T>>.request(
    noinline block: suspend T.() -> Result<R>
): Result<R> {
    return context.request(block)
}

suspend inline fun <T> NestedGlobalTask<GlobalTaskContext<T>>.emitEvent(event: Any) {
    context.emitEvent(event)
}
