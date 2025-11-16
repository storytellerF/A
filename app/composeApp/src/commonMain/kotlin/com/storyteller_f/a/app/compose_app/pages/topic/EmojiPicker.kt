package com.storyteller_f.a.app.compose_app.pages.topic

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
import com.storyteller_f.a.app.compose_app.CustomUserSessionManager
import com.storyteller_f.a.app.compose_app.LocalGlobalTask
import com.storyteller_f.a.app.compose_app.common.OnAddReaction
import com.storyteller_f.a.app.compose_app.common.OnRemoveReaction
import com.storyteller_f.a.app.core.components.BaseSheet
import com.storyteller_f.a.app.core.components.SheetContainer
import com.storyteller_f.a.app.core.components.GlobalTask
import com.storyteller_f.a.app.core.components.emitEvent
import com.storyteller_f.a.app.core.components.request
import com.storyteller_f.a.app.core.components.use
import com.storyteller_f.a.client.core.addReaction
import com.storyteller_f.a.client.core.deleteReaction
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.TopicInfo
import org.kodein.emoji.Emoji
import org.kodein.emoji.list

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPicker(
    sheetState: SheetState,
    showSheet: Boolean,
    topic: TopicInfo,
    hideSheet: () -> Unit,
) {
    var query by remember {
        mutableStateOf("")
    }
    BaseSheet(showSheet, sheetState, hideSheet) {
        EmojiPickerInternal(query, topic, hideSheet) {
            query = it
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EmojiPickerInternal(
    query: String,
    topic: TopicInfo,
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
            modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth()
                .padding(horizontal = 20.dp),
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
                    EmojiItem(emojiSize, topic, it, hideSheet)
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
    hideSheet: () -> Unit,
) {
    val globalTask = LocalGlobalTask.current
    val emojiText = emoji.details.string
    Box(modifier = Modifier.size(emojiSize).clickable {
        hideSheet()
        globalTask.use("${topic.id} $emojiText") { state ->
            state.use {
                addReaction(topic, emojiText)
            }
        }
    }, contentAlignment = Alignment.Center) {
        Text(emojiText, fontSize = 25.sp)
    }
}

suspend fun GlobalTask<CustomUserSessionManager>.addReaction(
    topic: TopicInfo,
    emojiText: String,
): Result<ReactionInfo> {
    val existing = topic.extension?.reactions?.firstOrNull {
        it.emoji == emojiText
    }
    val fakeInfo = existing?.copy(count = existing.count + 1) ?: ReactionInfo(
        emojiText,
        topic.id,
        1,
        true,
        Long.MAX_VALUE
    )
    emitEvent(OnAddReaction(fakeInfo, topic))
    return request {
        addReaction(topic.id, emojiText)
    }.onSuccess {
        emitEvent(OnAddReaction(it, topic))
    }.onFailure {
        emitEvent(OnRemoveReaction(fakeInfo.copy(count = fakeInfo.count - 1), topic))
    }
}

suspend fun GlobalTask<CustomUserSessionManager>.deleteReaction(
    topic: TopicInfo,
    emojiText: String,
    existing: ReactionInfo,
): Result<ReactionInfo> {
    val fakeInfo = existing.copy(count = existing.count - 1)
    emitEvent(OnRemoveReaction(fakeInfo, topic))
    return request { deleteReaction(emojiText, topic.id) }.onSuccess {
        emitEvent(OnRemoveReaction(it, topic))
    }.onFailure {
        emitEvent(OnRemoveReaction(existing, topic))
    }
}
