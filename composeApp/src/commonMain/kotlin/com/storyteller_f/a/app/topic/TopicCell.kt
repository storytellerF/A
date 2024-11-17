package com.storyteller_f.a.app.topic

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.common.viewModel
import com.storyteller_f.a.app.compontents.ReactionRow
import com.storyteller_f.a.app.compontents.TextUnitToPx
import com.storyteller_f.a.app.compontents.buildTexPainter
import com.storyteller_f.a.app.user.UserRow
import com.storyteller_f.a.app.user.UserViewModel
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.ObjectType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode

@Composable
fun TopicCell(
    topicInfo: TopicInfo?,
    contentAlignAvatar: Boolean = true,
    showAvatar: Boolean = true
) {
    if (topicInfo != null) {
        val author = topicInfo.author
        val authorViewModel = viewModel(UserViewModel::class, keys = listOf("user", author)) {
            UserViewModel(author)
        }
        val authorInfo by authorViewModel.handler.data.collectAsState()
        TopicCellInternal(topicInfo, showAvatar, authorInfo, contentAlignAvatar)
    }
}

@Composable
fun TopicCellInternal(
    topicInfo: TopicInfo,
    showAvatar: Boolean,
    authorInfo: UserInfo?,
    contentAlignAvatar: Boolean
) {
    val appNav = LocalAppNav.current
    val onClick = appNav::goto
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val avatarSize = 40.dp
        if (showAvatar) {
            UserRow(authorInfo, avatarSize)
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
                topicInfo.content,
                onClick = {
                    onClick(topicInfo.id, ObjectType.TOPIC)
                }
            )
            ReactionRow()
        }
    }
}

@Composable
fun CustomCodeFence(modal: MarkdownComponentModel, content: String) {
    val children = modal.node.children
    val langOffset = children.indexOfFirst {
        it.type == MarkdownTokenTypes.FENCE_LANG
    }
    val lang = children.getOrNull(langOffset)?.getTextInNode(content)
    (when {
        listOf("com.storyteller_f.a", "c.s.a", "csa").contains(lang) -> {
            RefBlock(children, langOffset, content)
        }

        lang == "math" -> {
            LatexBlock(children, langOffset, content)
        }

        else -> null
    }) ?: MarkdownCodeFence(modal.content, modal.node)
}

@Composable
private fun RefBlock(
    children: List<ASTNode>,
    langOffset: Int,
    content: String
) {
    val appNav = LocalAppNav.current
    val textInNode = readFenceContent(children, langOffset, content)
    TopicRoute.parseRefUri(textInNode).let {
        it.first?.let { it1 -> it1(it.second, appNav) }
    }
}

@Composable
private fun LatexBlock(
    children: List<ASTNode>,
    langOffset: Int,
    content: String
) {
    val textStyle = LocalTextStyle.current
    val size = TextUnitToPx(textStyle.fontSize)
    val backgroundColor = MaterialTheme.colorScheme.surface.value.toInt()
    val textColor = textStyle.color.value.toInt()
    val painter by produceState<Painter?>(null, children, langOffset, content, backgroundColor, textColor) {
        value = withContext(Dispatchers.Default) {
            buildTexPainter(
                readFenceContent(children, langOffset, content),
                backgroundColor,
                textColor,
                size
            )
        }
    }
    painter?.let {
        Image(
            painter = it,
            contentDescription = "math",
        )
    } ?: run {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(4.dp))
        }
    }
}

private fun readFenceContent(
    children: List<ASTNode>,
    langOffset: Int,
    content: String
): String {
    val start = children.subList(langOffset + 1, children.size).first {
        it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT
    }.startOffset
    val end = children.last {
        it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT
    }.endOffset
    return content.substring(start, end)
}

@Composable
fun TopicContentField(
    content1: TopicContent?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    if (content1 is TopicContent.Plain) {
        Markdown(
            content1.plain,
            modifier = modifier.fillMaxWidth().clickable(onClick != null) {
                onClick?.invoke()
            },
            colors = markdownColor(),
            typography = markdownTypography(),
            imageTransformer = Coil3ImageTransformerImpl,
            components = markdownComponents(codeFence = { model ->
                CustomCodeFence(model, content1.plain)
            })
        )
    } else if (content1 is TopicContent.DecryptFailed) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text("Permission denied")
        }
    }
}
