package com.storyteller_f.a.app.core.utils

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.Image
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.io.asOutputStream
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory

actual fun Image.coilImageToImageBitmap(): Result<ImageBitmap> {
    return runCatching { toBitmap().asImageBitmap() }
}

actual fun saveImageBitmap(
    imageBitmap: ImageBitmap,
    name: String,
    format: ImageFormat,
    quality: Int
): Result<Path> {
    return runCatching {
        val path = Path(SystemTemporaryDirectory, "tmpImage/$name-cropped.png")
        path.safeSink().buffered().asOutputStream().use {
            imageBitmap.asAndroidBitmap().compress(
                when (format) {
                    ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                    ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
                    ImageFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        Bitmap.CompressFormat.PNG
                    }
                },
                quality,
                it
            )
        }
        path
    }
}

actual fun ImageRequest.Builder.androidAllowHardware(b: Boolean): ImageRequest.Builder {
    return allowHardware(b)
}
