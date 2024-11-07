package com.storyteller_f.a.app.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storyteller_f.a.client_lib.LoadingHandler
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

abstract class SimpleViewModel<T> : ViewModel() {
    val handler = LoadingHandler<T?>(::load)

    abstract suspend fun loadInternal()

    protected fun load() {
        viewModelScope.launch {
            loadInternal()
        }
    }
}

@Composable
fun <VM : ViewModel> viewModel(
    modelClass: KClass<VM>,
    keys: List<Comparable<*>>? = null,
    factory: () -> VM
): VM {
    return viewModel(modelClass = modelClass, key = keys?.joinToString(), factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return factory() as T
        }
    })
}
