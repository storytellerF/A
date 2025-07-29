package com.storyteller_f.a.app.compose_app.compontents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.client.core.ServerErrorException
import dev.tclement.fonticons.FontIcon
import dev.tclement.fonticons.ProvideIconParameters
import kotlinx.coroutines.flow.MutableStateFlow

class DialogSaveState {
    val dialogShown = MutableStateFlow(false)
    fun markDialogShown() {
        dialogShown.value = true
    }
}

fun ServerErrorException.isHtmlContent(): Boolean = text.startsWith("<html") || text.startsWith("<!DOCTYPE html")

sealed interface IconRes {
    data class Vector(val vector: ImageVector, val description: String = "") : IconRes
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
        CustomIcon(icon)
        Text(title)
    }
}

@Composable
fun CustomIcon(icon: IconRes, onClick: (() -> Unit)? = null) {
    when (icon) {
        is IconRes.Font -> {
            ProvideIconParameters(
                size = 20.dp,
                tintProvider = LocalContentColor
            ) {
                FontIcon(icon.char, icon.description, modifier = Modifier.clickableIfNeed(onClick))
            }
        }

        is IconRes.Vector -> {
            Icon(
                imageVector = icon.vector,
                contentDescription = icon.description,
                modifier = Modifier.clickableIfNeed(onClick)
            )
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

class CommonDialogController(val show: MutableState<Boolean> = mutableStateOf(false)) {

    fun update(new: Boolean) {
        show.value = new
    }
}

fun Modifier.clickableIfNeed(onClick: (() -> Unit)?): Modifier {
    if (onClick != null) {
        return clickable {
            onClick.invoke()
        }
    }
    return this
}
