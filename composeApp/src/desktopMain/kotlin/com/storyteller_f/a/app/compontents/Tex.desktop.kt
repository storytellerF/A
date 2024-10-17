package com.storyteller_f.a.app.compontents

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPainter
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import java.awt.Color
import java.awt.image.BufferedImage

actual fun buildTexPainter(tex: String, backgroundColor: Int, color: Int, textSize: Float): Painter {
    val formula = TeXFormula(tex)
    val image = formula.createBufferedImage(TeXConstants.STYLE_DISPLAY, textSize, Color(color), Color(backgroundColor))
    return (image as BufferedImage).toPainter()
}
