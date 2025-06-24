package com.storyteller_f.a.app.pages.topic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.storyteller_f.a.app.LocalGlobalDialog
import com.storyteller_f.a.app.LocalSessionManager
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.model.OnAddReaction
import com.storyteller_f.a.client_lib.addReaction
import com.storyteller_f.shared.model.TopicInfo
import kotlinx.coroutines.launch
import org.kodein.emoji.Emoji
import org.kodein.emoji.list

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPicker(
    sheetState: SheetState,
    showSheet: Boolean,
    topic: TopicInfo,
    hideSheet: () -> Unit
) {
    var query by remember {
        mutableStateOf("")
    }
    BaseSheet(showSheet, sheetState, hideSheet) {
        EmojiPickerInternal(query, topic, sheetState, hideSheet) {
            query = it
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseSheet(
    showSheet: Boolean,
    sheetState: SheetState,
    hideSheet: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                hideSheet()
            },
            dragHandle = null,
            sheetState = sheetState,
            contentWindowInsets = {
                WindowInsets(0)
            },
        ) {
            content()
        }
    }
}

@Composable
fun SheetContainer(block: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().consumeWindowInsets(WindowInsets.navigationBars)) {
        Spacer(modifier = Modifier.height(20.dp))
        block()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EmojiPickerInternal(
    query: String,
    topic: TopicInfo,
    sheetState: SheetState,
    hideSheet: () -> Unit,
    updateQuery: (String) -> Unit,
) {
    SheetContainer {
        TextField(
            query,
            {
                updateQuery(it)
            },
            suffix = {
                Icon(Icons.Default.Clear, "clear reaction query")
            },
            modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(10.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
        Spacer(modifier = Modifier.height(10.dp))
        val emojiList by produceState(emptyList(), query) {
            value = if (query.isEmpty()) {
                Emoji.list()
            } else {
                Emoji.list().filter { emoji ->
                    emoji.details.description.contains(query, true)
                }
            }
        }
        val emojiSize = 50.dp
        BoxWithConstraints(modifier = Modifier.align(Alignment.CenterHorizontally)) {
            val contentWidth = this.maxWidth - 40.dp
            val count = (contentWidth / emojiSize).toInt()
            val style = if (emojiSize * count == contentWidth) {
                GridCells.FixedSize(emojiSize)
            } else {
                GridCells.Fixed(count)
            }
            LazyVerticalGrid(
                style,
                contentPadding = PaddingValues(20.dp, 10.dp),
                modifier = Modifier.wrapContentWidth().height(300.dp)
            ) {
                items(emojiList, key = {
                    it.toString()
                }) {
                    EmojiItem(emojiSize, topic, it, sheetState, hideSheet)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EmojiItem(
    emojiSize: Dp,
    topic: TopicInfo,
    emoji: Emoji,
    sheetState: SheetState,
    hideSheet: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sessionManager = LocalSessionManager.current
    val globalDialogController = LocalGlobalDialog.current
    Box(modifier = Modifier.size(emojiSize).clickable {
        scope.launch {
            globalDialogController.use {
                sessionManager.addReaction(topic.id, emoji.details.string)
                bus.emit(OnAddReaction(topic.id, emoji.details.string))
                sheetState.hide()
            }
        }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                hideSheet()
            }
        }
    }, contentAlignment = Alignment.Center) {
        Text(emoji.details.string, fontSize = 25.sp)
    }
}
