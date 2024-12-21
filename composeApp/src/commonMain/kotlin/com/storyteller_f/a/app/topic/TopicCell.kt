package com.storyteller_f.a.app.topic

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.permission_denied
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import chaintech.videoplayer.model.AudioFile
import chaintech.videoplayer.ui.audio.AudioPlayerComposable
import chaintech.videoplayer.ui.video.VideoPlayerComposable
import chaintech.videoplayer.ui.youtube.YouTubePlayerComposable
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.compontents.InteractionRow
import com.storyteller_f.a.app.compontents.TextUnitToPx
import com.storyteller_f.a.app.compontents.buildTexPainter
import com.storyteller_f.a.app.model.createMediaListViewModel
import com.storyteller_f.a.app.model.createTopicViewModel
import com.storyteller_f.a.app.model.createUserViewModel
import com.storyteller_f.a.app.user.UserCell
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.extractMarkdownHeadline
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes
import dev.zt64.compose.pdf.RemotePdfState
import dev.zt64.compose.pdf.component.PdfPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.jetbrains.compose.resources.stringResource
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicCell(
    topicInfoRaw: TopicInfo,
    contentAlignAvatar: Boolean = true,
    showAvatar: Boolean = true
) {
    val viewModel = createTopicViewModel(topicInfoRaw.id)
    val topicInfo by viewModel.handler.data.collectAsState()
    topicInfo?.let { info ->
        val author = info.author
        val authorViewModel = createUserViewModel(author)

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
                topicInfo,
                showHeadline = true,
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
fun CustomCodeFence(modal: MarkdownComponentModel, mediaList1: Map<String, MediaInfo>) {
    val content = modal.content
    val children = modal.node.children
    val langOffset = children.indexOfFirst {
        it.type == MarkdownTokenTypes.FENCE_LANG
    }
    val lang = children.getOrNull(langOffset)?.getTextInNode(content).toString().lowercase()
    when {
        listOf("com.storyteller_f.a", "c.s.a", "csa").contains(lang) -> RefBlock(children, langOffset, content)

        lang == "math" -> LatexBlock(children, langOffset, content)

        lang == "object" -> ObjectBlock(children, langOffset, content, modal, mediaList1)

        else -> HighlightCodeBlock(modal)
    }
}

@Serializable
data class MarkdownObject(val contentType: String, val name: String)

@Composable
fun ObjectBlock(
    children: List<ASTNode>,
    langOffset: Int,
    content: String,
    modal: MarkdownComponentModel,
    mediaList1: Map<String, MediaInfo>
) {
    val c = readFenceContent(children, langOffset, content)
    val obj = Json.decodeFromString<MarkdownObject>(c)
    if (obj.contentType == "video/youtube") {
        YouTubePlayerComposable(
            modifier = Modifier.height(200.dp),
            videoId = obj.name
        )
    } else {
        val mediaInfo = mediaList1[obj.name]
        val url = mediaInfo?.url
        val contentType = mediaInfo?.item?.contentType
        when {
            url == null || contentType == null -> {
                HighlightCodeBlock(modal)
            }
            contentType == "application/pdf" -> {
                val errorIndicator = rememberVectorPainter(Icons.Default.Error)
                val refreshIndicator = rememberVectorPainter(Icons.Default.Refresh)
                val state = remember(url, errorIndicator, refreshIndicator) {
                    RemotePdfState(URL(url), errorIndicator, refreshIndicator)
                }
                HorizontalPager(
                    state = rememberPagerState { state.pageCount }
                ) { i ->
                    PdfPage(
                        state = state,
                        index = i
                    )
                }
            }
            contentType.startsWith("video/") -> {
                VideoPlayerComposable(
                    modifier = Modifier.height(200.dp),
                    url = url
                )
            }
            contentType.startsWith("audio/") -> {
                AudioPlayerComposable(
                    modifier = Modifier,
                    audios = listOf(AudioFile(url, obj.name))
                )
            }
            else -> {
                HighlightCodeBlock(modal)
            }
        }
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

class CustomCoil3ImageTransformerImpl(val mediaMap: Map<String, MediaInfo>) : ImageTransformer {

    @Composable
    override fun transform(link: String): ImageData {
        return rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(mediaMap[link]?.url)
                .size(coil3.size.Size.ORIGINAL)
                .build()
        ).let { ImageData(it) }
    }

    @Composable
    override fun intrinsicSize(painter: Painter): Size {
        var size by remember(painter) { mutableStateOf(painter.intrinsicSize) }
        if (painter is AsyncImagePainter) {
            val painterState = painter.state.collectAsState()
            val intrinsicSize = painterState.value.painter?.intrinsicSize
            intrinsicSize?.also { size = it }
        }
        return size
    }
}

@Composable
fun TopicContentField(
    topicInfo: TopicInfo,
    showHeadline: Boolean,
    onClick: (() -> Unit)? = null
) {
    when (val content = topicInfo.content) {
        is TopicContent.Plain -> {
            val mediaList = if (topicInfo.isPrivate) {
                val list = createMediaListViewModel(topicInfo.rootId, 0)
                val media by list.handler.data.collectAsState()
                media?.data.orEmpty()
            } else {
                content.list
            }
            val plain by produceState("", content.plain) {
                value = if (showHeadline) {
                    extractMarkdownHeadline(content.plain)
                } else {
                    content.plain
                }
            }
            val mediaMap = mediaList.associateBy { it.item.name }
            Markdown(
                plain,
                modifier = Modifier.fillMaxWidth().clickable(onClick != null) {
                    onClick?.invoke()
                },
                colors = markdownColor(),
                typography = markdownTypography(),
                imageTransformer = CustomCoil3ImageTransformerImpl(mediaMap),
                components = markdownComponents(codeFence = {
                    CustomCodeFence(it, mediaMap)
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
