package com.storyteller_f.a.app.topic

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.input_is_empty
import a.composeapp.generated.resources.send
import a.composeapp.generated.resources.success
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.ExperimentalPagingApi
import app.cash.paging.compose.collectAsLazyPagingItems
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.common.viewModel
import com.storyteller_f.a.app.compontents.AlertDialogState
import com.storyteller_f.a.app.compontents.CustomAlertDialog
import com.storyteller_f.a.app.compontents.ReactionRow
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.room.InputGroupInternal
import com.storyteller_f.a.app.room.RoomSendButton
import com.storyteller_f.a.app.search.CustomSearchBar
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.decrypt
import com.storyteller_f.shared.getDerPrivateKey
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.client.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun TopicPage(topicId: PrimaryKey, onLogin: () -> Unit, onClick: (PrimaryKey, ObjectType) -> Unit) {
    val viewModel = viewModel(TopicViewModel::class, keys = listOf("topic", topicId)) {
        TopicViewModel(topicId)
    }
    val topic by viewModel.handler.data.collectAsState()

    val topicsViewModel = viewModel(TopicNestedViewModel::class, keys = listOf("topic-topics", topicId)) {
        TopicNestedViewModel(topicId)
    }
    val topics = topicsViewModel.flow.collectAsLazyPagingItems()
    val snackBarHost = remember {
        SnackbarHostState()
    }
    Scaffold(snackbarHost = {
        SnackbarHost(snackBarHost)
    }) {
        Column(modifier = Modifier.padding(it).consumeWindowInsets(WindowInsets.statusBars)) {
            var showDialog by remember {
                mutableStateOf(false)
            }
            CustomSearchBar(onLogin) {
                Icon(Icons.Default.Topic, "topic", modifier = Modifier.clickable {
                    showDialog = true
                })
                TopicDialog(topic, showDialog) {
                    showDialog = false
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                StateView(viewModel.handler, {
                    topics.refresh()
                }) {
                    LazyColumn(
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            TopicContentField(it.content, onFenceClick = onClick)
                        }

                        item {
                            ReactionRow()
                        }

                        item {
                            HorizontalDivider()
                        }

                        nestedStateView(topics) {
                            TopicCell(it, onClick = onClick)
                        }
                    }
                }
            }
            topic?.let { it1 -> TopicInputGroup(it1, topicId, snackBarHost, onClick) }
        }
    }
}

@Composable
private fun TopicInputGroup(
    topic: TopicInfo,
    topicId: PrimaryKey,
    snackBarHost: SnackbarHostState,
    onClick: (PrimaryKey, ObjectType) -> Unit,
) {
    var input by remember {
        mutableStateOf("")
    }
    var alertDialogState by remember {
        mutableStateOf<AlertDialogState?>(null)
    }
    val scope = rememberCoroutineScope()
    InputGroupInternal(input, {
        input = it
    }, sendButton = {
        if (topic.rootType == ObjectType.ROOM) {
            RoomSendButton(input) {
            }
        } else {
            IconButton({
                if (input.isBlank()) {
                    scope.launch {
                        globalDialogState.showMessage(getString(Res.string.input_is_empty))
                    }
                } else {
                    scope.launch {
                        try {
                            client.createNewTopic(ObjectType.TOPIC, topicId, input)
                            snackBarHost.showSnackbar(getString(Res.string.success))
                        } catch (e: Exception) {
                            globalDialogState.showError(e)
                        }
                    }
                }
            }, enabled = true) {
                Icon(Icons.AutoMirrored.Default.Send, stringResource(Res.string.send))
            }
        }
    })

    CustomAlertDialog(alertDialogState, {
        alertDialogState = null
    }) {
        onClick(topic.rootId, topic.rootType)
    }
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
                requestInfo(client)
            }
        }
    }
}

@OptIn(ExperimentalPagingApi::class)
class TopicNestedViewModel(topicId: PrimaryKey) : PagingViewModel<PrimaryKey, TopicInfo>({
    SimplePagingSource {
        serviceCatching {
            processEncryptedTopic(client.getTopicTopics(topicId, it, 10))
        }.map {
            APagingData(it.data, it.pagination?.nextPageToken?.toULongOrNull())
        }
    }
})

@OptIn(ExperimentalStdlibApi::class)
suspend fun processEncryptedTopic(info: ServerResponse<TopicInfo>): ServerResponse<TopicInfo> {
    val value = LoginViewModel.state.value
    val uid = LoginViewModel.user.value?.id
    val key = if (value is ClientSession.LoginSuccess) getDerPrivateKey(value.privateKey) else null
    return info.copy(info.data.map { topicInfo ->
        val content = topicInfo.content
        if (content !is TopicContent.Encrypted || uid == null || key == null) {
            topicInfo
        } else {
            val s = content.encryptedKey[uid]
            topicInfo.copy(
                content = if (s != null) {
                    runCatching {
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
    })
}
