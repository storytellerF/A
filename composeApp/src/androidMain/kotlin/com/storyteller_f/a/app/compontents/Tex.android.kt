package com.storyteller_f.a.app.compontents

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.core.graphics.drawable.toBitmap
import ru.noties.jlatexmath.JLatexMathDrawable

actual fun buildTexPainter(tex: String, backgroundColor: Int, color: Int, textSize: Float): Painter {
    val drawable = JLatexMathDrawable.builder(tex)
        .textSize(textSize)
        .padding(8)
        .background(backgroundColor)
        .align(JLatexMathDrawable.ALIGN_RIGHT)
        .build()
    val bitmap = drawable.toBitmap()
    return BitmapPainter(bitmap.asImageBitmap())
}
