package com.storyteller_f.a.client_lib

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

sealed class LoadingState {
    data object Loading : LoadingState()
    data class Error(val e: Throwable) : LoadingState()
    data object Done : LoadingState()
}

interface LoadingHandler<T> {
    val state: MutableStateFlow<LoadingState?>
    val data: StateFlow<T?>
    val refresh: () -> Unit

    suspend fun request(
        service: suspend () -> Result<T>,
    ) {
        if (state.value is LoadingState.Loading) return
        state.markLoading()
        service().onSuccess { res ->
            if (res != null) {
                done(res)
            } else {
                error(Exception("nil"))
            }
        }.onFailure {
            error(it)
        }
    }

    fun done(t: T)

    fun error(error: Throwable)

    fun update(t: T)
}

class SimpleLoadingHandler<T>(override val refresh: () -> Unit) : LoadingHandler<T> {
    override val state: MutableStateFlow<LoadingState?> = MutableStateFlow(null)
    override val data: MutableStateFlow<T?> = MutableStateFlow(null)

    override fun done(t: T) {
        data.value = t
        state.value = LoadingState.Done
    }

    override fun error(error: Throwable) {
        data.value = null
        state.value = LoadingState.Error(error)
    }

    override fun update(t: T) {
        done(t)
    }
}

class CachedLoadingHandler<T : Any>(
    databaseSource: DatabaseSource,
    name: String,
    scope: CoroutineScope,
    expression: Expression,
    override val refresh: () -> Unit,
    private val serializer: KSerializer<T>,
    val saveDocument: Collection.(String, T) -> Unit,
) : LoadingHandler<T> {
    private val collection = databaseSource.getCollection(name)
    override val state: MutableStateFlow<LoadingState?> = MutableStateFlow(null)
    override val data = collection.observe(
        serializer,
        expression
    ).stateIn(scope, SharingStarted.Lazily, null)

    override fun done(t: T) {
        try {
            val data = Json.encodeToString(serializer, t)
            collection.saveDocument(data, t)
            state.markDown()
        } catch (e: Exception) {
            error(e)
        }
    }

    override fun error(error: Throwable) {
        state.markError(error)
    }

    override fun update(t: T) {
        done(t)
    }
}

fun MutableStateFlow<LoadingState?>.markError(e: Throwable) {
    value = LoadingState.Error(e)
}

fun MutableStateFlow<LoadingState?>.markDown() {
    value = LoadingState.Done
}

fun MutableStateFlow<LoadingState?>.markError(e: String) {
    value = LoadingState.Error(Exception(e))
}

fun MutableStateFlow<LoadingState?>.markLoading() {
    value = LoadingState.Loading
}
