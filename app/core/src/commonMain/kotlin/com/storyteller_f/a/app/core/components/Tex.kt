package com.storyteller_f.a.app.core.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import com.storyteller_f.a.app.core.utils.safeSink
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
    backgroundColor: Int,
    textColor: Int,
    size: Float,
    tex: String
): Result<Path?> {
    return runCatching {
        val key = md5(tex)
        val output =
            Path(SystemTemporaryDirectory, "latex/$key-$backgroundColor-$textColor-$size.png")
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