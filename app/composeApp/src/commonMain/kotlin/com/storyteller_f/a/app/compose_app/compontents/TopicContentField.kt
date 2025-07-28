package com.storyteller_f.a.app.compose_app.compontents

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.storyteller_f.a.app.compose_app.Res
import com.storyteller_f.a.app.compose_app.permission_denied
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.CustomImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import org.jetbrains.compose.resources.stringResource

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
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
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
    val mediaMap = remember(rawMediaList) {
        rawMediaList.associateBy { it.name }.toImmutableMap()
    }
    CompositionLocalProvider(LocalInspectionMode provides true) {
        Markdown(
            plain,
            modifier = Modifier.fillMaxWidth(),
            colors = markdownColor(),
            typography = markdownTypography(),
            imageTransformer = CustomCoil3ImageTransformerImpl(mediaMap),
            components = markdownComponents(
                codeFence = {
                    CustomCodeFence(it, mediaMap)
                },
                codeBlock = { HighlightCodeBlock(it) },
                paragraph = {
                    CustomMarkdownParagraph(
                        it.content,
                        it.node,
                        mediaMap = mediaMap,
                        isEmbed = isEmbed
                    )
                }
            )
        )
    }
}
