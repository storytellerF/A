package com.storyteller_f.a.client.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class LoadingState {
    data object Loading : LoadingState()
    data class Processing(val value: Long, val total: Long) : LoadingState()
    data class Error(val e: Throwable) : LoadingState()
    data object Done : LoadingState()
}

interface LoadingHandler<T> {
    val state: MutableStateFlow<LoadingState?>
    val data: StateFlow<T?>

    suspend fun request(
        service: suspend () -> Result<T>,
    ) {
        if (state.value is LoadingState.Loading) return
        state.markLoading()
        service().onSuccess { res ->
            if (res != null) {
                done(res)
            } else {
                error(Exception("content not found"))
            }
        }.onFailure {
            error(it)
        }
    }

    fun update(t: T) {
        if (state.value is LoadingState.Loading) return
        done(t)
    }

    fun error(error: Throwable) {
        if (state.value !is LoadingState.Loading) return
        state.markError(error)
    }

    fun done(t: T)

    fun refresh()
}

class FixedLoadingHandler<T> : LoadingHandler<T> {
    override val state: MutableStateFlow<LoadingState?> = MutableStateFlow(null)
    override val data = MutableStateFlow<T?>(null)

    override fun done(t: T) {
        data.value = t
        state.markDown()
    }

    override fun refresh() = Unit
}

class SimpleLoadingHandler<T>(val scope: CoroutineScope, val loader: suspend () -> Result<T>) :
    LoadingHandler<T> {
    override val state: MutableStateFlow<LoadingState?> = MutableStateFlow(null)
    override val data: MutableStateFlow<T?> = MutableStateFlow(null)

    init {
        refresh()
    }

    override fun done(t: T) {
        data.value = t
        state.value = LoadingState.Done
    }

    override fun refresh() {
        scope.launch {
            request {
                loader()
            }
        }
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
