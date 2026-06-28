package com.storyteller_f.a.app.core.utils

import androidx.compose.ui.graphics.ImageBitmap
import coil3.Image
import coil3.request.ImageRequest
import kotlinx.io.files.Path

actual fun Image.coilImageToImageBitmap(): Result<ImageBitmap> =
    Result.failure(UnsupportedOperationException("not supported on wasm"))

actual fun ImageRequest.Builder.androidAllowHardware(b: Boolean): ImageRequest.Builder = this

actual fun saveImageBitmap(
    imageBitmap: ImageBitmap,
    path: String,
    format: ImageFormat,
    quality: Int
): Result<Path> = Result.failure(UnsupportedOperationException("not supported on wasm"))
