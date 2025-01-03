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
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import com.storyteller_f.a.app.*
import com.storyteller_f.a.app.compontents.*
import com.storyteller_f.a.app.compontents.TopicCell
import com.storyteller_f.a.app.model.*
import com.storyteller_f.a.app.pages.community.CommunityRefCell
import com.storyteller_f.a.app.pages.search.CustomSearchBar
import com.storyteller_f.a.app.pages.search.SearchScope
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
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
                RoomIcon(roomInfo, size = 40.dp, enableClick = true, showDialog = showDialog, updateShowDialog = {
                    showDialog = it
                })
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
    LazyColumn(
        state = lazyListState,
        modifier = modifier.padding(top = 10.dp),
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
                Spacer(modifier = Modifier.height(10.dp))
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
    val alertDialogState = remember {
        CustomAlertDialogController()
    }
    if (roomInfo != null) {
        val toaster = rememberToasterState()
        Toaster(toaster, alignment = Alignment.Center)
        val keysViewModel = createRoomKeysViewModel(roomId, roomInfo)
        val keyState by keysViewModel.handler.state.collectAsState()
        val keyData by keysViewModel.handler.data.collectAsState()
        val objectId = topicId ?: roomId
        val objectType = if (topicId != null) ObjectType.TOPIC else ObjectType.ROOM
        InputGroupInternal(objectId, objectType, input, MaterialTheme.colorScheme.tertiaryContainer, roomId.takeIf {
            roomInfo.isPrivate
        }, {
            input = it
        }, sendButton = {
            val p1 = stringResource(Res.string.private_room_pub_key_loading)
            val title = stringResource(Res.string.permission_denied)
            val message = stringResource(Res.string.join_room_prompt)
            RoomSendButton(input = input) {
                if (roomInfo.isJoined) {
                    sendMessage(roomInfo, input, scrollToNew, keyState, keyData, topicId) {
                        toaster.show(p1, duration = 1.seconds)
                    }
                } else {
                    alertDialogState.showMessage(title, message)
                }
            }
        })
    }

    CustomAlertDialog(alertDialogState, {
        alertDialogState.close()
    }) {
        checkRoomRouteAndAlert(appNav, roomId, startJoinRoom)
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
    val state by wsClient.connectionHandler.state.collectAsState()
    val sendState by wsClient.localState.collectAsState()
    val isSending = sendState is LoadingState.Loading
    CommonInputButton(state, input, send, isSending)
}

@Composable
fun CommonInputButton(
    state: LoadingState?,
    input: String,
    send: () -> Unit,
    isSending: Boolean
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

fun sendMessage(
    roomInfo: RoomInfo?,
    input: String,
    scrollToNew: () -> Unit,
    keyState: LoadingState?,
    keyData: List<Pair<PrimaryKey, String>>?,
    topicId: PrimaryKey?,
    notifyPubKeyStillLoading: () -> Unit
) {
    if (roomInfo != null) {
        if (keyState !is LoadingState.Done || keyData == null) {
            notifyPubKeyStillLoading()
            return
        }
        wsClient.useWebSocket {
            sendMessage(roomInfo, input, keyData, topicId)
            delay(500)
            scrollToNew()
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
    val alreadyLoginIn by LoginViewModel.isAlreadySignUp.collectAsState(false)
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
                    modifier = Modifier.clickable(enabled = alreadyLoginIn) {
                        appNav.gotoTopicCompose(objectType, objectId, false, privateRoomId)
                    }
                )
            }
        })

        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            sendButton()
        }
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
                updateShowDialog = commonDialogController::update,
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
    Column {
        ButtonNav(Icons.Default.Settings, stringResource(Res.string.settings))
        ButtonNav(Icons.Default.CardMembership, stringResource(Res.string.all_members)) {
            dismiss()
            appNav.gotoMemberPage(roomInfo.id, ObjectType.ROOM)
        }

        if (appNav.currentDestination?.destination?.hasRoute(RoomScreen::class) == true) {
            val scope = rememberCoroutineScope()
            val toaster = rememberToasterState()
            Toaster(toaster)
            val successMessage = stringResource(Res.string.success)
            if (roomInfo.isJoined) {
                ButtonNav(Icons.Default.Close, stringResource(Res.string.exit_room)) {
                    scope.launch {
                        exitRoom(roomInfo) {
                            toaster.show(successMessage, duration = 1.seconds)
                        }
                    }
                }
            } else {
                ButtonNav(Icons.Default.AddHome, stringResource(Res.string.join_room)) {
                    scope.launch {
                        joinRoom(roomInfo) {
                            toaster.show(successMessage, duration = 1.seconds)
                        }
                    }
                }
            }
        }
    }
}

private suspend fun joinRoom(roomInfo: RoomInfo, onSuccess: () -> Unit) {
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

private suspend fun exitRoom(roomInfo: RoomInfo, onSuccess: () -> Unit) {
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
