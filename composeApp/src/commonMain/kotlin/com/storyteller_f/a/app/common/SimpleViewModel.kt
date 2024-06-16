package com.storyteller_f.a.app.common

import com.storyteller_f.a.client_lib.LoadingHandler
import kotlinx.coroutines.launch
import moe.tlaster.precompose.viewmodel.ViewModel
import moe.tlaster.precompose.viewmodel.viewModelScope

abstract class SimpleViewModel<T> : ViewModel() {
    val handler = LoadingHandler<T?>(::load)

    abstract suspend fun loadInternal()

    protected fun load() {
        viewModelScope.launch {
            loadInternal()
        }
    }
}
