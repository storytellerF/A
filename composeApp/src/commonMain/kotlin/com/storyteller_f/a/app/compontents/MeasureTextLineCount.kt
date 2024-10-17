package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * @param extra 输入框中包含无法控制的padding
 */
@Composable
fun MeasureTextLineCount(
    text: String,
    textStyle: TextStyle,
    extra: Dp,
    textComponent: @Composable (Int, Int) -> Unit
) {
    BoxWithConstraints {
        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val width = with(density) {
            (maxWidth - extra).roundToPx()
        }
        val lineCount = remember(textStyle, text, width) {
            textMeasurer.measure(
                text = text,
                style = textStyle,
                constraints = Constraints.fixedWidth(width)
            ).lineCount
        }
        val total = remember {
            (maxHeight.value / textStyle.lineHeight.value).toInt()
        }
        textComponent(lineCount, total)
    }
}
