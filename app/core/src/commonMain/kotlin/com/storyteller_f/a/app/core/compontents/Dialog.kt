package com.storyteller_f.a.app.core.compontents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
fun rememberAlertDialogController(): CustomAlertDialogController {
    return remember {
        CustomAlertDialogController()
    }
}

class CustomAlertDialogController(
    val state: MutableState<CustomAlertDialogState?> = mutableStateOf(
        null
    )
) {

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
fun CustomAlertDialog(
    controller: CustomAlertDialogController,
    dismiss: () -> Unit,
    onClickOk: () -> Unit
) {
    val state by controller.state
    CustomAlertDialogInternal(state, dismiss, onClickOk)
}

@Composable
fun CustomAlertDialogInternal(
    dialogState: CustomAlertDialogState?,
    dismiss: () -> Unit,
    onClickOk: () -> Unit,
) {
    if (dialogState == null) return
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

@Composable
fun DialogContainer(block: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            block()
        }
    }
}
