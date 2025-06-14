package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
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
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.LocalClient
import com.storyteller_f.a.app.LocalJson
import com.storyteller_f.a.app.LocalToaster
import com.storyteller_f.a.app.pages.topic.TopicRoute
import com.storyteller_f.a.app.utils.setText
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.utils.MarkdownObject
import com.storyteller_f.shared.utils.getLang
import com.storyteller_f.shared.utils.md5
import com.storyteller_f.shared.utils.readCodeFence
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes
import dev.zt64.compose.pdf.RemotePdfState
import dev.zt64.compose.pdf.component.PdfPage
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.RawSink
import kotlinx.io.asOutputStream
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import net.bjoernpetersen.m3u.M3uParser
import java.net.URI
import kotlin.time.Duration.Companion.seconds

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

@Composable
fun ObjectBlock(
    modal: MarkdownComponentModel,
    mediaList1: Map<String, MediaInfo>
) {
    val json = LocalJson.current
    val obj = remember(modal.node, modal.content) {
        val c = readCodeFence(modal.node, modal.content)
        json.decodeFromString<MarkdownObject>(c)
    }
    when (obj.contentType) {
        YOUTUBE_MIMETYPE -> {
            val coverInfo = mediaList1[obj.cover]
            val name = "Youtube:${com.eygraber.uri.Uri.parse(obj.url).getQueryParameter("v")}"
            VideoView(
                RemoteMediaItem(obj.url, YOUTUBE_MIMETYPE, YOUTUBE_MIMETYPE, false, name, coverInfo, obj.title),
                true
            )
        }

        SOUND_CLOUD_MIME_TYPE -> {
            val coverInfo = mediaList1[obj.cover]
            val name = "SoundCloud:${com.eygraber.uri.Uri.parse(obj.url).lastPathSegment}"
            AudioView(
                RemoteMediaItem(
                    obj.url,
                    SOUND_CLOUD_MIME_TYPE,
                    SOUND_CLOUD_MIME_TYPE,
                    false,
                    name,
                    coverInfo,
                    obj.title
                ),
                true
            )
        }

        M3U8_MIMETYPE -> M3UView(obj, modal, mediaList1)

        else -> {
            val mediaInfo = mediaList1[obj.name]
            val url = mediaInfo?.url
            val contentType = mediaInfo?.contentType
            when {
                url == null || contentType == null -> HighlightCodeBlock(modal)

                contentType == "application/pdf" -> PdfView(url)

                contentType.startsWith("video/") -> {
                    val coverInfo = mediaList1[obj.cover]
                    VideoView(
                        RemoteMediaItem(url, contentType, obj.contentType, false, obj.name, coverInfo, obj.title),
                        true
                    )
                }

                contentType.startsWith("audio/") -> {
                    val coverInfo = mediaList1[obj.cover]
                    AudioView(
                        RemoteMediaItem(url, contentType, obj.contentType, false, obj.name, coverInfo, obj.title),
                        true
                    )
                }

                else -> HighlightCodeBlock(modal)
            }
        }
    }
}

@Composable
private fun M3UView(
    obj: MarkdownObject,
    modal: MarkdownComponentModel,
    mediaList1: Map<String, MediaInfo>,
) {
    when {
        obj.url.trim().isEmpty() -> HighlightCodeBlock(modal)
        runCatching {
            Url(obj.url)
        }.getOrNull() == null -> HighlightCodeBlock(modal)

        else -> {
            val coverInfo = mediaList1[obj.cover]
            VideoView(
                RemoteMediaItem(obj.url, M3U8_MIMETYPE, M3U8_MIMETYPE, obj.isPlayList, obj.url, coverInfo, obj.title),
                true
            )
        }
    }
}

