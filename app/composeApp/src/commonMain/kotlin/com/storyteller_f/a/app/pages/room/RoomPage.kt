package com.storyteller_f.a.app.pages.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddHome
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.LocalPlatformContext
import com.storyteller_f.a.app.AppGlobalDialogController
import com.storyteller_f.a.app.LocalAppNavFactory
import com.storyteller_f.a.app.LocalGlobalDialog
import com.storyteller_f.a.app.LocalSessionManager
import com.storyteller_f.a.app.LocalUiViewModel
import com.storyteller_f.a.app.LocalUserInfo
import com.storyteller_f.a.app.Res
import com.storyteller_f.a.app.aid_display
import com.storyteller_f.a.app.all_members
import com.storyteller_f.a.app.bubble
import com.storyteller_f.a.app.check_mark
import com.storyteller_f.a.app.clear_input
import com.storyteller_f.a.app.common.AppNavFactory
import com.storyteller_f.a.app.common.IdRoomViewModel
import com.storyteller_f.a.app.common.OnRoomExited
import com.storyteller_f.a.app.common.OnRoomJoined
import com.storyteller_f.a.app.common.RoomKeysViewModel
import com.storyteller_f.a.app.common.RoomScreen
import com.storyteller_f.a.app.common.createRoomKeysViewModel
import com.storyteller_f.a.app.common.createRoomTopicsViewModel
import com.storyteller_f.a.app.common.createRoomViewModel
import com.storyteller_f.a.app.common.hasRoute
import com.storyteller_f.a.app.common.hasRouteFlow
import com.storyteller_f.a.app.core.components.ButtonNav
import com.storyteller_f.a.app.core.components.CustomAlertDialog
import com.storyteller_f.a.app.core.components.CustomAlertDialogController
import com.storyteller_f.a.app.core.components.DialogContainer
import com.storyteller_f.a.app.core.components.FavoriteButton
import com.storyteller_f.a.app.core.components.IconRes
import com.storyteller_f.a.app.core.components.LocalToaster
import com.storyteller_f.a.app.core.components.RoomIcon
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.SubscriptionButton
import com.storyteller_f.a.app.core.components.Toast
import com.storyteller_f.a.app.core.components.emitEvent
import com.storyteller_f.a.app.core.components.horizontalSafeArea
import com.storyteller_f.a.app.core.components.imeAnimation
import com.storyteller_f.a.app.core.components.rememberAlertDialogController
import com.storyteller_f.a.app.core.components.rememberCommonDialogController
import com.storyteller_f.a.app.core.components.request
import com.storyteller_f.a.app.core.utils.getRemoteImageBitmap
import com.storyteller_f.a.app.error
import com.storyteller_f.a.app.exclamation_mark
import com.storyteller_f.a.app.exit_room
import com.storyteller_f.a.app.files_title
import com.storyteller_f.a.app.input_is_empty
import com.storyteller_f.a.app.join_room
import com.storyteller_f.a.app.join_room_prompt
import com.storyteller_f.a.app.need_sign_in
import com.storyteller_f.a.app.open_in_full
import com.storyteller_f.a.app.pages.community.CommunityRefCell
import com.storyteller_f.a.app.pages.search.CustomSearchBar
import com.storyteller_f.a.app.pages.search.SearchScope
import com.storyteller_f.a.app.pages.topic.FilePicker
import com.storyteller_f.a.app.pages.topic.RoomTopicList
import com.storyteller_f.a.app.pages.topic.TopicComposeData
import com.storyteller_f.a.app.pages.topic.insertContent
import com.storyteller_f.a.app.pages.user.ButtonBadgeSuffix
import com.storyteller_f.a.app.permission_denied
import com.storyteller_f.a.app.private_room_pub_key_loading
import com.storyteller_f.a.app.send
import com.storyteller_f.a.app.settings_title
import com.storyteller_f.a.app.start_call
import com.storyteller_f.a.app.success
import com.storyteller_f.a.app.utils.notifyNotification
import com.storyteller_f.a.app.utils.startCall
import com.storyteller_f.a.client.core.LoadingState
import com.storyteller_f.a.client.core.WebSocketClient
import com.storyteller_f.a.client.core.addReadLog
import com.storyteller_f.a.client.core.exitRoom
import com.storyteller_f.a.client.core.getCommunityInfo
import com.storyteller_f.a.client.core.joinRoom
import com.storyteller_f.a.client.core.processEncryptedTopic
import com.storyteller_f.a.client.core.sendMessage
import com.storyteller_f.shared.getPlatform
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.obj.UpdateUserRead
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.checkContent
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun RoomPage(roomId: PrimaryKey, needShowDialog: Boolean) {
    val room = createRoomViewModel(roomId)
    RoomPageInternal(room, needShowDialog, roomId)
}

