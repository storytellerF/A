package com.storyteller_f.a.app.topic

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.permission_denied
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.common.viewModel
import com.storyteller_f.a.app.compontents.InteractionRow
import com.storyteller_f.a.app.compontents.TextUnitToPx
import com.storyteller_f.a.app.compontents.buildTexPainter
import com.storyteller_f.a.app.user.UserCell
import com.storyteller_f.a.app.user.UserViewModel
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.ObjectType
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicCell(
    topicInfoRaw: TopicInfo,
    contentAlignAvatar: Boolean = true,
    showAvatar: Boolean = true
) {
    val viewModel = viewModel(TopicViewModel::class, keys = listOf("topic-single", topicInfoRaw.id)) {
        TopicViewModel(topicInfoRaw)
    }
    val topicInfo by viewModel.handler.data.collectAsState()
    topicInfo?.let { info ->
        val author = info.author
        val authorViewModel = viewModel(UserViewModel::class, keys = listOf("user", author)) {
            UserViewModel(author)
        }

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
    val onClick = appNav::goto
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val avatarSize = 40.dp
        if (showAvatar) {
            UserCell(authorInfo, true, avatarSize)
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
                    onClick(topicId, ObjectType.TOPIC)
                }
            )
            InteractionRow(topicInfo, startAddReaction) {
                onClick(topicId, ObjectType.TOPIC)
            }
        }
    }
}

@Composable
fun CustomCodeFence(modal: MarkdownComponentModel) {
    val content = modal.content
    val children = modal.node.children
    val langOffset = children.indexOfFirst {
        it.type == MarkdownTokenTypes.FENCE_LANG
    }
    val lang = children.getOrNull(langOffset)?.getTextInNode(content).toString().lowercase()
    when {
        listOf("com.storyteller_f.a", "c.s.a", "csa").contains(lang) -> RefBlock(children, langOffset, content)

        lang == "math" -> LatexBlock(children, langOffset, content)

        else -> HighlightCodeBlock(modal)
    }
}

@Composable
private fun HighlightCodeBlock(
    modal: MarkdownComponentModel
) {
    val isDarkTheme = isSystemInDarkTheme()
    val highlightsBuilder = remember(isDarkTheme) {
        Highlights.Builder().theme(SyntaxThemes.atom(darkMode = isDarkTheme))
    }
    MarkdownHighlightedCodeFence(modal.content, modal.node, highlightsBuilder)
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
    content: TopicContent?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    when (content) {
        is TopicContent.Plain -> {
            Markdown(
                content.plain,
                modifier = modifier.fillMaxWidth().clickable(onClick != null) {
                    onClick?.invoke()
                },
                colors = markdownColor(),
                typography = markdownTypography(),
                imageTransformer = Coil3ImageTransformerImpl,
                components = markdownComponents(codeFence = {
                    CustomCodeFence(it)
                }, codeBlock = { HighlightCodeBlock(it) })
            )
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
