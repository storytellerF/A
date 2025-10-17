package com.storyteller_f.a.app.compose_app.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow

class DialogSaveState {
    val dialogShown = MutableStateFlow(false)
    fun markDialogShown() {
        dialogShown.value = true
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

@Composable
fun rememberAlertDialogController(): CustomAlertDialogController {
    return remember {
        CustomAlertDialogController()
    }
}
class CustomAlertDialogController(val state: MutableState<CustomAlertDialogState?> = mutableStateOf(null)) {

    fun showMessage(title: String, message: String) {
        state.value = CustomAlertDialogState(title, message)
    }

    fun showTitle(title: String) {
        state.value = CustomAlertDialogState(title, "")
    }

    fun close() {
        state.value = null
    }

    fun showErrorMessage(e: Throwable) {
        state.value = CustomAlertDialogState(e.message.toString(), e.stackTraceToString())
    }
}

class CustomAlertDialogState(val title: String?, val message: String)

@Composable
fun CustomAlertDialog(controller: CustomAlertDialogController, dismiss: () -> Unit, onClickOk: () -> Unit) {
    val state by controller.state
    state?.let { it1 ->
        CustomAlertDialogInternal(it1, dismiss, onClickOk)
    }
}

@Composable
fun CustomAlertDialogInternal(
    dialogState: CustomAlertDialogState,
    dismiss: () -> Unit,
    onClickOk: () -> Unit,
) {
    AlertDialog({
        dismiss()
    }, title = {
        dialogState.title?.let {
            Text(it)
        }
    }, text = {
        Text(dialogState.message)
    }, confirmButton = {
        Button({
            dismiss()
            onClickOk()
        }) {
            Text("Yes")
        }
    })
}

@Composable
fun rememberCommonDialogController(): CommonDialogController {
    return remember {
        CommonDialogController()
    }
}

class CommonDialogController(val shown: MutableState<Boolean> = mutableStateOf(false)) {

    fun update(new: Boolean) {
        shown.value = new
    }
}
