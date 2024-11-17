package com.storyteller_f.a.app.topic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.common.getOrCreateCollection
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.client_lib.createNewTopic
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.client.call.body
import kotbase.MutableDocument
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TopicComposePage(objectType: ObjectType, objectId: PrimaryKey, backPrePage: () -> Unit) {
    var input by remember {
        mutableStateOf("")
    }
    val pagerState = rememberPagerState {
        2
    }
    var selected by remember {
        mutableIntStateOf(0)
    }
    Scaffold(topBar = {
        TopAppBar({
        }, actions = {
            TopicComposeSubmitButton(input, objectType, objectId, backPrePage)
        })
    }) { values ->
        Column(modifier = Modifier.padding(values)) {
            TopicComposeInternal(selected, pagerState, listOf("Edit", "Preview"), input, {
                input = it
            }) {
                selected = it
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicComposeInternal(
    selected: Int,
    pagerState: PagerState,
    tabs: List<String>,
    input: String,
    updateInput: (String) -> Unit,
    updateSelected: (Int) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    PrimaryTabRow(selected) {
        tabs.forEachIndexed { i, e ->
            Tab(selected = selected == i, onClick = {
                updateSelected(i)
                coroutineScope.launch {
                    pagerState.scrollToPage(i)
                }
            }) {
                Text(text = e, modifier = Modifier.padding(vertical = 12.dp))
            }
        }
    }
    HorizontalPager(pagerState, key = tabs::get) { index ->
        if (index == 0) {
            EditTopicPage(input) {
                updateInput(it)
            }
        } else {
            PreviewTopicPage(input)
        }
    }
}

@Composable
private fun TopicComposeSubmitButton(
    input: String,
    objectType: ObjectType,
    objectId: PrimaryKey,
    backPrePage: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    IconButton({
        val c = input.trim()
        if (c.isNotEmpty()) {
            coroutineScope.launch {
                try {
                    val info = client.createNewTopic(objectType, objectId, input).body<TopicInfo>()
                    getOrCreateCollection("topics${info.parentId}").save(
                        MutableDocument(
                            info.id.toString(),
                            Json.encodeToString(info)
                        )
                    )
                    backPrePage()
                } catch (e: Exception) {
                    globalDialogState.showError(e)
                }
            }
        }
    }) {
        Icon(imageVector = Icons.Default.Check, "submit")
    }
}

@Composable
fun PreviewTopicPage(input: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        TopicContentField(TopicContent.Plain(input))
    }
}

@Composable
fun EditTopicPage(input: String, updateInput: (String) -> Unit) {
    OutlinedTextField(input, updateInput, modifier = Modifier.fillMaxSize())
}
