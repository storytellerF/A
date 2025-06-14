package com.storyteller_f.a.app.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storyteller_f.a.app.LocalDatabase
import com.storyteller_f.a.app.LocalSessionManager
import com.storyteller_f.a.client_lib.ClientSessionState
import com.storyteller_f.a.client_lib.LoadingHandler
import com.storyteller_f.a.client_lib.SessionManager
import com.storyteller_f.storage.DatabaseSource
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.map

abstract class SimpleViewModel<T : Any> : ViewModel() {
    abstract val handler: LoadingHandler<T>

    fun update(t: T) {
        handler.update(t)
    }
}

@Composable
inline fun <reified VM : ViewModel> viewModel(
    keys: List<Comparable<*>?>? = null,
    crossinline factory: (SessionManager, DatabaseSource) -> VM
): VM {
    val sessionManager = LocalSessionManager.current
    val databaseSource = LocalDatabase.current
    Napier.i {
        "viewModel ${VM::class.simpleName}$keys composable"
    }
    val address by sessionManager.sessionModel.state.map {
        (it as? ClientSessionState.Success)?.session?.address()?.getOrNull()
    }.collectAsState(null)
    return viewModel(key = "$address:${keys?.joinToString()}", initializer = {
        Napier.i {
            "viewModel ${VM::class.simpleName}$keys build"
        }
        factory(sessionManager, databaseSource)
    })
}
