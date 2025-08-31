package com.storyteller_f.a.app.compose_app.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storyteller_f.a.app.compose_app.LocalDatabase
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.client.core.LoadingHandler
import com.storyteller_f.a.client.core.SessionManager
import com.storyteller_f.storage.ModelStorage
import io.github.aakira.napier.Napier

abstract class SimpleViewModel<T : Any> : ViewModel() {
    abstract val handler: LoadingHandler<T>
}

@Composable
inline fun <reified VM : ViewModel> viewModel(
    keys: List<Comparable<*>?>? = null,
    crossinline factory: (SessionManager, ModelStorage) -> VM
): VM {
    val sessionManager = LocalSessionManager.current
    val databaseSource = LocalDatabase.current
    Napier.i {
        "viewModel ${VM::class.simpleName}$keys composable"
    }
    val address by sessionManager.address.collectAsState()
    return viewModel(key = "$address:${keys?.joinToString()}") {
        Napier.i {
            "viewModel ${VM::class.simpleName}$keys build"
        }
        factory(sessionManager, databaseSource)
    }
}
