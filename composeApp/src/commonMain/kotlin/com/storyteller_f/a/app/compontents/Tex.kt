package com.storyteller_f.a.app.compontents

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit

expect fun buildTexPainter(tex: String, backgroundColor: Int, color: Int, textSize: Float) : Painter

@Composable
fun TextUnitToPx(textUnit: TextUnit): Float {
    val density = LocalDensity.current

    return if (textUnit.isSp) with(density) {
        textUnit.toPx()
    } else {
        0f
    }
}
