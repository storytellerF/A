package com.storyteller_f.a.app.room

import a.composeapp.generated.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddHome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemContentType
import app.cash.paging.compose.itemKey
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.clientWs
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.common.viewModel
import com.storyteller_f.a.app.community.CommunityRefCell
import com.storyteller_f.a.app.compontents.*
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.search.CustomSearchBar
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
import kotbase.ktx.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

data class OnRoomJoined(val id: PrimaryKey)

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
    }
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

class RoomViewModel(private val requestInfo: suspend HttpClient.() -> RoomInfo) : SimpleViewModel<RoomInfo>() {
    constructor(roomId: PrimaryKey) : this({
        requestRoomInfo(roomId)
    })

    constructor(roomAid: String) : this({
        requestRoomInfoByAid(roomAid)
    })

    init {
        load()
        viewModelScope.launch {
            for (i in bus) {
                if (i is OnRoomJoined && i.id == handler.data.value?.id) {
                    handler.refresh()
                }
            }
        }
    }

    override suspend fun loadInternal() {
        handler.request {
            serviceCatching {
                requestInfo(client)
            }
        }
    }
}

@Composable
fun RoomPage(roomId: PrimaryKey) {
    val viewModel = viewModel(TopicsViewModel::class, keys = listOf("room-topics", roomId)) {
        TopicsViewModel(roomId, ObjectType.ROOM)
    }
    val room = viewModel(RoomViewModel::class, keys = listOf("room", roomId)) {
        RoomViewModel(roomId)
    }
    val items = viewModel.flow.collectAsLazyPagingItems()
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
        Column(modifier = Modifier.navigationBarsPadding()) {
            CustomSearchBar {
                RoomIcon(roomInfo, size = 40.dp)
            }
            RoomPageInternal(lazyListState, items)
            val scope = rememberCoroutineScope()
            RoomInputGroup(roomId, roomInfo, {
                scope.launch {
                    snackBarHost.showSnackbar(it, withDismissAction = true)
                }
            }, {
                scope.launch {
                    lazyListState.animateScrollToItem(0)
                }
            })
        }
    }
}

@Composable
private fun ColumnScope.RoomPageInternal(
    lazyListState: LazyListState,
    items: LazyPagingItems<TopicInfo>
) {
    LazyColumn(
        state = lazyListState,
        modifier = Modifier.Companion.weight(1f),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
            TopicCell(
                current,
                false,
                next?.author != current?.author
            )
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

    override suspend fun loadInternal() {
        handler.request {
            runCatching {
                val result = mutableListOf<Pair<PrimaryKey, String>>()
                var last: PrimaryKey? = null
                while (true) {
                    val list = client.requestRoomKeys(id, last, 100)
                    result.addAll(list.data)
                    val nextKey = list.pagination?.nextPageToken?.toPrimaryKeyOrNull()
                    if (nextKey == null) break
                    last = nextKey
                }
                result
            }
        }
    }
}

@Composable
private fun RoomInputGroup(
    roomId: PrimaryKey,
    roomInfo: RoomInfo?,
    notifyError: (String) -> Unit,
    scrollToNew: () -> Unit
) {
    var input by remember {
        mutableStateOf("")
    }
    var alertDialogState by remember {
        mutableStateOf<AlertDialogState?>(null)
    }
    if (roomInfo != null) {
        val keysViewModel = viewModel(RoomKeysViewModel::class, keys = listOf("room-keys", roomId)) {
            RoomKeysViewModel(roomId, roomInfo.isPrivate)
        }
        val keyState by keysViewModel.handler.state.collectAsState()
        val keyData by keysViewModel.handler.data.collectAsState()
        InputGroupInternal(
            input,
            {
                input = it
            },
            sendButton = {
                RoomSendButton(input = input) {
                    if (roomInfo.isJoined) {
                        sendMessage(roomInfo, roomId, input, scrollToNew, keyState, keyData, notifyError)
                    } else {
                        alertDialogState = AlertDialogState("not join", "do you want to join room?")
                    }
                }
            }
        )
    }

    val appNav = LocalAppNav.current
    CustomAlertDialog(alertDialogState, {
        alertDialogState = null
    }) {
        appNav.goto(roomId, ObjectType.ROOM)
    }
}

@Composable
fun RoomSendButton(
    input: String,
    send: () -> Unit
) {
    val state by clientWs.connectionHandler.state.collectAsState()
    val sendState by clientWs.localState.collectAsState()
    val isSending = sendState is LoadingState.Loading
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
private fun sendMessage(
    roomInfo: RoomInfo?,
    roomId: PrimaryKey,
    input: String,
    scrollToNew: () -> Unit,
    keyState: LoadingState?,
    keyData: List<Pair<PrimaryKey, String>>?,
    notifyError: (String) -> Unit
) {
    if (roomInfo != null) {
        clientWs.useWebSocket {
            val content = if (roomInfo.isPrivate) {
                if (keyState !is LoadingState.Done || keyData == null) {
                    notifyError("loading")
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
                    NewTopic(
                        ObjectType.ROOM,
                        roomId,
                        content
                    )
                )
                sendSerialized(message)
                delay(500)
                scrollToNew()
            }
        }
    }
}

@Composable
fun InputGroupInternal(
    input: String,
    updateInput: (String) -> Unit,
    sendButton: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(input, {
            updateInput(it)
        }, modifier = Modifier.weight(1f))
        Box(modifier = Modifier.width(60.dp).height(40.dp), contentAlignment = Alignment.Center) {
            sendButton()
        }
    }
}

@Composable
fun RoomDialogInternal(roomInfo: RoomInfo) {
    val appNav = LocalAppNav.current
    val onClick = appNav::goto
    DialogContainer {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceDim, RoundedCornerShape(8.dp))
                .padding(8.dp),
            Arrangement.spacedBy(12.dp)
        ) {
            RoomIcon(roomInfo, size = 50.dp)
            Column {
                Text(roomInfo.name)
                Text("aid: ${roomInfo.aid}")
            }
        }

        roomInfo.communityId?.let {
            CommunityRefCell(it) {
                onClick(it, ObjectType.COMMUNITY)
            }
        }

        Column {
            ButtonNav(Icons.Default.Settings, stringResource(Res.string.settings))
            ButtonNav(Icons.Default.Close, stringResource(Res.string.close))

            val scope = rememberCoroutineScope()
            if (roomInfo.isJoined) {
                ButtonNav(Icons.Default.Close, "Exit Room")
            } else {
                ButtonNav(Icons.Default.AddHome, "Join Room") {
                    scope.launch {
                        globalDialogState.use {
                            val communityId = roomInfo.communityId
                            if (communityId != null) {
                                if (!client.getCommunityInfo(communityId).isJoined) {
                                    throw Exception("you should join community first.")
                                }
                            }
                            client.joinRoom(roomInfo.id)
                            bus.send(OnRoomJoined(roomInfo.id))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDialog(
    showDialog: Boolean,
    roomInfo: RoomInfo?,
    dismiss: () -> Unit
) {
    if (roomInfo != null && showDialog) {
        BasicAlertDialog({
            dismiss()
        }) {
            RoomDialogInternal(roomInfo)
        }
    }
}
