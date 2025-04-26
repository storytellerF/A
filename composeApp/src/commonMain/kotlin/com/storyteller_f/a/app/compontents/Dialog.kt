package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.ScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.storyteller_f.a.app.AppConfig
import com.storyteller_f.a.client_lib.ServerErrorException
import dev.tclement.fonticons.FontIcon
import dev.tclement.fonticons.ProvideIconParameters
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow

class DialogSaveState {
    val shownDialog = MutableStateFlow<Boolean>(false)
    fun markDialogShown() {
        shownDialog.value = true
    }
}

sealed interface DialogState {
    data object Loading : DialogState
    data class Error(val throwable: Throwable) : DialogState
    data object None : DialogState
    data class Text(val text: String) : DialogState
}

class GlobalDialogController(val state: MutableState<DialogState> = mutableStateOf(DialogState.None)) {
    fun showErrorState(error: Throwable) {
        state.value = DialogState.Error(error)
    }

    fun showMessage(message: String) {
        state.value = DialogState.Text(message)
    }

    private fun showLoadingState() {
        state.value = DialogState.Loading
    }

    private fun showCloseState() {
        state.value = DialogState.None
    }

    suspend fun <T> use(
        block: suspend () -> T
    ): Result<T> {
        try {
            showLoadingState()
            val result = block()
            showCloseState()
            return Result.success(result)
        } catch (e: Exception) {
            Napier.e(e) {
                "global dialog"
            }
            showErrorState(e)
            return Result.failure(e)
        }
    }
}

@Composable
fun GlobalDialog(state: GlobalDialogController) {
    var message by state.state
    if (message !is DialogState.None) {
        GlobalDialogInternal(message) {
            message = it
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalDialogInternal(message: DialogState, updateNewState: (DialogState) -> Unit) {
    val onDismissRequest = {
        updateNewState(DialogState.None)
    }
    if (message != DialogState.None) {
        val scrollState = rememberScrollState()

        BasicAlertDialog(
            onDismissRequest,
            properties = if (message is DialogState.Loading) {
                DialogProperties(
                    dismissOnClickOutside = false,
                    dismissOnBackPress = false
                )
            } else {
                DialogProperties()
            }
        ) {
            DialogContainer {
                GlobalDialogContent(message, scrollState, onDismissRequest)
            }
        }
    }
}

@Composable
private fun ColumnScope.GlobalDialogContent(
    message: DialogState,
    scrollState: ScrollState,
    onDismissRequest: () -> Unit
) {
    when (message) {
        is DialogState.Error -> {
            ErrorDialogContent(message, scrollState, onDismissRequest)
        }

        DialogState.Loading -> {
            LoadingDialogContent()
        }

        is DialogState.Text -> {
            TextDialogContent(message, scrollState, onDismissRequest)
        }

        else -> {}
    }
}

@Composable
private fun LoadingDialogContent() {
    Box(modifier = Modifier.size(100.dp).padding(20.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun TextDialogContent(
    message: DialogState.Text,
    scrollState: ScrollState,
    onDismissRequest: () -> Unit
) {
    val text = message.text
    MeasureTextLineCount(text, LocalTextStyle.current, 0.dp) { _, total ->
        Text(text, modifier = Modifier.verticalScroll(scrollState), maxLines = total - 10)
    }
    Box(contentAlignment = Alignment.CenterEnd) {
        Button({
            onDismissRequest()
        }) {
            Text("Close")
        }
    }
}

@Composable
private fun ErrorDialogContent(
    message: DialogState.Error,
    scrollState: ScrollState,
    onDismissRequest: () -> Unit
) {
    val throwable = message.throwable
    if (throwable is ServerErrorException && throwable.isHtmlContent()) {
        ExceptionView(throwable)
    } else {
        Text((throwable.localizedMessage ?: throwable::class.toString()).take(100))
        if (AppConfig.BUILD_TYPE != "prod") {
            val text = throwable.stackTraceToString()
            MeasureTextLineCount(text, LocalTextStyle.current, 0.dp) { _, total ->
                Text(
                    text,
                    modifier = Modifier.verticalScroll(scrollState),
                    maxLines = total.coerceIn(2, 20)
                )
            }
        }
        Box(contentAlignment = Alignment.CenterEnd) {
            Button({
                onDismissRequest()
            }) {
                Text("Close")
            }
        }
    }
}

fun ServerErrorException.isHtmlContent(): Boolean = text.startsWith("<html") || text.startsWith("<!DOCTYPE html")

sealed interface IconRes {
    data class Vector(val vector: ImageVector) : IconRes
    data class Font(val char: Char, val description: String = "") : IconRes
}

@Composable
fun ButtonNav(icon: ImageVector, title: String, onClick: () -> Unit = {}) {
    ButtonNav(IconRes.Vector(icon), title, onClick)
}

@Composable
fun ButtonNav(icon: Char, title: String, onClick: () -> Unit = {}) {
    ButtonNav(IconRes.Font(icon), title, onClick)
}

@Composable
fun ButtonNav(icon: IconRes, title: String, onClick: () -> Unit = {}) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clip(shape).clickable {
            onClick()
        }.padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        CustomIcon(icon, title)
        Text(title)
    }
}

@Composable
fun CustomIcon(icon: IconRes, title: String) {
    when (icon) {
        is IconRes.Font -> {
            ProvideIconParameters(
                size = 20.dp,
                tintProvider = LocalContentColor
            ) {
                FontIcon(icon.char, icon.description)
            }
        }

        is IconRes.Vector -> {
            Icon(imageVector = icon.vector, contentDescription = title)
        }
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
}

class CustomAlertDialogState(val title: String?, val message: String)

@Composable
fun CustomAlertDialog(controller: CustomAlertDialogController, dismiss: () -> Unit, onClickOk: () -> Unit) {
    val state by controller.state
    state?.let { it1 ->
        CustomAlertDialogInternal(dismiss, it1, onClickOk)
    }
}

@Composable
fun CustomAlertDialogInternal(
    dismiss: () -> Unit,
    dialogState: CustomAlertDialogState,
    onClickOk: () -> Unit
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

class CommonDialogController(val show: MutableState<Boolean> = mutableStateOf(false)) {

    fun update(new: Boolean) {
        show.value = new
    }
}
