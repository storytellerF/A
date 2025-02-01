package com.storyteller_f.a.app.pages.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.LocalClient
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.compontents.UserIcon
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.model.OnUpdateUser
import com.storyteller_f.a.app.pages.topic.MediaPicker
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.a.client_lib.updateUserInfo
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.UserInfo
import kotlinx.coroutines.launch

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
    val showSheet = showInputDialog is UserSettingOption.Icon
    val sheetState = rememberModalBottomSheetState()
    val my by LoginViewModel.user.collectAsState()
    my?.let { m ->
        Scaffold {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(it)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(60.dp).clickable {
                    showInputDialog = UserSettingOption.Icon(m.avatar?.item?.name)
                }) {
                    Text("Icon")
                    Spacer(modifier = Modifier.weight(1f))
                    UserIcon(my)
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(60.dp).clickable {
                    showInputDialog = UserSettingOption.Name(m.nickname)
                }) {
                    Text("Name")
                    Spacer(modifier = Modifier.weight(1f))
                    my?.nickname?.let { it1 -> Text(it1, textDecoration = TextDecoration.Underline) }
                }
                val aid = m.aid
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(60.dp).clickable(aid == null) {
                        showInputDialog = UserSettingOption.Aid(aid)
                    }) {
                    Text("Aid")
                    Spacer(modifier = Modifier.weight(1f))
                    if (aid == null) {
                        Text("undefined", textDecoration = TextDecoration.Underline)
                    } else {
                        Text(aid)
                    }
                }
            }
        }
    }
    val client = LocalClient.current
    val scope = rememberCoroutineScope()
    val updateIcon = { info: MediaInfo ->
        scope.launch {
            globalDialogState.use {
                val newInfo = client.updateUserInfo(UserInfo.EMPTY.copy(avatar = info)).getOrThrow()
                LoginViewModel.updateUser(newInfo)
                showInputDialog = null
            }
        }
    }
    MediaPicker(showSheet, sheetState, null, {
        updateIcon(it)
    }, {
        updateIcon(it)
    }, support = listOf("files")) {
        showInputDialog = null
    }
    showInputDialog?.let {
        InputDialog(true, it.name.orEmpty(), {
            showInputDialog = null
        }) {
            scope.launch {
                when (showInputDialog) {
                    is UserSettingOption.Name -> {
                        globalDialogState.use {
                            val newInfo = client.updateUserInfo(UserInfo.EMPTY.copy(nickname = it)).getOrThrow()
                            LoginViewModel.updateUser(newInfo)
                            bus.emit(OnUpdateUser(newInfo))
                            showInputDialog = null
                        }
                    }

                    is UserSettingOption.Aid -> {
                        globalDialogState.use {
                            val newInfo = client.updateUserInfo(UserInfo.EMPTY.copy(aid = it)).getOrThrow()
                            LoginViewModel.updateUser(newInfo)
                            bus.emit(OnUpdateUser(newInfo))
                            showInputDialog = null
                        }
                    }

                    else -> {}
                }
            }
        }
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
