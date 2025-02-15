package com.storyteller_f.a.app.pages.topic

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.success
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import com.dokar.sonner.ToasterState
import com.storyteller_f.a.app.*
import com.storyteller_f.a.app.common.StateView
import com.storyteller_f.a.app.common.nestedStateView
import com.storyteller_f.a.app.compontents.*
import com.storyteller_f.a.app.model.*
import com.storyteller_f.a.app.pages.room.CommonInputButton
import com.storyteller_f.a.app.pages.room.InputGroupInternal
import com.storyteller_f.a.app.pages.room.RoomInputGroup
import com.storyteller_f.a.app.pages.search.CustomSearchBar
import com.storyteller_f.a.app.pages.search.SearchScope
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.decrypt
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import io.ktor.client.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicPage(topicId: PrimaryKey) {
    val viewModel = createTopicViewModel(topicId)
    val topic by viewModel.handler.data.collectAsState()

    val topicsViewModel = createTopicsInTopicViewModel(topicId)
    val topics = topicsViewModel.flow.collectAsLazyPagingItems()
    val snackBarHost = remember {
        SnackbarHostState()
    }
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    Scaffold(snackbarHost = {
        SnackbarHost(snackBarHost)
    }) {
        TopicPageInternal(topicId, topic, viewModel, topics) {
            showBottomSheet = true
        }
        topic?.let {
            EmojiPicker(sheetState, showBottomSheet, it) {
                showBottomSheet = false
            }
        }
    }
}

@Composable
private fun TopicPageInternal(
    topicId: PrimaryKey,
    topic: TopicInfo?,
    viewModel: TopicViewModel,
    topics: LazyPagingItems<TopicInfo>,
    startAddReaction: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        var showDialog by remember {
            mutableStateOf(false)
        }
        CustomSearchBar(SearchScope.TopicTopic(topicId)) {
            Icon(Icons.Default.Topic, "topic", modifier = Modifier.clickable {
                showDialog = true
            })
            TopicDialog(topic, showDialog) {
                showDialog = false
            }
        }
        val lazyListState = rememberLazyListState()
        StateView(viewModel.handler, {
            topics.refresh()
        }, modifier = Modifier.weight(1f)) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                state = lazyListState,
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    TopicContentField(it)
                    Spacer(modifier = Modifier.height(12.dp))
                    InteractionRow(it, {
                        startAddReaction()
                    }) {
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                }

                nestedStateView(topics) {
                    it?.let { topicInfoRaw -> TopicCell(topicInfoRaw) }
                }
            }
        }
        val scope = rememberCoroutineScope()
        topic?.let {
            TopicPageInputGroup(it, topicId) {
                scope.launch {
                    delay(200)
                    lazyListState.animateScrollToItem(1)
                }
            }
        }
    }
}

@Composable
private fun TopicPageInputGroup(
    topic: TopicInfo,
    topicId: PrimaryKey,
    scrollTo: () -> Unit,
) {
    val alertDialogState = remember {
        CustomAlertDialogController()
    }
    if (topic.rootType == ObjectType.ROOM) {
        val roomId = topic.rootId
        val room = createRoomViewModel(roomId)
        val roomInfo by room.handler.data.collectAsState()
        RoomInputGroup(topic.rootId, roomInfo, topicId, {}, scrollTo)
    } else {
        TopicInputGroup(scrollTo, topic)
    }

    val appNav = LocalAppNav.current
    CustomAlertDialog(alertDialogState, {
        alertDialogState.close()
    }) {
        appNav.gotoTopic(topic.rootId)
    }
}

@Composable
private fun TopicInputGroup(
    scrollTo: () -> Unit,
    topic: TopicInfo
) {
    val scope = rememberCoroutineScope()
    val toasterState = LocalToaster.current
    var input by remember {
        mutableStateOf("")
    }
    val focusManager = LocalFocusManager.current
    val sendState = remember {
        mutableStateOf<LoadingState>(LoadingState.Done)
    }
    val client = LocalClient.current
    val isSending = sendState.value is LoadingState.Loading
    InputGroupInternal(
        topic.id,
        ObjectType.TOPIC,
        input,
        MaterialTheme.colorScheme.secondaryContainer,
        null,
        {
            input = it
        },
        { mediaInfo ->
            insertContent(mediaInfo, {
                input = it
            }, input)
            sendTopic(scope, sendState, topic, input, {
                input = it
            }, focusManager, toasterState, client, scrollTo)
        },
    ) {
        CommonInputButton(LoadingState.Done, input, isSending) {
            if (!isSending) {
                sendTopic(scope, sendState, topic, input, {
                    input = it
                }, focusManager, toasterState, client, scrollTo)
            }
        }
    }
}

private fun sendTopic(
    scope: CoroutineScope,
    sendState: MutableState<LoadingState>,
    topic: TopicInfo,
    input: String,
    updateInput: (String) -> Unit,
    focusManager: FocusManager,
    toasterState: ToasterState,
    client: HttpClient,
    scrollTo: () -> Unit
) {
    scope.launch {
        sendState.value = LoadingState.Loading
        try {
            val info = client.createNewTopic(ObjectType.TOPIC, topic.id, input).getOrThrow()
            updateDocumentInParent(info)
            updateInput("")
            focusManager.clearFocus()
            bus.emit(OnTopicChanged(topic.copy(commentCount = topic.commentCount + 1)))
            toasterState.show(getString(Res.string.success), duration = 1.seconds)
            scrollTo()
        } catch (e: Exception) {
            globalDialogState.showError(e)
        } finally {
            sendState.value = LoadingState.Done
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun processEncryptedTopic(info: List<TopicInfo>): List<TopicInfo> {
    val value = LoginViewModel.state.value
    val uid = LoginViewModel.user.value?.id
    val key = if (value is ClientSession.SignUpSuccess) value.session else null
    return info.map { topicInfo ->
        val content = topicInfo.content
        if (content !is TopicContent.Encrypted || uid == null || key == null) {
            topicInfo
        } else {
            val s = content.encryptedKey[uid]
            topicInfo.copy(
                content = if (s != null) {
                    runCatching {
                        key.decrypt(
                            content.encrypted.hexToByteArray(),
                            s.hexToByteArray()
                        )
                    }.fold(onSuccess = {
                        TopicContent.Plain(it)
                    }, onFailure = {
                        Napier.e(it) {
                            "decrypt ${topicInfo.id}"
                        }
                        TopicContent.DecryptFailed(it.message.toString())
                    })
                } else {
                    TopicContent.DecryptFailed("auth failed")
                }
            )
        }
    }
}
