package com.storyteller_f.a.app.compose_app.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.compose_app.LocalAppNavFactory
import com.storyteller_f.a.app.compose_app.LocalGlobalDialog
import com.storyteller_f.a.app.compose_app.common.OnTopicChanged
import com.storyteller_f.a.app.compose_app.pages.topic.EmojiPicker
import com.storyteller_f.a.app.compose_app.pages.topic.pinOrUnpinTopic
import com.storyteller_f.a.app.compose_app.pages.user.UserIconWithDialog
import com.storyteller_f.a.app.compose_app.ui.MaterialSymbolsOutlined
import com.storyteller_f.a.app.core.components.CustomIcon
import com.storyteller_f.a.app.core.components.IconRes
import com.storyteller_f.a.app.core.components.emitEvent
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.PrimaryKey
import dev.tclement.fonticons.FontIcon
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter
import org.jetbrains.compose.ui.tooling.preview.PreviewParameterProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicCell(info: TopicInfo?) {
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    TopicCellInternal(info, supportPin = false) {
        showBottomSheet = true
    }
    info?.let {
        EmojiPicker(sheetState, showBottomSheet, it) {
            showBottomSheet = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserTopicCell(info: TopicInfo?) {
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    UserTopicCellInternal(info, supportPin = true) {
        showBottomSheet = true
    }
    info?.let {
        EmojiPicker(sheetState, showBottomSheet, it) {
            showBottomSheet = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomTopicCell(
    info: TopicInfo?,
    showAvatar: Boolean = true
) {
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    RoomTopicCellInternal(info, showAvatar) {
        showBottomSheet = true
    }
    info?.let {
        EmojiPicker(sheetState, showBottomSheet, it) {
            showBottomSheet = false
        }
    }
}

class TopicPreviewProvider : PreviewParameterProvider<TopicInfo> {
    override val values: Sequence<TopicInfo>
        get() = sequenceOf(TopicInfo.EMPTY.copy(content = TopicContent.Plain("hello")))
}

@OptIn(ExperimentalFoundationApi::class)
@Preview
@Composable
fun TopicCellInternal(
    @PreviewParameter(TopicPreviewProvider::class) topicInfo: TopicInfo?,
    supportPin: Boolean = false,
    startAddReaction: () -> Unit = {}
) {
    topicInfo ?: return
    val topicId = topicInfo.id
    val authorInfo = topicInfo.extension?.authorInfo
    CommonTopicCellInternal(topicInfo, supportPin) {
        UserCell(authorInfo)
        TopicContentAndInteraction(topicInfo, startAddReaction, topicId)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Preview
@Composable
fun UserTopicCellInternal(
    @PreviewParameter(TopicPreviewProvider::class) topicInfo: TopicInfo?,
    supportPin: Boolean = false,
    startAddReaction: () -> Unit = {}
) {
    topicInfo ?: return
    val topicId = topicInfo.id
    CommonTopicCellInternal(topicInfo, supportPin) {
        TopicContentAndInteraction(topicInfo, startAddReaction, topicId)
    }
}

@Composable
private fun TopicContentAndInteraction(
    topicInfo: TopicInfo,
    startAddReaction: () -> Unit,
    topicId: PrimaryKey
) {
    val appNavFactory = LocalAppNavFactory.current
    Column(
        Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TopicContentField(topicInfo, true)
        InteractionRow(topicInfo, startAddReaction) {
            appNavFactory.newAppNav().gotoTopic(topicId)
        }
        SubTopics(topicInfo)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Preview
@Composable
fun CommonTopicCellInternal(
    @PreviewParameter(TopicPreviewProvider::class) topicInfo: TopicInfo?,
    supportPin: Boolean = false,
    block: @Composable () -> Unit = {}
) {
    topicInfo ?: return
    val topicId = topicInfo.id
    val appNavFactory = LocalAppNavFactory.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        Column(
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).combinedClickable(onLongClick = {
                if (supportPin) {
                    expanded = true
                }
            }, onLongClickLabel = "topic menu") {
                appNavFactory.newAppNav().gotoTopic(topicId)
            }
        ) {
            block()
        }

        if (topicInfo.isPin) {
            FontIcon(
                MaterialSymbolsOutlined.Keep,
                "is pinned",
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            )
        }
        TopicDropdownMenu(expanded, topicInfo) {
            expanded = false
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoomTopicCellInternal(
    topicInfo: TopicInfo?,
    showAvatar: Boolean,
    startAddReaction: () -> Unit
) {
    topicInfo ?: return
    val authorInfo = topicInfo.extension?.authorInfo
    val topicId = topicInfo.id
    val appNavFactory = LocalAppNavFactory.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        Column(
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).combinedClickable(onLongClick = {
                expanded = true
            }, onLongClickLabel = "topic menu") {
                appNavFactory.newAppNav().gotoTopic(topicId)
            }.padding(8.dp)
        ) {
            if (showAvatar) {
                UserCell(authorInfo)
            }
            Column(
                Modifier.fillMaxWidth().padding(start = 48.dp, end = 8.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp).padding(top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TopicContentField(topicInfo, true)
                if (topicInfo.reactionCount > 0) {
                    InteractionRow(topicInfo, startAddReaction) {
                        appNavFactory.newAppNav().gotoTopic(topicId)
                    }
                }
                if (topicInfo.commentCount > 0) {
                    SubTopics(topicInfo)
                }
            }
        }
        RoomTopicDropdownMenu(expanded, startAddReaction) {
            expanded = false
        }
    }
}

@Composable
private fun SubTopics(topicInfo: TopicInfo) {
    val topics = topicInfo.extension?.subTopics.orEmpty()
    if (topics.isNotEmpty()) {
        Column(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 8.dp)
        ) {
            repeat(topics.size) {
                val info = topics[it]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val userInfo = info.extension?.authorInfo
                    UserIconWithDialog(userInfo, size = 20.dp)
                    when (val content = info.content) {
                        is TopicContent.Extracted -> {
                            Text(content.plain, maxLines = 1)
                        }

                        is TopicContent.Plain -> {
                            Text(content.plain, maxLines = 1)
                        }

                        else -> {
                            Text("invalid")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TopicDropdownMenu(expanded: Boolean, topicInfo: TopicInfo, onDismissRequest: () -> Unit) {
    val scope = rememberCoroutineScope()
    val globalDialogController = LocalGlobalDialog.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        val title = if (topicInfo.isPin) "Unpin" else "Pin"
        DropdownMenuItem(
            leadingIcon = {
                val char = when {
                    topicInfo.isPin -> MaterialSymbolsOutlined.KeepOff
                    else -> MaterialSymbolsOutlined.Keep
                }
                Box(modifier = Modifier.size(20.dp)) {
                    CustomIcon(IconRes.Font(char))
                }
            },
            text = { Text(title) },
            onClick = {
                scope.launch {
                    globalDialogController.pinOrUnpinTopic(topicInfo).onSuccess {
                        onDismissRequest()
                        globalDialogController.emitEvent(OnTopicChanged(it))
                    }
                }
            }
        )
    }
}

@Composable
fun RoomTopicDropdownMenu(
    expanded: Boolean,
    startAddReaction: () -> Unit,
    onDismissRequest: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            leadingIcon = {
                Box(modifier = Modifier.size(20.dp)) {
                    CustomIcon(IconRes.Font(MaterialSymbolsOutlined.AddReaction))
                }
            },
            text = { Text("add reaction") },
            onClick = {
                startAddReaction()
            }
        )
    }
}
