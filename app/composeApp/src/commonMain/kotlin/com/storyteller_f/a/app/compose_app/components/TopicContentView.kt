package com.storyteller_f.a.app.compose_app.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.storyteller_f.a.app.compose_app.LocalAppNavFactory
import com.storyteller_f.a.app.compose_app.pages.topic.TopicRoute
import com.storyteller_f.a.app.core.CoreStrings
import com.storyteller_f.a.app.core.components.CustomCoil3ImageTransformerImpl
import com.storyteller_f.a.app.core.components.CustomMarkdown
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.utils.readCodeFence
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

@Composable
fun TopicContentView(topicInfo: TopicInfo, isEmbed: Boolean = false) {
    val content = topicInfo.content
    if (content is TopicContent.DecryptFailed) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(content.message)
        }
        return
    }
    if (content is TopicContent.Encrypted) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(CoreStrings.permission_denied())
        }
        return
    }
    val (plain, fileInfos) = when (content) {
        is TopicContent.Plain -> content.plain to content.fileInfos

        is TopicContent.Extracted -> content.plain to content.fileInfos

        else -> return
    }
    val mediaMap = remember(fileInfos.toImmutableList()) {
        fileInfos.toImmutableList().associateBy { it.name }.toImmutableMap()
    }
    val appNavFactory = LocalAppNavFactory.current
    val imageTransformer = remember(mediaMap) {
        CustomCoil3ImageTransformerImpl(mediaMap) {
            appNavFactory.newAppNav().gotoMedia(it)
        }
    }
    val dimensionMap = remember(mediaMap) {
        mediaMap.mapValues { it.value.dimension }.toImmutableMap()
    }
    CustomMarkdown(plain, isEmbed, imageTransformer, dimensionMap, {
        CustomCodeFence(it, mediaMap) {
            RefBlock(it)
        }
    })
}

@Composable
fun RefBlock(
    modal: MarkdownComponentModel
) {
    val (first, second) = remember(modal.node, modal.content) {
        val textInNode = readCodeFence(modal.node, modal.content)
        TopicRoute.parseRefUri(textInNode)
    }
    first?.let { it1 -> it1(second) }
}
