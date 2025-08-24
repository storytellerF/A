package com.storyteller_f.a.app.compose_app.pages.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.storyteller_f.a.app.compose_app.*
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.common.StateView
import com.storyteller_f.a.app.compose_app.common.bottomAppending
import com.storyteller_f.a.app.compose_app.common.topPrepend
import com.storyteller_f.a.app.compose_app.compontents.*
import com.storyteller_f.a.app.compose_app.model.*
import com.storyteller_f.a.app.compose_app.pages.community.CommunityRefCell
import com.storyteller_f.a.app.compose_app.pages.search.CustomSearchBar
import com.storyteller_f.a.app.compose_app.pages.search.SearchScope
import com.storyteller_f.a.app.compose_app.pages.topic.MediaPicker
import com.storyteller_f.a.app.compose_app.pages.topic.insertContent
import com.storyteller_f.a.client.core.LoadingState
import com.storyteller_f.a.client.core.SessionManager
import com.storyteller_f.a.client.core.WebSocketClient
import com.storyteller_f.a.client.core.WebSocketClientListener
import com.storyteller_f.a.client.core.addReadLog
import com.storyteller_f.a.client.core.exitRoom
import com.storyteller_f.a.client.core.getCommunityInfo
import com.storyteller_f.a.client.core.joinRoom
import com.storyteller_f.a.client.core.processEncryptedTopic
import com.storyteller_f.a.client.core.sendMessage
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserPubKeyInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.obj.UpdateUserRead
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
    val snackBarHost = remember {
        SnackbarHostState()
    }
    val wsClient = LocalWsClient.current
    LaunchedEffect(wsClient.remoteState) {
        wsClient.remoteState.collect {
            if (it is RoomFrame.Error) {
                snackBarHost.showSnackbar(it.error, withDismissAction = true)
            }
        }
    }
    Scaffold(snackbarHost = {
        SnackbarHost(snackBarHost)
    }) {
        Column(modifier = Modifier) {
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
            CustomSearchBar(
                SearchScope.RoomTopic(
                    roomId
                )
            ) {
                RoomIcon(roomInfo, showDialog = showDialog, size = 40.dp, setClickEvent = true) {
                    showDialog = it
                }
            }
            StateView(room.handler, Modifier.weight(1f)) {
                RoomPageInternal(it) {
                    showDialog = true
                }
            }
        }
    }
}

@Composable
private fun RoomPageInternal(
    roomInfo: RoomInfo,
    updateDialog: (Boolean) -> Unit,
) {
    val roomId = roomInfo.id
    val lazyListState = rememberLazyListState()
    val viewModel = createRoomTopicsViewModel(roomId)
    val items = viewModel.flow.collectAsLazyPagingItems()
    Column {
        Box(Modifier.weight(1f)) {
            RoomMessageList(items, lazyListState)
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
                    val info = items[firstVisibleItemIndex]
                    if (info != null) {
                        sessionManager.addReadLog(UpdateUserRead(roomInfo.tuple(), info.id))
                            .getOrThrow()
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
                    Icon(
                        Icons.Default.ArrowCircleDown,
                        "move to newer topic",
                    )
                }
            }
        }
        val scope = rememberCoroutineScope()
        RoomInputGroup(roomId, roomInfo, ObjectTuple(roomId, ObjectType.ROOM), {
            updateDialog(true)
        }) {
            scope.launch {
                lazyListState.animateScrollToItem(0)
            }
        }
    }
}

@Composable
private fun RoomMessageList(
    items: LazyPagingItems<TopicInfo>,
    lazyListState: LazyListState,
) {
    val debounced = items.loadState
    StateView(items) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.padding(top = 10.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            reverseLayout = true,
        ) {
            bottomAppending(debounced)
            items(
                count = items.itemSnapshotList.size,
                key = items.itemKey { topicInfo ->
                    topicInfo.id.toString()
                },
            ) { index ->
                val next = if (index + 1 < items.itemSnapshotList.size) {
                    items[index + 1]
                } else {
                    null
                }
                val info = items[index]
                TopicCell(
                    info,
                    info != null && next?.author != info.author
                )
            }
            topPrepend(debounced)
        }
    }
}

