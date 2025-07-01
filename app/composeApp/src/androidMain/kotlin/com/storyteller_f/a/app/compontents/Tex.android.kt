package com.storyteller_f.a.app.compontents

import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.io.Sink
import kotlinx.io.asOutputStream
import ru.noties.jlatexmath.JLatexMathDrawable

actual fun saveLatexToImage(
    tex: String,
    backgroundColor: Int,
    color: Int,
    textSize: Float,
    outputStream: Sink,
): Boolean {
    val drawable = JLatexMathDrawable.builder(tex)
        .textSize(textSize)
        .padding(8)
        .background(backgroundColor)
        .align(JLatexMathDrawable.ALIGN_RIGHT)
        .build()
    val bitmap = drawable.toBitmap()
    return outputStream.asOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 80, it) }
}
