package com.storyteller_f.a.app.compose_app.pages.topic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.storyteller_f.a.app.compose_app.LocalAppNav
import com.storyteller_f.a.app.compose_app.LocalGlobalTask
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.common.StateView
import com.storyteller_f.a.app.compose_app.common.bottomAppending
import com.storyteller_f.a.app.compose_app.common.topPrepend
import com.storyteller_f.a.app.compose_app.compontents.CustomAlertDialog
import com.storyteller_f.a.app.compose_app.compontents.CustomAlertDialogController
import com.storyteller_f.a.app.compose_app.compontents.InteractionRow
import com.storyteller_f.a.app.compose_app.compontents.TopicCell
import com.storyteller_f.a.app.compose_app.compontents.TopicContentField
import com.storyteller_f.a.app.compose_app.compontents.UserIcon
import com.storyteller_f.a.app.compose_app.compontents.use
import com.storyteller_f.a.app.compose_app.model.OnTopicCreated
import com.storyteller_f.a.app.compose_app.model.TopicViewModel
import com.storyteller_f.a.app.compose_app.model.createRoomViewModel
import com.storyteller_f.a.app.compose_app.model.createTopicViewModel
import com.storyteller_f.a.app.compose_app.model.createTopicsInTopicViewModel
import com.storyteller_f.a.app.compose_app.model.createUserViewModel
import com.storyteller_f.a.app.compose_app.pages.room.CommonInputButton
import com.storyteller_f.a.app.compose_app.pages.room.InputGroupInternal
import com.storyteller_f.a.app.compose_app.pages.room.RoomInputGroup
import com.storyteller_f.a.app.compose_app.pages.search.CustomSearchBar
import com.storyteller_f.a.app.compose_app.pages.search.SearchScope
import com.storyteller_f.a.client.core.LoadingState
import com.storyteller_f.a.client.core.createTopic
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.checkContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        TopicPageInternal(topicId, viewModel, snackBarHost) {
            showBottomSheet = true
        }
        TopicEmojiPicker(viewModel, sheetState, showBottomSheet) {
            showBottomSheet = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicEmojiPicker(
    viewModel: TopicViewModel,
    sheetState: SheetState,
    showBottomSheet: Boolean,
    close: () -> Unit
) {
    val topic by viewModel.handler.data.collectAsState()
    topic?.let {
        EmojiPicker(sheetState, showBottomSheet, it) {
            close()
        }
    }
}

@Composable
private fun TopicPageInternal(
    topicId: PrimaryKey,
    viewModel: TopicViewModel,
    snackBarHostState: SnackbarHostState,
    startAddReaction: () -> Unit
) {
    val topic by viewModel.handler.data.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        var showDialog by remember {
            mutableStateOf(false)
        }
        CustomSearchBar(SearchScope.TopicTopic(topicId)) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                topic?.let {
                    val author = it.author
                    val authorViewModel =
                        createUserViewModel(author)
                    val authorInfo by authorViewModel.handler.data.collectAsState()
                    UserIcon(authorInfo)
                }
                Icon(
                    Icons.Default.Topic,
                    "topic",
                    modifier = Modifier.size(40.dp).clip(CircleShape).clickable {
                        showDialog = true
                    }.padding(8.dp)
                )
            }
            TopicDialog(topic, showDialog) {
                showDialog = false
            }
        }
        topic?.let {
            if (it.rootId != it.parentId) {
                Box(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp)) {
                    TopicRefCell(it.parentId)
                }
            }
        }
        val lazyListState = rememberLazyListState()
        TopicPageContent(topicId, viewModel, startAddReaction, lazyListState)
        val scope = rememberCoroutineScope()
        topic?.let {
            TopicPageInputGroup(it, snackBarHostState) {
                scope.launch {
                    delay(200)
                    lazyListState.animateScrollToItem(1)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.TopicPageContent(
    topicId: PrimaryKey,
    viewModel: TopicViewModel,
    startAddReaction: () -> Unit,
    lazyListState: LazyListState
) {
    val subTopicsViewModel =
        createTopicsInTopicViewModel(topicId)
    val subTopics = subTopicsViewModel.flow.collectAsLazyPagingItems()
    val topicState by viewModel.handler.state.collectAsState()
    LaunchedEffect(topicState) {
        if (topicState is LoadingState.Done) {
            subTopics.refresh()
        }
    }
    StateView(viewModel.handler, modifier = Modifier.weight(1f)) {
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

            topPrepend(subTopics.loadState)
            items(
                subTopics.itemSnapshotList.size,
                key = subTopics.itemKey { subTopic ->
                    subTopic.id.toString()
                }
            ) { subTopicIndex ->
                subTopics[subTopicIndex]?.let { it1 ->
                    TopicCell(it1)
                }
                if (subTopics.itemSnapshotList.size - 1 != subTopicIndex) {
                    HorizontalDivider()
                }
            }
            bottomAppending(subTopics.loadState)
        }
    }
}

@Composable
private fun TopicPageInputGroup(
    topic: TopicInfo,
    snackBarHostState: SnackbarHostState,
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
            ObjectTuple(topic.id, ObjectType.TOPIC),
            snackBarHostState,
            {},
            scrollTo
        )
    } else {
        TopicInputGroup(scrollTo, topic, snackBarHostState)
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
    topic: TopicInfo,
    snackBarHostState: SnackbarHostState
) {
    var input by remember {
        mutableStateOf("")
    }
    val appNav = LocalAppNav.current
    val userSessionManager = LocalSessionManager.current
    val myInfo by userSessionManager.sessionModel.userHandler.data.collectAsState()
    val my = myInfo
    InputGroupInternal(
        input,
        MaterialTheme.colorScheme.secondaryContainer,
        {
            input = it
        },
        {
            appNav.gotoTopicCompose(
                ObjectType.TOPIC,
                topic.id,
                false,
                topic.rootId.takeIf { topic.isEncrypted },
                null
            )
        },
        if (topic.isEncrypted) {
            ObjectTuple(topic.rootId, topic.rootType)
        } else {
            ObjectTuple(
                my?.id ?: 0,
                ObjectType.USER
            )
        }
    ) {
        TopicSendButton(input, {
            input = it
        }, topic, scrollTo, snackBarHostState)
    }
}

@Composable
fun TopicSendButton(
    input: String,
    updateInput: (String) -> Unit,
    topic: TopicInfo,
    scrollTo: () -> Unit,
    snackBarHostState: SnackbarHostState
) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val sessionManager = LocalSessionManager.current
    val globalTask = LocalGlobalTask.current
    val key = "topic ${topic.id}"
    val isSending = globalTask.stateMap[key] is LoadingState.Loading
    CommonInputButton(
        LoadingState.Done,
        input,
        isSending
    ) {
        if (checkContent(input)) {
            globalTask.use(key) { state, bus ->
                state.use {
                    sessionManager.createTopic(ObjectType.TOPIC, topic.id, input).onSuccess {
                        bus.emit(OnTopicCreated(it))
                        updateInput("")
                        focusManager.clearFocus()
                        scrollTo()
                    }.onFailure {
                        snackBarHostState
                            .showSnackbar(it.message.toString())
                    }
                }
            }
        } else {
            scope.launch {
                snackBarHostState.showSnackbar("invalid")
            }
        }
    }
}
