package com.storyteller_f.a.app.compontents

import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.OutputStream
import javax.imageio.ImageIO

actual fun buildTexPainter(
    tex: String,
    backgroundColor: Int,
    color: Int,
    textSize: Float,
    outputStream: OutputStream
): Boolean {
    val formula = TeXFormula(tex)
    val image = formula.createBufferedImage(TeXConstants.STYLE_DISPLAY, textSize, Color(color), Color(backgroundColor))
    return ImageIO.write(image as BufferedImage, "png", outputStream)
}