@Composable
fun RoomInputGroup(
    roomId: PrimaryKey,
    roomInfo: RoomInfo?,
    parentTarget: ObjectTuple,
    startJoinRoom: () -> Unit,
    scrollToNew: () -> Unit,
) {
    val appNav = LocalAppNav.current
    var input by remember {
        mutableStateOf("")
    }
    val userSessionManager = LocalSessionManager.current
    val myInfo1 by userSessionManager.sessionModel.userHandler.data.collectAsState()
    val myInfo = myInfo1
    val controller = remember {
        CustomAlertDialogController()
    }
    val sessionManager = LocalSessionManager.current
    val wsClient = LocalWsClient.current
    val listener = remember(input, myInfo) {
        buildInputBoxContentListener(input, myInfo, sessionManager) {
            input = ""
        }
    }
    wsClient.addListener(listener)
    DisposableEffect(null) {
        onDispose {
            wsClient.removeListener(listener)
        }
    }
    if (roomInfo != null) {
        val localState by wsClient.localState.collectAsState()
        val isSending = localState is LoadingState.Loading

        RoomInputGroupInternal(roomId, roomInfo, parentTarget, input, scrollToNew) {
            if (!isSending) {
                input = it
            }
        }
    }

    CustomAlertDialog(controller, {
        controller.close()
    }) {
        checkRoomRouteAndAlert(appNav, roomId, startJoinRoom)
    }
}

@Composable
private fun RoomInputGroupInternal(
    roomId: PrimaryKey,
    roomInfo: RoomInfo,
    parentTarget: ObjectTuple,
    input: String,
    scrollToNew: () -> Unit,
    updateInput: (String) -> Unit,
) {
    val userSessionManager = LocalSessionManager.current
    val myInfo by userSessionManager.sessionModel.userHandler.data.collectAsState()
    val mediaTarget = if (roomInfo.isPrivate) {
        ObjectTuple(roomInfo.id, ObjectType.ROOM)
    } else {
        ObjectTuple(myInfo?.id ?: 0, ObjectType.USER)
    }
    val controller = remember {
        CustomAlertDialogController()
    }
    val keysViewModel =
        createRoomKeysViewModel(roomId, roomInfo)
    val keysData by keysViewModel.handler.data.collectAsState()
    val keysState by keysViewModel.handler.state.collectAsState()
    val appNav = LocalAppNav.current
    InputGroupInternal(
        input,
        MaterialTheme.colorScheme.tertiaryContainer,
        updateInput,
        {
            appNav.gotoTopicCompose(
                parentTarget.objectType,
                parentTarget.objectId,
                false,
                roomId.takeIf {
                    roomInfo.isPrivate
                },
                null
            )
        },
        mediaTarget,
        {
            RoomInputTopContent(roomInfo, keysState, keysData)
        }
    ) {
        RoomSendButton(input = input, roomInfo, scrollToNew, controller, parentTarget)
    }
}

@Composable
private fun RoomInputTopContent(
    roomInfo: RoomInfo,
    keysState: LoadingState?,
    keysData: List<UserPubKeyInfo>?
) {
    if (roomInfo.isPrivate) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (val ks = keysState) {
                LoadingState.Done -> Text("✅")
                is LoadingState.Error -> Text(ks.e.localizedMessage?.take(10) ?: "!")
                else -> CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 2.dp
                )
            }
            Text("${keysData?.size ?: 0}/${roomInfo.memberCount}")
        }
    }
}

private fun buildInputBoxContentListener(
    input: String,
    userInfo: UserInfo?,
    sessionManager: SessionManager,
    updateInput: (String) -> Unit,
): WebSocketClientListener {
    return object : WebSocketClientListener {
        override suspend fun onReceived(frame: RoomFrame) {
            if (frame is RoomFrame.NewTopicInfo) {
                val plainFrame = if (frame.topicInfo.content is TopicContent.Encrypted) {
                    val topicInfo =
                        processEncryptedTopic(listOf(frame.topicInfo), sessionManager).first()
                    RoomFrame.NewTopicInfo(topicInfo)
                } else {
                    frame
                }
                val topicInfo = plainFrame.topicInfo
                val content = topicInfo.content
                if (content is TopicContent.Plain && userInfo?.id == topicInfo.author && content.plain == input) {
                    updateInput("")
                }
            }
        }
    }
}

