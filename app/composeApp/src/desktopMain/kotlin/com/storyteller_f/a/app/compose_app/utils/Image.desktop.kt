package com.storyteller_f.a.app.compose_app.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import coil3.Image
import coil3.request.ImageRequest
import coil3.toBitmap
import com.storyteller_f.a.app.compose_app.utils.ImageFormat.*
import kotlinx.io.files.Path
import org.jetbrains.skia.EncodedImageFormat

actual fun Image.coilImageToImageBitmap(): Result<ImageBitmap> {
    return runCatching {
        toBitmap().asComposeImageBitmap()
    }
}

actual fun saveImageBitmap(
    imageBitmap: ImageBitmap,
    name: String,
    format: ImageFormat,
    quality: Int,
): Result<Path> {
    return runCatching {
        val data = imageBitmapToByteArray(imageBitmap, format, quality)
        writeImageFile(name, data)
    }
}

fun imageBitmapToByteArray(
    imageBitmap: ImageBitmap,
    format: ImageFormat = PNG,
    quality: Int = 100,
): ByteArray {
    // format.skiaEncodedFormat
    val skiaImage = org.jetbrains.skia.Image.makeFromBitmap(imageBitmap.asSkiaBitmap())
    return skiaImage.encodeToData(
        when (format) {
            PNG -> EncodedImageFormat.PNG
            JPEG -> EncodedImageFormat.JPEG
            WEBP -> EncodedImageFormat.WEBP
        }, quality
    )?.bytes ?: ByteArray(0)
}

actual fun ImageRequest.Builder.androidAllowHardware(b: Boolean): ImageRequest.Builder {
    return this
}
