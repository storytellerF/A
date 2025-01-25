package com.storyteller_f.a.app.pages.media

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.panpf.zoomimage.CoilZoomAsyncImage

@Composable
fun MediaPage(url: String) {
    CoilZoomAsyncImage(
        model = url,
        contentDescription = "view image",
        modifier = Modifier.fillMaxSize(),
    )
}
