package com.storyteller_f.a.app.pages.room

import a.composeapp.generated.resources.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemKey
import com.dokar.sonner.ToastType
import com.dokar.sonner.ToasterState
import com.storyteller_f.a.app.*
import com.storyteller_f.a.app.common.StateView
import com.storyteller_f.a.app.compontents.*
import com.storyteller_f.a.app.model.*
import com.storyteller_f.a.app.pages.community.CommunityRefCell
import com.storyteller_f.a.app.pages.search.CustomSearchBar
import com.storyteller_f.a.app.pages.search.SearchScope
import com.storyteller_f.a.app.pages.topic.MediaPicker
import com.storyteller_f.a.app.pages.topic.insertContent
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.client.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

@Composable
fun RoomPage(roomId: PrimaryKey, needShowDialog: Boolean) {
    val viewModel = createRoomTopicsViewModel(roomId)
    val items = viewModel.flow.collectAsLazyPagingItems()
    val room = createRoomViewModel(roomId)
    val roomInfo by room.handler.data.collectAsState()
    val lazyListState = rememberLazyListState()
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
            CustomSearchBar(SearchScope.RoomTopic(roomId)) {
                RoomIcon(roomInfo, showDialog = showDialog, size = 40.dp, setClickEvent = true) {
                    showDialog = it
                }
            }
            RoomPageInternal(modifier = Modifier.weight(1f), lazyListState, items)
            val scope = rememberCoroutineScope()
            RoomInputGroup(roomId, roomInfo, null, {
                showDialog = true
            }) {
                scope.launch {
                    lazyListState.animateScrollToItem(0)
                }
            }
        }
    }
}

@Composable
private fun RoomPageInternal(
    modifier: Modifier,
    lazyListState: LazyListState,
    items: LazyPagingItems<TopicInfo>
) {
    Box(modifier) {
        StateView(items) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.padding(top = 10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                reverseLayout = true,
            ) {
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
                    val current = items[index]
                    current?.let { info ->
                        TopicCell(
                            info,
                            false,
                            next?.author != info.author
                        )
                    }
                }
            }
        }
        if (lazyListState.firstVisibleItemScrollOffset > 0) {
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
}

