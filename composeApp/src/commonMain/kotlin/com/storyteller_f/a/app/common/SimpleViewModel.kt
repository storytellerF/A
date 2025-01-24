package com.storyteller_f.a.app.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storyteller_f.a.app.LocalClient
import com.storyteller_f.a.client_lib.LoadingHandler
import io.github.aakira.napier.Napier
import io.ktor.client.*
import kotlinx.coroutines.launch

abstract class SimpleViewModel<T>(val client: HttpClient) : ViewModel() {
    val handler = LoadingHandler<T?>(::load)

    abstract suspend fun loadInternal(): Result<T>

    protected fun load() {
        viewModelScope.launch {
            handler.request {
                loadInternal()
            }
        }
    }

    fun update(t: T) {
        handler.data.value = t
    }
}

@Composable
inline fun <reified VM : ViewModel> viewModel(
    keys: List<Comparable<*>?>? = null,
    crossinline factory: (HttpClient) -> VM
): VM {
    val client = LocalClient.current
    Napier.i {
        "viewModel ${VM::class.simpleName} $keys"
    }
    return viewModel(key = keys?.joinToString(), initializer = {
        Napier.i {
            "viewModel build ${VM::class.simpleName} $keys"
        }
        factory(client)
    })
}
