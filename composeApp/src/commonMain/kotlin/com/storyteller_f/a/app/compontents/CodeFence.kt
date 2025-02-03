package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gosyer.appdirs.AppDirs
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
import com.storyteller_f.a.app.LocalToaster
import com.storyteller_f.a.app.pages.topic.TopicRoute
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.utils.MarkdownObject
import com.storyteller_f.shared.utils.getLang
import com.storyteller_f.shared.utils.readCodeFence
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes
import dev.zt64.compose.pdf.RemotePdfState
import dev.zt64.compose.pdf.component.PdfPage
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.asOutputStream
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.json.Json
import net.bjoernpetersen.m3u.M3uParser
import java.net.URI
import java.security.MessageDigest
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
        M3U8_MIMETYPE -> M3UView(obj, modal)

        else -> {
            val mediaInfo = mediaList1[obj.name]
            val url = mediaInfo?.url
            val contentType = mediaInfo?.item?.contentType
            when {
                url == null || contentType == null -> HighlightCodeBlock(modal)

                contentType == "application/pdf" -> PdfView(url)

                contentType.startsWith("video/") -> VideoView(
                    modifier = Modifier,
                    url,
                    contentType,
                    listOf(PlayItem(url, title = url))
                )

                contentType.startsWith("audio/") -> AudioView(url)

                else -> HighlightCodeBlock(modal)
            }
        }
    }
}

@Composable
private fun M3UView(
    obj: MarkdownObject,
    modal: MarkdownComponentModel
) {
    when {
        obj.url.trim().isEmpty() -> HighlightCodeBlock(modal)
        runCatching {
            Url(obj.url)
        }.getOrNull() == null -> HighlightCodeBlock(modal)

        else -> {
            val client = LocalClient.current
            val url = obj.url
            val contentType = obj.contentType
            val playList by produceState<List<PlayItem>>(emptyList()) {
                value = parseM3UPlayList(contentType, url, obj, client)
            }
            VideoView(modifier = Modifier, obj.url, M3U8_MIMETYPE, playList)
        }
    }
}

private suspend fun parseM3UPlayList(
    contentType: String,
    url: String,
    obj: MarkdownObject,
    client: HttpClient
): List<PlayItem> =
    if (contentType == M3U8_MIMETYPE && (url.startsWith("http://") || url.startsWith("https://")) && obj.isPlayList) {
        val entries = withContext(Dispatchers.IO) {
            val content = client.get(url).bodyAsText()
            M3uParser.parse(content)
        }
        entries.map {
            PlayItem(it.location.url.toString(), it.metadata["tvg-logo"], it.title)
        }.distinctBy {
            it.url
        }
    } else {
        listOf(PlayItem(url, "", url))
    }

@Composable
private fun PdfView(url: String) {
    val errorIndicator = rememberVectorPainter(Icons.Default.Error)
    val refreshIndicator = rememberVectorPainter(Icons.Default.Refresh)
    val state = remember(url, errorIndicator, refreshIndicator) {
        RemotePdfState(URI.create(url).toURL(), errorIndicator, refreshIndicator)
    }
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .clip(shape)
    ) {
        HorizontalPager(
            state = rememberPagerState { state.pageCount }
        ) { i ->
            PdfPage(
                state = state,
                index = i
            )
        }
        OpRow(null, "application/pdf", url)
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
        if (SystemFileSystem.exists(output)) {
            true to output
        } else {
            output.parent?.let {
                if (!SystemFileSystem.exists(it))
                    SystemFileSystem.createDirectories(it)
            }
            SystemFileSystem.sink(output).buffered().use {
                buildTexPainter(tex, backgroundColor, textColor, size, it.asOutputStream()) to output
            }
        }
    }
}

@Composable
private fun imageRequestInMarkdown(
    info: MediaInfo?
): ImageRequest {
    val client = LocalClient.current
    return ImageRequest.Builder(LocalPlatformContext.current)
        .fetcherFactory(KtorNetworkFetcherFactory(client))
        .data(info?.url)
        .size(coil3.size.Size.ORIGINAL)
        .build()
}

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

class CustomCoil3ImageTransformerImpl(private val mediaMap: Map<String, MediaInfo>) : ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData {
        val appNav = LocalAppNav.current
        val info = mediaMap[link]
        return if (link.startsWith("file:///")) {
            val painter = rememberAsyncImagePainter(model = link.substring(7))
            return ImageData(painter)
        } else {
            val model = imageRequestInMarkdown(info)
            val painter = rememberAsyncImagePainter(model = model)
            return ImageData(
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
    return (px / density).dp
}

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
    return (dp.value * density).toInt()
}

fun md5(input: String): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(input.toByteArray()) // 计算 MD5
    return digest.joinToString("") { "%02x".format(it) } // 转换为十六进制字符串
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpRow(
    showPlayList: (() -> Unit)? = null,
    contentType: String,
    url: String
) {
    val toasterState = LocalToaster.current
    FlowRow {
        val clipboardManager = LocalClipboardManager.current

        if (showPlayList != null) {
            IconButton({
                showPlayList()
            }) {
                Icon(Icons.AutoMirrored.Default.List, "playlist")
            }
        }
        IconButton({
            clipboardManager.setText(buildAnnotatedString {
                append(url)
            })
            toasterState.show("copied", duration = 1.seconds)
        }) {
            Icon(Icons.Default.ContentCopy, "copy list")
        }
        if (contentType != M3U8_MIMETYPE) {
            val uriHandler = LocalUriHandler.current
            IconButton({
                uriHandler.openUri(url)
            }) {
                Icon(Icons.Default.Download, "download")
            }
        }
    }
}
