package com.storyteller_f.a.app.pages.topic

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
import com.storyteller_f.a.app.pages.room.getCurrentUserInfo
import com.storyteller_f.a.app.pages.search.CustomSearchBar
import com.storyteller_f.a.app.pages.search.SearchScope
import com.storyteller_f.a.client_lib.LoadingState
import com.storyteller_f.a.client_lib.SessionManager
import com.storyteller_f.a.client_lib.createNewTopic
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.checkContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicPage(topicId: PrimaryKey) {
    val viewModel = createTopicViewModel(topicId)
    val snackBarHost = remember {
        SnackbarHostState()
    }
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    Scaffold(snackbarHost = {
        SnackbarHost(snackBarHost)
    }) {
        TopicPageInternal(topicId, viewModel) {
            showBottomSheet = true
        }
        val topic by viewModel.handler.data.collectAsState()
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
    viewModel: TopicViewModel,
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
            val topic by viewModel.handler.data.collectAsState()
            TopicDialog(topic, showDialog) {
                showDialog = false
            }
        }
        TopicPageContent(topicId, viewModel, startAddReaction)
    }
}

@Composable
private fun ColumnScope.TopicPageContent(
    topicId: PrimaryKey,
    viewModel: TopicViewModel,
    startAddReaction: () -> Unit
) {
    val topicsViewModel = createTopicsInTopicViewModel(topicId)
    val topics = topicsViewModel.flow.collectAsLazyPagingItems()
    val lazyListState = rememberLazyListState()
    StateView(viewModel.handler, modifier = Modifier.Companion.weight(1f), {
        topics.refresh()
    }, {
        Column {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                state = lazyListState,
                modifier = Modifier.weight(1f).fillMaxWidth()
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

                nestedStateView(topics) { subInfo, i ->
                    subInfo?.let { it1 -> TopicCell(it1) }
                    if (topics.itemCount - 1 != i) {
                        HorizontalDivider()
                    }
                }
            }
            val scope = rememberCoroutineScope()
            TopicPageInputGroup(it, it.id) {
                scope.launch {
                    delay(200)
                    lazyListState.animateScrollToItem(1)
                }
            }
        }
    })
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
        RoomInputGroup(topic.rootId, roomInfo, ObjectTuple(topicId, ObjectType.TOPIC), {}, scrollTo)
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
    val sessionManager = LocalSessionManager.current
    val isSending = sendState.value is LoadingState.Loading
    val appNav = LocalAppNav.current
    val my = getCurrentUserInfo()
    InputGroupInternal(
        input,
        MaterialTheme.colorScheme.secondaryContainer,
        {
            input = it
        },
        {
            appNav.gotoTopicCompose(ObjectType.TOPIC, topic.id, false, topic.rootId.takeIf { topic.isPrivate })
        },
        if (topic.isPrivate) ObjectTuple(topic.rootId, topic.rootType) else ObjectTuple(my?.id ?: 0, ObjectType.USER)
    ) {
        CommonInputButton(LoadingState.Done, input, isSending) {
            if (!isSending) {
                sendTopic(scope, sendState, topic, input, {
                    input = it
                }, focusManager, toasterState, sessionManager, scrollTo)
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
    sessionManager: SessionManager,
    scrollTo: () -> Unit
) {
    if (!checkContent(input)) {
        toasterState.show("invalid")
        return
    }
    scope.launch {
        sendState.value = LoadingState.Loading
        try {
            val info = sessionManager.createNewTopic(ObjectType.TOPIC, topic.id, input).getOrThrow()
            bus.emit(OnTopicCreated(info))
            updateInput("")
            focusManager.clearFocus()
            bus.emit(OnTopicChanged(topic.copy(commentCount = topic.commentCount + 1)))
            toasterState.show(getString(Res.string.success), duration = 1.seconds)
            scrollTo()
        } catch (e: Exception) {
            globalDialogState.showErrorState(e)
        } finally {
            sendState.value = LoadingState.Done
        }
    }
}
