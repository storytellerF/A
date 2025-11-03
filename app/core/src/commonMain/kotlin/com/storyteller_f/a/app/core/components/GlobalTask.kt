package com.storyteller_f.a.app.core.components

import androidx.compose.runtime.compositionLocalOf
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

interface GlobalTask {
    val stateMap: SnapshotStateMap<String, LoadingState?>

    fun use(
        key: String,
        block: suspend (MutableStateFlow<LoadingState?>, MutableSharedFlow<Any>) -> Unit
    )

    companion object {
        val EMPTY = object : GlobalTask {
            override val stateMap: SnapshotStateMap<String, LoadingState?>
                get() = TODO("Not yet implemented")

            override fun use(
                key: String,
                block: suspend (MutableStateFlow<LoadingState?>, MutableSharedFlow<Any>) -> Unit
            ) {
                TODO("Not yet implemented")
            }
        }
    }
}

class CustomGlobalTask(val scope: CoroutineScope, val events: MutableSharedFlow<Any>) : GlobalTask {
    val mutex = Mutex()
    override val stateMap = mutableStateMapOf<String, LoadingState?>()

    override fun use(
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

suspend inline fun <T> MutableStateFlow<LoadingState?>.use(block: suspend () -> Result<T>): Result<T> {
    value = LoadingState.Loading
    return block()
}

val LocalGlobalTask = compositionLocalOf {
    GlobalTask.EMPTY
}
