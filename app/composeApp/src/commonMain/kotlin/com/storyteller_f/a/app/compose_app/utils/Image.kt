package com.storyteller_f.a.app.compose_app.utils

import androidx.compose.ui.graphics.ImageBitmap
import coil3.Image
import coil3.request.ImageRequest
import io.ktor.utils.io.core.*
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory

expect fun Image.coilImageToImageBitmap(): Result<ImageBitmap>

expect fun ImageRequest.Builder.androidAllowHardware(b: Boolean): ImageRequest.Builder

enum class ImageFormat {
    PNG,
    JPEG,
    WEBP
}

expect fun saveImageBitmap(
    imageBitmap: ImageBitmap,
    name: String,
    format: ImageFormat = ImageFormat.PNG,
    quality: Int = 90
): Result<Path>

fun writeImageFile(filePath: String, data: ByteArray): Path {
    val path = Path(SystemTemporaryDirectory, "tmpImage/$filePath-cropped.png")
    SystemFileSystem.sink(path).buffered().use {
        it.writeFully(data)
    }
    return path
}
