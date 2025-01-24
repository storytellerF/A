package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ImageRequest
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.storyteller_f.a.app.LocalClient
import com.storyteller_f.a.app.pages.topic.TopicRoute
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.utils.MarkdownObject
import com.storyteller_f.shared.utils.getLang
import com.storyteller_f.shared.utils.readCodeFence
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes
import dev.zt64.compose.pdf.RemotePdfState
import dev.zt64.compose.pdf.component.PdfPage
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.URI

@Composable
fun CustomCodeFence(modal: MarkdownComponentModel, mediaList1: Map<String, MediaInfo>) {
    val lang = remember(modal.node, modal.content) {
        getLang(modal.node, modal.content)
    }
    when {
        listOf("com.storyteller_f.a", "c.s.a", "csa").contains(lang) -> RefBlock(modal)

        lang == "math" -> LatexBlock(modal)

        lang == "object" -> ObjectBlock(modal, mediaList1)

        else -> HighlightCodeBlock(modal)
    }
}

private val json = Json {
    ignoreUnknownKeys = true
}

@Composable
fun ObjectBlock(
    modal: MarkdownComponentModel,
    mediaList1: Map<String, MediaInfo>
) {
    val obj = remember(modal.node, modal.content) {
        val c = readCodeFence(modal.node, modal.content)
        json.decodeFromString<MarkdownObject>(c)
    }
    when (obj.contentType) {
        "video/youtube" -> HighlightCodeBlock(modal)
        "application/vnd.apple.mpegurl" -> {
            when {
                obj.url.trim().isEmpty() -> HighlightCodeBlock(modal)
                runCatching {
                    Url(obj.url)
                }.getOrNull() == null -> HighlightCodeBlock(modal)

                else -> VideoView(modifier = Modifier, obj.url, "application/vnd.apple.mpegurl")
            }
        }

        else -> {
            val mediaInfo = mediaList1[obj.name]
            val url = mediaInfo?.url
            val contentType = mediaInfo?.item?.contentType
            when {
                url == null || contentType == null -> HighlightCodeBlock(modal)

                contentType == "application/pdf" -> PdfView(url)

                contentType.startsWith("video/") -> VideoView(modifier = Modifier, url, contentType)

                contentType.startsWith("audio/") -> AudioView(url)

                else -> HighlightCodeBlock(modal)
            }
        }
    }
}

@Composable
private fun PdfView(url: String) {
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
    modal: MarkdownComponentModel
) {
    val (first, second) = remember(modal.node, modal.content) {
        val textInNode = readCodeFence(modal.node, modal.content)
        TopicRoute.parseRefUri(textInNode)
    }
    first?.let { it1 -> it1(second) }
}

@Composable
private fun LatexBlock(
    modal: MarkdownComponentModel
) {
    val textStyle = LocalTextStyle.current
    val size = TextUnitToPx(textStyle.fontSize)
    val backgroundColor = MaterialTheme.colorScheme.surface.value.toInt()
    val textColor = textStyle.color.value.toInt()
    val painter by produceState<Painter?>(null, modal.node, modal.content, backgroundColor, textColor) {
        value = withContext(Dispatchers.Default) {
            val tex = readCodeFence(modal.node, modal.content)
            buildTexPainter(
                tex,
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

@Composable
private fun imageRequestInMarkdown(link: String, mediaMap: Map<String, MediaInfo>): ImageRequest {
    val client = LocalClient.current
    val size = mediaMap[link]?.dimension?.let {
        coil3.size.Size(it.width, it.height)
    } ?: coil3.size.Size.ORIGINAL
    val url = mediaMap[link]?.url
    return ImageRequest.Builder(LocalPlatformContext.current)
        .fetcherFactory(KtorNetworkFetcherFactory(client))
        .data(url)
        .size(size)
        .build()
}

class CustomCoil3ImageTransformerImpl(private val mediaMap: Map<String, MediaInfo>) : ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData {
        return rememberAsyncImagePainter(
            model = imageRequestInMarkdown(link, mediaMap)
        ).let { ImageData(it, modifier = Modifier.clip(RoundedCornerShape(10.dp)).fillMaxWidth()) }
    }
}
