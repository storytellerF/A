package com.storyteller_f.a.app.pages.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import com.storyteller_f.a.api.SignInBody
import com.storyteller_f.a.app.AppGlobalDialogController
import com.storyteller_f.a.app.CustomUserSessionManager
import com.storyteller_f.a.app.LocalAccountSwitcher
import com.storyteller_f.a.app.LocalAppNavFactory
import com.storyteller_f.a.app.LocalGlobalDialog
import com.storyteller_f.a.app.LocalSessionManager
import com.storyteller_f.a.app.Res
import com.storyteller_f.a.app.common.UserScreen
import com.storyteller_f.a.app.common.hasRouteFlow
import com.storyteller_f.a.app.connected
import com.storyteller_f.a.app.create
import com.storyteller_f.a.app.disconnected
import com.storyteller_f.a.app.file_explorer
import com.storyteller_f.a.app.grant_notification
import com.storyteller_f.a.app.preference
import com.storyteller_f.a.app.settings
import com.storyteller_f.a.app.sign_out
import com.storyteller_f.a.app.sign_out_prompt
import com.storyteller_f.a.app.switch_account
import com.storyteller_f.a.app.ui.MaterialSymbolsOutlined
import com.storyteller_f.a.app.utils.createConnectivity
import com.storyteller_f.a.app.utils.unregisterPushService
import com.storyteller_f.a.app.core.CoreStrings
import com.storyteller_f.a.app.core.components.ButtonNav
import com.storyteller_f.a.app.core.components.CustomAlertDialog
import com.storyteller_f.a.app.core.components.CustomAlertDialogController
import com.storyteller_f.a.app.core.components.CustomIcon
import com.storyteller_f.a.app.core.components.DialogContainer
import com.storyteller_f.a.app.core.components.IconRes
import com.storyteller_f.a.app.core.components.SignInButton
import com.storyteller_f.a.app.core.components.UserIcon
import com.storyteller_f.a.client.core.ClientSessionState
import com.storyteller_f.a.client.core.LoadingHandler
import com.storyteller_f.a.client.core.LoadingState
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.getData
import com.storyteller_f.a.client.core.getUserInfo
import com.storyteller_f.a.client.core.getUserInfoByAid
import com.storyteller_f.a.client.core.signIn
import com.storyteller_f.a.client.core.signOut
import com.storyteller_f.shared.finalData
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserOverview
import dev.jordond.connectivity.Connectivity
import dev.jordond.connectivity.compose.rememberConnectivityState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import nl.jacobras.humanreadable.HumanReadable
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter
import org.jetbrains.compose.ui.tooling.preview.PreviewParameterProvider

@Composable
private fun SelfDialogInternal(
    signOutController: CustomAlertDialogController,
    userInfo: UserInfo?,
    dismiss: () -> Unit,
    clickCreate: () -> Unit,
    overviewHandler: LoadingHandler<UserOverview>
) {
    val sessionManager = LocalSessionManager.current
    val isAlreadySignIn by sessionManager.isAlreadySignIn.collectAsState()
    DialogContainer {
        if (isAlreadySignIn) {
            SelfUserDetailCard(userInfo, dismiss, overviewHandler, clickCreate)
            AccountSwitchButton(dismiss, overviewHandler)
            NotificationButton()
            ConnectionButton()
            FileExplorerButton(dismiss)
            SettingsButton(dismiss)
            SignOutButton(signOutController)
        } else {
            SignInBox(dismiss)
        }
        SystemSettingsButton(dismiss)
    }
}

@Composable
private fun RowScope.StatCell(value: Long, iconRes: IconRes, onClick: () -> Unit) {
    val str = remember(value) {
        HumanReadable.number(value.toFloat())
    }
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier.weight(1f).clip(shape).clickable {
            onClick()
        }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CustomIcon(iconRes)
        Spacer(modifier = Modifier.width(8.dp))
        Text(str)
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
    ButtonNav(
        MaterialSymbolsOutlined.Settings,
        stringResource(Res.string.preference)
    ) {
        dismiss()
        appNavFactory.newAppNav().gotoPreference()
    }
}

