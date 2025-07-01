package com.storyteller_f.a.app.compontents

import kotlinx.io.Sink
import kotlinx.io.asOutputStream
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

actual fun saveLatexToImage(
    tex: String,
    backgroundColor: Int,
    color: Int,
    textSize: Float,
    outputStream: Sink,
): Boolean {
    val formula = TeXFormula(tex)
    val image = formula.createBufferedImage(
        TeXConstants.STYLE_DISPLAY,
        textSize,
        Color(color),
        if (backgroundColor == 0) null else Color(backgroundColor, true)
    )
    return outputStream.asOutputStream().use { ImageIO.write(image as BufferedImage, "png", it) }
}
