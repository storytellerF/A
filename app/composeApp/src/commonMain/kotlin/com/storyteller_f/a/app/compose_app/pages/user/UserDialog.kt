package com.storyteller_f.a.app.compose_app.pages.user

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kdroid.composenotification.builder.getNotificationProvider
import com.storyteller_f.a.app.compose_app.*
import com.storyteller_f.a.app.compose_app.compontents.*
import com.storyteller_f.a.app.compose_app.pages.LoginButton
import com.storyteller_f.a.app.compose_app.ui.MaterialSymbolsOutlined
import com.storyteller_f.a.app.core.utils.clearStorage
import com.storyteller_f.a.app.compose_app.utils.createConnectivity
import com.storyteller_f.a.app.compose_app.utils.unregisterPushService
import com.storyteller_f.a.client.core.ClientSessionState
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.getData
import com.storyteller_f.a.client.core.getUserInfo
import com.storyteller_f.a.client.core.getUserInfoByAid
import com.storyteller_f.a.client.core.signIn
import com.storyteller_f.a.client.core.signOut
import com.storyteller_f.shared.SignInPack
import com.storyteller_f.shared.finalData
import com.storyteller_f.shared.model.UserInfo
import dev.jordond.connectivity.Connectivity
import dev.jordond.connectivity.compose.rememberConnectivityState
import io.github.aakira.napier.Napier
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
    val sessionManager = LocalSessionManager.current
    LaunchedEffect(isMe, userInfo) {
        if (isMe) {
            refreshMyInfo(userInfo, sessionManager)
        }
    }
    val scope = rememberCoroutineScope()
    val isSignIn by sessionManager.isAlreadySignUp.collectAsState()
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
                iconSize = 60.dp
            ) {
                dismiss()
                appNav.gotoUser(it.id)
            }
        }
        Column {
            if (isMe && isSignIn) {
                ButtonNav(MaterialSymbolsOutlined.Money, "ACG ${userInfo?.acg ?: 0}")
                UserDialogMenuList(dismiss, clickCreate, appNav, controller)
            }
            ButtonNav(MaterialSymbolsOutlined.Settings, "Preference") {
                dismiss()
                appNav.gotoPreference()
            }
        }
    }
    val globalDialogController = LocalGlobalDialog.current
    CustomAlertDialog(controller, {
        controller.close()
    }) {
        scope.launch {
            signOut(sessionManager, globalDialogController)
        }
    }
}

@Composable
private fun UserDialogMenuList(
    dismiss: () -> Unit,
    clickCreate: () -> Unit,
    appNav: AppNav,
    controller: CustomAlertDialogController,
) {
    ButtonNav(Icons.Default.Add, "Create") {
        dismiss()
        clickCreate()
    }
    val accountSwitcher = LocalAccountSwitcher.current
    ButtonNav(Icons.Default.SwitchAccount, "Switch Account") {
        dismiss()
        accountSwitcher.switch()
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
    val connectivity = remember {
        createConnectivity()
    }
    val scope = rememberCoroutineScope()
    val connectivityState = rememberConnectivityState(connectivity, scope)
    when (val s = connectivityState.status) {
        is Connectivity.Status.Connected -> {
            ButtonNav(if (s.metered) Icons.Default.SignalCellularAlt else Icons.Default.Wifi, "Connected")
        }

        else -> {
            ButtonNav(MaterialSymbolsOutlined.SignalDisconnected, "Disconnected")
        }
    }
    val title = stringResource(Res.string.sign_out_prompt)
    ButtonNav(MaterialSymbolsOutlined.SettingsAccountBox, stringResource(Res.string.settings)) {
        dismiss()
        appNav.gotoUserSetting()
    }
    val (_, isSwitched) = isSwitched()
    if (!isSwitched) {
        ButtonNav(Icons.AutoMirrored.Default.Logout, stringResource(Res.string.sign_out)) {
            controller.showTitle(title)
        }
    }
}

suspend fun signOut(
    sessionManager: UserSessionManager,
    globalDialogController: GlobalDialogController,
) {
    val settings = (sessionManager as CustomUserSessionManager).settings
    globalDialogController.useResult {
        sessionManager.signOut()
    }.onSuccess {
        sessionManager.model.clear()
        clearStorage(settings)
        unregisterPushService()
    }
}

@OptIn(DelicateCoroutinesApi::class)
private fun refreshMyInfo(my: UserInfo?, sessionManager: UserSessionManager) {
    GlobalScope.launch {
        try {
            val sessionModel = sessionManager.model
            if (my == null) {
                val value = sessionModel.state.value
                if (value is ClientSessionState.Success) {
                    val data = sessionManager.getData().getOrThrow()
                    val address = value.session.address().getOrThrow()
                    val signature = value.session.signature(finalData(data)).getOrThrow()
                    val userInfo = sessionManager.signIn(SignInPack(address, signature)).getOrThrow()
                    sessionModel.updateUser(userInfo)
                    sessionModel.updateSignature(data, signature)
                }
            } else {
                val aid = my.aid
                val userInfo = if (aid.isNullOrBlank()) {
                    sessionManager.getUserInfo(my.id)
                } else {
                    sessionManager.getUserInfoByAid(aid)
                }.getOrThrow()
                sessionModel.updateUser(userInfo)
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
    dismiss: () -> Unit,
) {
    if (showDialog) {
        BasicAlertDialog({
            dismiss()
        }) {
            UserDialogInternal(isMe, userInfo, clickCreate, dismiss)
        }
    }
}
