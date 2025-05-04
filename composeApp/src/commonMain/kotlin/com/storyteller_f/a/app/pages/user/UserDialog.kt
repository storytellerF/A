package com.storyteller_f.a.app.pages.user

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.settings
import a.composeapp.generated.resources.sign_out
import a.composeapp.generated.resources.sign_out_prompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kdroid.composenotification.builder.getNotificationProvider
import com.storyteller_f.a.app.*
import com.storyteller_f.a.app.compontents.ButtonNav
import com.storyteller_f.a.app.compontents.CustomAlertDialog
import com.storyteller_f.a.app.compontents.CustomAlertDialogController
import com.storyteller_f.a.app.compontents.DialogContainer
import com.storyteller_f.a.app.ui.MaterialSymbolsOutlined
import com.storyteller_f.a.app.utils.clearStorage
import com.storyteller_f.a.client_lib.ClientSession
import com.storyteller_f.a.client_lib.SignInViewModel
import com.storyteller_f.a.client_lib.getData
import com.storyteller_f.a.client_lib.getUserInfo
import com.storyteller_f.a.client_lib.getUserInfoByAid
import com.storyteller_f.a.client_lib.signIn
import com.storyteller_f.a.client_lib.signOut
import com.storyteller_f.shared.finalData
import com.storyteller_f.shared.model.UserInfo
import io.github.aakira.napier.Napier
import io.ktor.client.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun UserDialogInternal(isMe: Boolean, userInfo: UserInfo?, clickCreate: () -> Unit, dismiss: () -> Unit = {}) {
    val controller = remember {
        CustomAlertDialogController()
    }
    val appNav = LocalAppNav.current
    val client = LocalClient.current
    LaunchedEffect(isMe, userInfo) {
        if (isMe) {
            refreshMyInfo(userInfo, client)
        }
    }
    val scope = rememberCoroutineScope()
    val isSignIn by SignInViewModel.isAlreadySignUp.collectAsState()
    DialogContainer {
        if (!isSignIn && isMe) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth()) {
                LoginButton {
                    dismiss()
                }
            }
        } else {
            val isUserPage by appNav.hasRouteFlow<UserScreen> {
                it.uid == userInfo?.id
            }.collectAsState(false)
            UserCell(
                userInfo,
                false,
                iconClickable = false,
                cellClickable = !isUserPage,
                size = 60.dp
            ) {
                dismiss()
                appNav.gotoUser(it.id)
            }
        }
        Column {
            if (isMe && isSignIn) {
                ButtonNav(MaterialSymbolsOutlined.Star, "acg ${userInfo?.acg ?: 0}")
                UserDialogMenuList(dismiss, clickCreate, appNav, controller)
            }
            ButtonNav(Icons.Default.Settings, "preference") {
                dismiss()
                appNav.gotoPreference()
            }
        }
    }
    CustomAlertDialog(controller, {
        controller.close()
    }) {
        scope.launch {
            signOut(client)
        }
    }
}

@Composable
private fun UserDialogMenuList(
    dismiss: () -> Unit,
    clickCreate: () -> Unit,
    appNav: AppNav,
    controller: CustomAlertDialogController
) {
    ButtonNav(Icons.Default.Add, "Create") {
        dismiss()
        clickCreate()
    }
    val notificationProvider = getNotificationProvider()
    val hasPermission by notificationProvider.hasPermissionState

    if (!hasPermission) {
        ButtonNav(Icons.Default.Notifications, "Grant notification") {
            notificationProvider.requestPermission(
                onGranted = {
                    notificationProvider.updatePermissionState(true)
                },
                onDenied = {
                    notificationProvider.updatePermissionState(false)
                }
            )
        }
    }
    val title = stringResource(Res.string.sign_out_prompt)
    ButtonNav(MaterialSymbolsOutlined.SettingsAccountBox, stringResource(Res.string.settings)) {
        dismiss()
        appNav.gotoUserSetting()
    }
    ButtonNav(Icons.AutoMirrored.Default.Logout, stringResource(Res.string.sign_out)) {
        controller.showTitle(title)
    }
}

suspend fun signOut(client: HttpClient) {
    globalDialogState.use {
        client.signOut()
        SignInViewModel.signOut()
        clearStorage()
    }
}

@OptIn(DelicateCoroutinesApi::class)
private fun refreshMyInfo(my: UserInfo?, client: HttpClient) {
    GlobalScope.launch {
        try {
            if (my == null) {
                val value = SignInViewModel.state.value
                if (value is ClientSession.SignInSuccess) {
                    val data = client.getData().getOrThrow()
                    val address = value.session.address().getOrThrow()
                    val signature = value.session.signature(finalData(data)).getOrThrow()
                    val userInfo = client.signIn(address, signature).getOrThrow()
                    SignInViewModel.updateUser(userInfo)
                }
            } else {
                val aid = my.aid
                val userInfo = if (aid.isNullOrBlank()) {
                    client.getUserInfo(my.id)
                } else {
                    client.getUserInfoByAid(aid)
                }.getOrThrow()
                SignInViewModel.updateUser(userInfo)
            }
        } catch (e: Exception) {
            Napier.e(e) {
                "refresh user info"
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun UserDialog(
    isMe: Boolean,
    userInfo: UserInfo?,
    showDialog: Boolean,
    clickCreate: () -> Unit,
    dismiss: () -> Unit
) {
    if (showDialog) {
        BasicAlertDialog({
            dismiss()
        }) {
            UserDialogInternal(isMe, userInfo, clickCreate, dismiss)
        }
    }
}
