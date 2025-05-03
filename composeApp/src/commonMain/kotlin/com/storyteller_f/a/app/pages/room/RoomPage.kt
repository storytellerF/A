package com.storyteller_f.a.app.pages.room

import a.composeapp.generated.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemKey
import com.dokar.sonner.ToastType
import com.dokar.sonner.ToasterState
import com.storyteller_f.a.app.*
import com.storyteller_f.a.app.common.StateView
import com.storyteller_f.a.app.common.bottomAppending
import com.storyteller_f.a.app.common.topPrepend
import com.storyteller_f.a.app.compontents.*
import com.storyteller_f.a.app.model.*
import com.storyteller_f.a.app.pages.community.CommunityRefCell
import com.storyteller_f.a.app.pages.search.CustomSearchBar
import com.storyteller_f.a.app.pages.search.SearchScope
import com.storyteller_f.a.app.pages.topic.MediaPicker
import com.storyteller_f.a.app.pages.topic.insertContent
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.obj.UpdateUserRead
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.checkContent
import io.github.aakira.napier.Napier
import io.ktor.client.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

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
            val dialogShown by room.dialog.shownDialog.collectAsState()
            LaunchedEffect(needShowDialog, dialogShown) {
                if (needShowDialog && !dialogShown) {
                    room.dialog.markDialogShown()
                    showDialog = true
                }
            }
            val roomInfo by room.handler.data.collectAsState()
            CustomSearchBar(SearchScope.RoomTopic(roomId)) {
                RoomIcon(roomInfo, showDialog = showDialog, size = 40.dp, setClickEvent = true) {
                    showDialog = it
                }
            }
            StateView(room.handler) {
                RoomPageInternal(Modifier.weight(1f), roomId, it) {
                    showDialog = true
                }
            }
        }
    }
}

