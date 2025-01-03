package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.model.createUserViewModel
import com.storyteller_f.a.app.pages.topic.EmojiPicker
import com.storyteller_f.a.app.pages.user.UserCell
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicCell(
    info: TopicInfo,
    contentAlignAvatar: Boolean = true,
    showAvatar: Boolean = true
) {
    val author = info.author
    val authorViewModel = createUserViewModel(author)

    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    val authorInfo by authorViewModel.handler.data.collectAsState()
    TopicCellInternal(info, showAvatar, authorInfo, contentAlignAvatar) {
        showBottomSheet = true
    }
    EmojiPicker(sheetState, showBottomSheet, info) {
        showBottomSheet = false
    }
}

@Composable
fun TopicCellInternal(
    topicInfo: TopicInfo,
    showAvatar: Boolean,
    authorInfo: UserInfo?,
    contentAlignAvatar: Boolean,
    startAddReaction: () -> Unit
) {
    val topicId = topicInfo.id
    val appNav = LocalAppNav.current
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val avatarSize = 40.dp
        if (showAvatar) {
            UserCell(authorInfo, true, avatarSize) {
                appNav.gotoUser(it)
            }
        }
        Column(
            if (contentAlignAvatar) {
                Modifier
            } else {
                Modifier.fillMaxWidth().padding(horizontal = avatarSize)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                    .padding(18.dp)
            },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TopicContentField(
                topicInfo,
                onClick = {
                    appNav.gotoTopic(topicId)
                }
            )
            InteractionRow(topicInfo, startAddReaction) {
                appNav.gotoTopic(topicId)
            }
        }
    }
}
