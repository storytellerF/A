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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemContentType
import app.cash.paging.compose.itemKey
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.clientWs
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.compontents.*
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
import com.storyteller_f.shared.type.OKey
import com.storyteller_f.shared.type.ObjectType
import io.github.aakira.napier.Napier
import io.ktor.client.plugins.websocket.*
import kotbase.Expression
import kotbase.MutableDocument
import kotbase.ktx.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.tlaster.precompose.viewmodel.viewModel
import moe.tlaster.precompose.viewmodel.viewModelScope
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

data class OnRoomJoined(val id: OKey)

@OptIn(ExperimentalPagingApi::class)
class RoomTopicsViewModel(roomId: OKey) : PagingViewModel<OKey, TopicInfo>({
    CustomQueryPagingSource(
        select = select(all()),
        collectionName = "topics$roomId",
        queryProvider = {
            where {
                if (it != null) {
                    "id" lessThan it.toLong()
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

}, RoomTopicsRemoteMediator("topics$roomId") { loadKey ->
    processEncryptedTopic(client.getRoomTopics(roomId, loadKey))
})

@OptIn(ExperimentalPagingApi::class)
class RoomTopicsRemoteMediator(
    private val collectionName: String,
    val networkService: suspend (OKey?) -> ServerResponse<TopicInfo>
) :
    RemoteMediator<OKey, TopicInfo>() {
    private val scope = database.defaultScope
    private val collection
        get() = scope.getCollection(collectionName) ?: database.createCollection(
            collectionName
        )

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<OKey, TopicInfo>
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
                        }, Json.encodeToString(it)
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

class RoomViewModel(private val roomId: OKey) : SimpleViewModel<RoomInfo>() {
    init {
        load()
        viewModelScope.launch {
            for (i in bus) {
                if (i is OnRoomJoined && i.id == roomId) {
                    handler.refresh()
                }
            }
        }
    }

    override suspend fun loadInternal() {
        handler.request {
            serviceCatching {
                client.requestRoomInfo(roomId)
            }
        }
    }

}

@Composable
fun RoomPage(roomId: OKey, onClick: (OKey, ObjectType) -> Unit) {
    val viewModel = viewModel(RoomTopicsViewModel::class, keys = listOf("room-topics", roomId)) {
        RoomTopicsViewModel(roomId)
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
        Column(modifier = Modifier.padding(it).consumeWindowInsets(WindowInsets.statusBars)) {
            CustomSearchBar {
                RoomIcon(roomInfo, size = 40.dp)
            }
            RoomPageInternal(lazyListState, items, onClick)
            val scope = rememberCoroutineScope()
            InputGroup(roomId, roomInfo, {
                scope.launch {
                    snackBarHost.showSnackbar(it, withDismissAction = true)
                }
            }) {
                scope.launch {
                    lazyListState.animateScrollToItem(0)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.RoomPageInternal(
    lazyListState: LazyListState,
    items: LazyPagingItems<TopicInfo>,
    onClick: (OKey, ObjectType) -> Unit
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
                topicInfo.id
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
                next?.author != current?.author,
                onClick = onClick
            )
        }
    }
}

class RoomKeysViewModel(private val id: OKey, private: Boolean) : SimpleViewModel<List<Pair<OKey, String>>>() {

    init {
        if (private)
            load()
    }

    override suspend fun loadInternal() {
        handler.request {
            runCatching {
                val list = client.requestRoomKeys(id)
                list.data
            }
        }
    }
}

@Composable
private fun InputGroup(
    roomId: OKey,
    roomInfo: RoomInfo?,
    notifyError: (String) -> Unit,
    scrollToNew: () -> Unit
) {
    var input by remember {
        mutableStateOf("")
    }
    if (roomInfo != null) {
        val keysViewModel = viewModel(RoomKeysViewModel::class, keys = listOf("room-keys", roomId)) {
            RoomKeysViewModel(roomId, roomInfo.isPrivate)
        }
        val keyState by keysViewModel.handler.state.collectAsState()
        val keyData by keysViewModel.handler.data.collectAsState()
        val state by clientWs.connectionHandler.state.collectAsState()
        val sendState by clientWs.localState.collectAsState()
        val isSending = sendState is LoadingState.Loading
        InputGroupInternal(input, {
            input = it
        }, roomId, roomInfo.isJoined, state, isSending, {
            sendMessage(roomInfo, roomId, input, scrollToNew, keyState, keyData, notifyError)
        })
    }

}

@OptIn(ExperimentalStdlibApi::class)
private fun sendMessage(
    roomInfo: RoomInfo?,
    roomId: OKey,
    input: String,
    scrollToNew: () -> Unit,
    keyState: LoadingState?,
    keyData: List<Pair<OKey, String>>?,
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
    roomId: OKey?,
    isJoined: Boolean,
    wsState: LoadingState?,
    isSending: Boolean,
    sendMessage: () -> Unit = {},
) {
    val dialogState = rememberEventState()
    val scope = rememberCoroutineScope()
    if (isJoined) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(input, {
                updateInput(it)
            }, modifier = Modifier.weight(1f))
            when (wsState) {
                is LoadingState.Done -> {
                    IconButton({
                        if (input.isBlank()) {
                            scope.launch {
                                dialogState.showMessage(getString(Res.string.input_is_empty))
                            }
                        } else {
                            sendMessage()
                        }
                    }, enabled = !isSending) {
                        Icon(Icons.AutoMirrored.Default.Send, stringResource(Res.string.send))
                    }
                }

                is LoadingState.Error -> {
                    IconButton({
                        dialogState.showError(wsState.e)
                    }) {
                        Icon(Icons.Default.Error, stringResource(Res.string.error))
                    }
                }

                else -> {
                    CircularProgressIndicator()
                }
            }
        }
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Button({
                scope.launch {
                    if (roomId != null)
                        dialogState.use {
                            client.joinRoom(roomId)
                            bus.send(OnRoomJoined(roomId))
                        }
                }
            }) {
                Text("Join")
            }
        }
    }

    EventDialog(dialogState)
}

@Composable
fun RoomDialogInternal(roomInfo: RoomInfo) {
    DialogContainer {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceDim, RoundedCornerShape(8.dp))
                .padding(8.dp), Arrangement.spacedBy(12.dp)
        ) {
            RoomIcon(roomInfo, size = 50.dp)
            Column {
                Text(roomInfo.name)
                Text("aid: ${roomInfo.aid}")
            }
        }

        Column {
            ButtonNav(Icons.Default.Settings, stringResource(Res.string.settings))
            ButtonNav(Icons.Default.Close, stringResource(Res.string.close))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDialog(showDialog: Boolean, roomInfo: RoomInfo?, dismiss: () -> Unit) {
    if (roomInfo != null && showDialog) {
        BasicAlertDialog({
            dismiss()
        }) {
            RoomDialogInternal(roomInfo)
        }
    }
}
