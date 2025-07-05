package com.storyteller_f.a.app.compose_app.pages.topic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.storyteller_f.a.app.compose_app.LocalAppNav
import com.storyteller_f.a.app.compose_app.LocalGlobalDialog
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.LocalToaster
import com.storyteller_f.a.app.compose_app.Res
import com.storyteller_f.a.app.compose_app.Toast
import com.storyteller_f.a.app.compose_app.bus
import com.storyteller_f.a.app.compose_app.common.StateView
import com.storyteller_f.a.app.compose_app.common.debounceState
import com.storyteller_f.a.app.compose_app.common.nestedStateView
import com.storyteller_f.a.app.compose_app.compontents.CustomAlertDialog
import com.storyteller_f.a.app.compose_app.compontents.CustomAlertDialogController
import com.storyteller_f.a.app.compose_app.compontents.GlobalDialogController
import com.storyteller_f.a.app.compose_app.compontents.InteractionRow
import com.storyteller_f.a.app.compose_app.compontents.TopicCell
import com.storyteller_f.a.app.compose_app.compontents.TopicContentField
import com.storyteller_f.a.app.compose_app.model.OnTopicChanged
import com.storyteller_f.a.app.compose_app.model.OnTopicCreated
import com.storyteller_f.a.app.compose_app.model.TopicViewModel
import com.storyteller_f.a.app.compose_app.model.createRoomViewModel
import com.storyteller_f.a.app.compose_app.model.createTopicViewModel
import com.storyteller_f.a.app.compose_app.model.createTopicsInTopicViewModel
import com.storyteller_f.a.app.compose_app.pages.room.CommonInputButton
import com.storyteller_f.a.app.compose_app.pages.room.InputGroupInternal
import com.storyteller_f.a.app.compose_app.pages.room.RoomInputGroup
import com.storyteller_f.a.app.compose_app.pages.search.CustomSearchBar
import com.storyteller_f.a.app.compose_app.pages.search.SearchScope
import com.storyteller_f.a.app.compose_app.success
import com.storyteller_f.a.client.core.LoadingState
import com.storyteller_f.a.client.core.SessionManager
import com.storyteller_f.a.client.core.createNewTopic
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.checkContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

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
        CustomSearchBar(
            SearchScope.TopicTopic(
                topicId
            )
        ) {
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
    val topicsViewModel =
        createTopicsInTopicViewModel(topicId)
    val topics = topicsViewModel.flow.collectAsLazyPagingItems()
    val lazyListState = rememberLazyListState()
    val debounced = debounceState(topics.loadState)
    val topicState by viewModel.handler.state.collectAsState()
    LaunchedEffect(topicState) {
        if (topicState is LoadingState.Done) {
            topics.refresh()
        }
    }
    StateView(
        viewModel.handler,
        modifier = Modifier.weight(1f),
        {
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

                    nestedStateView(topics, debounced, { subInfo, i ->
                        subInfo?.let { it1 ->
                            TopicCell(it1)
                        }
                        if (topics.itemCount - 1 != i) {
                            HorizontalDivider()
                        }
                    })
                }
                val scope = rememberCoroutineScope()
                TopicPageInputGroup(it, it.id) {
                    scope.launch {
                        delay(200)
                        lazyListState.animateScrollToItem(1)
                    }
                }
            }
        }
    )
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
        RoomInputGroup(
            topic.rootId,
            roomInfo,
            ObjectTuple(topicId, ObjectType.TOPIC),
            {},
            scrollTo
        )
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
    val userSessionManager = LocalSessionManager.current
    val myInfo by userSessionManager.sessionModel.userHandler.data.collectAsState()
    val my = myInfo
    val globalDialogController = LocalGlobalDialog.current
    InputGroupInternal(
        input,
        MaterialTheme.colorScheme.secondaryContainer,
        {
            input = it
        },
        {
            appNav.gotoTopicCompose(ObjectType.TOPIC, topic.id, false, topic.rootId.takeIf { topic.isEncrypted }, null)
        },
        if (topic.isEncrypted) ObjectTuple(topic.rootId, topic.rootType) else ObjectTuple(my?.id ?: 0, ObjectType.USER)
    ) {
        CommonInputButton(
            LoadingState.Done,
            input,
            isSending
        ) {
            if (!isSending) {
                sendTopic(scope, sendState, topic, input, {
                    input = it
                }, focusManager, toasterState, sessionManager, globalDialogController, scrollTo)
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
    toasterState: Toast,
    sessionManager: SessionManager,
    globalDialogController: GlobalDialogController,
    scrollTo: () -> Unit
) {
    if (!checkContent(input)) {
        toasterState.showMessage("invalid")
        return
    }
    scope.launch {
        sendState.value = LoadingState.Loading
        try {
            val info = sessionManager.createNewTopic(ObjectType.TOPIC, topic.id, input).getOrThrow()
            bus.emit(
                OnTopicCreated(
                    info
                )
            )
            updateInput("")
            focusManager.clearFocus()
            bus.emit(
                OnTopicChanged(
                    topic.copy(commentCount = topic.commentCount + 1)
                )
            )
            toasterState.showMessage(getString(Res.string.success))
            scrollTo()
        } catch (e: Exception) {
            globalDialogController.showErrorMessage(e)
        } finally {
            sendState.value = LoadingState.Done
        }
    }
}
