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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.ExperimentalPagingApi
import app.cash.paging.compose.collectAsLazyPagingItems
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.common.viewModel
import com.storyteller_f.a.app.compontents.AlertDialogState
import com.storyteller_f.a.app.compontents.CustomAlertDialog
import com.storyteller_f.a.app.compontents.ReactionRow
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.room.InputGroupInternal
import com.storyteller_f.a.app.room.RoomSendButton
import com.storyteller_f.a.app.room.TopicsViewModel
import com.storyteller_f.a.app.search.CustomSearchBar
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.decrypt
import com.storyteller_f.shared.getDerPrivateKey
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKeyOrNull
import io.ktor.client.*
import io.ktor.client.call.body
import kotbase.MutableDocument
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
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
        Column(modifier = Modifier.padding(it).consumeWindowInsets(WindowInsets.statusBars)) {
            var showDialog by remember {
                mutableStateOf(false)
            }
            CustomSearchBar {
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
            topic?.let { it1 -> TopicInputGroup(it1, topicId) }
        }
    }
}

@Composable
private fun TopicInputGroup(
    topic: TopicInfo,
    topicId: PrimaryKey,
) {
    var input by remember {
        mutableStateOf("")
    }
    var alertDialogState by remember {
        mutableStateOf<AlertDialogState?>(null)
    }
    val toaster = rememberToasterState()
    Toaster(toaster, alignment = Alignment.Center)
    val scope = rememberCoroutineScope()
    InputGroupInternal(input, {
        input = it
    }, sendButton = {
        if (topic.rootType == ObjectType.ROOM) {
            RoomSendButton(input) {
                toaster.show("not yet support", duration = 1.seconds)
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
                            val info = client.createNewTopic(ObjectType.TOPIC, topicId, input).body<TopicInfo>()
                            getOrCreateCollection("topics${info.parentId}").save(
                                MutableDocument(
                                    info.id.toString(),
                                    Json.encodeToString(info)
                                )
                            )
                            toaster.show(getString(Res.string.success), duration = 1.seconds)
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

    val appNav = LocalAppNav.current
    CustomAlertDialog(alertDialogState, {
        alertDialogState = null
    }) {
        appNav.goto(topic.rootId, topic.rootType)
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
                val info = requestInfo(client)
                processEncryptedTopic(listOf(info)).first()
            }
        }
    }
}

@OptIn(ExperimentalPagingApi::class)
class TopicNestedViewModel(topicId: PrimaryKey) : PagingViewModel<PrimaryKey, TopicInfo>({
    SimplePagingSource {
        serviceCatching {
            val info = client.getTopicTopics(topicId, it, 10)
            info.copy(processEncryptedTopic(info.data))
        }.map {
            APagingData(it.data, it.pagination?.nextPageToken?.toPrimaryKeyOrNull())
        }
    }
})

@OptIn(ExperimentalStdlibApi::class)
suspend fun processEncryptedTopic(info: List<TopicInfo>): List<TopicInfo> {
    val value = LoginViewModel.state.value
    val uid = LoginViewModel.user.value?.id
    val key = if (value is ClientSession.LoginSuccess) getDerPrivateKey(value.privateKey) else null
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
