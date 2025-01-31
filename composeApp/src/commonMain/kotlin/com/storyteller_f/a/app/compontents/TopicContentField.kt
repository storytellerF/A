package com.storyteller_f.a.app.compontents

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.permission_denied
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.DefaultMarkdownAnimation
import com.mikepenz.markdown.model.markdownAnimations
import com.storyteller_f.a.app.model.createMediaListViewModel
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import org.jetbrains.compose.resources.stringResource

@Composable
fun TopicContentField(
    topicInfo: TopicInfo,
) {
    when (val content = topicInfo.content) {
        is TopicContent.Plain -> {
            TopicContentFieldInternal(topicInfo.isPrivate, topicInfo, content.list, content.plain)
        }

        is TopicContent.Extracted -> {
            TopicContentFieldInternal(topicInfo.isPrivate, topicInfo, content.list, content.plain)
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
        modifier = Modifier.fillMaxWidth().testTag("content"),
        colors = markdownColor(),
        typography = markdownTypography(),
        imageTransformer = CustomCoil3ImageTransformerImpl(mediaMap),
        components = markdownComponents(codeFence = {
            CustomCodeFence(it, mediaMap)
        }, codeBlock = { HighlightCodeBlock(it) }),
        animations = markdownAnimations(animateTextSize = {
            this
        })
    )
}
