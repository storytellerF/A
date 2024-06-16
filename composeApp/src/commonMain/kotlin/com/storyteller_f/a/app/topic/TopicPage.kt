package com.storyteller_f.a.app.topic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.ExperimentalPagingApi
import app.cash.paging.compose.collectAsLazyPagingItems
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.compontents.ReactionRow
import com.storyteller_f.a.app.search.CustomSearchBar
import com.storyteller_f.a.client_lib.ClientSession
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.a.client_lib.getTopicInfo
import com.storyteller_f.a.client_lib.getTopicTopics
import com.storyteller_f.shared.decrypt
import com.storyteller_f.shared.getDerPrivateKey
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.OKey
import com.storyteller_f.shared.type.ObjectType
import moe.tlaster.precompose.viewmodel.viewModel

@Composable
fun TopicPage(topicId: OKey, onClick: (OKey, ObjectType) -> Unit) {
    val viewModel = viewModel(TopicViewModel::class, keys = listOf("topic", topicId)) {
        TopicViewModel(topicId)
    }
    val topic by viewModel.handler.data.collectAsState()

    val topics = viewModel(TopicNestedViewModel::class, keys = listOf("topic-topics", topicId)) {
        TopicNestedViewModel(topicId)
    }
    val items = topics.flow.collectAsLazyPagingItems()

    Surface {
        Column {
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
            StateView(viewModel.handler) {
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        TopicContentField(it.content, onClick = onClick)
                    }

                    item {
                        ReactionRow()
                    }

                    item {
                        HorizontalDivider()
                    }

                    nestedStateView(items) {
                        TopicCell(it, onClick = onClick)
                    }
                }
            }
        }
    }
}

class TopicViewModel(private val topicId: OKey) : SimpleViewModel<TopicInfo>() {
    init {
        load()
    }

    override suspend fun loadInternal() {
        handler.request {
            serviceCatching {
                client.getTopicInfo(topicId)
            }
        }
    }


}

@OptIn(ExperimentalPagingApi::class)
class TopicNestedViewModel(topicId: OKey) : PagingViewModel<Int, TopicInfo>({
    SimplePagingSource {
        serviceCatching {
            processEncryptedTopic(client.getTopicTopics(topicId))
        }.map {
            APagingData(it.data, null)
        }

    }
})


@OptIn(ExperimentalStdlibApi::class)
suspend fun processEncryptedTopic(info: ServerResponse<TopicInfo>): ServerResponse<TopicInfo> {
    val key = getDerPrivateKey((LoginViewModel.state.value as ClientSession.PrivateKeyLogin).privateKey)
    val uid = LoginViewModel.user.value!!.id
    return info.copy(info.data.map { topicInfo ->
        val content = topicInfo.content
        if (content is TopicContent.Encrypted) {
            val s = content.encryptedKey[uid]
            topicInfo.copy(content = if (s != null) {
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
        } else {
            topicInfo
        }
    })
}
