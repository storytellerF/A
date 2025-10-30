package com.storyteller_f.a.app.compose_app.pages.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kdroid.composenotification.builder.getNotificationProvider
import com.storyteller_f.a.app.compose_app.CustomUserSessionManager
import com.storyteller_f.a.app.compose_app.LocalAccountSwitcher
import com.storyteller_f.a.app.compose_app.LocalAppNavFactory
import com.storyteller_f.a.app.compose_app.LocalGlobalDialog
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.Res
import com.storyteller_f.a.app.compose_app.common.UserScreen
import com.storyteller_f.a.app.compose_app.common.hasRouteFlow
import com.storyteller_f.a.app.compose_app.components.ButtonNav
import com.storyteller_f.a.app.compose_app.components.CustomAlertDialog
import com.storyteller_f.a.app.compose_app.components.CustomAlertDialogController
import com.storyteller_f.a.app.compose_app.components.DialogContainer
import com.storyteller_f.a.app.compose_app.components.GlobalDialogController
import com.storyteller_f.a.app.compose_app.settings
import com.storyteller_f.a.app.compose_app.sign_out
import com.storyteller_f.a.app.compose_app.sign_out_prompt
import com.storyteller_f.a.app.compose_app.ui.MaterialSymbolsOutlined
import com.storyteller_f.a.app.compose_app.utils.createConnectivity
import com.storyteller_f.a.app.compose_app.utils.unregisterPushService
import com.storyteller_f.a.app.core.compontents.SignInButton
import com.storyteller_f.a.app.core.compontents.UserIcon
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
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter
import org.jetbrains.compose.ui.tooling.preview.PreviewParameterProvider

@Composable
private fun SelfDialogInternal(
    signOutController: CustomAlertDialogController,
    userInfo: UserInfo?,
    dismiss: () -> Unit,
    clickCreate: () -> Unit
) {
    val sessionManager = LocalSessionManager.current
    val isAlreadySignIn by sessionManager.isAlreadySignIn.collectAsState()
    DialogContainer {
        if (isAlreadySignIn) {
            UserDialogUserInfoCell(userInfo, dismiss)
        } else {
            SignInBox(dismiss)
        }
        Column {
            if (isAlreadySignIn) {
                ButtonNav(MaterialSymbolsOutlined.Money, "ACG ${userInfo?.acg ?: 0}")
                CreateButton(dismiss, clickCreate)
                AccountSwitchButton(dismiss)
                NotificationButton()
                ConnectionButton()
                SettingsButton(dismiss)
                SignOutButton(signOutController)
                FavoriteButton(dismiss)
                SubscriptionButton(dismiss)
            }
            SystemSettingsButton(dismiss)
        }
    }
}

@Composable
fun FavoriteButton(dismiss: () -> Unit) {
    val appNavFactory = LocalAppNavFactory.current
    ButtonNav(Icons.Default.Favorite, "Favorites") {
        dismiss()
        appNavFactory.newAppNav().gotoFavoritePage()
    }
}

@Composable
fun SubscriptionButton(dismiss: () -> Unit) {
    val appNavFactory = LocalAppNavFactory.current
    ButtonNav(Icons.Default.NotificationsActive, "Subscriptions") {
        dismiss()
        appNavFactory.newAppNav().gotoSubscriptionPage()
    }
}

@Composable
fun SignInBox(dismiss: () -> Unit) {
    val appNavFactory = LocalAppNavFactory.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth()
    ) {
        SignInButton {
            dismiss()
            appNavFactory.newAppNav().gotoLogin()
        }
    }
}

@Composable
fun SystemSettingsButton(dismiss: () -> Unit) {
    val appNavFactory = LocalAppNavFactory.current
    ButtonNav(MaterialSymbolsOutlined.Settings, "Preference") {
        dismiss()
        appNavFactory.newAppNav().gotoPreference()
    }
}

@Composable
fun CreateButton(dismiss: () -> Unit, clickCreate: () -> Unit) {
    ButtonNav(Icons.Default.Add, "Create") {
        dismiss()
        clickCreate()
    }
}

class UserInfoPreviewProvider : PreviewParameterProvider<UserInfo> {
    override val values: Sequence<UserInfo>
        get() = sequenceOf(UserInfo.EMPTY.copy(nickname = "hello"))
}

