package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.aakira.napier.Napier
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.ExperimentalUuidApi

sealed interface GlobalDialogState {
    data class Loading(val stack: PersistentList<GlobalDialogStageState>) : GlobalDialogState
    data class Error(val throwable: Throwable) : GlobalDialogState
    data object None : GlobalDialogState
    class Custom(val content: CustomGlobalDialogContent) : GlobalDialogState
}

private val mutex = Mutex()

data class GlobalDialogStateProgress(val value: Float, val total: Float)

data class GlobalDialogStageState(
    val title: String? = null,
    val progress: GlobalDialogStateProgress? = null,
    val content: CustomGlobalDialogContent? = null,
)

interface GlobalDialogController {
    val state: MutableState<GlobalDialogState>
    suspend fun <T> useResult(
        block: suspend GlobalDialogController.() -> Result<T>,
    ): Result<T>

    suspend fun showErrorMessage(throwable: Throwable) {
        useResult<Unit> {
            Result.failure(throwable)
        }
    }

    suspend fun showErrorState(throwable: Throwable) {
        useResult<Unit> {
            Result.failure(throwable)
        }
    }

    suspend fun <T> use(
        block: suspend GlobalDialogController.() -> T,
    ): Result<T> {
        return useResult {
            runCatching {
                block()
            }
        }
    }

    suspend fun showMessage(text: String) {
        use {
            CustomGlobalDialogContent {
                Text(text)
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
class NestedGlobalDialogController(val customGlobalDialogController: CustomGlobalDialogController, val level: Int) :
    GlobalDialogController {
    override val state: MutableState<GlobalDialogState>
        get() = customGlobalDialogController.state

    override suspend fun <T> useResult(block: suspend GlobalDialogController.() -> Result<T>): Result<T> {
        val value = customGlobalDialogController.state.value
        if (value !is GlobalDialogState.Loading) {
            return Result.failure(Exception("lock failed"))
        }
        val stack = value.stack
        if (stack.size != level) {
            return Result.failure(Exception("level mismatch"))
        }
        val newLevel = GlobalDialogStageState()
        customGlobalDialogController.state.value = GlobalDialogState.Loading(stack.add(newLevel))
        val newController = NestedGlobalDialogController(customGlobalDialogController, level + 1)
        newController.block()
        return try {
            val nestedGlobalDialogController = NestedGlobalDialogController(customGlobalDialogController, 1)
            nestedGlobalDialogController.block()
        } finally {
            customGlobalDialogController.state.value = GlobalDialogState.Loading(stack)
        }
    }
}

class CustomGlobalDialogContent(val content: @Composable () -> Unit)

class CustomGlobalDialogController(
    override val state: MutableState<GlobalDialogState> = mutableStateOf(
        GlobalDialogState.None
    ),
) :
    GlobalDialogController {

    private fun showCloseState() {
        state.value = GlobalDialogState.None
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun <T> useResult(
        block: suspend GlobalDialogController.() -> Result<T>,
    ): Result<T> {
        return mutex.withLock {
            val dialogState = state.value
            if (dialogState is GlobalDialogState.None) {
                val initStage = GlobalDialogStageState()
                state.value = GlobalDialogState.Loading(persistentListOf(initStage))
            } else {
                return Result.failure(Exception("lock failed, currentState is $dialogState"))
            }
            try {
                val nestedGlobalDialogController = NestedGlobalDialogController(this, 1)
                val result = nestedGlobalDialogController.block().getOrThrow()
                if (result is CustomGlobalDialogContent) {
                    state.value = GlobalDialogState.Custom(result)
                } else {
                    showCloseState()
                }
                Result.success(result)
            } catch (e: Exception) {
                Napier.e(e) {
                    "global dialog"
                }
                state.value = GlobalDialogState.Error(e)
                Result.failure(e)
            }
        }
    }
}

@Composable
fun GlobalDialog(state: CustomGlobalDialogController) {
    var message by state.state
    GlobalDialogInternal(message) {
        message = GlobalDialogState.None
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalDialogInternal(message: GlobalDialogState, dismiss: () -> Unit) {
    if (message != GlobalDialogState.None) {
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
                Box(contentAlignment = Alignment.CenterEnd) {
                    Button({
                        onDismissRequest()
                    }) {
                        Text("Close")
                    }
                }
            }

            is GlobalDialogState.Loading -> {
                val stageState = message.stack.last()
                if (stageState.content != null) {
                    stageState.content.content()
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            stageState.title?.let {
                                Text(it)
                                Spacer(Modifier.height(20.dp))
                            }
                            if (stageState.progress != null) {
                                LinearProgressIndicator(
                                    progress = { stageState.progress.value / stageState.progress.total },
                                    color = ProgressIndicatorDefaults.linearColor,
                                    trackColor = ProgressIndicatorDefaults.linearTrackColor,
                                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
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

            else -> {}
        }
    }
}
