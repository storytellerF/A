package com.storyteller_f.a.app.core.compontents

import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.tclement.fonticons.FontIcon
import dev.tclement.fonticons.ProvideIconParameters
sealed interface IconRes {
    data class Vector(val vector: ImageVector, val description: String = "") : IconRes
    data class Font(val char: Char, val description: String = "") : IconRes
}

fun Modifier.clickableIfNeed(onClick: (() -> Unit)?): Modifier {
    if (onClick != null) {
        return clickable {
            onClick.invoke()
        }
    }
    return this
}

@Composable
fun CustomIcon(icon: IconRes, onClick: (() -> Unit)? = null) {
    when (icon) {
        is IconRes.Font -> {
            ProvideIconParameters(
                size = 20.dp,
                tintProvider = LocalContentColor
            ) {
                FontIcon(icon.char, icon.description, modifier = Modifier.Companion.clickableIfNeed(onClick))
            }
        }

        is IconRes.Vector -> {
            Icon(
                imageVector = icon.vector,
                contentDescription = icon.description,
                modifier = Modifier.Companion.clickableIfNeed(onClick)
            )
        }
    }
}

@Composable
fun CustomIcon(vector: ImageVector, onClick: (() -> Unit)? = null) {
    CustomIcon(IconRes.Vector(vector), onClick)
}
