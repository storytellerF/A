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
        state.loading()
        service().onSuccess { res ->
            if (res != null) {
                data.value = res
                state.loaded()
            } else state.error("nil")
        }.onFailure {
            state.error(it)
        }
    }

    fun done(t: T) {
        data.value = t
        state.value = LoadingState.Done()
    }
}


fun MutableStateFlow<LoadingState?>.loaded() {
    value = LoadingState.Done()
}

fun MutableStateFlow<LoadingState?>.error(e: Throwable) {
    value = LoadingState.Error(e)
}

fun MutableStateFlow<LoadingState?>.error(e: String) {
    value = LoadingState.Error(Exception(e))
}

fun MutableStateFlow<LoadingState?>.loading(message: String = "") {
    value = LoadingState.Loading(message)
}