@Preview
@Composable
fun UserDialogUserInfoCell(
    @PreviewParameter(UserInfoPreviewProvider::class) userInfo: UserInfo?,
    dismiss: () -> Unit = {}
) {
    val appNavFactory = LocalAppNavFactory.current
    val isUserPage by appNavFactory.hasRouteFlow<UserScreen> {
        it.uid == userInfo?.id
    }
    val shape = RoundedCornerShape(8.dp)
    val cellClickable = !isUserPage
    val modifier = Modifier.fillMaxWidth()
        .testTag("user-dialog-cell")
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceDim, shape)
        .clickable(userInfo != null && cellClickable) {
            dismiss()
            userInfo?.id?.let { appNavFactory.newAppNav().gotoUser(it) }
        }.padding(8.dp)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UserIcon(
            setClickEvent = false,
            userInfo?.avatar?.url,
            60.dp
        ) {}
        if (userInfo != null) {
            Column {
                Text(userInfo.nickname, style = MaterialTheme.typography.titleMedium)
                val aid = userInfo.aid
                if (aid != null) {
                    Text("aid: $aid", style = MaterialTheme.typography.labelSmall)
                } else {
                    Text("ad: ${userInfo.address}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun AccountSwitchButton(dismiss: () -> Unit) {
    val accountSwitcher = LocalAccountSwitcher.current
    ButtonNav(Icons.Default.SwitchAccount, "Switch Account") {
        dismiss()
        accountSwitcher.switch()
    }
}

@Composable
fun SignOutButton(controller: CustomAlertDialogController) {
    val title = stringResource(Res.string.sign_out_prompt)
    val isInChildAccount by isInChildAccount()
    if (!isInChildAccount) {
        ButtonNav(Icons.AutoMirrored.Default.Logout, stringResource(Res.string.sign_out)) {
            controller.showTitle(title)
        }
    }
}

@Composable
fun NotificationButton() {
    val inspectionMode = LocalInspectionMode.current
    if (inspectionMode) return
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
}

@Composable
fun ConnectionButton() {
    val inspectionMode = LocalInspectionMode.current
    if (inspectionMode) return
    val connectivity = remember {
        createConnectivity()
    }
    val scope = rememberCoroutineScope()
    val connectivityState = rememberConnectivityState(connectivity, scope)
    when (val s = connectivityState.status) {
        is Connectivity.Status.Connected -> {
            ButtonNav(
                if (s.metered) Icons.Default.SignalCellularAlt else Icons.Default.Wifi,
                "Connected"
            )
        }

        else -> {
            ButtonNav(MaterialSymbolsOutlined.SignalDisconnected, "Disconnected")
        }
    }
}

@Composable
fun SettingsButton(dismiss: () -> Unit) {
    val appNavFactory = LocalAppNavFactory.current
    ButtonNav(MaterialSymbolsOutlined.SettingsAccountBox, stringResource(Res.string.settings)) {
        dismiss()
        appNavFactory.newAppNav().gotoUserSetting()
    }
}

suspend fun signOut(
    sessionManager: CustomUserSessionManager,
    globalDialogController: GlobalDialogController,
) {
    globalDialogController.useResult {
        sessionManager.signOut()
    }.onSuccess {
        sessionManager.clearSession()
        unregisterPushService()
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun refreshMyInfo(my: UserInfo?, sessionManager: UserSessionManager) {
    GlobalScope.launch {
        try {
            val sessionModel = sessionManager.model
            if (my == null) {
                val value = sessionModel.state.value
                if (value is ClientSessionState.Success) {
                    val data = sessionManager.getData().getOrThrow()
                    val address = value.userPass.address().getOrThrow()
                    val signature = value.userPass.signature(finalData(data)).getOrThrow()
                    val userInfo =
                        sessionManager.signIn(SignInPack(address, signature)).getOrThrow()
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
    userInfo: UserInfo?,
    showDialog: Boolean,
    dismiss: () -> Unit,
) {
    if (showDialog) {
        BasicAlertDialog({
            dismiss()
        }) {
            DialogContainer {
                UserDialogUserInfoCell(userInfo, dismiss)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SelfDialog(
    userInfo: UserInfo?,
    showDialog: Boolean,
    clickCreate: () -> Unit,
    dismiss: () -> Unit,
) {
    if (showDialog) {
        BasicAlertDialog({
            dismiss()
        }) {
            val signOutController = remember {
                CustomAlertDialogController()
            }
            val sessionManager = LocalSessionManager.current
            LaunchedEffect(userInfo) {
                refreshMyInfo(userInfo, sessionManager)
            }
            SelfDialogInternal(signOutController, userInfo, dismiss, clickCreate)
            val scope = rememberCoroutineScope()
            val globalDialogController = LocalGlobalDialog.current
            CustomAlertDialog(signOutController, {
                signOutController.close()
            }) {
                scope.launch {
                    signOut(sessionManager, globalDialogController)
                }
            }
        }
    }
}

@Composable
fun UserIconWithDialog(
    userInfo: UserInfo?,
    setClickEvent: Boolean = true,
    size: Dp = 40.dp,
) {
    var showUserDialog by remember {
        mutableStateOf(false)
    }
    val url = userInfo?.avatar?.url
    UserIcon(setClickEvent, url, size = size) {
        showUserDialog = true
    }
    UserDialog(
        userInfo,
        showUserDialog
    ) {
        showUserDialog = false
    }
}

@Composable
fun SelfUserIconWithDialog(
    userInfo: UserInfo?,
    size: Dp = 40.dp,
    onClickCreate: () -> Unit = {},
) {
    var showUserDialog by remember {
        mutableStateOf(false)
    }
    val url = userInfo?.avatar?.url
    UserIcon(true, url, size = size) {
        showUserDialog = true
    }
    SelfDialog(
        userInfo,
        showUserDialog,
        onClickCreate
    ) {
        showUserDialog = false
    }
}
