package com.storyteller_f.a.app.core.components

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

interface GlobalTask<out C> {
    val stateMap: SnapshotStateMap<String, LoadingState?>

    val context: GlobalTaskContext<C>

    fun use(key: String, block: suspend GlobalTask<C>.(MutableStateFlow<LoadingState?>) -> Unit)

    companion object
}

class CustomGlobalTask<C>(
    val scope: CoroutineScope,
    override val context: GlobalTaskContext<C>
) : GlobalTask<C> {
    val mutex = Mutex()
    override val stateMap = mutableStateMapOf<String, LoadingState?>()

    override fun use(
        key: String,
        block: suspend GlobalTask<C>.(MutableStateFlow<LoadingState?>) -> Unit
    ) {
        scope.launch {
            useInternal(key, block)
        }
    }

    private suspend fun useInternal(
        key: String,
        block: suspend GlobalTask<C>.(MutableStateFlow<LoadingState?>) -> Unit
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
                    block.invoke(this@CustomGlobalTask, newFlow)
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

suspend inline fun <T> MutableStateFlow<LoadingState?>.use(block: suspend () -> Result<T>): Result<T> {
    value = LoadingState.Loading
    return block()
}

class GlobalTaskContext<out C>(val events: MutableSharedFlow<Any>, val sessionManager: C) {
    suspend fun emitEvent(any: Any) {
        events.emit(any)
    }

    suspend fun <T> request(block: suspend C.() -> Result<T>): Result<T> {
        return block(sessionManager)
    }
}

/**
 * 便捷方法：从 GlobalTask 触发事件或发起请求（与 GlobalDialogController 的扩展一致）。
 */
suspend inline fun <T, R> GlobalTask<T>.request(noinline block: suspend T.() -> Result<R>): Result<R> {
    return context.request(block)
}

suspend inline fun <T> GlobalTask<T>.emitEvent(event: Any) {
    context.emitEvent(event)
}
