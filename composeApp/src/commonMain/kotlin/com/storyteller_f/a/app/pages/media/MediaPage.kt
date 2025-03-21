package com.storyteller_f.a.app.pages.media

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.storyteller_f.a.app.MediaPlaySession
import com.storyteller_f.a.app.common.CenterBox
import com.storyteller_f.a.app.compontents.VideoView

@Composable
fun MediaPage(session: MediaPlaySession) {
    when (session) {
        is MediaPlaySession.Image -> {
            CoilZoomAsyncImage(
                model = session.mediaInfo.url,
                contentDescription = "view image",
                modifier = Modifier.fillMaxSize(),
            )
        }

        is MediaPlaySession.Video -> {
            CenterBox {
                VideoView(session.obj, session.coverMedia, false)
            }
        }
    }
}
