package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashampoo.kim.Kim
import com.ashampoo.kim.common.convertToPhotoMetadata
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.compose.LocalImageTransformer
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.ImageTransformer
import com.storyteller_f.a.app.Res
import com.storyteller_f.a.app.permission_denied
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.CustomImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.intellij.markdown.ast.ASTNode
import org.jetbrains.compose.resources.stringResource
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.use

@Composable
fun TopicContentField(
    topicInfo: TopicInfo,
    isEmbed: Boolean = false,
) {
    when (val content = topicInfo.content) {
        is TopicContent.Plain -> {
            TopicContentFieldInternal(content.list.toImmutableList(), content.plain, isEmbed)
        }

        is TopicContent.Extracted -> {
            TopicContentFieldInternal(content.list.toImmutableList(), content.plain, isEmbed)
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
    rawMediaList: CustomImmutableList<MediaInfo>,
    plain: String,
    isEmbed: Boolean,
) {
    val mediaMap = rawMediaList.associateBy { it.name }.toImmutableMap()
    Markdown(
        plain,
        modifier = Modifier.fillMaxWidth().testTag("content"),
        colors = markdownColor(),
        typography = markdownTypography(),
        imageTransformer = CustomCoil3ImageTransformerImpl(mediaMap),
        components = markdownComponents(
            codeFence = {
                CustomCodeFence(it, mediaMap)
            },
            codeBlock = { HighlightCodeBlock(it) },
            paragraph = {
                CustomMarkdownParagraph(it.content, it.node, mediaMap = mediaMap, isEmbed = isEmbed)
            }
        )
    )
}

@Composable
fun CustomMarkdownParagraph(
    content: String,
    node: ASTNode,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalMarkdownTypography.current.paragraph,
    mediaMap: ImmutableMap<String, MediaInfo>,
    isEmbed: Boolean,
) {
    val density = LocalDensity.current
    val annotatorSettings = annotatorSettings()
    val transformer = LocalImageTransformer.current
    BoxWithConstraints {
        val width = convertDpToPx(this.maxWidth)
        val (styledText, inlineContentMap) = remember(
            style,
            content,
            node.children,
        ) {
            val inlineContentMap = mutableMapOf<String, String>()
            val text = buildAnnotatedString {
                pushStyle(style.toSpanStyle())
                customBuildMarkdownAnnotatedString(
                    content,
                    node.children,
                    annotatorSettings,
                    density,
                    inlineContentMap,
                    width
                )
                pop()
            }
            text to buildInlineTextContentMap(inlineContentMap, mediaMap, width, isEmbed, density, transformer)
        }

        CustomMarkdownText(
            styledText,
            modifier = modifier,
            style = style,
            inlineContentMap = inlineContentMap
        )
    }
}

private fun buildInlineTextContentMap(
    inlineContentMap: MutableMap<String, String>,
    mediaMap: ImmutableMap<String, MediaInfo>,
    width: Int,
    isEmbed: Boolean,
    density: Density,
    transformer: ImageTransformer
): ImmutableMap<String, InlineTextContent> {
    val map = inlineContentMap.mapValues { (_, value) ->
        val pair = if (value.startsWith("file:///")) {
            val metadata = SystemFileSystem.source(Path(value.substring(7))).buffered().use {
                Kim.readMetadata(it.readByteArray())?.convertToPhotoMetadata()
            }
            if (metadata != null) {
                val widthPx = metadata.widthPx
                val heightPx = metadata.heightPx
                if (widthPx != null && heightPx != null) {
                    widthPx to heightPx
                } else {
                    null
                }
            } else {
                null
            }
        } else {
            val info = mediaMap[value]
            val dimension = info?.dimension
            if (dimension != null) {
                dimension.width to dimension.height
            } else {
                null
            }
        }
        pair
    }
    return map.mapValues { (key, pair) ->
        if (width != 0 && pair != null) {
            val width = minOf(width, pair.first)
            val height =
                minOf(
                    width * pair.second / pair.first,
                    if (isEmbed) dpToPx(300.dp, density.density) else width * 2
                )
            val recalculatedWidth = height * pair.first / pair.second
            InlineTextContent(
                Placeholder(
                    pxToSp(recalculatedWidth, density.density),
                    pxToSp(height, density.density),
                    PlaceholderVerticalAlign.Bottom
                )
            ) {
                val value = inlineContentMap[key]
                transformer.transform(value.orEmpty())?.let { imageData ->
                    CustomMarkdownImage(imageData)
                }
            }
        } else {
            InlineTextContent(Placeholder(0.sp, 0.sp, PlaceholderVerticalAlign.Bottom)) {}
        }
    }.toImmutableMap()
}
