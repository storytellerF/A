package com.storyteller_f.a.app.topic

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.permission_denied
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.compontents.InteractionRow
import com.storyteller_f.a.app.model.createMediaListViewModel
import com.storyteller_f.a.app.model.createUserViewModel
import com.storyteller_f.a.app.user.UserCell
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import org.jetbrains.compose.resources.stringResource

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

@Composable
fun TopicContentField(
    topicInfo: TopicInfo,
    onClick: (() -> Unit)? = null
) {
    when (val content = topicInfo.content) {
        is TopicContent.Plain -> {
            TopicContentFieldInternal(topicInfo.isPrivate, topicInfo, content.list, content.plain, onClick)
        }

        is TopicContent.Extracted -> {
            TopicContentFieldInternal(topicInfo.isPrivate, topicInfo, content.list, content.plain, onClick)
        }

        is TopicContent.DecryptFailed, is TopicContent.Encrypted -> {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.permission_denied))
            }
        }

        else -> {
        }
    }
}

@Composable
private fun TopicContentFieldInternal(
    isPrivate: Boolean,
    topicInfo: TopicInfo,
    rawMediaList: List<MediaInfo>,
    plain: String,
    onClick: (() -> Unit)?
) {
    val mediaList = if (isPrivate) {
        val list = createMediaListViewModel(topicInfo.rootId, 0)
        val media by list.handler.data.collectAsState()
        media?.data.orEmpty()
    } else {
        rawMediaList
    }
    val mediaMap = mediaList.associateBy { it.item.noPrefixName }
    Markdown(
        plain,
        modifier = Modifier.fillMaxWidth().clickable(onClick != null) {
            onClick?.invoke()
        },
        colors = markdownColor(),
        typography = markdownTypography(),
        imageTransformer = CustomCoil3ImageTransformerImpl(mediaMap),
        components = markdownComponents(codeFence = {
            CustomCodeFence(it, mediaMap)
        }, codeBlock = { HighlightCodeBlock(it) })
    )
}
