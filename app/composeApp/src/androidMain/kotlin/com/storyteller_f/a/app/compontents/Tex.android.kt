package com.storyteller_f.a.app.compontents

import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import ru.noties.jlatexmath.JLatexMathDrawable
import java.io.OutputStream

actual fun buildTexPainter(
    tex: String,
    backgroundColor: Int,
    color: Int,
    textSize: Float,
    outputStream: OutputStream
): Boolean {
    val drawable = JLatexMathDrawable.builder(tex)
        .textSize(textSize)
        .padding(8)
        .background(backgroundColor)
        .align(JLatexMathDrawable.ALIGN_RIGHT)
        .build()
    val bitmap = drawable.toBitmap()
    return bitmap.compress(Bitmap.CompressFormat.PNG, 80, outputStream)
}
