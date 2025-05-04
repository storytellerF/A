package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.storyteller_f.a.app.LocalClient
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.ui.MaterialSymbolsOutlined


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
        modifier, loading = {
            ImageLoading()
        }, error = {
            ImageError(it.result.throwable)
        }, contentScale = contentScale
    )
}

@Composable
fun ImageError(throwable: Throwable) {
    CustomIcon(IconRes.Font(MaterialSymbolsOutlined.Error)) {
        globalDialogState.showErrorState(throwable)
    }
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
        ImageRequest.Builder(platformContext).data(url).crossfade(true).fetcherFactory(
            KtorNetworkFetcherFactory(client)
        ).build()
    }
}
