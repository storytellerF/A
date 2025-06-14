package com.storyteller_f.a.app.compontents

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
import com.storyteller_f.a.app.Res
import com.storyteller_f.a.app.model.createAllMediaListViewModel
import com.storyteller_f.a.app.permission_denied
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import org.intellij.markdown.ast.ASTNode
import org.jetbrains.compose.resources.stringResource

@Composable
fun TopicContentField(
    topicInfo: TopicInfo,
    isEmbed: Boolean = false
) {
    when (val content = topicInfo.content) {
        is TopicContent.Plain -> {
            TopicContentFieldInternal(topicInfo, content.list.toImmutableList(), content.plain, isEmbed)
        }

        is TopicContent.Extracted -> {
            TopicContentFieldInternal(topicInfo, content.list.toImmutableList(), content.plain, isEmbed)
        }

        is TopicContent.Encrypted, is TopicContent.DecryptFailed, is TopicContent.Invalid -> {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                when (content) {
                    is TopicContent.DecryptFailed -> {
                        Text(content.message)
                    }
                    is TopicContent.Encrypted -> {
                        Text(stringResource(Res.string.permission_denied))
                    }
                    TopicContent.Invalid -> {
                        Text("invalid")
                    }
                    else -> {}
                }
            }
        }

        else -> {
        }
    }
}

@Composable
private fun TopicContentFieldInternal(
    topicInfo: TopicInfo,
    rawMediaList: ImmutableList<MediaInfo>,
    plain: String,
    isEmbed: Boolean
) {
    val (mediaList, objectTuple) = if (topicInfo.isEncrypted) {
        val list = createAllMediaListViewModel(topicInfo.rootId ob topicInfo.rootType)
        val media by list.handler.data.collectAsState()
        media?.data.orEmpty() to ObjectTuple(topicInfo.rootId, topicInfo.rootType)
    } else {
        rawMediaList to ObjectTuple(topicInfo.author, ObjectType.USER)
    }
    val mediaMap = mediaList.associateBy { it.name }.toImmutableMap()
    Markdown(
        plain,
        modifier = Modifier.fillMaxWidth().testTag("content"),
        colors = markdownColor(),
        typography = markdownTypography(),
        imageTransformer = CustomCoil3ImageTransformerImpl(mediaMap, objectTuple),
        components = markdownComponents(codeFence = {
            CustomCodeFence(it, mediaMap)
        }, codeBlock = { HighlightCodeBlock(it) }, paragraph = {
            CustomMarkdownParagraph(it.content, it.node, mediaMap = mediaMap, isEmbed = isEmbed)
        })
    )
}

@Composable
fun CustomMarkdownParagraph(
    content: String,
    node: ASTNode,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalMarkdownTypography.current.paragraph,
    mediaMap: ImmutableMap<String, MediaInfo>,
    isEmbed: Boolean
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
    ) {
        inlineContentMap.clear()
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
        inlineContentMap = inlineContentMap.toImmutableMap(),
        mediaMap = mediaMap,
        isEmbed = isEmbed
    )
}
