package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ImageRequest
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.PlaceholderConfig
import com.storyteller_f.a.app.LocalAppNav
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
import kotlinx.io.asOutputStream
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.json.Json
import java.net.URI
import java.security.MessageDigest

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
    val paintState by rememberGeneratedLatexImage(modal)
    paintState?.let {
        if (it.isSuccess) {
            val (r, path) = it.getOrThrow()
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
    } ?: run {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(4.dp))
        }
    }
}

@Composable
fun rememberGeneratedLatexImage(modal: MarkdownComponentModel): State<Result<Pair<Boolean, Path>>?> {
    val textStyle = LocalTextStyle.current
    val size = textUnitToPx(textStyle.fontSize)
    val backgroundColor = 0
    val textColor = textStyle.color.value.toInt()
    return produceState<Result<Pair<Boolean, Path>>?>(
        null,
        modal.node,
        modal.content,
        backgroundColor,
        textColor
    ) {
        value = generateLatexImage(backgroundColor, textColor, size, readCodeFence(modal.node, modal.content))
    }
}

suspend fun generateLatexImage(
    backgroundColor: Int,
    textColor: Int,
    size: Float,
    tex: String
): Result<Pair<Boolean, Path>> = withContext(Dispatchers.Default) {
    runCatching {
        val key = md5(tex)
        val output = Path(SystemTemporaryDirectory, "${key}-${backgroundColor}-${textColor}-${size}.png")
        if (SystemFileSystem.exists(output)) {
            true to output
        } else {
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

    override fun placeholderConfig(density: Density, containerSize: Size, intrinsicImageSize: Size): PlaceholderConfig {
        return PlaceholderConfig(with(density) {
            when {
                containerSize.isUnspecified -> Size(180f, 180f)
                intrinsicImageSize.isUnspecified -> Size(containerSize.width.toSp().value, 180f)
                else -> {
                    val width = minOf(intrinsicImageSize.width, containerSize.width)
                    val height = if (intrinsicImageSize.width < containerSize.width) {
                        intrinsicImageSize.height
                    } else {
                        (intrinsicImageSize.height * containerSize.width) / intrinsicImageSize.width
                    }
                    Size(width.toSp().value, height.toSp().value)
                }
            }
        }, animate = false)
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
    return (px / density).sp
}

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