private fun sendRoomTopic(
    roomInfo: RoomInfo,
    input: String,
    scrollToNew: () -> Unit,
    scope: CoroutineScope,
    toasterState: Toast,
    alertDialogState: CustomAlertDialogController,
    keysViewModel: RoomKeysViewModel,
    wsClient: WebSocketClient,
    parentTarget: ObjectTuple,
) {
    val handler = keysViewModel.handler
    val keyState = handler.state.value
    val keyData = handler.data.value
    if (roomInfo.isJoined) {
        if (!checkContent(input)) {
            toasterState.showMessage("invalid")
            return
        }
        if ((keyState !is LoadingState.Done || keyData == null) && roomInfo.isPrivate) {
            scope.launch {
                toasterState.showMessage(
                    getString(Res.string.private_room_pub_key_loading),
                )
            }
            return
        }
        wsClient.useWebSocket {
            sendMessage(parentTarget, roomInfo.isPrivate, input, keyData.orEmpty())
            delay(500)
            scrollToNew()
        }
    } else {
        scope.launch {
            val title = getString(Res.string.permission_denied)
            val message = getString(Res.string.join_room_prompt)
            alertDialogState.showMessage(title, message)
        }
    }
}

private fun checkRoomRouteAndAlert(
    appNav: AppNav,
    roomId: PrimaryKey,
    startJoinRoom: () -> Unit,
) {
    val current = appNav.currentDestination
    if (current != null) {
        val currentDestination = current.destination
        if (currentDestination.hasRoute(RoomScreen::class)) {
            if (current.toRoute<RoomScreen>().roomId == roomId) {
                startJoinRoom()
                return
            }
        }
    }
    appNav.gotoRoom(roomId, true)
}

@Composable
fun RoomSendButton(
    input: String,
    roomInfo: RoomInfo,
    scrollToNew: () -> Unit,
    alertDialogState: CustomAlertDialogController,
    parentTarget: ObjectTuple,
) {
    val toasterState = LocalToaster.current
    val scope = rememberCoroutineScope()
    val wsClient = LocalWsClient.current
    val state by wsClient.connectionHandler.state.collectAsState()
    val sendState by wsClient.localState.collectAsState()
    val isSending = sendState is LoadingState.Loading
    val keysViewModel =
        createRoomKeysViewModel(roomInfo.id, roomInfo)
    CommonInputButton(state, input, isSending) {
        sendRoomTopic(
            roomInfo,
            input,
            scrollToNew,
            scope,
            toasterState,
            alertDialogState,
            keysViewModel,
            wsClient,
            parentTarget
        )
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

@Composable
fun InputGroupInternal(
    input: String,
    backgroundColor: Color,
    updateInput: (String) -> Unit,
    gotoCompose: () -> Unit,
    mediaTarget: ObjectTuple,
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
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(input, {
                updateInput(it)
            }, modifier = Modifier.weight(1f), suffix = {
                InputGroupSuffix(input, updateInput, mediaTarget, gotoCompose)
            })

            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                sendButton()
            }
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
    val userSessionManager = LocalSessionManager.current
    val alertDialogController = rememberAlertDialogController()
    val alreadyLoginIn by userSessionManager.isAlreadySignUp.collectAsState(false)
    var showSheet by remember {
        mutableStateOf(false)
    }
    val scope = rememberCoroutineScope()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (input.isNotEmpty()) {
            Icon(Icons.Default.Clear, "clear input", modifier = Modifier.clickable {
                updateInput("")
            })
        }
        Icon(
            Icons.Default.OpenInFull,
            "open in full",
            modifier = Modifier.clickable {
                if (alreadyLoginIn) {
                    gotoCompose()
                } else {
                    alertDialogController.showTitle("need sign in")
                }
            }
        )
        Icon(Icons.Filled.PermMedia, contentDescription = null, modifier = Modifier.clickable {
            if (alreadyLoginIn) {
                showSheet = true
            } else {
                alertDialogController.showTitle("need sign in")
            }
        })
    }
    val sheetState = rememberModalBottomSheetState()
    MediaPicker(
        showSheet,
        sheetState,
        mediaTarget,
        onClickItems = { info ->
            insertContent(
                info.first(),
                updateInput,
                input
            )
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
    val appNav = LocalAppNav.current
    DialogContainer {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceDim, RoundedCornerShape(8.dp))
                .padding(8.dp),
            Arrangement.spacedBy(12.dp)
        ) {
            val commonDialogController =
                rememberCommonDialogController()
            val shown by commonDialogController.show
            RoomIcon(
                roomInfo,
                showDialog = shown,
                onClickIcon = commonDialogController::update,
            )
            Column {
                Text(roomInfo.name)
                Text("aid: ${roomInfo.aid}")
            }
        }

        roomInfo.communityId?.let { communityId ->
            CommunityRefCell(communityId) {
                dismiss()
                appNav.gotoCommunity(communityId, false)
            }
        }

        RoomDialogButtons(dismiss, appNav, roomInfo)
    }
}