@Composable
fun CreateButton(dismiss: () -> Unit, clickCreate: () -> Unit) {
    ButtonNav(Icons.Default.Add, stringResource(Res.string.create)) {
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
fun UserCard(
    @PreviewParameter(UserInfoPreviewProvider::class) userInfo: UserInfo?,
    dismiss: () -> Unit = {}
) {
    UserCardContainer(userInfo, dismiss) {
        UnboundSimpleUserCell(userInfo)
    }
}

@Composable
fun UserCardContainer(userInfo: UserInfo?, dismiss: () -> Unit, content: @Composable () -> Unit) {
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
    Box(modifier) {
        content()
    }
}

@Composable
fun SelfUserDetailCard(
    userInfo: UserInfo?,
    dismiss: () -> Unit = {},
    overviewHandler: LoadingHandler<UserOverview>,
    onClickCreate: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        UserCardContainer(userInfo, dismiss) {
            UnboundSimpleUserCell(userInfo)
        }
        val userOverview by overviewHandler.data.collectAsState()
        val userOverviewState by overviewHandler.state.collectAsState()
        val isLoading by remember {
            derivedStateOf {
                userOverviewState is LoadingState.Loading
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton({
                dismiss()
                onClickCreate()
            }) {
                Icon(Icons.Default.Add, "create")
            }
            UserOverviewRow(userOverview, isLoading, dismiss)
        }
    }
}

@Composable
private fun UserOverviewRow(
    userOverview: UserOverview?,
    isLoading: Boolean,
    dismiss: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    val appNav = LocalAppNavFactory.current
    Row(
        modifier = Modifier.clip(shape)
            .background(MaterialTheme.colorScheme.surfaceDim, shape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatCell(
            userOverview?.acg ?: 0,
            if (isLoading) IconRes.Loading else IconRes.Vector(Icons.Default.AccountBalanceWallet)
        ) {
        }
        StatCell(
            userOverview?.favoriteCount ?: 0,
            if (isLoading) IconRes.Loading else IconRes.Vector(Icons.Default.Favorite)
        ) {
            dismiss()
            appNav.newAppNav().gotoFavoritePage()
        }
        StatCell(
            userOverview?.subscriptionCount ?: 0,
            if (isLoading) IconRes.Loading else IconRes.Vector(Icons.Default.NotificationsActive)
        ) {
            dismiss()
            appNav.newAppNav().gotoSubscriptionPage()
        }
        StatCell(
            userOverview?.reactionRecordCount ?: 0,
            if (isLoading) IconRes.Loading else IconRes.Vector(Icons.Default.ThumbUp)
        ) {
            dismiss()
            appNav.newAppNav().gotoUserReactionRecordsPage()
        }
        StatCell(
            userOverview?.commentCount ?: 0,
            if (isLoading) IconRes.Loading else IconRes.Vector(Icons.Default.ChatBubble)
        ) {
            dismiss()
            appNav.newAppNav().gotoUserCommentsPage()
        }
    }
}

@Composable
private fun UnboundSimpleUserCell(userInfo: UserInfo?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
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
                    Text(CoreStrings.aid(aid), style = MaterialTheme.typography.labelSmall)
                } else {
                    Text(
                        CoreStrings.ad(userInfo.address),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
fun AccountSwitchButton(dismiss: () -> Unit, overviewHandler: LoadingHandler<UserOverview>) {
    val accountSwitcher = LocalAccountSwitcher.current
    val userOverview by overviewHandler.data.collectAsState()
    val userOverviewState by overviewHandler.state.collectAsState()
    val isLoading by remember {
        derivedStateOf {
            userOverviewState is LoadingState.Loading
        }
    }
    ButtonNav(
        if (isLoading) IconRes.Loading else IconRes.Vector(Icons.Default.SwitchAccount),
        stringResource(Res.string.switch_account),
        {
            ButtonBadgeSuffix(userOverview?.childAccountCount ?: 0)
        }
    ) {
        dismiss()
        accountSwitcher.switch()
    }
}

@Composable
fun ButtonBadgeSuffix(number: Long) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        number.toString(),
        modifier = Modifier.clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
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
        ButtonNav(Icons.Default.Notifications, stringResource(Res.string.grant_notification)) {
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
                stringResource(Res.string.connected)
            )
        }

        else -> {
            ButtonNav(
                MaterialSymbolsOutlined.SignalDisconnected,
                stringResource(Res.string.disconnected)
            )
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

@Composable
fun FileExplorerButton(dismiss: () -> Unit) {
    val appNavFactory = LocalAppNavFactory.current
    ButtonNav(Icons.Default.Folder, stringResource(Res.string.file_explorer)) {
        dismiss()
        appNavFactory.newAppNav().gotoFileExplorer()
    }
}

suspend fun signOut(
    sessionManager: CustomUserSessionManager,
    globalDialogController: AppGlobalDialogController,
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
                        sessionManager.signIn(SignInBody(address, signature)).getOrThrow()
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
                UserCard(userInfo, dismiss)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SelfDialog(
    userInfo: UserInfo?,
    showDialog: Boolean,
    overviewHandler: LoadingHandler<UserOverview>,
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
            SelfDialogInternal(signOutController, userInfo, dismiss, clickCreate, overviewHandler)
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
    overviewHandler: LoadingHandler<UserOverview>,
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
        overviewHandler,
        onClickCreate
    ) {
        showUserDialog = false
    }
}