suspend fun parseM3UPlayList(
    obj: RemoteMediaItem,
    client: HttpClient
): List<ConstPlayItem> =
    if ((obj.url.startsWith("http://") || obj.url.startsWith("https://")) && obj.isM3U8PlayList) {
        val entries = withContext(Dispatchers.IO) {
            val content = client.get(obj.url).bodyAsText()
            M3uParser.parse(content)
        }
        entries.map {
            ConstPlayItem(it.location.url.toString(), it.metadata["tvg-logo"], it.title)
        }.distinctBy {
            it.id
        }
    } else {
        listOf(ConstPlayItem(obj.url, "", obj.url))
    }

@Composable
private fun PdfView(url: String) {
    val errorIndicator = rememberVectorPainter(Icons.Default.Error)
    val refreshIndicator = rememberVectorPainter(Icons.Default.Refresh)
    val state = remember(url, errorIndicator, refreshIndicator) {
        RemotePdfState(URI.create(url).toURL(), errorIndicator, refreshIndicator)
    }
    ObjectBlock(300.dp) {
        HorizontalPager(
            state = rememberPagerState { state.pageCount },
            modifier = Modifier.weight(1f)
        ) { i ->
            PdfPage(state = state, index = i)
        }
        val toasterState = LocalToaster.current
        val scope = rememberCoroutineScope()
        FlowRow {
            val clipboardManager = LocalClipboard.current
            IconButton({
                scope.launch {
                    clipboardManager.setText(url)
                    toasterState.show("copied", duration = 1.seconds)
                }
            }) {
                Icon(Icons.Default.ContentCopy, "copy list")
            }
            val uriHandler = LocalUriHandler.current
            IconButton({
                uriHandler.openUri(url)
            }) {
                Icon(Icons.Default.Download, "download")
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
    MarkdownHighlightedCodeFence(modal.content, modal.node, highlights = highlightsBuilder)
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
    val paintState = rememberGeneratedLatexImage(modal)
    if (paintState.isSuccess) {
        val (r, path) = paintState.getOrThrow()
        if (r) {
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
fun rememberGeneratedLatexImage(modal: MarkdownComponentModel): Result<Pair<Boolean, Path>> {
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
        generateLatexImage(backgroundColor, textColor, size, readCodeFence(modal.node, modal.content))
    }
}

fun generateLatexImage(
    backgroundColor: Int,
    textColor: Int,
    size: Float,
    tex: String
): Result<Pair<Boolean, Path>> {
    return runCatching {
        val key = md5(tex)
        val output = Path(SystemTemporaryDirectory, "latex/$key-$backgroundColor-$textColor-$size.png")
        Napier.i {
            "output $output"
        }
        if (SystemFileSystem.exists(output)) {
            true to output
        } else {
            output.sink().buffered().use {
                buildTexPainter(tex, backgroundColor, textColor, size, it.asOutputStream()) to output
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
    info: MediaInfo?
): ImageRequest {
    val client = LocalClient.current
    val context = LocalPlatformContext.current
    return imageRequest(context, client, info).build()
}

fun imageRequest(
    context: PlatformContext,
    client: HttpClient,
    info: MediaInfo?
) = ImageRequest.Builder(context)
    .fetcherFactory(KtorNetworkFetcherFactory(client))
    .data(info?.url)

@Composable
fun getSize(info: MediaInfo?): Pair<Float, Float>? {
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

class CustomCoil3ImageTransformerImpl(private val mediaMap: Map<String, MediaInfo>, val objectTuple: ObjectTuple) :
    ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData {
        val appNav = LocalAppNav.current
        return if (link.startsWith("file:///")) {
            val painter = rememberAsyncImagePainter(model = link.substring(7))
            ImageData(painter)
        } else {
            val info = mediaMap[link]
            val model = imageRequestInMarkdown(info)
            val painter = rememberAsyncImagePainter(model = model)
            ImageData(
                painter,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(info != null) {
                    info?.let { it1 -> appNav.gotoMedia(info, objectTuple) }
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
fun convertDpToPx(dp: Dp): Int {
    val density = LocalDensity.current.density
    return dpToPx(dp, density)
}

fun dpToPx(dp: Dp, density: Float) = (dp.value * density).toInt()

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
