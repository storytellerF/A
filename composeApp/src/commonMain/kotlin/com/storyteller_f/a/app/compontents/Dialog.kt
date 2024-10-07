package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

sealed interface DialogState {
    data object Loading : DialogState
    data class Error(val throwable: Throwable) : DialogState
    data object None : DialogState
    data class Text(val text: String) : DialogState
}

class EventState(val state: MutableState<DialogState> = mutableStateOf(DialogState.None)) {
    fun showError(error: Throwable) {
        state.value = DialogState.Error(error)
    }

    fun showMessage(message: String) {
        state.value = DialogState.Text(message)
    }

    fun showLoading() {
        state.value = DialogState.Loading
    }

    fun close() {
        state.value = DialogState.None
    }
}

@Composable
fun rememberEventState(): EventState {
    return remember {
        EventState()
    }
}

suspend fun EventState.use(onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
    try {
        showLoading()
        block()
        close()
        onSuccess()
    } catch (e: Exception) {
        showError(e)
    }
}

@Composable
fun EventDialog(state: EventState) {
    var message by state.state
    if (message !is DialogState.None) {
        EventAlertDialog(message) {
            message = it
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventAlertDialog(message: DialogState, updateNewState: (DialogState) -> Unit) {
    val onDismissRequest = {
        updateNewState(DialogState.None)
    }
    when (message) {
        is DialogState.Loading -> {
            BasicAlertDialog(
                onDismissRequest,
                properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false)
            ) {
                Surface(shape = RoundedCornerShape(12.dp), shadowElevation = 10.dp) {
                    Box(modifier = Modifier.size(100.dp).padding(20.dp)) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        is DialogState.Error -> {
            val throwable = message.throwable
            val scrollState = rememberScrollState()
            AlertDialog(onDismissRequest, {
                Button({
                    onDismissRequest()
                }) {
                    Text("Close")
                }
            }, title = {
                Text(throwable.message ?: throwable::class.simpleName ?: throwable.toString())
            }, text = {
                val text = throwable.stackTraceToString()
                MeasureTextLineCount(text, LocalTextStyle.current, 0.dp) { _, total ->
                    Text(text, modifier = Modifier.verticalScroll(scrollState), maxLines = (total).coerceIn(2, 20))
                }
            })
        }

        is DialogState.Text -> {
            val scrollState = rememberScrollState()
            AlertDialog(onDismissRequest, {
                Button({
                    onDismissRequest()
                }) {
                    Text("Close")
                }
            }, text = {
                val text = message.text
                MeasureTextLineCount(text, LocalTextStyle.current, 0.dp) { _, total ->
                    Text(text, modifier = Modifier.verticalScroll(scrollState), maxLines = total - 10)
                }
            })
        }

        DialogState.None -> {}
    }
}

@Composable
fun ButtonNav(icon: ImageVector, title: String, onClick: () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clickable {
            onClick()
        }.padding(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = title)
        Text(title)
    }
}

@Composable
fun DialogContainer(block: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            block()
        }
    }
}

class AlertDialogState(val title: String, val message: String)

@Composable
fun CustomAlertDialog(state: AlertDialogState?, dismiss: () -> Unit, onClick: () -> Unit) {
    if (state != null) {
        androidx.compose.material.AlertDialog({
            dismiss()
        }, title = {
            Text(state.title)
        }, text = {
            Text(state.title)
        }, confirmButton = {
            Button({
                onClick()
            }) {
                Text("Yes")
            }
        })
    }
}