@Composable
private fun RoomPageInternal(
    room: IdRoomViewModel,
    needShowDialog: Boolean,
    roomId: PrimaryKey
) {
    val snackBarHost = remember {
        SnackbarHostState()
    }

    Scaffold(snackbarHost = {
        SnackbarHost(snackBarHost)
    }) {
        Column(modifier = Modifier.horizontalSafeArea(it, LocalLayoutDirection.current)) {
            var showDialog by remember {
                mutableStateOf(false)
            }
            val dialogShown by room.dialog.dialogShown.collectAsState()
            LaunchedEffect(needShowDialog, dialogShown) {
                if (needShowDialog && !dialogShown) {
                    room.dialog.markDialogShown()
                    showDialog = true
                }
            }
            val roomInfo by room.handler.data.collectAsState()
            CustomSearchBar(SearchScope.RoomTopic(roomId)) {
                RoomIconWithDialog(
                    roomInfo,
                    showDialog = showDialog,
                    width = 40.dp,
                    setClickEvent = true
                ) {
                    showDialog = it
                }
            }
            val lazyListState = rememberLazyListState()
            StateView(room.handler, Modifier.weight(1f)) {
                val roomId = it.id
                val viewModel = createRoomTopicsViewModel(roomId)
                val items = viewModel.flow.collectAsLazyPagingItems()
                Box(Modifier.weight(1f)) {
                    RoomTopicList(items, viewModel, lazyListState)
                    NewTopicView(lazyListState, it, items)
                }
            }
            val scope = rememberCoroutineScope()
            roomInfo?.let {
                RoomInputGroup(roomId, it, roomId ob ObjectType.ROOM, snackBarHost, {
                    showDialog = true
                }) {
                    scope.launch {
                        lazyListState.animateScrollToItem(0)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.NewTopicView(
    lazyListState: LazyListState,
    roomInfo: RoomInfo,
    items: LazyPagingItems<TopicInfo>
) {
    val firstVisibleItemScrollOffset by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemScrollOffset
        }
    }
    val firstVisibleItemIndex by remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }
    val sessionManager = LocalSessionManager.current
    LaunchedEffect(firstVisibleItemIndex, roomInfo) {
        delay(1000)
        try {
            if (items.itemCount > 0) {
                val info = items[firstVisibleItemIndex]
                if (info != null) {
                    sessionManager.addReadLog(UpdateUserRead(roomInfo.tuple(), info.id))
                        .getOrThrow()
                }
            }
        } catch (e: Exception) {
            Napier.e(e) {
                "add read log failed"
            }
        }
    }
    if (firstVisibleItemScrollOffset != 0 || firstVisibleItemIndex != 0) {
        val scope = rememberCoroutineScope()
        IconButton({
            scope.launch {
                lazyListState.animateScrollToItem(0, 0)
            }
        }, modifier = Modifier.align(Alignment.BottomStart)) {
            Icon(Icons.Default.ArrowCircleDown, "move to newer topic",)
        }
    }
}

@Composable
fun RoomInputGroup(
    roomId: PrimaryKey,
    roomInfo: RoomInfo?,
    parentTarget: ObjectTuple,
    snackBarHost: SnackbarHostState,
    startJoinRoom: () -> Unit,
    scrollToNew: () -> Unit
) {
    val appNavFactory = LocalAppNavFactory.current
    var input by remember {
        mutableStateOf("")
    }
    val userSessionManager = LocalSessionManager.current
    val myInfo = LocalUserInfo.current
    val controller = remember {
        CustomAlertDialogController()
    }
    val wsClient = userSessionManager.webSocketClient
    LaunchedEffect(wsClient.frameFlow) {
        wsClient.frameFlow.collect { frame ->
            if (frame is RoomFrame.Error) {
                snackBarHost.showSnackbar(frame.error, withDismissAction = true)
            } else if (frame is RoomFrame.NewTopicInfo) {
                val plainFrame = if (frame.topicInfo.content is TopicContent.Encrypted) {
                    val topicInfo = processEncryptedTopic(listOf(frame.topicInfo), userSessionManager).first()
                    RoomFrame.NewTopicInfo(topicInfo)
                } else {
                    frame
                }
                val topicInfo = plainFrame.topicInfo
                val content = topicInfo.content
                if (content is TopicContent.Plain && myInfo?.id == topicInfo.author && content.plain == input) {
                    input = ""
                }
            }
        }
    }
    if (roomInfo != null) {
        val localState by wsClient.localState.collectAsState()
        val isSending = localState is LoadingState.Loading

        RoomInputGroupInternal(roomId, roomInfo, parentTarget, controller, input, scrollToNew) {
            if (!isSending) {
                input = it
            }
        }
    }

    CustomAlertDialog(controller, {
        controller.close()
    }) {
        checkRoomRouteAndAlert(appNavFactory, roomId, startJoinRoom)
    }
}

@Composable
private fun RoomInputGroupInternal(
    roomId: PrimaryKey,
    roomInfo: RoomInfo,
    parentTarget: ObjectTuple,
    controller: CustomAlertDialogController,
    input: String,
    scrollToNew: () -> Unit,
    updateInput: (String) -> Unit,
) {
    val myInfo = LocalUserInfo.current
    val mediaTarget = if (roomInfo.isPrivate) {
        ObjectTuple(roomInfo.id, ObjectType.ROOM)
    } else {
        ObjectTuple(myInfo?.id ?: 0, ObjectType.USER)
    }

    val appNavFactory = LocalAppNavFactory.current
    InputGroupInternal(
        mediaTarget,
        MaterialTheme.colorScheme.tertiaryContainer,
        input,
        updateInput,
        {
            appNavFactory.newAppNav().gotoTopicCompose(
                roomInfo.communityId?.let {
                    TopicComposeData.PublicRoom(roomId, it, parentTarget)
                } ?: TopicComposeData.PrivateRoom(roomId, parentTarget)
            )
        },
        {
            RoomInputTopContent(roomInfo)
        },
        {
            RoomSendButton(input, roomInfo, parentTarget, controller, scrollToNew)
        }
    )
}

@Composable
private fun RoomInputTopContent(roomInfo: RoomInfo) {
    val keysViewModel = createRoomKeysViewModel(roomInfo.id, roomInfo)
    val keysData by keysViewModel.handler.data.collectAsState()
    val keysState by keysViewModel.handler.state.collectAsState()
    if (roomInfo.isPrivate) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (val ks = keysState) {
                LoadingState.Done -> Text(stringResource(Res.string.check_mark))
                is LoadingState.Error -> Text(
                    ks.e.localizedMessage?.take(10) ?: stringResource(Res.string.exclamation_mark)
                )

                else -> CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 2.dp)
            }
            Text("${keysData?.size ?: 0}/${roomInfo.memberCount}")
        }
    }
}

