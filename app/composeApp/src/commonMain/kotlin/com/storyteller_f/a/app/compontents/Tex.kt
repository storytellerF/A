package com.storyteller_f.a.app.compontents

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import kotlinx.io.Sink

expect fun saveLatexToImage(
    tex: String,
    backgroundColor: Int,
    color: Int,
    textSize: Float,
    outputStream: Sink
): Boolean

@Composable
fun textUnitToPx(textUnit: TextUnit): Float {
    val density = LocalDensity.current

    return textUnitToPx(textUnit, density)
}

fun textUnitToPx(textUnit: TextUnit, density: Density): Float {
    return if (textUnit.isSp) {
        with(density) {
            textUnit.toPx()
        }
    } else {
        0f
    }
}
