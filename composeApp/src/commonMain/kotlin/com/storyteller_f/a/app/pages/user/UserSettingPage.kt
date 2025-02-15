package com.storyteller_f.a.app.pages.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToasterState
import com.storyteller_f.a.app.LocalClient
import com.storyteller_f.a.app.LocalToaster
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.compontents.UserIcon
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.model.OnUpdateUser
import com.storyteller_f.a.app.pages.topic.MediaPicker
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.a.client_lib.updateUserInfo
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.UserInfo
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

sealed class UserSettingOption(open val name: String?) {
    data class Name(override val name: String?) : UserSettingOption(name)
    data class Aid(override val name: String?) : UserSettingOption(name)
    data class Icon(override val name: String?) : UserSettingOption(name)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSettingPage() {
    var showInputDialog by remember {
        mutableStateOf<UserSettingOption?>(null)
    }
    val sheetState = rememberModalBottomSheetState()
    val my by LoginViewModel.user.collectAsState()
    val toasterState = LocalToaster.current
    val showDialog = { option: UserSettingOption ->
        showInputDialog = option
    }
    my?.let { m ->
        Scaffold {
            UserSettingInternal(it, showDialog, m, toasterState)
        }
    }
    val closeDialog = {
        showInputDialog = null
    }
    val client = LocalClient.current
    val scope = rememberCoroutineScope()
    val updateIcon = { info: MediaInfo ->
        scope.launch {
            globalDialogState.use {
                val newInfo = client.updateUserInfo(UserInfo.EMPTY.copy(avatar = info)).getOrThrow()
                LoginViewModel.updateUser(newInfo)
                closeDialog()
            }
        }
    }

    MediaPicker(showInputDialog is UserSettingOption.Icon, sheetState, null, {
        updateIcon(it)
    }, {
        updateIcon(it)
    }, support = listOf("files")) {
        closeDialog()
    }

    showInputDialog?.let {
        InputDialog(it !is UserSettingOption.Icon, it.name.orEmpty(), {
            closeDialog()
        }) {
            scope.launch {
                updateUser(showInputDialog, client, it, closeDialog)
            }
        }
    }
}

@Composable
private fun UserSettingInternal(
    values: PaddingValues,
    showDialog: (UserSettingOption) -> Unit,
    m: UserInfo,
    toasterState: ToasterState
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(values)) {
        UserSettingOptionView("Icon", {
            showDialog(UserSettingOption.Icon(m.avatar?.item?.name))
        }, {
            UserIcon(m)
        })
        UserSettingOptionView("Name", {
            showDialog(UserSettingOption.Name(m.nickname))
        }, {
            Text(m.nickname, textDecoration = TextDecoration.Underline)
        })
        val aid = m.aid
        UserSettingOptionView("Aid", {
            if (aid == null) {
                showDialog(UserSettingOption.Aid(aid))
            } else {
                toasterState.show("forbid", duration = 1.seconds)
            }
        }, {
            if (aid == null) {
                Text("undefined", textDecoration = TextDecoration.Underline)
            } else {
                Text(aid)
            }
        })
    }
}

@Composable
fun UserSettingOptionView(title: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(60.dp).clickable {
        onClick()
    }) {
        Text(title)
        Spacer(modifier = Modifier.weight(1f))
        content()
    }
    HorizontalDivider()
}

private suspend fun updateUser(
    showInputDialog: UserSettingOption?,
    client: HttpClient,
    string: String,
    closeDialog: () -> Unit
) {
    when (showInputDialog) {
        is UserSettingOption.Name -> {
            globalDialogState.use {
                val newInfo = client.updateUserInfo(UserInfo.EMPTY.copy(nickname = string)).getOrThrow()
                LoginViewModel.updateUser(newInfo)
                bus.emit(OnUpdateUser(newInfo))
                closeDialog()
            }
        }

        is UserSettingOption.Aid -> {
            globalDialogState.use {
                val newInfo = client.updateUserInfo(UserInfo.EMPTY.copy(aid = string)).getOrThrow()
                LoginViewModel.updateUser(newInfo)
                bus.emit(OnUpdateUser(newInfo))
                closeDialog()
            }
        }

        else -> {}
    }
}

@Composable
fun InputDialog(show: Boolean, init: String, dismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var input by remember {
        mutableStateOf(init)
    }
    if (show) {
        AlertDialog(dismiss, {
            Button({
                onConfirm(input)
            }) {
                Text("OK")
            }
        }, text = {
            OutlinedTextField(input, {
                input = it
            })
        })
    }
}
