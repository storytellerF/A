package com.storyteller_f.a.app.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storyteller_f.a.app.LocalClient
import com.storyteller_f.a.app.LocalDatabase
import com.storyteller_f.a.client_lib.DatabaseSource
import com.storyteller_f.a.client_lib.LoadingHandler
import io.github.aakira.napier.Napier
import io.ktor.client.*
import kotlinx.coroutines.launch

abstract class SimpleViewModel<T : Any>(val client: HttpClient) : ViewModel() {
    abstract val handler: LoadingHandler<T>

    abstract suspend fun loadInternal(): Result<T>

    protected fun load() {
        viewModelScope.launch {
            handler.request {
                loadInternal()
            }
        }
    }

    fun update(t: T) {
        handler.update(t)
    }
}

@Composable
inline fun <reified VM : ViewModel> viewModel(
    keys: List<Comparable<*>?>? = null,
    crossinline factory: (HttpClient, DatabaseSource) -> VM
): VM {
    val client = LocalClient.current
    val databaseSource = LocalDatabase.current
    Napier.i {
        "viewModel ${VM::class.simpleName} $keys"
    }
    return viewModel(key = keys?.joinToString(), initializer = {
        Napier.i {
            "viewModel build ${VM::class.simpleName} $keys"
        }
        factory(client, databaseSource)
    })
}
