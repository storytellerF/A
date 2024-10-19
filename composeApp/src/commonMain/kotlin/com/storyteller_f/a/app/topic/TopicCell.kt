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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eygraber.uri.Uri
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.storyteller_f.a.app.common.StateView2
import com.storyteller_f.a.app.community.CommunityRefCell
import com.storyteller_f.a.app.compontents.ReactionRow
import com.storyteller_f.a.app.compontents.TextUnitToPx
import com.storyteller_f.a.app.compontents.UserIcon
import com.storyteller_f.a.app.compontents.buildTexPainter
import com.storyteller_f.a.app.room.RoomRefCell
import com.storyteller_f.a.app.user.UserRefCell
import com.storyteller_f.a.app.user.UserViewModel
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.ObjectType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.tlaster.precompose.viewmodel.viewModel
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode

@Composable
fun TopicCell(
    topicInfo: TopicInfo?,
    contentAlignAvatar: Boolean = true,
    showAvatar: Boolean = true,
    onClick: (PrimaryKey, ObjectType) -> Unit = { _, _ -> }
) {
    if (topicInfo != null) {
        val author = topicInfo.author
        val authorViewModel = viewModel(UserViewModel::class, keys = listOf("user", author)) {
            UserViewModel(author)
        }
        val authorInfo by authorViewModel.handler.data.collectAsState()
        TopicCellInternal(topicInfo, showAvatar, authorInfo, contentAlignAvatar, onClick)
    }
}

@Composable
fun TopicCellInternal(
    topicInfo: TopicInfo,
    showAvatar: Boolean,
    authorInfo: UserInfo?,
    contentAlignAvatar: Boolean,
    onClick: (PrimaryKey, ObjectType) -> Unit = { _, _ -> }
) {
    Column(
        modifier = Modifier.clickable {
            onClick(topicInfo.id, ObjectType.TOPIC)
        },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val avatarSize = 40.dp
        if (showAvatar) {
            UserHeadRow(authorInfo, avatarSize)
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
                onClick = onClick
            )
            ReactionRow()
        }
    }
}

@Composable
fun UserHeadRow(userInfo: UserInfo?, avatarSize: Dp = 40.dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UserIcon(userInfo, avatarSize)
        Column {
            userInfo?.let { Text(it.nickname) }
        }
    }
}

@Composable
fun TopicRefCell(topicId: PrimaryKey, onClick: (PrimaryKey) -> Unit) {
    val viewModel = viewModel(TopicViewModel::class, keys = listOf("topic", topicId)) {
        TopicViewModel(topicId)
    }

    StateView2(viewModel.handler) {
        TopicRefCellContent(it, onClick, topicId)
    }
}

@Composable
private fun TopicRefCellContent(
    it: TopicInfo,
    onClick: (PrimaryKey) -> Unit,
    topicId: PrimaryKey,
) {
    val author = it.author
    val authorViewModel = viewModel(UserViewModel::class, keys = listOf("user", author)) {
        UserViewModel(author)
    }
    val authorInfo by authorViewModel.handler.data.collectAsState()
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(4.dp)).clickable {
                onClick(topicId)
            }.padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        authorInfo?.let {
            Text("${authorInfo?.nickname} :")
        }
        Text(
            (it.content as? TopicContent.Plain)?.plain.toString(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
fun CustomCodeFence(modal: MarkdownComponentModel, content: String, onClick: (PrimaryKey, ObjectType) -> Unit) {
    val children = modal.node.children
    val langOffset = children.indexOfFirst {
        it.type == MarkdownTokenTypes.FENCE_LANG
    }
    val lang = children.getOrNull(langOffset)?.getTextInNode(content)
    (when {
        listOf("com.storyteller_f.a", "c.s.a", "csa").contains(lang) -> {
            RefBlock(children, langOffset, content, onClick)
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
    content: String,
    onClick: (PrimaryKey, ObjectType) -> Unit
): Unit? {
    val textInNode = readFenceContent(children, langOffset, content)
    val uri = Uri.parse(textInNode)
    return if (uri.pathSegments.size == 2) {
        val id = uri.pathSegments[1].toULong()
        val p1 = uri.pathSegments[0]
        when (p1) {
            "topic" -> TopicRefCell(id) {
                onClick(it, ObjectType.TOPIC)
            }

            "room" -> RoomRefCell(roomId = id) {
                onClick(it, ObjectType.ROOM)
            }

            "community" -> CommunityRefCell(communityId = id) {
                onClick(it, ObjectType.COMMUNITY)
            }

            "user" -> UserRefCell(userId = id)

            else -> null
        }
    } else {
        null
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
    onClick: (PrimaryKey, ObjectType) -> Unit = { _, _ -> }
) {
    if (content1 is TopicContent.Plain) {
        Markdown(
            content1.plain,
            modifier = modifier,
            colors = markdownColor(),
            typography = markdownTypography(),
            imageTransformer = Coil3ImageTransformerImpl,
            components = markdownComponents(codeFence = { model ->
                CustomCodeFence(model, content1.plain) { id, type ->
                    onClick(id, type)
                }
            })
        )
    } else if (content1 is TopicContent.DecryptFailed) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text("Permission denied")
        }
    }
}
