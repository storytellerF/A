package com.storyteller_f.a.app.compontents

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.permission_denied
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.compose.*
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.utils.*
import com.storyteller_f.a.app.model.createMediaListViewModel
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import org.intellij.markdown.ast.ASTNode
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

        is TopicContent.Encrypted -> {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.permission_denied))
            }
        }

        is TopicContent.DecryptFailed -> {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(content.message)
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
        }, codeBlock = { HighlightCodeBlock(it) }, paragraph = {
            CustomMarkdownParagraph(it.content, it.node, mediaMap = mediaMap)
        })
    )
}

@Composable
fun CustomMarkdownParagraph(
    content: String,
    node: ASTNode,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalMarkdownTypography.current.paragraph,
    mediaMap: Map<String, MediaInfo>
) {
    val density = LocalDensity.current
    val inlineContentMap = remember {
        mutableMapOf<String, String>()
    }
    val annotatorSettings = annotatorSettings()
    val styledText = remember(
        style,
        content,
        node.children,
        inlineContentMap,
    ) {
        buildAnnotatedString {
            pushStyle(style.toSpanStyle())
            customBuildMarkdownAnnotatedString(
                content,
                node.children,
                annotatorSettings,
                density,
                inlineContentMap
            )
            pop()
        }
    }

    CustomMarkdownText(
        styledText,
        modifier = modifier,
        style = style,
        inlineContentMap = inlineContentMap,
        mediaMap = mediaMap
    )
}