private fun sendRoomTopic(
    roomInfo: RoomInfo,
    input: String,
    scrollToNew: () -> Unit,
    scope: CoroutineScope,
    toasterState: Toast,
    keysViewModel: RoomKeysViewModel,
    wsClient: WebSocketClient,
    parentTarget: ObjectTuple,
) {
    val handler = keysViewModel.handler
    val keyState = handler.state.value
    val keyData = handler.data.value
    checkContent(input).exceptionOrNull()?.let {
        toasterState.showMessage(it.message.toString())
        return
    }
    if ((keyState !is LoadingState.Done || keyData == null) && roomInfo.isPrivate) {
        scope.launch {
            toasterState.showMessage(getString(Res.string.private_room_pub_key_loading),)
        }
        return
    }
    wsClient.useWebSocket {
        sendMessage(parentTarget, roomInfo.isPrivate, input, keyData.orEmpty())
        delay(500)
        scrollToNew()
    }
}

private fun checkRoomRouteAndAlert(
    appNavFactory: AppNavFactory,
    roomId: PrimaryKey,
    startJoinRoom: () -> Unit,
) {
    val appNav = appNavFactory.newAppNav()
    if (appNav.hasRoute<RoomScreen>()) {
        val navKey = appNav.backStack.last()
        if (navKey is RoomScreen && navKey.roomId == roomId) {
            startJoinRoom()
            return
        }
    }
    appNav.gotoRoom(roomId, true)
}

