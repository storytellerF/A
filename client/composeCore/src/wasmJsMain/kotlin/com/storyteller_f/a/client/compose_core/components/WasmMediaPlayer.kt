/*
 * This is a private project. All rights reserved.
 */

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.storyteller_f.a.client.compose_core.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage
import com.storyteller_f.a.client.compose_core.Res
import com.storyteller_f.a.client.compose_core.media_failed
import com.storyteller_f.a.client.compose_core.media_loading
import com.storyteller_f.a.client.compose_core.media_mute
import com.storyteller_f.a.client.compose_core.media_pause
import com.storyteller_f.a.client.compose_core.media_play
import com.storyteller_f.a.client.compose_core.media_position
import com.storyteller_f.a.client.compose_core.media_unmute
import com.storyteller_f.a.client.compose_core.media_volume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.webext.installPixelsFromArrayBuffer
import org.jetbrains.skiko.ExperimentalSkikoApi
import org.khronos.webgl.ArrayBuffer
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise

@JsModule("wasm-media-host")
private external fun createHost(
    url: String,
    onState: (MediaSnapshot) -> Unit,
    onFrame: (PixelFrame) -> Boolean,
    autoStart: Boolean,
    path: String,
): BrowserMediaHost

private external interface BrowserMediaHost : JsAny {
    fun start(): Promise<JsAny?>
    fun togglePlayback(): Promise<JsAny?>
    fun toggleMute()
    fun seek(time: Double): Promise<JsAny?>
    fun setVolume(volume: Double)
    fun failRendering()
    fun close(): Promise<JsAny?>
}

private external interface MediaSnapshot : JsAny {
    val status: String
    val position: Double
    val duration: Double
    val volume: Double
    val muted: Boolean
    val error: String
}

internal external interface PixelFrame : JsAny {
    val generation: Int
    val timestamp: Double
    val pixels: ArrayBuffer
    val width: Int
    val height: Int
}

@Composable
internal fun WasmMediaPlayer(item: RemoteMediaItem, modifier: Modifier, video: Boolean, fill: Boolean = false) {
    val gpu = remember { useGpuTextures() }
    val profiling = remember { profileMediaFrames() }
    val textureRenderer = remember(item.url, gpu) { if (gpu && video) WasmTextureRenderer() else null }
    var state by remember(item.url) { mutableStateOf<MediaSnapshot?>(null) }
    var pixels by remember(item.url) { mutableStateOf<PixelFrame?>(null) }
    var bitmap by remember(item.url) { mutableStateOf<Bitmap?>(null) }
    val host =
        remember(item.url) {
            createHost(item.url, { state = it }, { frame ->
                if (textureRenderer != null) {
                    textureRenderer.accept(frame)
                } else {
                    if (profiling) recordPixelStage("received", frame.timestamp)
                    pixels = frame
                    true
                }
            }, autoStart = false, path = if (textureRenderer != null) "gpu-texture" else "rgba-copy")
        }
    LaunchedEffect(pixels) {
        installMediaBitmap(pixels, profiling) { bitmap = it }
    }
    DisposableEffect(host) {
        textureRenderer?.setFailureHandler { host.failRendering() }
        host.start()
        onDispose {
            host.close()
            textureRenderer?.close()
        }
    }
    DisposableEffect(bitmap) {
        val ownedBitmap = bitmap
        onDispose { ownedBitmap?.close() }
    }
    Column(if (fill) modifier else Modifier) {
        if (video) {
            val videoModifier = if (fill) Modifier.weight(1f).fillMaxWidth() else modifier
            Box(videoModifier.background(Color.Black), contentAlignment = Alignment.Center) {
                val frame = bitmap
                if (textureRenderer != null) {
                    Canvas(Modifier.fillMaxSize()) {
                        textureRenderer.draw(drawContext.canvas.skiaCanvas, size) { host.failRendering() }
                    }
                } else if (frame != null) {
                    val composeImage = remember(frame) { frame.asComposeImageBitmap() }
                    Image(composeImage, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    AsyncImage(item.cover?.url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
        }
        MediaControls(state, host)
    }
}

@Composable
private fun MediaControls(state: MediaSnapshot?, host: BrowserMediaHost) {
    val status = state?.status ?: "loading"
    var seekDraft by remember { mutableStateOf<Float?>(null) }
    val positionLabel = stringResource(Res.string.media_position)
    val volumeLabel = stringResource(Res.string.media_volume)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = { host.togglePlayback() },
            enabled = status in listOf("playing", "buffering", "paused", "ended"),
        ) {
            val label = if (status in listOf("playing", "buffering")) Res.string.media_pause else Res.string.media_play
            Text(stringResource(label))
        }
        Button(onClick = { host.toggleMute() }) {
            Text(stringResource(if (state?.muted == true) Res.string.media_unmute else Res.string.media_mute))
        }
        Text("${state?.position?.toInt() ?: 0} / ${state?.duration?.toInt() ?: 0} s")
    }
    Slider(
        value = seekDraft ?: state?.position?.toFloat() ?: 0f,
        onValueChange = { seekDraft = it },
        onValueChangeFinished = {
            seekDraft?.let { host.seek(it.toDouble()) }
            seekDraft = null
        },
        valueRange = 0f..(state?.duration?.toFloat()?.takeIf { it > 0f } ?: 1f),
        enabled = status in listOf("playing", "buffering", "paused", "ended"),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = positionLabel },
    )
    Slider(
        value = state?.volume?.toFloat() ?: 1f,
        onValueChange = { host.setVolume(it.toDouble()) },
        modifier = Modifier.semantics { contentDescription = volumeLabel },
    )
    if (!state?.error.isNullOrEmpty()) Text(stringResource(Res.string.media_failed))
    if (status in listOf("loading", "buffering")) Text(stringResource(Res.string.media_loading))
}

@OptIn(ExperimentalSkikoApi::class)
private suspend fun PixelFrame.toSkiaBitmap(): Bitmap {
    val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL, ColorSpace.sRGB)
    val bitmap = Bitmap()
    return try {
        check(bitmap.installPixelsFromArrayBuffer(info, pixels, info.minRowBytes)) { "Cannot install media pixels" }
        bitmap.setImmutable()
    } catch (failure: Throwable) {
        bitmap.close()
        throw failure
    }
}

private fun useGpuTextures(): Boolean =
    js(
    "new URLSearchParams(globalThis.location.search).get('mediaPath') === 'gpu-texture'",
)

private suspend fun installMediaBitmap(frame: PixelFrame?, profiling: Boolean, publish: (Bitmap?) -> Unit) {
    if (frame == null) {
        publish(null)
        return
    }
    if (profiling) recordPixelStage("begin", frame.timestamp)
    val decoded = withContext(NonCancellable) { frame.toSkiaBitmap() }
    try {
        currentCoroutineContext().ensureActive()
        publish(decoded)
        if (profiling) recordPixelStage("published", frame.timestamp)
    } catch (cancelled: CancellationException) {
        decoded.close()
        if (profiling) recordPixelStage("cancelled", frame.timestamp)
        throw cancelled
    }
}

@JsFun("(stage, timestamp) => { performance.mark('media.rgba.' + stage, {detail:{timestamp}}); }")
private external fun recordPixelStage(stage: String, timestamp: Double)

private fun profileMediaFrames(): Boolean =
    js("new URLSearchParams(globalThis.location.search).get('mediaProfile') === 'true'")
