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
import com.mikepenz.markdown.model.ImageData
import com.storyteller_f.a.app.compose_app.LocalClient
import com.storyteller_f.a.app.compose_app.ui.MaterialSymbolsOutlined

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
        ImageRequest.Builder(platformContext).data(url.replace("http://", "https://")).crossfade(true).fetcherFactory(
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
