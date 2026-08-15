package com.storyteller_f.a.client.compose_core.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.storyteller_f.a.client.compose_core.Res
import com.storyteller_f.a.client.compose_core.close
import io.github.aakira.napier.Napier
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.ExperimentalUuidApi

sealed interface GlobalDialogState {
    data class Loading(
        val title: String? = null,
        val progress: GlobalDialogStateProgress? = null,
        val content: CustomGlobalDialogContent? = null,
    ) : GlobalDialogState

    data class Error(val throwable: Throwable) : GlobalDialogState

    class Custom(val content: CustomGlobalDialogContent) : GlobalDialogState
}

data class GlobalDialogStateProgress(val value: Long, val total: Long?)

class CustomGlobalDialogContent(val content: @Composable () -> Unit)

class GlobalDialogContext<C>(val events: MutableSharedFlow<Any>, val sessionManager: C) {
    suspend fun emitEvent(any: Any) {
        events.emit(any)
    }

    suspend fun <T> request(block: suspend C.() -> Result<T>): Result<T> {
        return block(sessionManager)
    }
}

class CustomGlobalDialogController<C>(
    val scope: CoroutineScope,
    val context: C,
    val state: MutableStateFlow<PersistentList<GlobalDialogState>> = MutableStateFlow(persistentListOf())
) {
    private val mutex = Mutex()

    fun launch(block: suspend NestedGlobalDialogController<C>.() -> Unit) {
        scope.launch {
            val nestedController = NestedGlobalDialogController(this@CustomGlobalDialogController)
            nestedController.block()
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun <T> useResult(
        block: suspend CustomGlobalDialogController<C>.() -> Result<T>,
    ): Result<T> {
        return mutex.withLock {
            val dialogState = state.value
            if (!dialogState.isEmpty()) {
                return@withLock Result.failure(Exception("dialog show failed"))
            }
            try {
                state.value = persistentListOf(GlobalDialogState.Loading())
                val result = this.block().getOrThrow()
                if (result is CustomGlobalDialogContent) {
                    state.value = persistentListOf(GlobalDialogState.Custom(result))
                } else {
                    state.value = persistentListOf()
                }
                Result.success(result)
            } catch (e: Exception) {
                Napier.e(e) {
                    "global dialog"
                }
                state.value = persistentListOf(GlobalDialogState.Error(e))
                Result.failure(e)
            }
        }
    }

    fun emitProgress(block: (GlobalDialogState.Loading) -> GlobalDialogState.Loading) = Unit
}

class NestedGlobalDialogController<C>(
    val controller: CustomGlobalDialogController<C>
) {
    val state: MutableStateFlow<PersistentList<GlobalDialogState>>
        get() = controller.state

    val context: C
        get() = controller.context

    suspend fun <T> useResult(block: suspend NestedGlobalDialogController<C>.() -> Result<T>): Result<T> {
        val currentState = state.value
        state.value = currentState.adding(GlobalDialogState.Loading())
        try {
            return block()
        } finally {
            state.value = state.value.removingAt(state.value.lastIndex)
        }
    }

    fun emitProgress(block: (GlobalDialogState.Loading) -> GlobalDialogState.Loading) {
        val value = state.value
        val last = value.lastOrNull() ?: return
        if (last !is GlobalDialogState.Loading) return
        state.value = value.replacingAt(value.lastIndex, block(last))
    }
}

@Composable
fun <C> GlobalDialog(state: CustomGlobalDialogController<C>) {
    val message by state.state.collectAsState()
    val dialogState = message.lastOrNull()
    dialogState?.let {
        GlobalDialogInternal(it) {
            state.state.value = persistentListOf()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalDialogInternal(message: GlobalDialogState, dismiss: () -> Unit) {
    val scrollState = rememberScrollState()

    BasicAlertDialog(
        dismiss,
        properties = if (message is GlobalDialogState.Loading) {
            DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false)
        } else {
            DialogProperties()
        }
    ) {
        DialogContainer {
            GlobalDialogContent(message, scrollState, dismiss)
        }
    }
}

@Composable
private fun GlobalDialogContent(
    message: GlobalDialogState,
    scrollState: ScrollState,
    onDismissRequest: () -> Unit,
) {
    Column(modifier = Modifier.height(200.dp)) {
        when (message) {
            is GlobalDialogState.Error -> {
                ExceptionView(message.throwable, modifier = Modifier.weight(1f).verticalScroll(scrollState))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Button({
                        onDismissRequest()
                    }) {
                        Text(stringResource(Res.string.close))
                    }
                }
            }

            is GlobalDialogState.Loading -> {
                LoadingGlobalDialogContent(message)
            }

            is GlobalDialogState.Custom -> {
                message.content.content()
                Box(contentAlignment = Alignment.CenterEnd) {
                    Button({
                        onDismissRequest()
                    }) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingGlobalDialogContent(
    loading: GlobalDialogState.Loading,
) {
    if (loading.content != null) {
        loading.content.content()
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                loading.title?.let {
                    Text(it)
                    Spacer(Modifier.height(20.dp))
                }
                if (loading.progress != null && loading.progress.total != null) {
                    LinearProgressIndicator(
                        progress = { loading.progress.value.toFloat() / loading.progress.total },
                    )
                } else {
                    Box(modifier = Modifier, contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

suspend inline fun <T, R> NestedGlobalDialogController<GlobalDialogContext<T>>.request(
    noinline block: suspend T.() -> Result<R>
): Result<R> {
    return context.request(block)
}

suspend inline fun <T> NestedGlobalDialogController<GlobalDialogContext<T>>.emitEvent(event: Any) {
    context.emitEvent(event)
}

suspend inline fun <T, R> CustomGlobalDialogController<GlobalDialogContext<T>>.request(
    noinline block: suspend T.() -> Result<R>
): Result<R> {
    return context.request(block)
}

suspend inline fun <T> CustomGlobalDialogController<GlobalDialogContext<T>>.emitEvent(event: Any) {
    context.emitEvent(event)
}
