package com.storyteller_f.a.app.compose_app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.eygraber.uri.Uri
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.storyteller_f.a.app.compose_app.LocalAppNavFactory
import com.storyteller_f.a.app.compose_app.pages.topic.TopicRoute
import com.storyteller_f.a.app.core.utils.imageRequest
import com.storyteller_f.a.app.core.common.LocalClient
import com.storyteller_f.a.app.core.components.PdfViewBlock
import com.storyteller_f.a.app.core.components.convertPxToDp
import com.storyteller_f.a.app.core.components.getTexPath
import com.storyteller_f.a.app.core.components.textUnitToPx
import com.storyteller_f.shared.commonJson
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.utils.MarkdownObject
import com.storyteller_f.shared.utils.getLang
import com.storyteller_f.shared.utils.readCodeFence
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes

@Composable
fun CustomCodeFence(modal: MarkdownComponentModel, mediaList: Map<String, FileInfo>) {
    val lang = remember(modal.node, modal.content) {
        getLang(modal.node, modal.content)
    }
    when {
        listOf("com.storyteller_f.a", "c.s.a", "csa").contains(lang) -> RefBlock(modal)

        lang == "math" -> LatexBlock(modal)

        lang == "object" -> ObjectBlock(modal, mediaList)

        else -> HighlightCodeBlock(modal)
    }
}

@Composable
fun ObjectBlock(
    modal: MarkdownComponentModel,
    mediaList: Map<String, FileInfo>
) {
    val obj = remember(modal.node, modal.content) {
        val c = readCodeFence(modal.node, modal.content)
        commonJson.decodeFromString<MarkdownObject>(c)
    }
    if (obj.contentType.isNullOrBlank()) {
        FileObjectBlock(obj, modal, mediaList)
    } else {
        CustomObjectBlock(obj, modal, mediaList)
    }
}

@Composable
private fun FileObjectBlock(
    obj: MarkdownObject,
    modal: MarkdownComponentModel,
    mediaMap: Map<String, FileInfo>
) {
    val mediaInfo = mediaMap[obj.name]
    val url = mediaInfo?.url
    val contentType = mediaInfo?.contentType
    when {
        contentType.isNullOrBlank() || url.isNullOrBlank() -> HighlightCodeBlock(modal)

        contentType == FileInfo.PDF_CONTENT_TYPE -> PdfViewBlock(url)

        contentType.startsWith("video/") -> {
            val coverInfo = mediaMap[obj.cover]
            val obj1 =
                RemoteMediaItem(url, contentType, false, obj.name, coverInfo, obj.title)
            VideoView(obj1, false)
        }

        contentType.startsWith("audio/") -> {
            val coverInfo = mediaMap[obj.cover]
            val obj1 =
                RemoteMediaItem(url, contentType, false, obj.name, coverInfo, obj.title)
            AudioView(obj1, false)
        }

        else -> HighlightCodeBlock(modal)
    }
}

@Composable
private fun CustomObjectBlock(
    obj: MarkdownObject,
    modal: MarkdownComponentModel,
    mediaList: Map<String, FileInfo>
) {
    val name = when (obj.contentType) {
        FileInfo.YOUTUBE_MIMETYPE -> {
            "Youtube:${Uri.parse(obj.url).getQueryParameter("v")}"
        }

        FileInfo.SOUND_CLOUD_MIME_TYPE -> {
            "SoundCloud:${Uri.parse(obj.url).lastPathSegment}"
        }

        FileInfo.M3U8_MIMETYPE -> {
            "M3U:${Uri.parse(obj.url).lastPathSegment}"
        }

        else -> {
            HighlightCodeBlock(modal)
            return
        }
    }
    val mediaItem = RemoteMediaItem(
        obj.url,
        FileInfo.YOUTUBE_MIMETYPE,
        false,
        name,
        mediaList[obj.cover],
        obj.title
    )
    when (obj.contentType) {
        FileInfo.YOUTUBE_MIMETYPE -> {
            VideoView(mediaItem, false)
        }

        FileInfo.SOUND_CLOUD_MIME_TYPE -> {
            AudioView(mediaItem, false)
        }

        FileInfo.M3U8_MIMETYPE -> {
            VideoView(mediaItem, false)
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
    MarkdownHighlightedCodeFence(modal.content, modal.node, highlightsBuilder = highlightsBuilder)
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
    val typography = LocalMarkdownTypography.current
    val textStyle = typography.code
    val path = getTexPath(
        readCodeFence(modal.node, modal.content),
        textStyle.background.toArgb(),
        textStyle.color.toArgb(),
        textUnitToPx(textStyle.fontSize)
    )
    AsyncImage(model = path.toString(), contentDescription = "math")
}

@Composable
fun imageRequestInMarkdown(
    info: FileInfo?
): ImageRequest {
    val client = LocalClient.current
    val context = LocalPlatformContext.current
    return imageRequest(context, client, info).build()
}

@Composable
fun getSize(info: FileInfo?): Pair<Float, Float>? {
    val dimension = info?.dimension
    return if (info != null && dimension != null) {
        val hDp = convertPxToDp(dimension.height).value
        val height = minOf(hDp, 200f)
        val width = dimension.width * height / dimension.height
        width to height
    } else {
        null
    }
}

class CustomCoil3ImageTransformerImpl(private val mediaMap: Map<String, FileInfo>) :
    ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData {
        val appNavFactory = LocalAppNavFactory.current
        return if (link.startsWith("file:///")) {
            val model = link.substring(7)
            val painter = rememberAsyncImagePainter(model = model)
            ImageData(painter)
        } else {
            val info = mediaMap[link]
            val model = imageRequestInMarkdown(info)
            val painter = rememberAsyncImagePainter(model = model)
            ImageData(
                painter,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(info != null) {
                    info?.let { it1 -> appNavFactory.newAppNav().gotoMedia(it1) }
                }
            )
        }
    }
}

@Composable
fun ObjectBlock(maxHeight: Dp = 200.dp, block: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .heightIn(max = maxHeight)
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .clip(shape)

    ) {
        block()
    }
}