@Composable
private fun RoomDialogButtons(
    dismiss: () -> Unit,
    appNav: AppNav,
    roomInfo: RoomInfo,
) {
    val userSessionManager = LocalSessionManager.current
    val myInfo by userSessionManager.sessionModel.userHandler.data.collectAsState()
    val me = myInfo
    val sessionManager = LocalSessionManager.current
    val globalDialogController = LocalGlobalDialog.current
    Column {
        ButtonNav(Icons.Default.CardMembership, stringResource(Res.string.all_members)) {
            dismiss()
            appNav.gotoMemberPage(roomInfo.id, ObjectType.ROOM)
        }
        val isCommunityPage by appNav.hasRouteFlow<RoomScreen>().collectAsState(false)
        if (isCommunityPage) {
            val scope = rememberCoroutineScope()
            val toasterState = LocalToaster.current
            if (roomInfo.isJoined) {
                ButtonNav(Icons.Default.Close, stringResource(Res.string.exit_room)) {
                    scope.launch {
                        exitRoom(roomInfo, sessionManager, globalDialogController) {
                            toasterState.showMessage(
                                getString(Res.string.success),
                            )
                        }
                    }
                }
            } else {
                ButtonNav(Icons.Default.AddHome, stringResource(Res.string.join_room)) {
                    scope.launch {
                        joinRoom(roomInfo, sessionManager, globalDialogController) {
                            toasterState.showMessage(
                                getString(Res.string.success),
                            )
                        }
                    }
                }
            }

            if (roomInfo.creator == me?.id) {
                ButtonNav(Icons.Default.Settings, "Settings") {
                    dismiss()
                    appNav.gotoSettingPage(roomInfo.id, ObjectType.ROOM)
                }
            }
        }
    }
}

private suspend fun joinRoom(
    roomInfo: RoomInfo,
    sessionManager: SessionManager,
    globalDialogController: GlobalDialogController,
    onSuccess: suspend () -> Unit,
) {
    globalDialogController.useResult {
        runCatching {
            val communityId = roomInfo.communityId
            if (communityId != null) {
                if (!sessionManager.getCommunityInfo(communityId).getOrThrow().isJoined) {
                    throw Exception("you should join community first.")
                }
            }
            sessionManager.joinRoom(roomInfo.id).getOrThrow()
        }
    }.onSuccess { info ->
        bus.emit(OnRoomJoined(info))
        onSuccess()
    }
}

private suspend fun exitRoom(
    roomInfo: RoomInfo,
    sessionManager: SessionManager,
    globalDialogController: GlobalDialogController,
    onSuccess: suspend () -> Unit,
) {
    globalDialogController.useResult {
        sessionManager.exitRoom(roomInfo.id)
    }.onSuccess { info ->
        bus.emit(OnRoomExited(info))
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
