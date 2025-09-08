package com.storyteller_f.a.app.compose_app.compontents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ImageRequest
import com.eygraber.uri.Uri
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.storyteller_f.a.app.compose_app.LocalAppNav
import com.storyteller_f.a.app.compose_app.LocalClient
import com.storyteller_f.a.app.compose_app.pages.topic.TopicRoute
import com.storyteller_f.shared.commonJson
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.utils.MarkdownObject
import com.storyteller_f.shared.utils.getLang
import com.storyteller_f.shared.utils.md5
import com.storyteller_f.shared.utils.readCodeFence
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import kotlinx.io.RawSink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory

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
    if (obj.contentType.isBlank()) {
        FileObjectBlock(obj, modal, mediaList)
    } else {
        CustomObjectBlock(obj, modal, mediaList)
    }
}

@Composable
private fun FileObjectBlock(
    obj: MarkdownObject,
    modal: MarkdownComponentModel,
    mediaList: Map<String, FileInfo>
) {
    val mediaInfo = mediaList[obj.name]
    val url = mediaInfo?.url
    val contentType = mediaInfo?.contentType
    when {
        contentType.isNullOrBlank() || url.isNullOrBlank() -> HighlightCodeBlock(modal)

        contentType == FileInfo.PDF_CONTENT_TYPE -> PdfViewBlock(url)

        contentType.startsWith("video/") -> {
            val coverInfo = mediaList[obj.cover]
            val obj1 =
                RemoteMediaItem(url, contentType, false, obj.name, coverInfo, obj.title)
            VideoView(obj1, true)
        }

        contentType.startsWith("audio/") -> {
            val coverInfo = mediaList[obj.cover]
            val obj1 =
                RemoteMediaItem(url, contentType, false, obj.name, coverInfo, obj.title)
            AudioView(obj1, true)
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
    val obj1 = RemoteMediaItem(
        obj.url,
        FileInfo.YOUTUBE_MIMETYPE,
        false,
        name,
        mediaList[obj.cover],
        obj.title
    )
    when (obj.contentType) {
        FileInfo.YOUTUBE_MIMETYPE -> {
            VideoView(obj1, true)
        }

        FileInfo.SOUND_CLOUD_MIME_TYPE -> {
            AudioView(obj1, true)
        }

        FileInfo.M3U8_MIMETYPE -> {
            VideoView(obj1, true)
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
    MarkdownHighlightedCodeFence(modal.content, modal.node, highlights = highlightsBuilder)
}

@Composable
private fun RefBlock(
    modal: MarkdownComponentModel
) {
    val (first, second) = remember(modal.node, modal.content) {
        val textInNode = readCodeFence(modal.node, modal.content)
        TopicRoute.Companion.parseRefUri(textInNode)
    }
    first?.let { it1 -> it1(second) }
}

@Composable
private fun LatexBlock(
    modal: MarkdownComponentModel
) {
    val paintState = rememberGeneratedLatexImage(modal)
    if (paintState.isSuccess) {
        val path = paintState.getOrThrow()
        if (path != null) {
            AsyncImage(
                model = path.toString(),
                contentDescription = "math",
            )
        } else {
            HighlightCodeBlock(modal)
        }
    } else {
        HighlightCodeBlock(modal)
    }
}

@Composable
fun rememberGeneratedLatexImage(modal: MarkdownComponentModel): Result<Path?> {
    val textStyle = LocalTextStyle.current
    val size = textUnitToPx(textStyle.fontSize)
    val backgroundColor = 0
    val textColor = textStyle.color.value.toInt()
    return remember(
        modal.node,
        modal.content,
        backgroundColor,
        textColor
    ) {
        generateLatexImage(
            backgroundColor,
            textColor,
            size,
            readCodeFence(modal.node, modal.content)
        )
    }
}

fun generateLatexImage(
    backgroundColor: Int,
    textColor: Int,
    size: Float,
    tex: String
): Result<Path?> {
    return runCatching {
        val key = md5(tex)
        val output =
            Path(SystemTemporaryDirectory, "latex/$key-$backgroundColor-$textColor-$size.png")
        Napier.i {
            "generate latex $tex to $output"
        }
        if (SystemFileSystem.exists(output)) {
            output
        } else {
            output.sink().buffered().use {
                if (saveLatexToImage(tex, backgroundColor, textColor, size, it)) {
                    output
                } else {
                    null
                }
            }
        }
    }
}

fun Path.sink(): RawSink {
    parent?.let {
        if (!SystemFileSystem.exists(it)) {
            SystemFileSystem.createDirectories(it)
        }
    }
    return SystemFileSystem.sink(this)
}

@Composable
fun imageRequestInMarkdown(
    info: FileInfo?
): ImageRequest {
    val client = LocalClient.current
    val context = LocalPlatformContext.current
    return imageRequest(context, client, info).build()
}

fun imageRequest(
    context: PlatformContext,
    client: HttpClient,
    info: FileInfo?
) = ImageRequest.Builder(context)
    .fetcherFactory(KtorNetworkFetcherFactory(client))
    .data(info?.url)

@Composable
fun getSize(info: FileInfo?): Pair<Float, Float>? {
    val dimension = info?.dimension
    val s = if (info != null && dimension != null) {
        val hDp = convertPxToDp(dimension.height).value
        val height = minOf(hDp, 200f)
        val width = dimension.width * height / dimension.height
        width to height
    } else {
        null
    }
    return s
}

class CustomCoil3ImageTransformerImpl(private val mediaMap: Map<String, FileInfo>) :
    ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData {
        val appNav = LocalAppNav.current
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
                    info?.let { it1 -> appNav.gotoMedia(info) }
                }
            )
        }
    }
}

@Composable
fun convertPxToDp(px: Int): Dp {
    // 获取当前屏幕密度
    val density = LocalDensity.current.density
    // 将像素值转换为 dp
    return pxToDp(px, density)
}

fun pxToDp(px: Int, density: Float) = (px / density).dp

@Composable
fun convertPxToSp(px: Int): TextUnit {
    // 获取当前屏幕密度
    val density = LocalDensity.current.density
    // 将像素值转换为 dp
    return pxToSp(px, density)
}

fun pxToSp(px: Int, density: Float): TextUnit = (px / density).sp

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
