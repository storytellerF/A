package com.storyteller_f.a.app.compose_app.compontents

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ashampoo.kim.Kim
import com.ashampoo.kim.common.convertToPhotoMetadata
import com.mikepenz.markdown.model.ImageData
import com.storyteller_f.a.app.compose_app.AppConfig
import com.storyteller_f.a.app.compose_app.LocalClient
import com.storyteller_f.a.app.compose_app.ui.MaterialSymbolsOutlined
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.FileInfo
import io.github.aakira.napier.Napier
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlin.use

@Composable
fun CommonImage(
    model: String,
    contentDescription: String,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    SubcomposeAsyncImage(
        globalLoader(model),
        contentDescription = contentDescription,
        modifier,
        loading = {
            ImageLoading()
        },
        error = {
            ImageError()
        },
        contentScale = contentScale
    )
}

@Composable
fun ImageError() {
    CustomIcon(IconRes.Font(MaterialSymbolsOutlined.Error))
}

@Composable
fun ImageLoading() {
    Box(modifier = Modifier.fillMaxSize(), Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun globalLoader(url: String): ImageRequest {
    val client = LocalClient.current
    val platformContext = LocalPlatformContext.current
    return remember(url) {
        @Suppress("KotlinConstantConditions") val data =
            if (AppConfig.BUILD_TYPE == "prod") url.replace("http://", "https://") else url
        ImageRequest.Builder(platformContext).data(data).crossfade(true).fetcherFactory(
            KtorNetworkFetcherFactory(client)
        ).build()
    }
}

@Composable
fun CustomMarkdownImage(imageData: ImageData) {
    val painter = imageData.painter
    if (painter is AsyncImagePainter) {
        val state by painter.state.collectAsState()
        when (val s = state) {
            is AsyncImagePainter.State.Empty,
            is AsyncImagePainter.State.Loading -> {
                ImageLoading()
            }

            is AsyncImagePainter.State.Success -> {
                Image(
                    painter = painter,
                    contentDescription = imageData.contentDescription,
                    modifier = imageData.modifier,
                    alignment = imageData.alignment,
                    contentScale = imageData.contentScale,
                    alpha = imageData.alpha,
                    colorFilter = imageData.colorFilter
                )
            }

            is AsyncImagePainter.State.Error -> {
                Napier.e(s.result.throwable) {
                    "CustomMarkdownImage"
                }
                ImageError()
            }
        }
    } else {
        Image(
            painter = painter,
            contentDescription = imageData.contentDescription,
            modifier = imageData.modifier,
            alignment = imageData.alignment,
            contentScale = imageData.contentScale,
            alpha = imageData.alpha,
            colorFilter = imageData.colorFilter
        )
    }
}

fun getImageDimension(
    value: String,
    mediaMap: ImmutableMap<String, FileInfo>,
): Dimension? {
    if (!value.startsWith("file:///")) {
        return mediaMap[value]?.dimension
    }

    val metadata = SystemFileSystem.source(Path(value.substring(7))).buffered().use {
        Kim.readMetadata(it.readByteArray())?.convertToPhotoMetadata()
    } ?: return null

    val widthPx = metadata.widthPx
    val heightPx = metadata.heightPx
    if (widthPx == null || heightPx == null) {
        return null
    }
    return Dimension(widthPx, heightPx)
}