@Composable
private fun RoomPageInternal(
    modifier: Modifier,
    roomId: PrimaryKey,
    roomInfo: RoomInfo,
    updateDialog: (Boolean) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val viewModel = createRoomTopicsViewModel(roomId, roomInfo)
    val items = viewModel.flow.collectAsLazyPagingItems()
    Column {
        Box(modifier) {
            StateView(items) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.padding(top = 10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    reverseLayout = true,
                ) {
                    bottomAppending(items)
                    items(
                        count = items.itemCount,
                        key = items.itemKey { topicInfo ->
                            topicInfo.id.toString()
                        },
                    ) { index ->
                        val next = if (index + 1 < items.itemCount) {
                            items[index + 1]
                        } else {
                            null
                        }
                        items[index]?.let { info ->
                            TopicCell(
                                info,
                                false,
                                next?.author != info.author
                            )
                        }
                    }
                    topPrepend(items)
                }
            }
            val firstVisibleItemScrollOffset by remember { derivedStateOf { lazyListState.firstVisibleItemScrollOffset } }
            val firstVisibleItemIndex by remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }
            val client = LocalClient.current
            LaunchedEffect(firstVisibleItemIndex, roomInfo) {
                delay(1000)
                try {
                    val info = items[firstVisibleItemIndex]
                    if (info != null) {
                        client.addReadLog(UpdateUserRead(roomInfo.tuple(), info.id)).getOrThrow()
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
    val myInfo by SignInViewModel.user.collectAsState()
    val controller = remember {
        CustomAlertDialogController()
    }
    val wsClient = LocalWsClient.current
    val listener = remember(input, myInfo) {
        buildInputBoxContentListener(input, myInfo) {
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
        val mediaTarget = if (roomInfo.isPrivate) {
            ObjectTuple(roomInfo.id, ObjectType.ROOM)
        } else {
            ObjectTuple(myInfo?.id ?: 0, ObjectType.USER)
        }
        RoomInputGroupInternal(roomId, roomInfo, parentTarget, input, scrollToNew, mediaTarget) {
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
    mediaTarget: ObjectTuple,
    updateInput: (String) -> Unit
) {
    val wsClient = LocalWsClient.current

    val controller = remember {
        CustomAlertDialogController()
    }
    val scope = rememberCoroutineScope()
    val toasterState = LocalToaster.current
    val keysViewModel = createRoomKeysViewModel(roomId, roomInfo)
    val keysData by keysViewModel.handler.data.collectAsState()
    val keysState by keysViewModel.handler.state.collectAsState()
    val appNav = LocalAppNav.current
    InputGroupInternal(
        input,
        MaterialTheme.colorScheme.tertiaryContainer,
        updateInput,
        {
            appNav.gotoTopicCompose(parentTarget.objectType, parentTarget.objectId, false, roomId.takeIf {
                roomInfo.isPrivate
            })
        },
        mediaTarget,
        {
            if (roomInfo.isPrivate)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (val ks = keysState) {
                        LoadingState.Done -> Text("✅")
                        is LoadingState.Error -> Text(ks.e.localizedMessage?.take(10) ?: "!")
                        null, LoadingState.Loading -> CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 2.dp)
                    }
                    Text("${keysData?.size ?: 0}/${roomInfo.memberCount}")
                }
        }
    ) {
        RoomSendButton(input = input) {
            sendRoomTopic(
                roomInfo,
                input,
                scrollToNew,
                scope,
                toasterState,
                controller,
                keysViewModel,
                wsClient,
                parentTarget
            )
        }
    }
}

private fun buildInputBoxContentListener(
    input: String,
    userInfo: UserInfo?,
    updateInput: (String) -> Unit
): ClientWsListener {
    return object : ClientWsListener {
        override fun onReceived(frame: RoomFrame) {
            if (frame is RoomFrame.NewTopicInfo) {
                val topicInfo = frame.topicInfo
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
    toasterState: ToasterState,
    alertDialogState: CustomAlertDialogController,
    keysViewModel: RoomKeysViewModel,
    wsClient: ClientWebSocket,
    parentTarget: ObjectTuple
) {
    val handler = keysViewModel.handler
    val keyState = handler.state.value
    val keyData = handler.data.value
    if (roomInfo.isJoined) {
        if (!checkContent(input)) {
            toasterState.show("invalid", duration = 1.seconds)
            return
        }
        if (keyState !is LoadingState.Done || keyData == null) {
            scope.launch {
                toasterState.show(
                    getString(Res.string.private_room_pub_key_loading),
                    type = ToastType.Info,
                    duration = 1.seconds
                )
            }
            return
        }
        wsClient.useWebSocket {
            sendMessage(parentTarget, roomInfo.isPrivate, input, keyData)
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
    startJoinRoom: () -> Unit
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
    send: () -> Unit
) {
    val wsClient = LocalWsClient.current
    val state by wsClient.connectionHandler.state.collectAsState()
    val sendState by wsClient.localState.collectAsState()
    val isSending = sendState is LoadingState.Loading
    CommonInputButton(state, input, isSending, send)
}

@Composable
fun CommonInputButton(
    state: LoadingState?,
    input: String,
    isSending: Boolean,
    send: () -> Unit
) {
    val scope = rememberCoroutineScope()
    when (state) {
        is LoadingState.Done -> {
            IconButton({
                if (input.isBlank()) {
                    scope.launch {
                        globalDialogState.showMessage(getString(Res.string.input_is_empty))
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
                globalDialogState.showErrorState(state.e)
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
    sendButton: @Composable () -> Unit
) {
    Column(
        Modifier.background(
            backgroundColor,
            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
        ).padding(horizontal = 20.dp).padding(top = 10.dp).navigationBarsPadding().imePadding().imeAnimation(),
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
    gotoCompose: () -> Unit
) {
    val alreadyLoginIn by SignInViewModel.isAlreadySignUp.collectAsState(false)
    var showSheet by remember {
        mutableStateOf(false)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    globalDialogState.showMessage("need sign in")
                }
            }
        )
        Icon(Icons.Filled.PermMedia, contentDescription = null, modifier = Modifier.clickable {
            if (alreadyLoginIn) {
                showSheet = true
            } else {
                globalDialogState.showMessage("need sign in")
            }
        })
    }
    val sheetState = rememberModalBottomSheetState()
    MediaPicker(showSheet, sheetState, mediaTarget, { info ->
        insertContent(info.first(), updateInput, input)
    }) {
        showSheet = false
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
            val commonDialogController = rememberCommonDialogController()
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
            CommunityRefCell(communityId)
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
    val me by SignInViewModel.user.collectAsState()
    val client = LocalClient.current
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
                        exitRoom(roomInfo, client) {
                            toasterState.show(
                                getString(Res.string.success),
                                type = ToastType.Success,
                                duration = 1.seconds
                            )
                        }
                    }
                }
            } else {
                ButtonNav(Icons.Default.AddHome, stringResource(Res.string.join_room)) {
                    scope.launch {
                        joinRoom(roomInfo, client) {
                            toasterState.show(
                                getString(Res.string.success),
                                type = ToastType.Success,
                                duration = 1.seconds
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

private suspend fun joinRoom(roomInfo: RoomInfo, client: HttpClient, onSuccess: suspend () -> Unit) {
    globalDialogState.use {
        val communityId = roomInfo.communityId
        if (communityId != null) {
            if (!client.getCommunityInfo(communityId).getOrThrow().isJoined) {
                throw Exception("you should join community first.")
            }
        }
        val info = client.joinRoom(roomInfo.id).getOrThrow()
        bus.emit(OnRoomJoined(info))
        onSuccess()
    }
}

private suspend fun exitRoom(roomInfo: RoomInfo, client: HttpClient, onSuccess: suspend () -> Unit) {
    globalDialogState.use {
        val info = client.exitRoom(roomInfo.id).getOrThrow()
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
