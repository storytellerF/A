package com.storyteller_f.a.app.topic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.compontents.EventDialog
import com.storyteller_f.a.app.compontents.rememberEventState
import com.storyteller_f.a.client_lib.createNewTopic
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.launch

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
    val messageState = rememberEventState()
    val coroutineScope = rememberCoroutineScope()
    val tabs = listOf("Edit", "Preview")
    Scaffold(topBar = {
        TopAppBar({
        }, actions = {
            IconButton({
                val c = input.trim()
                if (c.isNotEmpty()) {
                    coroutineScope.launch {
                        try {
                            client.createNewTopic(objectType, objectId, input)
                            backPrePage()
                        } catch (e: Exception) {
                            messageState.showError(e)
                        }
                    }
                }
            }) {
                Icon(imageVector = Icons.Default.Check, "submit")
            }
        })
    }) { values ->
        Column(modifier = Modifier.padding(values)) {
            PrimaryTabRow(selected) {
                tabs.forEachIndexed { i, e ->
                    Tab(selected = selected == i, onClick = {
                        selected = i
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
                        input = it
                    }
                } else {
                    PreviewTopicPage(input)
                }
            }
        }
    }
    EventDialog(messageState)
}

@Composable
fun PreviewTopicPage(input: String) {
    TopicContentField(TopicContent.Plain(input))
}

@Composable
fun EditTopicPage(input: String, updateInput: (String) -> Unit) {
    OutlinedTextField(input, updateInput, modifier = Modifier.fillMaxSize())
}
