package com.storyteller_f.a.app.topic

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.storyteller_f.a.app.compontents.AudioView
import com.storyteller_f.a.app.compontents.TextUnitToPx
import com.storyteller_f.a.app.compontents.VideoView
import com.storyteller_f.a.app.compontents.buildTexPainter
import com.storyteller_f.shared.model.MediaInfo
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes
import dev.zt64.compose.pdf.RemotePdfState
import dev.zt64.compose.pdf.component.PdfPage
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import java.net.URI
import java.net.URL

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
        HighlightCodeBlock(modal)
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
                    RemotePdfState(URI.create(url).toURL(), errorIndicator, refreshIndicator)
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
                VideoView(url = url)
            }

            contentType.startsWith("audio/") -> {
                AudioView(url)
            }

            else -> {
                HighlightCodeBlock(modal)
            }
        }
    }
}

@Composable
fun HighlightCodeBlock(
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
    val textInNode = readFenceContent(children, langOffset, content)
    TopicRoute.parseRefUri(textInNode).let {
        it.first?.let { it1 -> it1(it.second) }
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

class CustomCoil3ImageTransformerImpl(private val mediaMap: Map<String, MediaInfo>) : ImageTransformer {

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
