package com.storyteller_f.a.app.core.compontents

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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.aakira.napier.Napier
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

private val mutex = Mutex()

data class GlobalDialogStateProgress(val value: Long, val total: Long?)

interface GlobalDialogController {
    val state: MutableState<PersistentList<GlobalDialogState>>

    suspend fun <T> useResult(
        block: suspend GlobalDialogController.() -> Result<T>,
    ): Result<T>

    fun emitProgress(block: (GlobalDialogState.Loading) -> GlobalDialogState.Loading)

    suspend fun emitEvent(any: Any)

    companion object {
        val EMPTY = object : GlobalDialogController {
            override val state: MutableState<PersistentList<GlobalDialogState>>
                get() = TODO("Not yet implemented")

            override suspend fun <T> useResult(block: suspend GlobalDialogController.() -> Result<T>): Result<T> {
                TODO("Not yet implemented")
            }

            override fun emitProgress(block: (GlobalDialogState.Loading) -> GlobalDialogState.Loading) {
                TODO("Not yet implemented")
            }

            override suspend fun emitEvent(any: Any) {
                TODO("Not yet implemented")
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
class NestedGlobalDialogController(
    val customGlobalDialogController: CustomGlobalDialogController,
    val level: Int
) :
    GlobalDialogController {
    override val state: MutableState<PersistentList<GlobalDialogState>>
        get() = customGlobalDialogController.state

    override suspend fun <T> useResult(block: suspend GlobalDialogController.() -> Result<T>): Result<T> {
        val value = state.value
        if (value.last() !is GlobalDialogState.Loading) {
            return Result.failure(Exception("lock failed"))
        }
        val stack = state.value
        if (stack.size != level) {
            return Result.failure(Exception("level mismatch"))
        }
        state.value = stack.add(GlobalDialogState.Loading())
        val nestedGlobalDialogController =
            NestedGlobalDialogController(customGlobalDialogController, level + 1)
        try {
            return nestedGlobalDialogController.block()
        } finally {
            state.value = stack
        }
    }

    override fun emitProgress(block: (GlobalDialogState.Loading) -> GlobalDialogState.Loading) {
        val value = state.value
        if (value.size != level) {
            return
        }
        val last = value.last()
        if (last !is GlobalDialogState.Loading) {
            return
        }
        state.value = value.set(level - 1, block(last))
    }

    override suspend fun emitEvent(any: Any) {
        customGlobalDialogController.events.emit(any)
    }
}

class CustomGlobalDialogContent(val content: @Composable () -> Unit)

class CustomGlobalDialogController(
    val events: MutableSharedFlow<Any>,
    override val state: MutableState<PersistentList<GlobalDialogState>> = mutableStateOf(
        persistentListOf()
    ),
) : GlobalDialogController {

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun <T> useResult(
        block: suspend GlobalDialogController.() -> Result<T>,
    ): Result<T> {
        return mutex.withLock {
            val dialogState = state.value
            if (!dialogState.isEmpty()) {
                return Result.failure(Exception("dialog show failed"))
            }
            try {
                state.value = persistentListOf(GlobalDialogState.Loading())
                val nestedGlobalDialogController = NestedGlobalDialogController(this, 1)
                val result = nestedGlobalDialogController.block().getOrThrow()
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

    override fun emitProgress(block: (GlobalDialogState.Loading) -> GlobalDialogState.Loading) =
        Unit

    override suspend fun emitEvent(any: Any) {
        events.emit(any)
    }
}

@Composable
fun GlobalDialog(state: CustomGlobalDialogController) {
    var message by state.state
    val dialogState = message.lastOrNull()
    dialogState?.let {
        GlobalDialogInternal(it) {
            message = persistentListOf()
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
            DialogProperties(
                dismissOnClickOutside = false,
                dismissOnBackPress = false
            )
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
                ExceptionView(
                    message.throwable,
                    modifier = Modifier.weight(1f).verticalScroll(scrollState)
                )
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Button({
                        onDismissRequest()
                    }) {
                        Text("Close")
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

val LocalGlobalDialog = compositionLocalOf {
    GlobalDialogController.EMPTY
}
