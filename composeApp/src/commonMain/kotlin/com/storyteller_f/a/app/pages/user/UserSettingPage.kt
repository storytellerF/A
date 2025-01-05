package com.storyteller_f.a.app.pages.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.compontents.UserIcon
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.pages.topic.MediaPicker
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.a.client_lib.updateUserInfo
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.UserInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSettingPage() {
    var showSheet by remember {
        mutableStateOf(false)
    }
    var showInputDialog by remember {
        mutableStateOf(false)
    }
    val sheetState = rememberModalBottomSheetState()
    val my by LoginViewModel.user.collectAsState()
    Scaffold {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(it)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(60.dp).clickable {
                showSheet = true
            }) {
                Text("Icon")
                Spacer(modifier = Modifier.weight(1f))
                UserIcon(my)
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(60.dp).clickable {
                showInputDialog = true
            }) {
                Text("Name")
                Spacer(modifier = Modifier.weight(1f))
                my?.nickname?.let { it1 -> Text(it1, textDecoration = TextDecoration.Underline) }
            }
        }
    }
    val scope = rememberCoroutineScope()
    val updateIcon = { info: MediaInfo ->
        scope.launch {
            globalDialogState.use {
                val newInfo = client.updateUserInfo(UserInfo.EMPTY.copy(avatar = info)).getOrThrow()
                LoginViewModel.updateUser(newInfo)
                showSheet = false
            }
        }
    }
    MediaPicker(showSheet, sheetState, null, {
        updateIcon(it)
    }, {
        updateIcon(it)
    }, support = listOf("files")) {
        showSheet = false
    }
    InputDialog(showInputDialog, my?.nickname.orEmpty(), {
        showInputDialog = false
    }) {
        scope.launch {
            globalDialogState.use {
                val newInfo = client.updateUserInfo(UserInfo.EMPTY.copy(nickname = it)).getOrThrow()
                LoginViewModel.updateUser(newInfo)
                showInputDialog = false
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
