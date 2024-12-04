package com.storyteller_f.a.client_lib

import kotlinx.coroutines.flow.MutableStateFlow

sealed class LoadingState {
    class Loading(val state: String) : LoadingState()
    class Error(val e: Throwable) : LoadingState()
    class Done(val itemCount: Int = 1) : LoadingState()
}

class LoadingHandler<T>(val refresh: () -> Unit) {
    val state: MutableStateFlow<LoadingState?> = MutableStateFlow(null)
    val data: MutableStateFlow<T?> = MutableStateFlow(null)

    suspend fun request(
        service: suspend () -> Result<T>,
    ) {
        if (state.value is LoadingState.Loading) return
        state.markLoading()
        service().onSuccess { res ->
            if (res != null) {
                data.value = res
                state.markLoaded()
            } else {
                state.markError("nil")
            }
        }.onFailure {
            state.markError(it)
        }
    }

    fun done(t: T) {
        data.value = t
        state.value = LoadingState.Done()
    }
}

fun MutableStateFlow<LoadingState?>.markLoaded() {
    value = LoadingState.Done()
}

fun MutableStateFlow<LoadingState?>.markError(e: Throwable) {
    value = LoadingState.Error(e)
}

fun MutableStateFlow<LoadingState?>.markError(e: String) {
    value = LoadingState.Error(Exception(e))
}

fun MutableStateFlow<LoadingState?>.markLoading(message: String = "") {
    value = LoadingState.Loading(message)
}