@Composable
fun RoomInputGroup(
    roomId: PrimaryKey,
    roomInfo: RoomInfo?,
    topicId: PrimaryKey?,
    startJoinRoom: () -> Unit,
    scrollToNew: () -> Unit,
) {
    val appNav = LocalAppNav.current
    var input by remember {
        mutableStateOf("")
    }
    val my by LoginViewModel.user.collectAsState()
    val controller = remember {
        CustomAlertDialogController()
    }
    val wsClient = LocalWsClient.current
    val listener = remember(input, my) {
        buildInputBoxContentListener(input, my) {
            input = ""
        }
    }
    wsClient.addListener(listener)
    DisposableEffect(null) {
        onDispose {
            wsClient.removeListener(listener)
        }
    }
    val scope = rememberCoroutineScope()
    val localState by wsClient.localState.collectAsState()
    val isSending = localState is LoadingState.Loading
    val updateInput: (String) -> Unit = {
        if (!isSending) {
            input = it
        }
    }
    if (roomInfo != null) {
        RoomInputGroupInternal(roomId, roomInfo, topicId, input, scrollToNew, scope, controller, wsClient, updateInput)
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
    topicId: PrimaryKey?,
    input: String,
    scrollToNew: () -> Unit,
    scope: CoroutineScope,
    controller: CustomAlertDialogController,
    wsClient: ClientWebSocket,
    updateInput: (String) -> Unit
) {
    val toasterState = LocalToaster.current
    val keysViewModel = createRoomKeysViewModel(roomId, roomInfo)
    val objectId = topicId ?: roomId
    val objectType = if (topicId != null) ObjectType.TOPIC else ObjectType.ROOM
    val c = RoomMessageContext(
        roomInfo,
        input,
        scrollToNew,
        topicId,
        scope,
        toasterState,
        controller,
        keysViewModel,
        wsClient
    )
    InputGroupInternal(
        objectId,
        objectType,
        input,
        MaterialTheme.colorScheme.tertiaryContainer,
        roomId.takeIf {
            roomInfo.isPrivate
        },
        updateInput,
    ) {
        RoomSendButton(input = input) {
            sendRoomTopic(c)
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

class RoomMessageContext(
    val roomInfo: RoomInfo,
    val input: String,
    val scrollToNew: () -> Unit,
    val topicId: PrimaryKey?,
    val scope: CoroutineScope,
    val toasterState: ToasterState,
    val alertDialogState: CustomAlertDialogController,
    val keysViewModel: RoomKeysViewModel,
    val wsClient: ClientWebSocket
)

private fun sendRoomTopic(
    c: RoomMessageContext
) {
    val handler = c.keysViewModel.handler
    val keyState = handler.state.value
    val keyData = handler.data.value
    if (c.roomInfo.isJoined) {
        c.wsClient.useWebSocket {
            sendMessage(c.roomInfo, c.input, keyData, c.topicId, keyState) {
                c.scope.launch {
                    c.toasterState.show(
                        getString(Res.string.private_room_pub_key_loading),
                        type = ToastType.Info,
                        duration = 1.seconds
                    )
                }
            }
            delay(500)
            c.scrollToNew()
        }
    } else {
        c.scope.launch {
            val title = getString(Res.string.permission_denied)
            val message = getString(Res.string.join_room_prompt)
            c.alertDialogState.showMessage(title, message)
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
                globalDialogState.showError(state.e)
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
    objectId: PrimaryKey,
    objectType: ObjectType,
    input: String,
    backgroundColor: Color,
    privateRoomId: PrimaryKey?,
    updateInput: (String) -> Unit,
    sendButton: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.background(
            backgroundColor,
            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
        ).padding(horizontal = 20.dp).padding(top = 10.dp).navigationBarsPadding().imePadding().imeAnimation(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(input, {
            updateInput(it)
        }, modifier = Modifier.weight(1f), suffix = {
            InputGroupSuffix(input, updateInput, objectType, objectId, privateRoomId)
        })

        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            sendButton()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputGroupSuffix(
    input: String,
    updateInput: (String) -> Unit,
    objectType: ObjectType,
    objectId: PrimaryKey,
    privateRoomId: PrimaryKey?
) {
    val alreadyLoginIn by LoginViewModel.isAlreadySignUp.collectAsState(false)
    var showSheet by remember {
        mutableStateOf(false)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (input.isNotEmpty()) {
            Icon(Icons.Default.Clear, "clear input", modifier = Modifier.clickable {
                updateInput("")
            })
        }
        val appNav = LocalAppNav.current
        Icon(
            Icons.Default.OpenInFull,
            "open in full",
            modifier = Modifier.clickable {
                if (alreadyLoginIn) {
                    appNav.gotoTopicCompose(objectType, objectId, false, privateRoomId)
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
    MediaPicker(showSheet, sheetState, privateRoomId, { info ->
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
    val me by LoginViewModel.user.collectAsState()
    val client = LocalClient.current
    Column {
        ButtonNav(Icons.Default.CardMembership, stringResource(Res.string.all_members)) {
            dismiss()
            appNav.gotoMemberPage(roomInfo.id, ObjectType.ROOM)
        }

        if (appNav.hasRoute(RoomScreen::class)) {
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
                    appNav.gotoRoomSetting(roomInfo.id)
                }
            }
        }
    }
}

private suspend fun joinRoom(roomInfo: RoomInfo, client: HttpClient, onSuccess: suspend () -> Unit) {
    globalDialogState.use {
        val communityId = roomInfo.communityId
        if (communityId != null) {
            if (!client.getCommunityInfo(communityId, fillJoinInfo = true).getOrThrow().isJoined) {
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