@Composable
fun RoomSendButton(
    input: String,
    roomInfo: RoomInfo,
    parentTarget: ObjectTuple,
    controller: CustomAlertDialogController,
    scrollToNew: () -> Unit,
) {
    val toasterState = LocalToaster.current
    val scope = rememberCoroutineScope()
    val uiViewModel = LocalUiViewModel.current
    val instance by uiViewModel.instance.collectAsState()
    val wsClient = instance.sessionManager.webSocketClient
    val state by wsClient.connectionHandler.state.collectAsState()
    val sendState by wsClient.localState.collectAsState()
    val isSending = sendState is LoadingState.Loading
    val keysViewModel = createRoomKeysViewModel(roomInfo.id, roomInfo)
    CommonInputButton(state, input, isSending) {
        if (roomInfo.isJoined) {
            sendRoomTopic(roomInfo, input, scrollToNew, scope, toasterState, keysViewModel, wsClient, parentTarget)
        } else {
            scope.launch {
                val title = getString(Res.string.permission_denied)
                val message = getString(Res.string.join_room_prompt)
                controller.showMessage(title, message)
            }
        }
    }
}

@Composable
fun CommonInputButton(
    state: LoadingState?,
    input: String,
    isSending: Boolean,
    send: () -> Unit,
) {
    val alertDialogController = rememberAlertDialogController()
    val scope = rememberCoroutineScope()
    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        when (state) {
            is LoadingState.Done -> {
                IconButton({
                    if (input.isBlank()) {
                        scope.launch {
                            alertDialogController.showTitle(getString(Res.string.input_is_empty))
                        }
                    } else {
                        send()
                    }
                }, enabled = !isSending) {
                    Icon(Icons.AutoMirrored.Default.Send, stringResource(Res.string.send))
                }
            }

            is LoadingState.Error -> {
                IconButton({
                    alertDialogController.showErrorMessage(state.e)
                }) {
                    Icon(Icons.Default.Error, stringResource(Res.string.error))
                }
            }

            else -> {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun InputGroupInternal(
    mediaTarget: ObjectTuple,
    backgroundColor: Color,
    input: String,
    updateInput: (String) -> Unit,
    gotoCompose: () -> Unit,
    topContent: @Composable () -> Unit = {},
    sendButton: @Composable () -> Unit,
) {
    Column(
        Modifier.background(
            backgroundColor,
            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
        ).padding(horizontal = 20.dp).padding(top = 10.dp).navigationBarsPadding().imePadding()
            .imeAnimation(),
    ) {
        topContent()
        Row(
            modifier = Modifier.padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            OutlinedTextField(input, {
                updateInput(it)
            }, modifier = Modifier.weight(1f), suffix = {
                if (input.isNotEmpty()) {
                    Icon(
                        Icons.Default.Clear,
                        stringResource(Res.string.clear_input),
                        modifier = Modifier.clickable {
                            updateInput("")
                        }
                    )
                }
            })

            InputGroupSuffix(input, updateInput, mediaTarget, gotoCompose)

            sendButton()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputGroupSuffix(
    input: String,
    updateInput: (String) -> Unit,
    mediaTarget: ObjectTuple,
    gotoCompose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val userSessionManager = LocalSessionManager.current
    val alertDialogController = rememberAlertDialogController()
    val alreadySignIn by userSessionManager.isAlreadySignIn.collectAsState(false)
    var showSheet by remember {
        mutableStateOf(false)
    }

    IconButton({
        if (alreadySignIn) {
            gotoCompose()
        } else {
            scope.launch {
                alertDialogController.showTitle(getString(Res.string.need_sign_in))
            }
        }
    }) {
        Icon(Icons.Default.OpenInFull, stringResource(Res.string.open_in_full),)
    }
    IconButton({
        if (alreadySignIn) {
            showSheet = true
        } else {
            scope.launch {
                alertDialogController.showTitle(getString(Res.string.need_sign_in))
            }
        }
    }) {
        Icon(Icons.Filled.PermMedia, contentDescription = null)
    }
    val sheetState = rememberModalBottomSheetState()
    FilePicker(
        showSheet,
        sheetState,
        mediaTarget,
        onClickItems = { info ->
            insertContent(info.first(), input, updateInput)
        }
    ) {
        showSheet = false
    }
    CustomAlertDialog(alertDialogController, {
        alertDialogController.close()
    }) {
    }
}

@Composable
fun RoomDialogInternal(roomInfo: RoomInfo, dismiss: () -> Unit) {
    val appNavFactory = LocalAppNavFactory.current
    DialogContainer {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceDim, RoundedCornerShape(8.dp))
                .padding(8.dp),
            Arrangement.spacedBy(12.dp)
        ) {
            val commonDialogController = rememberCommonDialogController()
            RoomIcon(roomInfo, 50.dp, false, commonDialogController::update)
            Column {
                Text(roomInfo.name)
                Text(stringResource(Res.string.aid_display, roomInfo.aid))
            }
        }

        roomInfo.communityId?.let { communityId ->
            CommunityRefCell(communityId) {
                dismiss()
                appNavFactory.newAppNav().gotoCommunity(communityId, false)
            }
        }

        RoomDialogButtons(roomInfo, dismiss)
    }
}

@Composable
private fun RoomDialogButtons(roomInfo: RoomInfo, dismiss: () -> Unit) {
    val appNavFactory = LocalAppNavFactory.current
    val me = LocalUserInfo.current
    val globalDialogController = LocalGlobalDialog.current
    Column {
        val isRoomPage = appNavFactory.hasRouteFlow<RoomScreen>()
        if (isRoomPage) {
            RoomAllMembers(roomInfo, dismiss, appNavFactory)
            RoomMemberStatus(roomInfo, globalDialogController)
            FavoriteButton(roomInfo.favoriteId, roomInfo.tuple())
            SubscriptionButton(roomInfo.subscriptionId, roomInfo.tuple())
            StartCallButton(roomInfo)
            if (roomInfo.isPrivate || roomInfo.creator == me?.id) {
                RoomFileExplorerButton(roomInfo, dismiss, appNavFactory)
            }
            RoomSettings(roomInfo, me, dismiss, appNavFactory)
        }
    }
}

@Composable
private fun RoomSettings(
    roomInfo: RoomInfo,
    me: UserInfo?,
    dismiss: () -> Unit,
    appNavFactory: AppNavFactory
) {
    if (roomInfo.creator == me?.id) {
        ButtonNav(Icons.Default.Settings, stringResource(Res.string.settings_title)) {
            dismiss()
            appNavFactory.newAppNav().gotoSettingPage(roomInfo.id, ObjectType.ROOM)
        }
    }
}

@Composable
private fun RoomFileExplorerButton(
    roomInfo: RoomInfo,
    dismiss: () -> Unit,
    appNavFactory: AppNavFactory
) {
    ButtonNav(Icons.Default.Folder, stringResource(Res.string.files_title)) {
        dismiss()
        appNavFactory.newAppNav().gotoFileExplorer(roomInfo.id ob ObjectType.ROOM)
    }
}

@Composable
private fun RoomAllMembers(
    roomInfo: RoomInfo,
    dismiss: () -> Unit,
    appNavFactory: AppNavFactory
) {
    ButtonNav(
        IconRes.Vector(Icons.Default.CardMembership),
        stringResource(Res.string.all_members),
        {
            ButtonBadgeSuffix(roomInfo.memberCount)
        }
    ) {
        dismiss()
        appNavFactory.newAppNav().gotoMemberPage(roomInfo.id, ObjectType.ROOM)
    }
}

@Composable
private fun RoomMemberStatus(
    roomInfo: RoomInfo,
    globalDialogController: AppGlobalDialogController
) {
    val scope = rememberCoroutineScope()

    val toasterState = LocalToaster.current
    if (roomInfo.isJoined) {
        ButtonNav(Icons.Default.Close, stringResource(Res.string.exit_room)) {
            scope.launch {
                exitRoom(roomInfo, globalDialogController) {
                    toasterState.showMessage(getString(Res.string.success),)
                }
            }
        }
    } else {
        ButtonNav(Icons.Default.AddHome, stringResource(Res.string.join_room)) {
            scope.launch {
                joinRoom(roomInfo, globalDialogController) {
                    val message = getString(Res.string.success)
                    toasterState.showMessage(message)
                }
            }
        }
    }
}

@Composable
private fun StartCallButton(
    roomInfo: RoomInfo
) {
    val sessionManager = LocalSessionManager.current

    val scope = rememberCoroutineScope()

    ButtonNav(Icons.Default.Call, stringResource(Res.string.start_call)) {
        startCall(roomInfo.id)
    }
    if (getPlatform().name.contains("android", ignoreCase = true)) {
        val context = LocalPlatformContext.current
        ButtonNav(Icons.Default.ChatBubble, stringResource(Res.string.bubble)) {
            scope.launch {
                val bitmap = roomInfo.icon?.let { getRemoteImageBitmap(sessionManager, context, it) }
                    ?.getOrNull()
                notifyNotification(roomInfo, bitmap)
            }
        }
    }
}

private suspend fun joinRoom(
    roomInfo: RoomInfo,
    globalDialogController: AppGlobalDialogController,
    onSuccess: suspend () -> Unit,
) {
    globalDialogController.useResult {
        request {
            runCatching {
                val communityId = roomInfo.communityId
                if (communityId != null) {
                    if (!getCommunityInfo(communityId).getOrThrow().isJoined) {
                        throw Exception("you should join community first.")
                    }
                }
                joinRoom(roomInfo.id).getOrThrow()
            }
        }
    }.onSuccess { info ->
        globalDialogController.emitEvent(OnRoomJoined(info))
        onSuccess()
    }
}

private suspend fun exitRoom(
    roomInfo: RoomInfo,
    globalDialogController: AppGlobalDialogController,
    onSuccess: suspend () -> Unit,
) {
    globalDialogController.useResult {
        request {
            exitRoom(roomInfo.id)
        }
    }.onSuccess { info ->
        globalDialogController.emitEvent(OnRoomExited(info))
        onSuccess()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDialog(
    showDialog: Boolean,
    roomInfo: RoomInfo?,
    dismiss: () -> Unit,
) {
    if (roomInfo != null && showDialog) {
        BasicAlertDialog({
            dismiss()
        }) {
            RoomDialogInternal(roomInfo, dismiss)
        }
    }
}
