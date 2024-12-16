package com.storyteller_f.a.app.room

import a.composeapp.generated.resources.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemContentType
import app.cash.paging.compose.itemKey
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import com.storyteller_f.a.app.*
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.community.CommunityRefCell
import com.storyteller_f.a.app.compontents.*
import com.storyteller_f.a.app.search.CustomSearchBar
import com.storyteller_f.a.app.search.SearchScope
import com.storyteller_f.a.app.topic.TopicCell
import com.storyteller_f.a.app.topic.processEncryptedTopic
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.encrypt
import com.storyteller_f.shared.encryptAesKey
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKeyOrNull
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import kotbase.Expression
import kotbase.MutableDocument
import kotbase.ktx.all
import kotbase.ktx.orderBy
import kotbase.ktx.select
import kotbase.ktx.where
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

data class OnRoomJoined(val id: PrimaryKey, val newInfo: RoomInfo)
data class OnRoomExited(val id: PrimaryKey, val newInfo: RoomInfo)

@OptIn(ExperimentalPagingApi::class)
class TopicsViewModel(id: PrimaryKey, val type: ObjectType) : PagingViewModel<PrimaryKey, TopicInfo>({
    CustomQueryPagingSource(
        select = select(all()),
        collectionName = "topics$id",
        queryProvider = {
            where {
                if (it != null) {
                    "id" lessThan it
                } else {
                    Expression.intValue(0) equalTo Expression.intValue(0)
                }
            }.orderBy {
                "id".descending()
            }
        },
        jsonStringMapper = { json: String ->
            kotlin.runCatching {
                Json.decodeFromString<TopicInfo>(json)
            }.getOrNull()
        }
    )
}, TopicsRemoteMediator("topics$id") { loadKey ->
    val info = when (type) {
        ObjectType.ROOM -> client.getRoomTopics(id, loadKey, 20)
        ObjectType.COMMUNITY -> client.getCommunityTopics(id, loadKey, 20)
        else -> client.getTopicTopics(id, loadKey, 20)
    }.getOrThrow()
    info.copy(processEncryptedTopic(info.data))
})

@OptIn(ExperimentalPagingApi::class)
class TopicsRemoteMediator(
    private val collectionName: String,
    val networkService: suspend (PrimaryKey?) -> ServerResponse<TopicInfo>
) :
    RemoteMediator<PrimaryKey, TopicInfo>() {
    private val scope = database.defaultScope
    private val collection
        get() = scope.getCollection(collectionName) ?: database.createCollection(
            collectionName
        )

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<PrimaryKey, TopicInfo>
    ): MediatorResult {
        Napier.v(tag = "pagination") {
            "mediator load $loadType"
        }
        val loadKey = when (loadType) {
            LoadType.REFRESH -> null
            LoadType.PREPEND -> return MediatorResult.Success(
                endOfPaginationReached = true
            )

            LoadType.APPEND -> {
                val lastItem = state.lastItemOrNull()
                    ?: return MediatorResult.Success(
                        endOfPaginationReached = true
                    )

                lastItem.id
            }
        }
        return try {
            val response = networkService(loadKey)
            if (loadType == LoadType.REFRESH) {
                database.deleteCollection(collectionName)
            }
            response.data.forEach {
                val rawId = it.id.toString(2)
                collection.save(
                    MutableDocument(
                        if (rawId.length == 64) {
                            rawId
                        } else {
                            "0$rawId"
                        },
                        Json.encodeToString(it)
                    )
                )
            }
            Napier.v(tag = "pagination") {
                "mediator success $loadKey"
            }
            MediatorResult.Success(
                endOfPaginationReached = response.data.isEmpty()
            )
        } catch (e: Exception) {
            Napier.e(e, tag = "pagination") {
                "mediator load error"
            }
            MediatorResult.Error(e)
        }
    }
}

class DialogSaveState {
    val shownDialog = MutableStateFlow<Boolean>(false)
    fun markDialogShown() {
        shownDialog.value = true
    }
}

class RoomViewModel(private val requestInfo: suspend HttpClient.() -> Result<RoomInfo>) : SimpleViewModel<RoomInfo>() {
    val dialog = DialogSaveState()

    constructor(roomId: PrimaryKey) : this({
        requestRoomInfo(roomId, LoginViewModel.currentIsAlreadySignUp)
    })

    constructor(roomAid: String) : this({
        requestRoomInfoByAid(roomAid, LoginViewModel.currentIsAlreadySignUp)
    })

