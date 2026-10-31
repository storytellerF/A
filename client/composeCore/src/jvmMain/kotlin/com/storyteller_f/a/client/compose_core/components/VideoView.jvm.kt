/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.client.compose_core.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.storyteller_f.a.client.compose_core.Res
import com.storyteller_f.a.client.compose_core.media_playback_error
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

private val desktopVideoCoordination = Dispatchers.Default.limitedParallelism(1)

@Composable
actual fun VideoViewEmbed(remoteMediaItem: RemoteMediaItem) {
    DesktopMedia(remoteMediaItem, Modifier.aspectRatio(16f / 9), video = true)
}

@Composable
actual fun VideoViewFullScreen(remoteMediaItem: RemoteMediaItem) {
    DesktopMedia(remoteMediaItem, Modifier.fillMaxSize(), video = true)
}

@Composable
actual fun VideoViewFilled(remoteMediaItem: RemoteMediaItem) {
    DesktopMedia(remoteMediaItem, Modifier.fillMaxSize(), video = true)
}

@Composable
internal fun DesktopMedia(item: RemoteMediaItem, modifier: Modifier, video: Boolean) {
    val scope = rememberCoroutineScope()
    val host =
        remember(item.url, video, scope) {
            DesktopVideoHost(item.url, scope, desktopVideoCoordination, Dispatchers.IO) {
                VlcMediaPlayer(autoPlay = video)
            }
        }
    DisposableEffect(host) { onDispose { host.close() } }
    val state by host.state.collectAsState()
    val ready = state as? DesktopVideoState.Ready
    val bitmap by produceState<ImageBitmap?>(null, ready?.frame) {
        value =
            ready?.frame?.let { frame ->
                withContext(Dispatchers.Default) {
                    org.jetbrains.skia.Image.makeRaster(
                        ImageInfo(frame.width, frame.height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE),
                        frame.copyPixels(),
                        frame.rowBytes,
                    ).toComposeImageBitmap()
                }
            }
    }
    Column(modifier) {
        Box(Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
            if (!video) Text(item.title ?: item.name)
            bitmap?.let { image ->
                // VLC can scale decoder output to a different buffer aspect; restore the stream display ratio.
                val ratio = ready?.aspectRatio ?: image.width.toFloat() / image.height
                Image(
                    image,
                    contentDescription = "video frame ${item.name}",
                    modifier = Modifier.aspectRatio(ratio),
                    contentScale = ContentScale.FillBounds,
                )
            }
            when (state) {
                DesktopVideoState.Loading -> CircularProgressIndicator()
                DesktopVideoState.Failed -> Text(stringResource(Res.string.media_playback_error))
                is DesktopVideoState.Ready -> if (ready?.buffering == true) CircularProgressIndicator()
            }
        }
        IconButton(onClick = { if (ready?.playing == true) host.pause() else host.play() }, enabled = ready != null) {
            if (ready?.playing == true) {
                Icon(Icons.Default.PauseCircle, "pause")
            } else {
                Icon(Icons.Default.PlayCircle, "play")
            }
        }
    }
}

@Composable
actual fun rememberIsInPipMode(): Boolean = false
