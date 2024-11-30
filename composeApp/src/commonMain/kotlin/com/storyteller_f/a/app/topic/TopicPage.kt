package com.storyteller_f.a.app.topic

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import app.cash.paging.compose.collectAsLazyPagingItems
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.compontents.CustomAlertDialog
import com.storyteller_f.a.app.compontents.CustomAlertDialogController
import com.storyteller_f.a.app.compontents.ReactionRow
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.room.*
import com.storyteller_f.a.app.search.CustomSearchBar
import com.storyteller_f.a.app.search.SearchScope
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.decrypt
import com.storyteller_f.shared.getDerPrivateKey
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.client.*
import io.ktor.client.call.*
import kotbase.MutableDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration.Companion.seconds

@Composable
fun TopicPage(topicId: PrimaryKey) {
    val viewModel = viewModel(TopicViewModel::class, keys = listOf("topic", topicId)) {
        TopicViewModel(topicId)
    }
    val topic by viewModel.handler.data.collectAsState()

    val topicsViewModel = viewModel(TopicsViewModel::class, keys = listOf("topic-topics", topicId)) {
        TopicsViewModel(topicId, ObjectType.TOPIC)
    }
    val topics = topicsViewModel.flow.collectAsLazyPagingItems()
    val snackBarHost = remember {
        SnackbarHostState()
    }
    Scaffold(snackbarHost = {
        SnackbarHost(snackBarHost)
    }) {
        Column(modifier = Modifier) {
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
            Box(modifier = Modifier.weight(1f)) {
                StateView(viewModel.handler, {
                    topics.refresh()
                }) {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        state = lazyListState
                    ) {
                        item {
                            TopicContentField(it.content)
                            Spacer(modifier = Modifier.height(12.dp))
                            ReactionRow()
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider()
                        }

                        nestedStateView(topics) {
                            TopicCell(it)
                        }
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
    val scope = rememberCoroutineScope()
    if (topic.rootType == ObjectType.ROOM) {
        val roomId = topic.rootId
        val room = viewModel(RoomViewModel::class, keys = listOf("room", roomId)) {
            RoomViewModel(roomId)
        }
        val roomInfo by room.handler.data.collectAsState()
        RoomInputGroup(topic.rootId, roomInfo, {}, {
        })
    } else {
        TopicInputGroup(scope, topicId, scrollTo)
    }

    val appNav = LocalAppNav.current
    CustomAlertDialog(alertDialogState, {
        alertDialogState.close()
    }) {
        appNav.goto(topic.rootId, topic.rootType)
    }
}

@Composable
private fun TopicInputGroup(
    scope: CoroutineScope,
    topicId: PrimaryKey,
    scrollTo: () -> Unit
) {
    val toaster = rememberToasterState()
    Toaster(toaster, alignment = Alignment.Center)
    var input by remember {
        mutableStateOf("")
    }
    InputGroupInternal(input, MaterialTheme.colorScheme.secondaryContainer, {
        input = it
    }, sendButton = {
        val focusManager = LocalFocusManager.current
        var sendState by remember {
            mutableStateOf<LoadingState>(LoadingState.Done())
        }
        val isSending = sendState is LoadingState.Loading
        CommonInputButton(LoadingState.Done(), input, {
            if (!isSending) {
                scope.launch {
                    sendState = LoadingState.Loading("")
                    try {
                        val info = client.createNewTopic(ObjectType.TOPIC, topicId, input).body<TopicInfo>()
                        getOrCreateCollection("topics${info.parentId}").save(
                            MutableDocument(
                                info.id.toString(),
                                Json.encodeToString(info)
                            )
                        )
                        sendState = LoadingState.Done()
                        input = ""
                        focusManager.clearFocus()
                        toaster.show(getString(Res.string.success), duration = 1.seconds)
                        scrollTo()
                    } catch (e: Exception) {
                        globalDialogState.showError(e)
                    } finally {
                        sendState = LoadingState.Done()
                    }
                }
            }
        }, isSending)
    })
}

class TopicViewModel(private val requestInfo: suspend HttpClient.() -> TopicInfo) : SimpleViewModel<TopicInfo>() {
    constructor(topicId: PrimaryKey) : this({
        getTopicInfo(topicId)
    })

    constructor(topicAid: String) : this({
        getTopicInfoByAid(topicAid)
    })

    init {
        load()
    }

    override suspend fun loadInternal() {
        handler.request {
            serviceCatching {
                val info = requestInfo(client)
                processEncryptedTopic(listOf(info)).first()
            }
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun processEncryptedTopic(info: List<TopicInfo>): List<TopicInfo> {
    val value = LoginViewModel.state.value
    val uid = LoginViewModel.user.value?.id
    val key = if (value is ClientSession.SignUpSuccess) getDerPrivateKey(value.privateKey) else null
    return info.map { topicInfo ->
        val content = topicInfo.content
        if (content !is TopicContent.Encrypted || uid == null || key == null) {
            topicInfo
        } else {
            val s = content.encryptedKey[uid]
            topicInfo.copy(
                content = if (s != null) {
                    runCatching<String> {
                        decrypt(
                            key,
                            content.encrypted.hexToByteArray(),
                            s.hexToByteArray()
                        )
                    }.fold(onSuccess = {
                        TopicContent.Plain(it)
                    }, onFailure = {
                        TopicContent.DecryptFailed(it.message.toString())
                    })
                } else {
                    TopicContent.DecryptFailed("auth failed")
                }
            )
        }
    }
}