    init {
        load()
        viewModelScope.launch {
            bus.collect { i ->
                if (handler.state.value !is LoadingState.Loading) {
                    val id = handler.data.value?.id
                    when (i) {
                        is OnRoomJoined -> {
                            if (i.id == id) {
                                update(i.newInfo)
                            }
                        }

                        is OnRoomExited -> {
                            if (i.id == id) {
                                update(i.newInfo)
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun loadInternal() = requestInfo(client)
}

@Composable
fun RoomPage(roomId: PrimaryKey, needShowDialog: Boolean) {
    val viewModel = viewModel(keys = listOf("room-topics", roomId)) {
        TopicsViewModel(roomId, ObjectType.ROOM)
    }
    val items = viewModel.flow.collectAsLazyPagingItems()
    val room = viewModel(keys = listOf("room", roomId)) {
        RoomViewModel(roomId)
    }
    val roomInfo by room.handler.data.collectAsState()
    val lazyListState = rememberLazyListState()
    val snackBarHost = remember {
        SnackbarHostState()
    }
    LaunchedEffect(clientWs.remoteState) {
        clientWs.remoteState.collect {
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
            contentType = items.itemContentType()
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

class RoomKeysViewModel(private val id: PrimaryKey, private: Boolean) :
    SimpleViewModel<List<Pair<PrimaryKey, String>>>() {

    init {
        if (private) {
            load()
        }
    }

    override suspend fun loadInternal() = runCatching {
        val result = mutableListOf<Pair<PrimaryKey, String>>()
        var last: PrimaryKey? = null
        while (true) {
            val list = client.requestRoomKeys(id, last, 100).getOrThrow()
            result.addAll(list.data)
            val nextKey = list.pagination?.nextPageToken?.toPrimaryKeyOrNull() ?: break
            last = nextKey
        }
        result
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
        val keysViewModel = viewModel(keys = listOf("room-keys", roomId)) {
            RoomKeysViewModel(roomId, roomInfo.isPrivate)
        }
        val keyState by keysViewModel.handler.state.collectAsState()
        val keyData by keysViewModel.handler.data.collectAsState()
        val objectId = topicId ?: roomId
        val objectType = if (topicId != null) ObjectType.TOPIC else ObjectType.ROOM
        InputGroupInternal(objectId, objectType, input, MaterialTheme.colorScheme.tertiaryContainer, {
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
    val state by clientWs.connectionHandler.state.collectAsState()
    val sendState by clientWs.localState.collectAsState()
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
    when (val wsState = state) {
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
                globalDialogState.showError(wsState.e)
            }) {
                Icon(Icons.Default.Error, stringResource(Res.string.error))
            }
        }

        else -> {
            CircularProgressIndicator()
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
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
        val roomId = roomInfo.id
        clientWs.useWebSocket {
            val content = if (roomInfo.isPrivate) {
                if (keyState !is LoadingState.Done || keyData == null) {
                    notifyPubKeyStillLoading()
                    null
                } else {
                    val (encrypted, aes) = encrypt(input)
                    TopicContent.Encrypted(encrypted.toHexString(), keyData.associate {
                        it.first to encryptAesKey(it.second, aes).toHexString()
                    })
                }
            } else {
                TopicContent.Plain(input)
            }
            if (content != null) {
                val message: RoomFrame = RoomFrame.Message(
                    if (topicId != null) {
                        NewTopic(
                            ObjectType.TOPIC,
                            topicId,
                            content
                        )
                    } else {
                        NewTopic(
                            ObjectType.ROOM,
                            roomId,
                            content
                        )
                    }
                )
                sendSerialized(message)
                delay(500)
                scrollToNew()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InputGroupInternal(
    objectId: PrimaryKey,
    objectType: ObjectType,
    input: String,
    backgroundColor: Color,
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (input.isNotEmpty()) {
                    Icon(Icons.Default.Clear, "clear input", modifier = Modifier.clickable {
                        updateInput("")
                    })
                }
                val appNav = LocalAppNav.current
                Icon(Icons.Default.OpenInFull, "open in full", modifier = Modifier.combinedClickable(onLongClick = {
                    appNav.gotoTopicCompose(objectType, objectId, true)
                }) {
                    appNav.gotoTopicCompose(objectType, objectId, false)
                })
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
    val onClick = appNav::goto
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

        roomInfo.communityId?.let {
            CommunityRefCell(it) {
                dismiss()
                onClick(it, ObjectType.COMMUNITY)
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
        bus.emit(OnRoomJoined(roomInfo.id, info))
        onSuccess()
    }
}

private suspend fun exitRoom(roomInfo: RoomInfo, onSuccess: () -> Unit) {
    globalDialogState.use {
        val info = client.exitRoom(roomInfo.id).getOrThrow()
        bus.emit(OnRoomExited(roomInfo.id, info))
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
