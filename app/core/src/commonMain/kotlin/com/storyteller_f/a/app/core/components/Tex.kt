package com.storyteller_f.a.app.core.components

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import com.storyteller_f.a.app.core.utils.safeSink
import com.storyteller_f.shared.utils.MathContext
import com.storyteller_f.shared.utils.md5
import io.github.aakira.napier.Napier
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.use

expect fun saveLatexToImage(
    tex: String,
    backgroundColor: Int,
    color: Int,
    textSize: Float,
    outputStream: Sink
): Boolean

fun generateLatexImage(
    style: TextStyle,
    density: Density,
    context: MathContext
): Result<Path?> {
    return generateLatexImage(
        style.background.toArgb(),
        style.color.toArgb(),
        textUnitToPx(style.fontSize, density),
        context.content
    )
}

fun generateLatexImage(
    backgroundColor: Int,
    textColor: Int,
    size: Float,
    tex: String
): Result<Path?> {
    return runCatching {
        val output = getTexPath(tex, backgroundColor, textColor, size)
        Napier.i {
            "generate latex $tex to $output"
        }
        if (SystemFileSystem.exists(output)) {
            output
        } else {
            output.safeSink().buffered().use {
                if (saveLatexToImage(tex, backgroundColor, textColor, size, it)) {
                    output
                } else {
                    null
                }
            }
        }
    }
}

fun getTexPath(
    tex: String,
    backgroundColor: Int,
    textColor: Int,
    size: Float
): Path {
    val key = md5(tex)
    return Path(SystemTemporaryDirectory, "latex/$key-$backgroundColor-$textColor-$size.png")
}
