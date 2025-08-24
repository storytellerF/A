package com.storyteller_f.a.app.compose_app.compontents

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

@Composable
fun CustomIcon(icon: IconRes, onClick: (() -> Unit)? = null) {
    when (icon) {
        is IconRes.Font -> {
            ProvideIconParameters(
                size = 20.dp,
                tintProvider = LocalContentColor
            ) {
                FontIcon(icon.char, icon.description, modifier = Modifier.clickableIfNeed(onClick))
            }
        }

        is IconRes.Vector -> {
            Icon(
                imageVector = icon.vector,
                contentDescription = icon.description,
                modifier = Modifier.clickableIfNeed(onClick)
            )
        }
    }
}
