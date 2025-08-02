package com.storyteller_f.a.app.compose_app.compontents

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.compose_app.LocalAppNav
import com.storyteller_f.a.app.compose_app.RoomScreen
import com.storyteller_f.a.app.compose_app.pages.topic.EmojiPicker
import com.storyteller_f.a.app.compose_app.pages.topic.TopicDropdownMenu
import com.storyteller_f.a.app.compose_app.pages.user.UserCell
import com.storyteller_f.a.app.compose_app.ui.MaterialSymbolsOutlined
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import dev.tclement.fonticons.FontIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicCell(
    info: TopicInfo?,
    showAvatar: Boolean = true
) {
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    TopicCellInternal(info, showAvatar) {
        showBottomSheet = true
    }
    info?.let {
        EmojiPicker(sheetState, showBottomSheet, it) {
            showBottomSheet = false
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopicCellInternal(
    topicInfo: TopicInfo?,
    showAvatar: Boolean,
    startAddReaction: () -> Unit
) {
    topicInfo ?: return
    val authorInfo = topicInfo.extension?.authorInfo
    val topicId = topicInfo.id
    val appNav = LocalAppNav.current
    var expanded by remember { mutableStateOf(false) }
    val isRoomPage = appNav.hasRoute(RoomScreen::class)
    Box {
        Column(
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).combinedClickable(onLongClick = {
                appNav.gotoTopic(topicId)
            }, onLongClickLabel = "topic menu") {
                appNav.gotoTopic(topicId)
            }.padding(8.dp)
        ) {
            if (showAvatar) {
                UserCell(authorInfo, true)
            }
            Column(
                if (isRoomPage) {
                    Modifier.fillMaxWidth().padding(start = 48.dp, end = 8.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp).padding(top = 8.dp, bottom = 12.dp)
                } else {
                    Modifier.padding(horizontal = 8.dp)
                },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TopicContentField(topicInfo, true)
                InteractionRow(topicInfo, startAddReaction) {
                    appNav.gotoTopic(topicId)
                }
                SubTopics(topicInfo)
            }
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
                .padding(8.dp)
        ) {
            repeat(topics.size) {
                val info = topics[it]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val userInfo = info.extension?.authorInfo
                    UserIcon(userInfo, size = 20.dp)
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
