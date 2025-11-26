package com.storyteller_f.a.app.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.storyteller_f.a.app.LocalAppNavFactory
import com.storyteller_f.a.app.pages.topic.TopicRoute
import com.storyteller_f.a.app.core.components.TopicContentView
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.utils.readCodeFence

@Composable
fun AppTopicContentView(topicInfo: TopicInfo, isEmbed: Boolean = false) {
    val appNavFactory = LocalAppNavFactory.current
    TopicContentView(topicInfo, {
        appNavFactory.newAppNav().gotoMedia(it)
    }, isEmbed, {
        RefBlock(it)
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
