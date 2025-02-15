package com.storyteller_f.a.app.pages.user

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.settings
import a.composeapp.generated.resources.sign_out
import a.composeapp.generated.resources.sign_out_prompt
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.LocalClient
import com.storyteller_f.a.app.UserScreen
import com.storyteller_f.a.app.compontents.ButtonNav
import com.storyteller_f.a.app.compontents.CustomAlertDialog
import com.storyteller_f.a.app.compontents.CustomAlertDialogController
import com.storyteller_f.a.app.compontents.DialogContainer
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.toRoute
import com.storyteller_f.a.app.utils.clearStorage
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.a.client_lib.getUserInfo
import com.storyteller_f.a.client_lib.getUserInfoByAid
import com.storyteller_f.a.client_lib.signOut
import com.storyteller_f.shared.model.UserInfo
import io.ktor.client.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDialogInternal(userInfo: UserInfo, clickCreate: () -> Unit, dismiss: () -> Unit = {}) {
    val controller = remember {
        CustomAlertDialogController()
    }
    val appNav = LocalAppNav.current
    val client = LocalClient.current
    val my by LoginViewModel.user.collectAsState()
    DialogContainer {
        val clickable = !appNav.hasRoute(UserScreen::class) || appNav.toRoute<UserScreen>()?.uid != userInfo.id
        UserCell(
            userInfo,
            false,
            couldShowDialog = false,
            clickable = clickable
        ) {
            dismiss()
            appNav.gotoUser(it.id)
        }
        Column {
            if (my?.id == userInfo.id) {
                LaunchedEffect(null) {
                    refreshMyInfo(my, client)
                }
                ButtonNav(Icons.Default.Add, "Create") {
                    dismiss()
                    clickCreate()
                }
                val title = stringResource(Res.string.sign_out_prompt)
                ButtonNav(Icons.Default.Settings, stringResource(Res.string.settings)) {
                    dismiss()
                    appNav.gotoUserSetting()
                }
                ButtonNav(Icons.AutoMirrored.Default.Logout, stringResource(Res.string.sign_out)) {
                    controller.showTitle(title)
                }
            }
        }
    }
    val scope = rememberCoroutineScope()
    CustomAlertDialog(controller, {
        controller.close()
    }) {
        scope.launch {
            signOut(client)
        }
    }
}

suspend fun signOut(client: HttpClient) {
    globalDialogState.use {
        client.signOut()
        LoginViewModel.signOut()
        clearStorage()
    }
}

@OptIn(DelicateCoroutinesApi::class)
private fun refreshMyInfo(my: UserInfo?, client: HttpClient) {
    my ?: return
    GlobalScope.launch {
        val aid = my.aid
        if (aid.isNullOrBlank()) {
            client.getUserInfo(my.id)
        } else {
            client.getUserInfoByAid(aid)
        }.getOrNull()?.let {
            LoginViewModel.updateUser(it)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun UserDialog(
    userInfo: UserInfo?,
    showDialog: Boolean,
    clickCreate: () -> Unit,
    dismiss: () -> Unit
) {
    if (userInfo != null && showDialog) {
        BasicAlertDialog({
            dismiss()
        }) {
            UserDialogInternal(userInfo, clickCreate, dismiss)
        }
    }
}
